import request from 'supertest';
import jwt from 'jsonwebtoken';
import app from '../src/app';

// Mock the database
jest.mock('../src/db', () => ({
  query: jest.fn(),
  getClient: jest.fn(),
  closePool: jest.fn(),
}));

import { query } from '../src/db';

const mockQuery = query as jest.MockedFunction<typeof query>;

describe('Auth Router', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('POST /auth/register', () => {
    it('should register a new user successfully', async () => {
      // Mock: no existing user
      mockQuery.mockResolvedValueOnce({ rows: [], rowCount: 0 });
      
      // Mock: insert user
      mockQuery.mockResolvedValueOnce({
        rows: [{
          id: 'test-uuid',
          email: 'test@example.com',
          displayName: 'Test User',
          createdAt: new Date(),
        }],
        rowCount: 1,
      });

      const response = await request(app)
        .post('/auth/register')
        .send({
          email: 'test@example.com',
          password: 'password123',
          displayName: 'Test User',
        });

      expect(response.status).toBe(201);
      expect(response.body.success).toBe(true);
      expect(response.body.data.user.email).toBe('test@example.com');
      expect(response.body.data.token).toBeDefined();
    });

    it('should reject registration with existing email', async () => {
      // Mock: existing user found
      mockQuery.mockResolvedValueOnce({
        rows: [{ id: 'existing-uuid' }],
        rowCount: 1,
      });

      const response = await request(app)
        .post('/auth/register')
        .send({
          email: 'existing@example.com',
          password: 'password123',
        });

      expect(response.status).toBe(409);
      expect(response.body.success).toBe(false);
      expect(response.body.error).toBe('Email already registered');
    });

    it('should reject registration with invalid email', async () => {
      const response = await request(app)
        .post('/auth/register')
        .send({
          email: 'not-an-email',
          password: 'password123',
        });

      expect(response.status).toBe(400);
      expect(response.body.success).toBe(false);
    });

    it('should reject registration with short password', async () => {
      const response = await request(app)
        .post('/auth/register')
        .send({
          email: 'test@example.com',
          password: '123',
        });

      expect(response.status).toBe(400);
      expect(response.body.success).toBe(false);
    });
  });

  describe('POST /auth/login', () => {
    it('should login successfully with valid credentials', async () => {
      // Generate a real bcrypt hash for 'password123'
      const bcrypt = require('bcryptjs');
      const passwordHash = await bcrypt.hash('password123', 12);
      
      mockQuery.mockResolvedValueOnce({
        rows: [{
          id: 'user-uuid',
          email: 'test@example.com',
          displayName: 'Test User',
          passwordHash,
        }],
        rowCount: 1,
      });

      const response = await request(app)
        .post('/auth/login')
        .send({
          email: 'test@example.com',
          password: 'password123',
        });

      expect(response.status).toBe(200);
      expect(response.body.success).toBe(true);
      expect(response.body.data.token).toBeDefined();
    });

    it('should reject login with non-existent email', async () => {
      mockQuery.mockResolvedValueOnce({ rows: [], rowCount: 0 });

      const response = await request(app)
        .post('/auth/login')
        .send({
          email: 'nonexistent@example.com',
          password: 'password123',
        });

      expect(response.status).toBe(401);
      expect(response.body.error).toBe('Invalid email or password');
    });

    it('should reject login with wrong password', async () => {
      const bcrypt = require('bcryptjs');
      const passwordHash = await bcrypt.hash('password123', 12);
      
      mockQuery.mockResolvedValueOnce({
        rows: [{
          id: 'user-uuid',
          email: 'test@example.com',
          displayName: 'Test User',
          passwordHash,
        }],
        rowCount: 1,
      });

      const response = await request(app)
        .post('/auth/login')
        .send({
          email: 'test@example.com',
          password: 'wrongpassword',
        });

      expect(response.status).toBe(401);
      expect(response.body.error).toBe('Invalid email or password');
    });
  });

  describe('GET /auth/me', () => {
    it('should return user profile with valid token', async () => {
      const token = jwt.sign(
        { id: 'user-uuid', email: 'test@example.com', displayName: 'Test' },
        'test-secret-key',
        { expiresIn: '1h' }
      );

      mockQuery.mockResolvedValueOnce({
        rows: [{
          id: 'user-uuid',
          email: 'test@example.com',
          displayName: 'Test User',
          createdAt: new Date(),
        }],
        rowCount: 1,
      });

      const response = await request(app)
        .get('/auth/me')
        .set('Authorization', `Bearer ${token}`);

      expect(response.status).toBe(200);
      expect(response.body.success).toBe(true);
      expect(response.body.data.user.email).toBe('test@example.com');
    });

    it('should reject request without token', async () => {
      const response = await request(app).get('/auth/me');

      expect(response.status).toBe(401);
      expect(response.body.error).toBe('Authorization header required');
    });

    it('should reject request with invalid token', async () => {
      const response = await request(app)
        .get('/auth/me')
        .set('Authorization', 'Bearer invalid-token');

      expect(response.status).toBe(401);
      expect(response.body.error).toBe('Invalid token');
    });

    it('should reject request with expired token', async () => {
      const token = jwt.sign(
        { id: 'user-uuid', email: 'test@example.com' },
        'test-secret-key',
        { expiresIn: '-1h' } // Already expired
      );

      const response = await request(app)
        .get('/auth/me')
        .set('Authorization', `Bearer ${token}`);

      expect(response.status).toBe(401);
      expect(response.body.error).toBe('Token expired');
    });
  });

  describe('GET /health', () => {
    it('should return healthy status', async () => {
      const response = await request(app).get('/health');

      expect(response.status).toBe(200);
      expect(response.body.success).toBe(true);
      expect(response.body.data.status).toBe('healthy');
    });
  });
});
