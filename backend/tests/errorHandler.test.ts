import { Request, Response, NextFunction } from 'express';
import { ZodError, ZodIssue } from 'zod';
import { errorHandler, HttpError, notFoundHandler, AppError } from '../src/middleware/errorHandler';

// Mock request and next
const mockRequest = (overrides = {}): Partial<Request> => ({
  method: 'GET',
  path: '/test',
  ...overrides,
});

// Mock response
const mockResponse = (): Partial<Response> => {
  const res: Partial<Response> = {};
  res.status = jest.fn().mockReturnValue(res);
  res.json = jest.fn().mockReturnValue(res);
  return res;
};

// Mock next function
const mockNext = jest.fn() as NextFunction;

describe('errorHandler middleware', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('ZodError handling', () => {
    it('should return 400 with validation details for ZodError', () => {
      const req = mockRequest() as Request;
      const res = mockResponse() as Response;
      
      const zodError = new ZodError([
        {
          code: 'invalid_type',
          expected: 'string',
          received: 'undefined',
          path: ['email'],
          message: 'Required',
        } as ZodIssue,
      ]);

      errorHandler(zodError, req, res, mockNext);

      expect(res.status).toHaveBeenCalledWith(400);
      expect(res.json).toHaveBeenCalledWith({
        success: false,
        error: 'Validation failed',
        details: [{ field: 'email', message: 'Required' }],
      });
    });
  });

  describe('operational error handling', () => {
    it('should use error statusCode for operational errors', () => {
      const req = mockRequest() as Request;
      const res = mockResponse() as Response;
      
      const error: AppError = new Error('Not found');
      error.statusCode = 404;
      error.isOperational = true;

      errorHandler(error, req, res, mockNext);

      expect(res.status).toHaveBeenCalledWith(404);
      expect(res.json).toHaveBeenCalledWith({
        success: false,
        error: 'Not found',
      });
    });

    it('should default to 500 for operational errors without statusCode', () => {
      const req = mockRequest() as Request;
      const res = mockResponse() as Response;
      
      const error: AppError = new Error('Something went wrong');
      error.isOperational = true;

      errorHandler(error, req, res, mockNext);

      expect(res.status).toHaveBeenCalledWith(500);
    });
  });

  describe('unknown error handling', () => {
    it('should return 500 for unknown errors', () => {
      const req = mockRequest() as Request;
      const res = mockResponse() as Response;
      
      const error = new Error('Unknown error');

      errorHandler(error, req, res, mockNext);

      expect(res.status).toHaveBeenCalledWith(500);
      expect(res.json).toHaveBeenCalledWith({
        success: false,
        error: 'Unknown error',
      });
    });

    it('should use error statusCode if provided', () => {
      const req = mockRequest() as Request;
      const res = mockResponse() as Response;
      
      const error: AppError = new Error('Server error');
      error.statusCode = 502;

      errorHandler(error, req, res, mockNext);

      expect(res.status).toHaveBeenCalledWith(502);
    });
  });
});

describe('HttpError class', () => {
  it('should create an HttpError with correct properties', () => {
    const error = new HttpError(403, 'Forbidden');
    
    expect(error.statusCode).toBe(403);
    expect(error.message).toBe('Forbidden');
    expect(error.isOperational).toBe(true);
    expect(error.name).toBe('HttpError');
  });

  it('should allow non-operational errors', () => {
    const error = new HttpError(500, 'Fatal', false);
    
    expect(error.isOperational).toBe(false);
  });
});

describe('notFoundHandler middleware', () => {
  it('should return 404 with route information', () => {
    const req = mockRequest({ method: 'POST', path: '/api/unknown' }) as Request;
    const res = mockResponse() as Response;

    notFoundHandler(req, res, mockNext);

    expect(res.status).toHaveBeenCalledWith(404);
    expect(res.json).toHaveBeenCalledWith({
      success: false,
      error: 'Route POST /api/unknown not found',
    });
  });
});
