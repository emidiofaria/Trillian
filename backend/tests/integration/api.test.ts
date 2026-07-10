import request from 'supertest';
import { Pool } from 'pg';
import * as fs from 'fs';
import * as path from 'path';
import app from '../../src/app';
import { generateSyntheticLaps, samplesToJsonl, generateSingleLap } from './testHelpers';

// Use test database
const TEST_DATABASE_URL = process.env.DATABASE_URL_TEST || 'postgresql://localhost:5432/driving_coach_test';

// Mock Anthropic SDK for coaching tests
jest.mock('@anthropic-ai/sdk', () => {
  return jest.fn().mockImplementation(() => ({
    messages: {
      create: jest.fn().mockResolvedValue({
        content: [{
          type: 'text',
          text: JSON.stringify([
            { headline: 'Brake later into T1', detail: 'You are braking 15m too early at turn 1. Trail brake to the apex for 0.3s improvement.' },
            { headline: 'Smoother throttle in S2', detail: 'Sector 2 shows aggressive throttle inputs. Smooth application will improve traction.' },
            { headline: 'Use full track width', detail: 'You are not using the full track at turns 3 and 5. Use kerbs for optimal line.' },
            { headline: 'Consistent lap rhythm', detail: 'Your lap 3 was 0.5s faster. Focus on replicating that lap rhythm consistently.' },
          ])
        }]
      })
    }
  }));
});

