import { Router, Response } from 'express';
import { z } from 'zod';
import * as fs from 'fs';
import * as path from 'path';
import multer from 'multer';
import { v4 as uuidv4 } from 'uuid';
import { query } from '../db';
import { requireAuth } from '../middleware/requireAuth';
import { AuthRequest, Session, Lap, CoachingInsight, ApiResponse } from '../types';
import { config } from '../config';
import { processSession } from '../lap';

const router = Router();

// Multer configuration for telemetry uploads
const storage = multer.diskStorage({
  destination: (req: AuthRequest, _file, cb) => {
    const userId = req.user?.id || 'anonymous';
    const uploadPath = path.join(config.upload.dir, userId);
    
    // Create user directory if it doesn't exist
    if (!fs.existsSync(uploadPath)) {
      fs.mkdirSync(uploadPath, { recursive: true });
    }
    
    cb(null, uploadPath);
  },
  filename: (req, _file, cb) => {
    const sessionId = req.body.sessionId || uuidv4();
    cb(null, `${sessionId}.jsonl`);
  },
});

const upload = multer({
  storage,
  limits: {
    fileSize: 50 * 1024 * 1024, // 50MB max
  },
  fileFilter: (_req, file, cb) => {
    if (
      file.mimetype === 'application/jsonl' ||
      file.mimetype === 'application/octet-stream' ||
      file.originalname.endsWith('.jsonl')
    ) {
      cb(null, true);
    } else {
      cb(new Error('Only JSONL files are allowed'));
    }
  },
});

// Validation schemas
const uploadSchema = z.object({
  sessionId: z.string().uuid().optional(),
  trackName: z.string().min(1).max(200),
  startedAt: z.coerce.number().positive(),
  endedAt: z.coerce.number().positive(),
  // Optional start/finish line coordinates (all 4 or none)
  startLineLat1: z.coerce.number().min(-90).max(90).optional(),
  startLineLng1: z.coerce.number().min(-180).max(180).optional(),
  startLineLat2: z.coerce.number().min(-90).max(90).optional(),
  startLineLng2: z.coerce.number().min(-180).max(180).optional(),
}).refine(
  (data) => {
    // Either all 4 startLine fields are present or none
    const fields = [data.startLineLat1, data.startLineLng1, data.startLineLat2, data.startLineLng2];
    const presentCount = fields.filter(f => f !== undefined).length;
    return presentCount === 0 || presentCount === 4;
  },
  { message: 'All 4 startLine coordinates must be provided together, or none at all' }
);

// Async lap processing (fire-and-forget)
async function processSessionAsync(sessionId: string): Promise<void> {
  try {
    console.log(`Starting async processing for session: ${sessionId}`);
    
    // Update status to PROCESSING
    await query(
      'UPDATE sessions SET processing_status = $1 WHERE id = $2',
      ['PROCESSING', sessionId]
    );
    
    // Run lap detection and coaching generation
    await processSession(sessionId);
    
    console.log(`Completed processing for session: ${sessionId}`);
  } catch (error) {
    console.error(`Error processing session ${sessionId}:`, error);
    
    await query(
      'UPDATE sessions SET processing_status = $1 WHERE id = $2',
      ['FAILED', sessionId]
    ).catch(console.error);
  }
}

// POST /telemetry/upload
router.post(
  '/upload',
  requireAuth,
  upload.single('file'),
  async (req: AuthRequest, res: Response): Promise<void> => {
    try {
      if (!req.user) {
        res.status(401).json({
          success: false,
          error: 'Not authenticated',
        } as ApiResponse);
        return;
      }
      
      if (!req.file) {
        res.status(400).json({
          success: false,
          error: 'Telemetry file is required',
        } as ApiResponse);
        return;
      }
      
      // Validate request body
      const validationResult = uploadSchema.safeParse(req.body);
      if (!validationResult.success) {
        // Delete uploaded file on validation failure
        fs.unlinkSync(req.file.path);
        
        res.status(400).json({
          success: false,
          error: 'Validation failed',
          details: validationResult.error.errors,
        } as ApiResponse);
        return;
      }
      
      const { trackName, startedAt, endedAt, startLineLat1, startLineLng1, startLineLat2, startLineLng2 } = validationResult.data;
      const sessionId = validationResult.data.sessionId || uuidv4();
      const userId = req.user.id;
      const rawFilePath = req.file.path;
      
      // Rename file to use sessionId if it was generated
      if (!validationResult.data.sessionId) {
        const newPath = path.join(
          path.dirname(rawFilePath),
          `${sessionId}.jsonl`
        );
        fs.renameSync(rawFilePath, newPath);
      }
      
      const finalFilePath = path.join(
        config.upload.dir,
        userId,
        `${sessionId}.jsonl`
      );
      
      // Insert or update session (with optional startLine coords)
      const result = await query<Session>(
        `INSERT INTO sessions (id, user_id, track_name, started_at, ended_at, raw_file_path, processing_status, start_line_lat1, start_line_lng1, start_line_lat2, start_line_lng2)
         VALUES ($1, $2, $3, to_timestamp($4::double precision / 1000), to_timestamp($5::double precision / 1000), $6, 'PENDING', $7, $8, $9, $10)
         ON CONFLICT (id) DO UPDATE SET
           track_name = EXCLUDED.track_name,
           started_at = EXCLUDED.started_at,
           ended_at = EXCLUDED.ended_at,
           raw_file_path = EXCLUDED.raw_file_path,
           processing_status = 'PENDING',
           start_line_lat1 = EXCLUDED.start_line_lat1,
           start_line_lng1 = EXCLUDED.start_line_lng1,
           start_line_lat2 = EXCLUDED.start_line_lat2,
           start_line_lng2 = EXCLUDED.start_line_lng2,
           updated_at = NOW()
         RETURNING id`,
        [sessionId, userId, trackName, startedAt, endedAt, finalFilePath, startLineLat1 ?? null, startLineLng1 ?? null, startLineLat2 ?? null, startLineLng2 ?? null]
      );
      
      const insertedSessionId = result.rows[0].id;
      
      // Fire-and-forget async processing
      processSessionAsync(insertedSessionId).catch(console.error);
      
      res.status(201).json({
        success: true,
        data: {
          sessionId: insertedSessionId,
          status: 'PROCESSING',
        },
      } as ApiResponse);
    } catch (error) {
      console.error('Upload error:', error);
      
      // Clean up file on error
      if (req.file && fs.existsSync(req.file.path)) {
        fs.unlinkSync(req.file.path);
      }
      
      res.status(500).json({
        success: false,
        error: 'Failed to upload telemetry',
      } as ApiResponse);
    }
  }
);

