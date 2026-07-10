import request from 'supertest';
import jwt from 'jsonwebtoken';
import * as fs from 'fs';
import * as path from 'path';
import app from '../src/app';

// Mock the database
jest.mock('../src/db', () => ({
  query: jest.fn(),
  getClient: jest.fn(),
  closePool: jest.fn(),
}));

import { query } from '../src/db';

const mockQuery = query as jest.MockedFunction<typeof query>;

describe('Telemetry Router', () => {
  const testToken = jwt.sign(
    { id: 'test-user-uuid', email: 'test@example.com', displayName: 'Test User' },
    'test-secret-key',
    { expiresIn: '1h' }
  );
  
  const testSessionId = '550e8400-e29b-41d4-a716-446655440000';
  
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('POST /telemetry/upload', () => {
    const uploadsDir = path.join(__dirname, '..', 'uploads', 'test-user-uuid');
    
    afterEach(() => {
      // Clean up test uploads
      if (fs.existsSync(uploadsDir)) {
        fs.rmSync(uploadsDir, { recursive: true, force: true });
      }
    });

    it('should upload telemetry file successfully', async () => {
      mockQuery.mockResolvedValueOnce({
        rows: [{ id: testSessionId }],
        rowCount: 1,
      });

      const telemetryContent = [
        '{"timestampMs":1000,"latitude":48.135,"longitude":11.582,"speedMs":25}',
        '{"timestampMs":1100,"latitude":48.136,"longitude":11.583,"speedMs":26}',
      ].join('\n');

      const response = await request(app)
        .post('/telemetry/upload')
        .set('Authorization', `Bearer ${testToken}`)
        .field('trackName', 'Nürburgring')
        .field('startedAt', '1704067200000')
        .field('endedAt', '1704070800000')
        .attach('file', Buffer.from(telemetryContent), 'telemetry.jsonl');

      expect(response.status).toBe(201);
      expect(response.body.success).toBe(true);
      expect(response.body.data.sessionId).toBeDefined();
      expect(response.body.data.status).toBe('PROCESSING');
    });

    it('should upload telemetry with startLine coordinates', async () => {
      mockQuery.mockResolvedValueOnce({
        rows: [{ id: testSessionId }],
        rowCount: 1,
      });

      const telemetryContent = [
        '{"timestampMs":1000,"latitude":48.135,"longitude":11.582,"speedMs":25}',
        '{"timestampMs":1100,"latitude":48.136,"longitude":11.583,"speedMs":26}',
      ].join('\n');

      const response = await request(app)
        .post('/telemetry/upload')
        .set('Authorization', `Bearer ${testToken}`)
        .field('trackName', 'Nürburgring GP')
        .field('startedAt', '1704067200000')
        .field('endedAt', '1704070800000')
        .field('startLineLat1', '48.13517')
        .field('startLineLng1', '11.5820')
        .field('startLineLat2', '48.13517')
        .field('startLineLng2', '11.5822')
        .attach('file', Buffer.from(telemetryContent), 'telemetry.jsonl');

      expect(response.status).toBe(201);
      expect(response.body.success).toBe(true);
      expect(response.body.data.sessionId).toBeDefined();
      expect(response.body.data.status).toBe('PROCESSING');
    });

    it('should reject upload with partial startLine coordinates', async () => {
      const telemetryContent = '{"timestampMs":1000,"latitude":48.135,"longitude":11.582,"speedMs":25}';

      const response = await request(app)
        .post('/telemetry/upload')
        .set('Authorization', `Bearer ${testToken}`)
        .field('trackName', 'Test Track')
        .field('startedAt', '1704067200000')
        .field('endedAt', '1704070800000')
        .field('startLineLat1', '48.13517')  // Only partial - missing lng1, lat2, lng2
        .attach('file', Buffer.from(telemetryContent), 'telemetry.jsonl');

      expect(response.status).toBe(400);
      expect(response.body.error).toBe('Validation failed');
    });

    it('should reject upload without authentication', async () => {
      const response = await request(app)
        .post('/telemetry/upload')
        .field('trackName', 'Test Track')
        .field('startedAt', '1704067200000')
        .field('endedAt', '1704070800000')
        .attach('file', Buffer.from('{}'), 'test.jsonl');

      expect(response.status).toBe(401);
    });

    it('should reject upload without file', async () => {
      const response = await request(app)
        .post('/telemetry/upload')
        .set('Authorization', `Bearer ${testToken}`)
        .field('trackName', 'Test Track')
        .field('startedAt', '1704067200000')
        .field('endedAt', '1704070800000');

      expect(response.status).toBe(400);
      expect(response.body.error).toBe('Telemetry file is required');
    });

    it('should reject upload with missing trackName', async () => {
      const response = await request(app)
        .post('/telemetry/upload')
        .set('Authorization', `Bearer ${testToken}`)
        .field('startedAt', '1704067200000')
        .field('endedAt', '1704070800000')
        .attach('file', Buffer.from('{}'), 'test.jsonl');

      expect(response.status).toBe(400);
      expect(response.body.error).toBe('Validation failed');
    });
  });

  describe('GET /sessions', () => {
    it('should list sessions for authenticated user', async () => {
      mockQuery.mockResolvedValueOnce({
        rows: [
          {
            id: testSessionId,
            userId: 'test-user-uuid',
            trackName: 'Nürburgring',
            startedAt: new Date(),
            endedAt: new Date(),
            processingStatus: 'COMPLETED',
            createdAt: new Date(),
          },
        ],
        rowCount: 1,
      });

      const response = await request(app)
        .get('/sessions')
        .set('Authorization', `Bearer ${testToken}`);

      expect(response.status).toBe(200);
      expect(response.body.success).toBe(true);
      expect(response.body.data.sessions).toHaveLength(1);
      expect(response.body.data.sessions[0].trackName).toBe('Nürburgring');
    });

    it('should reject without authentication', async () => {
      const response = await request(app).get('/sessions');

      expect(response.status).toBe(401);
    });
  });

  describe('GET /sessions/:sessionId', () => {
    it('should return session with laps and insights', async () => {
      // Mock session query
      mockQuery.mockResolvedValueOnce({
        rows: [{
          id: testSessionId,
          userId: 'test-user-uuid',
          trackName: 'Nürburgring',
          startedAt: new Date(),
          endedAt: new Date(),
          processingStatus: 'COMPLETED',
          createdAt: new Date(),
        }],
        rowCount: 1,
      });

      // Mock laps query
      mockQuery.mockResolvedValueOnce({
        rows: [
          {
            id: 'lap-1',
            sessionId: testSessionId,
            lapNumber: 1,
            durationMs: 90000,
            isBestLap: true,
          },
          {
            id: 'lap-2',
            sessionId: testSessionId,
            lapNumber: 2,
            durationMs: 92000,
            isBestLap: false,
          },
        ],
        rowCount: 2,
      });

      // Mock insights query
      mockQuery.mockResolvedValueOnce({
        rows: [{
          id: 'insight-1',
          sessionId: testSessionId,
          headline: 'Great corner entry',
          detail: 'You improved your braking point by 5 meters',
          generatedAt: new Date(),
        }],
        rowCount: 1,
      });

      const response = await request(app)
        .get(`/sessions/${testSessionId}`)
        .set('Authorization', `Bearer ${testToken}`);

      expect(response.status).toBe(200);
      expect(response.body.success).toBe(true);
      expect(response.body.data.session.trackName).toBe('Nürburgring');
      expect(response.body.data.laps).toHaveLength(2);
      expect(response.body.data.coachingInsights).toHaveLength(1);
    });

    it('should return 404 for non-existent session', async () => {
      mockQuery.mockResolvedValueOnce({
        rows: [],
        rowCount: 0,
      });

      const response = await request(app)
        .get('/sessions/non-existent-id')
        .set('Authorization', `Bearer ${testToken}`);

      expect(response.status).toBe(404);
      expect(response.body.error).toBe('Session not found');
    });
  });

  describe('GET /sessions/:sessionId/laps', () => {
    it('should return laps for session', async () => {
      // Mock session check
      mockQuery.mockResolvedValueOnce({
        rows: [{ id: testSessionId }],
        rowCount: 1,
      });

      // Mock laps query
      mockQuery.mockResolvedValueOnce({
        rows: [
          {
            id: 'lap-1',
            sessionId: testSessionId,
            lapNumber: 1,
            startTs: 1000,
            endTs: 91000,
            durationMs: 90000,
            sector1Ms: 30000,
            sector2Ms: 30000,
            sector3Ms: 30000,
            isBestLap: true,
          },
        ],
        rowCount: 1,
      });

      const response = await request(app)
        .get(`/sessions/${testSessionId}/laps`)
        .set('Authorization', `Bearer ${testToken}`);

      expect(response.status).toBe(200);
      expect(response.body.success).toBe(true);
      expect(response.body.data.laps).toHaveLength(1);
      expect(response.body.data.laps[0].lapNumber).toBe(1);
      expect(response.body.data.laps[0].isBestLap).toBe(true);
    });
  });
});