describe('Backend API Integration Tests', () => {
  let pool: Pool;
  let testUserId: string;
  let testUserToken: string;

  beforeAll(async () => {
    // Connect to test database
    pool = new Pool({ connectionString: TEST_DATABASE_URL });
    
    // Run migrations
    const schemaPath = path.join(__dirname, '../../src/db/schema.sql');
    const schema = fs.readFileSync(schemaPath, 'utf8');
    await pool.query(schema);
    
    // Ensure upload directory exists
    const uploadDir = path.join(__dirname, '../../uploads/test');
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }
  });

  afterAll(async () => {
    // Truncate all tables
    await pool.query('TRUNCATE TABLE coaching_insights, laps, sessions, users CASCADE');
    
    // Close connection
    await pool.end();
    
    // Clean up test uploads
    const uploadDir = path.join(__dirname, '../../uploads/test');
    if (fs.existsSync(uploadDir)) {
      fs.rmSync(uploadDir, { recursive: true, force: true });
    }
  });

  beforeEach(async () => {
    // Clear tables before each test
    await pool.query('TRUNCATE TABLE coaching_insights, laps, sessions, users CASCADE');
  });

  describe('Auth Flow', () => {
    const testEmail = 'test@example.com';
    const testPassword = 'SecurePassword123!';

    it('POST /auth/register with valid email/password returns 201 and token', async () => {
      const response = await request(app)
        .post('/auth/register')
        .send({
          email: testEmail,
          password: testPassword,
          displayName: 'Test User'
        })
        .expect(201);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toHaveProperty('token');
      expect(response.body.data).toHaveProperty('userId');
      expect(typeof response.body.data.token).toBe('string');
      expect(response.body.data.token.length).toBeGreaterThan(0);

      testUserToken = response.body.data.token;
      testUserId = response.body.data.userId;
    });

    it('POST /auth/login with valid credentials returns 200 and token', async () => {
      // First register
      await request(app)
        .post('/auth/register')
        .send({ email: testEmail, password: testPassword, displayName: 'Test User' });

      // Then login
      const response = await request(app)
        .post('/auth/login')
        .send({ email: testEmail, password: testPassword })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toHaveProperty('token');
      expect(typeof response.body.data.token).toBe('string');

      testUserToken = response.body.data.token;
    });

    it('GET /auth/me with valid token returns 200 and user data', async () => {
      // Register and get token
      const registerRes = await request(app)
        .post('/auth/register')
        .send({ email: testEmail, password: testPassword, displayName: 'Test User' });
      
      const token = registerRes.body.data.token;

      // Get user profile
      const response = await request(app)
        .get('/auth/me')
        .set('Authorization', `Bearer ${token}`)
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.data).toHaveProperty('email', testEmail);
      expect(response.body.data).toHaveProperty('displayName', 'Test User');
    });

    it('GET /auth/me without token returns 401', async () => {
      const response = await request(app)
        .get('/auth/me')
        .expect(401);

      expect(response.body.success).toBe(false);
      expect(response.body.error).toContain('Unauthorized');
    });

    it('GET /auth/me with invalid token returns 401', async () => {
      const response = await request(app)
        .get('/auth/me')
        .set('Authorization', 'Bearer invalid-token-here')
        .expect(401);

      expect(response.body.success).toBe(false);
    });
  });

  describe('Upload and Processing', () => {
    let authToken: string;
    let userId: string;

    beforeEach(async () => {
      // Create test user
      const registerRes = await request(app)
        .post('/auth/register')
        .send({
          email: `test-${Date.now()}@example.com`,
          password: 'TestPassword123!',
          displayName: 'Test Driver'
        });

      authToken = registerRes.body.data.token;
      userId = registerRes.body.data.userId;
    });

    it('POST /telemetry/upload with synthetic 5-lap JSONL returns 201', async () => {
      // Generate synthetic telemetry for 5 laps
      const samples = generateSyntheticLaps(
        5,           // 5 laps
        60000,       // 60 seconds per lap
        48.1351,     // Munich latitude
        11.5820      // Munich longitude
      );

      const jsonlContent = samplesToJsonl(samples);
      const tempFilePath = path.join(__dirname, `test-telemetry-${Date.now()}.jsonl`);
      fs.writeFileSync(tempFilePath, jsonlContent);

      try {
        const response = await request(app)
          .post('/telemetry/upload')
          .set('Authorization', `Bearer ${authToken}`)
          .field('sessionId', 'test-session-' + Date.now())
          .field('trackName', 'Test Circuit Munich')
          .field('startedAt', Date.now() - 400000)
          .field('endedAt', Date.now())
          .attach('file', tempFilePath)
          .expect(201);

        expect(response.body.success).toBe(true);
        expect(response.body.data).toHaveProperty('sessionId');
        expect(response.body.data.status).toBe('PROCESSING');
      } finally {
        // Cleanup temp file
        if (fs.existsSync(tempFilePath)) {
          fs.unlinkSync(tempFilePath);
        }
      }
    });

    it('GET /sessions returns uploaded sessions', async () => {
      // Upload a session first
      const samples = generateSyntheticLaps(5, 60000, 48.1351, 11.5820);
      const jsonlContent = samplesToJsonl(samples);
      const tempFilePath = path.join(__dirname, `test-telemetry-${Date.now()}.jsonl`);
      fs.writeFileSync(tempFilePath, jsonlContent);

      try {
        await request(app)
          .post('/telemetry/upload')
          .set('Authorization', `Bearer ${authToken}`)
          .field('sessionId', 'session-' + Date.now())
          .field('trackName', 'Test Track')
          .field('startedAt', Date.now() - 400000)
          .field('endedAt', Date.now())
          .attach('file', tempFilePath);

        // Get sessions
        const response = await request(app)
          .get('/sessions')
          .set('Authorization', `Bearer ${authToken}`)
          .expect(200);

        expect(response.body.success).toBe(true);
        expect(Array.isArray(response.body.data)).toBe(true);
        expect(response.body.data.length).toBeGreaterThanOrEqual(1);
        expect(response.body.data[0]).toHaveProperty('trackName', 'Test Track');
      } finally {
        if (fs.existsSync(tempFilePath)) {
          fs.unlinkSync(tempFilePath);
        }
      }
    });

    it('processes session and detects 5 laps with coaching insights', async () => {
      // Generate and upload
      const samples = generateSyntheticLaps(5, 60000, 48.1351, 11.5820);
      const jsonlContent = samplesToJsonl(samples);
      const tempFilePath = path.join(__dirname, `test-telemetry-${Date.now()}.jsonl`);
      fs.writeFileSync(tempFilePath, jsonlContent);

      let sessionId: string;

      try {
        const uploadRes = await request(app)
          .post('/telemetry/upload')
          .set('Authorization', `Bearer ${authToken}`)
          .field('sessionId', 'session-' + Date.now())
          .field('trackName', 'Processing Test Track')
          .field('startedAt', Date.now() - 400000)
          .field('endedAt', Date.now())
          .attach('file', tempFilePath);

        sessionId = uploadRes.body.data.sessionId;

        // Poll for processing completion (max 15 seconds)
        let processingComplete = false;
        let sessionData: any;
        const startTime = Date.now();
        const maxWaitMs = 15000;

        while (!processingComplete && Date.now() - startTime < maxWaitMs) {
          const sessionRes = await request(app)
            .get(`/sessions/${sessionId}`)
            .set('Authorization', `Bearer ${authToken}`);

          sessionData = sessionRes.body.data;

          if (sessionData.processingStatus === 'COMPLETED' || sessionData.processingStatus === 'FAILED') {
            processingComplete = true;
          } else {
            await new Promise(resolve => setTimeout(resolve, 500));
          }
        }

        // Assert processing completed
        expect(sessionData.processingStatus).toBe('COMPLETED');

        // Assert 5 laps detected
        expect(sessionData.laps).toHaveLength(5);

        // Assert one lap is marked as best
        const bestLaps = sessionData.laps.filter((l: any) => l.isBestLap);
        expect(bestLaps.length).toBe(1);

        // Assert coaching insights (3-5 expected from mock)
        expect(sessionData.coachingInsights.length).toBeGreaterThanOrEqual(3);
        expect(sessionData.coachingInsights.length).toBeLessThanOrEqual(5);
        expect(sessionData.coachingInsights[0]).toHaveProperty('headline');
        expect(sessionData.coachingInsights[0]).toHaveProperty('detail');

      } finally {
        if (fs.existsSync(tempFilePath)) {
          fs.unlinkSync(tempFilePath);
        }
      }
    }, 20000); // Increase timeout for this test
  });

  describe('Lap Detection Edge Cases', () => {
    let authToken: string;

    beforeEach(async () => {
      const registerRes = await request(app)
        .post('/auth/register')
        .send({
          email: `edge-test-${Date.now()}@example.com`,
          password: 'TestPassword123!',
          displayName: 'Edge Test Driver'
        });

      authToken = registerRes.body.data.token;
    });

    it('upload with only 1 complete lap results in FAILED status', async () => {
      // Generate single lap (insufficient for lap detection)
      const samples = generateSingleLap(60000, 48.1351, 11.5820);
      const jsonlContent = samplesToJsonl(samples);
      const tempFilePath = path.join(__dirname, `single-lap-${Date.now()}.jsonl`);
      fs.writeFileSync(tempFilePath, jsonlContent);

      let sessionId: string;

      try {
        const uploadRes = await request(app)
          .post('/telemetry/upload')
          .set('Authorization', `Bearer ${authToken}`)
          .field('sessionId', 'single-lap-' + Date.now())
          .field('trackName', 'Single Lap Track')
          .field('startedAt', Date.now() - 100000)
          .field('endedAt', Date.now())
          .attach('file', tempFilePath);

        sessionId = uploadRes.body.data.sessionId;

        // Wait for processing
        let processingComplete = false;
        let sessionData: any;
        const startTime = Date.now();

        while (!processingComplete && Date.now() - startTime < 10000) {
          const sessionRes = await request(app)
            .get(`/sessions/${sessionId}`)
            .set('Authorization', `Bearer ${authToken}`);

          sessionData = sessionRes.body.data;

          if (sessionData.processingStatus === 'COMPLETED' || sessionData.processingStatus === 'FAILED') {
            processingComplete = true;
          } else {
            await new Promise(resolve => setTimeout(resolve, 500));
          }
        }

        // Should fail because insufficient laps
        expect(sessionData.processingStatus).toBe('FAILED');
        expect(sessionData.laps).toHaveLength(0);

      } finally {
        if (fs.existsSync(tempFilePath)) {
          fs.unlinkSync(tempFilePath);
        }
      }
    }, 15000);

    it('upload with empty file returns 400 error', async () => {
      const tempFilePath = path.join(__dirname, `empty-${Date.now()}.jsonl`);
      fs.writeFileSync(tempFilePath, ''); // Empty file

      try {
        const response = await request(app)
          .post('/telemetry/upload')
          .set('Authorization', `Bearer ${authToken}`)
          .field('sessionId', 'empty-session-' + Date.now())
          .field('trackName', 'Empty Track')
          .field('startedAt', Date.now() - 100000)
          .field('endedAt', Date.now())
          .attach('file', tempFilePath)
          .expect(400);

        expect(response.body.success).toBe(false);
        expect(response.body.error).toMatch(/empty|invalid|no samples/i);

      } finally {
        if (fs.existsSync(tempFilePath)) {
          fs.unlinkSync(tempFilePath);
        }
      }
    });

    it('upload without file returns 400 error', async () => {
      const response = await request(app)
        .post('/telemetry/upload')
        .set('Authorization', `Bearer ${authToken}`)
        .field('sessionId', 'no-file-' + Date.now())
        .field('trackName', 'No File Track')
        .field('startedAt', Date.now() - 100000)
        .field('endedAt', Date.now())
        .expect(400);

      expect(response.body.success).toBe(false);
    });

    it('upload without auth returns 401', async () => {
      const samples = generateSyntheticLaps(5, 60000, 48.1351, 11.5820);
      const jsonlContent = samplesToJsonl(samples);
      const tempFilePath = path.join(__dirname, `unauth-${Date.now()}.jsonl`);
      fs.writeFileSync(tempFilePath, jsonlContent);

      try {
        await request(app)
          .post('/telemetry/upload')
          .field('sessionId', 'unauth-' + Date.now())
          .field('trackName', 'Unauth Track')
          .field('startedAt', Date.now() - 400000)
          .field('endedAt', Date.now())
          .attach('file', tempFilePath)
          .expect(401);
      } finally {
        if (fs.existsSync(tempFilePath)) {
          fs.unlinkSync(tempFilePath);
        }
      }
    });
  });
});