// GET /sessions
router.get(
  '/',
  requireAuth,
  async (req: AuthRequest, res: Response): Promise<void> => {
    try {
      if (!req.user) {
        res.status(401).json({
          success: false,
          error: 'Not authenticated',
        } as ApiResponse);
        return;
      }
      
      const result = await query<Session>(
        `SELECT 
           id,
           user_id as "userId",
           track_name as "trackName",
           started_at as "startedAt",
           ended_at as "endedAt",
           processing_status as "processingStatus",
           created_at as "createdAt"
         FROM sessions
         WHERE user_id = $1
         ORDER BY started_at DESC`,
        [req.user.id]
      );
      
      res.json({
        success: true,
        data: {
          sessions: result.rows,
        },
      } as ApiResponse);
    } catch (error) {
      console.error('List sessions error:', error);
      res.status(500).json({
        success: false,
        error: 'Failed to list sessions',
      } as ApiResponse);
    }
  }
);

// GET /sessions/:sessionId
router.get(
  '/:sessionId',
  requireAuth,
  async (req: AuthRequest, res: Response): Promise<void> => {
    try {
      if (!req.user) {
        res.status(401).json({
          success: false,
          error: 'Not authenticated',
        } as ApiResponse);
        return;
      }
      
      const { sessionId } = req.params;
      
      // Get session
      const sessionResult = await query<Session>(
        `SELECT 
           id,
           user_id as "userId",
           track_name as "trackName",
           started_at as "startedAt",
           ended_at as "endedAt",
           processing_status as "processingStatus",
           created_at as "createdAt"
         FROM sessions
         WHERE id = $1 AND user_id = $2`,
        [sessionId, req.user.id]
      );
      
      if (sessionResult.rows.length === 0) {
        res.status(404).json({
          success: false,
          error: 'Session not found',
        } as ApiResponse);
        return;
      }
      
      // Get laps
      const lapsResult = await query<Lap>(
        `SELECT 
           id,
           session_id as "sessionId",
           lap_number as "lapNumber",
           start_ts as "startTs",
           end_ts as "endTs",
           duration_ms as "durationMs",
           sector_1_ms as "sector1Ms",
           sector_2_ms as "sector2Ms",
           sector_3_ms as "sector3Ms",
           is_best_lap as "isBestLap"
         FROM laps
         WHERE session_id = $1
         ORDER BY lap_number ASC`,
        [sessionId]
      );
      
      // Get coaching insights
      const insightsResult = await query<CoachingInsight>(
        `SELECT 
           id,
           session_id as "sessionId",
           headline,
           detail,
           generated_at as "generatedAt"
         FROM coaching_insights
         WHERE session_id = $1
         ORDER BY generated_at DESC`,
        [sessionId]
      );
      
      res.json({
        success: true,
        data: {
          session: sessionResult.rows[0],
          laps: lapsResult.rows,
          coachingInsights: insightsResult.rows,
        },
      } as ApiResponse);
    } catch (error) {
      console.error('Get session error:', error);
      res.status(500).json({
        success: false,
        error: 'Failed to get session',
      } as ApiResponse);
    }
  }
);

// GET /sessions/:sessionId/laps
router.get(
  '/:sessionId/laps',
  requireAuth,
  async (req: AuthRequest, res: Response): Promise<void> => {
    try {
      if (!req.user) {
        res.status(401).json({
          success: false,
          error: 'Not authenticated',
        } as ApiResponse);
        return;
      }
      
      const { sessionId } = req.params;
      
      // Verify session belongs to user
      const sessionResult = await query<Session>(
        'SELECT id FROM sessions WHERE id = $1 AND user_id = $2',
        [sessionId, req.user.id]
      );
      
      if (sessionResult.rows.length === 0) {
        res.status(404).json({
          success: false,
          error: 'Session not found',
        } as ApiResponse);
        return;
      }
      
      // Get laps
      const lapsResult = await query<Lap>(
        `SELECT 
           id,
           session_id as "sessionId",
           lap_number as "lapNumber",
           start_ts as "startTs",
           end_ts as "endTs",
           duration_ms as "durationMs",
           sector_1_ms as "sector1Ms",
           sector_2_ms as "sector2Ms",
           sector_3_ms as "sector3Ms",
           is_best_lap as "isBestLap"
         FROM laps
         WHERE session_id = $1
         ORDER BY lap_number ASC`,
        [sessionId]
      );
      
      res.json({
        success: true,
        data: {
          laps: lapsResult.rows,
        },
      } as ApiResponse);
    } catch (error) {
      console.error('Get laps error:', error);
      res.status(500).json({
        success: false,
        error: 'Failed to get laps',
      } as ApiResponse);
    }
  }
);

export default router;
