import { Router, Response } from 'express';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { query } from '../db';
import { generateToken } from '../middleware/requireAuth';
import { AuthRequest, User, ApiResponse } from '../types';
import { requireAuth } from '../middleware/requireAuth';

const router = Router();

// Validation schemas
const registerSchema = z.object({
  email: z.string().email('Invalid email format'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  displayName: z.string().min(1).max(100).optional(),
});

const loginSchema = z.object({
  email: z.string().email('Invalid email format'),
  password: z.string().min(1, 'Password is required'),
});

// POST /auth/register
router.post('/register', async (req: AuthRequest, res: Response): Promise<void> => {
  try {
    const { email, password, displayName } = registerSchema.parse(req.body);
    
    // Check if user already exists
    const existingUser = await query<User>(
      'SELECT id FROM users WHERE email = $1',
      [email.toLowerCase()]
    );
    
    if (existingUser.rows.length > 0) {
      res.status(409).json({
        success: false,
        error: 'Email already registered',
      } as ApiResponse);
      return;
    }
    
    // Hash password with cost factor 12
    const passwordHash = await bcrypt.hash(password, 12);
    
    // Insert new user
    const result = await query<User>(
      `INSERT INTO users (email, display_name, password_hash)
       VALUES ($1, $2, $3)
       RETURNING id, email, display_name as "displayName", created_at as "createdAt"`,
      [email.toLowerCase(), displayName || null, passwordHash]
    );
    
    const user = result.rows[0];
    
    // Generate JWT
    const token = generateToken({
      id: user.id,
      email: user.email,
      displayName: user.displayName,
    });
    
    res.status(201).json({
      success: true,
      data: {
        user: {
          id: user.id,
          email: user.email,
          displayName: user.displayName,
        },
        token,
      },
    } as ApiResponse);
  } catch (error) {
    if (error instanceof z.ZodError) {
      res.status(400).json({
        success: false,
        error: 'Validation failed',
        details: error.errors,
      } as ApiResponse);
      return;
    }
    
    console.error('Register error:', error);
    res.status(500).json({
      success: false,
      error: 'Registration failed',
    } as ApiResponse);
  }
});

// POST /auth/login
router.post('/login', async (req: AuthRequest, res: Response): Promise<void> => {
  try {
    const { email, password } = loginSchema.parse(req.body);
    
    // Find user by email
    const result = await query<User>(
      `SELECT id, email, display_name as "displayName", password_hash as "passwordHash"
       FROM users WHERE email = $1`,
      [email.toLowerCase()]
    );
    
    if (result.rows.length === 0) {
      res.status(401).json({
        success: false,
        error: 'Invalid email or password',
      } as ApiResponse);
      return;
    }
    
    const user = result.rows[0];
    
    // Verify password
    const isValid = await bcrypt.compare(password, user.passwordHash);
    
    if (!isValid) {
      res.status(401).json({
        success: false,
        error: 'Invalid email or password',
      } as ApiResponse);
      return;
    }
    
    // Generate JWT
    const token = generateToken({
      id: user.id,
      email: user.email,
      displayName: user.displayName,
    });
    
    res.json({
      success: true,
      data: {
        user: {
          id: user.id,
          email: user.email,
          displayName: user.displayName,
        },
        token,
      },
    } as ApiResponse);
  } catch (error) {
    if (error instanceof z.ZodError) {
      res.status(400).json({
        success: false,
        error: 'Validation failed',
        details: error.errors,
      } as ApiResponse);
      return;
    }
    
    console.error('Login error:', error);
    res.status(500).json({
      success: false,
      error: 'Login failed',
    } as ApiResponse);
  }
});

// GET /auth/me
router.get('/me', requireAuth, async (req: AuthRequest, res: Response): Promise<void> => {
  try {
    if (!req.user) {
      res.status(401).json({
        success: false,
        error: 'Not authenticated',
      } as ApiResponse);
      return;
    }
    
    // Get fresh user data
    const result = await query<User>(
      `SELECT id, email, display_name as "displayName", created_at as "createdAt"
       FROM users WHERE id = $1`,
      [req.user.id]
    );
    
    if (result.rows.length === 0) {
      res.status(404).json({
        success: false,
        error: 'User not found',
      } as ApiResponse);
      return;
    }
    
    const user = result.rows[0];
    
    res.json({
      success: true,
      data: {
        user: {
          id: user.id,
          email: user.email,
          displayName: user.displayName,
          createdAt: user.createdAt,
        },
      },
    } as ApiResponse);
  } catch (error) {
    console.error('Get me error:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to get user profile',
    } as ApiResponse);
  }
});

export default router;
