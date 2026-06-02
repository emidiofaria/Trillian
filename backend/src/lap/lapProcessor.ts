import { query } from '../db';
import { parseJsonlFile } from '../telemetry/telemetryService';
import {
  detectLaps,
  computeSectors,
  findBestLap,
  LapDetectionError,
  DetectedLap,
} from './lapDetector';
import { generateCoaching as generateAICoaching } from '../coaching';
import { Session } from '../types';

/**
 * Processes a session: detects laps, computes sectors, and stores results.
 */
export async function processSession(sessionId: string): Promise<void> {
  console.log(`Processing session: ${sessionId}`);
  
  try {
    // Step 1: Load session from DB
    const sessionResult = await query<Session>(
      'SELECT id, raw_file_path as "rawFilePath" FROM sessions WHERE id = $1',
      [sessionId]
    );
    
    if (sessionResult.rows.length === 0) {
      throw new Error(`Session not found: ${sessionId}`);
    }
    
    const session = sessionResult.rows[0];
    
    if (!session.rawFilePath) {
      throw new Error(`Session ${sessionId} has no telemetry file`);
    }
    
    // Step 2: Parse JSONL file
    console.log(`Parsing telemetry file: ${session.rawFilePath}`);
    const samples = await parseJsonlFile(session.rawFilePath);
    
    if (samples.length === 0) {
      throw new Error(`No telemetry samples found in file`);
    }
    
    console.log(`Loaded ${samples.length} samples`);
    
    // Step 3: Detect laps
    let laps: DetectedLap[];
    try {
      laps = detectLaps(samples);
      console.log(`Detected ${laps.length} laps`);
    } catch (error) {
      if (error instanceof LapDetectionError) {
        console.warn(`Lap detection failed: ${error.message}`);
        // Update status and return without failing completely
        await query(
          'UPDATE sessions SET processing_status = $1 WHERE id = $2',
          ['NO_LAPS', sessionId]
        );
        return;
      }
      throw error;
    }
    
    // Step 4: Compute sectors for each lap
    const lapsWithSectors = laps.map((lap) => ({
      ...lap,
      sectors: computeSectors(lap),
    }));
    
    // Step 5: Find best lap
    const bestLap = findBestLap(laps);
    const bestLapNumber = bestLap?.lapNumber ?? 0;
    
    console.log(`Best lap: #${bestLapNumber} (${bestLap?.durationMs}ms)`);
    
    // Step 6: Delete existing laps for this session (in case of reprocessing)
    await query('DELETE FROM laps WHERE session_id = $1', [sessionId]);
    
    // Step 7: Insert laps into DB
    for (const lap of lapsWithSectors) {
      await query(
        `INSERT INTO laps (
          session_id, lap_number, start_ts, end_ts, duration_ms,
          sector_1_ms, sector_2_ms, sector_3_ms, is_best_lap
        ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
        [
          sessionId,
          lap.lapNumber,
          lap.startTs,
          lap.endTs,
          lap.durationMs,
          lap.sectors.sector1Ms,
          lap.sectors.sector2Ms,
          lap.sectors.sector3Ms,
          lap.lapNumber === bestLapNumber,
        ]
      );
    }
    
    // Step 8: Update status to LAPS_DONE
    await query(
      'UPDATE sessions SET processing_status = $1 WHERE id = $2',
      ['LAPS_DONE', sessionId]
    );
    
    // Step 9: Trigger AI coaching generation
    try {
      await generateAICoaching(sessionId);
    } catch (coachingError) {
      console.error(`Coaching generation failed: ${coachingError}`);
      // Continue - coaching failure shouldn't fail the whole process
    }
    
    // Step 10: Update status to COMPLETE
    await query(
      'UPDATE sessions SET processing_status = $1 WHERE id = $2',
      ['COMPLETE', sessionId]
    );
    
    console.log(`Session ${sessionId} processing complete`);
  } catch (error) {
    console.error(`Error processing session ${sessionId}:`, error);
    
    // Update status to FAILED
    await query(
      'UPDATE sessions SET processing_status = $1 WHERE id = $2',
      ['FAILED', sessionId]
    ).catch(console.error);
    
    throw error;
  }
}

/**
 * Formats milliseconds as a lap time string (M:SS.mmm or SS.mmm).
 */
function formatLapTime(ms: number): string {
  const totalSeconds = ms / 1000;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  
  if (minutes > 0) {
    return `${minutes}:${seconds.toFixed(3).padStart(6, '0')}`;
  }
  return `${seconds.toFixed(3)}s`;
}

export { formatLapTime };
