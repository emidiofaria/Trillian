// Test setup file
import dotenv from 'dotenv';

dotenv.config({ path: '.env.test' });

// Set test environment
process.env.NODE_ENV = 'test';
process.env.JWT_SECRET = 'test-secret-key';

// Increase timeout for database operations
jest.setTimeout(10000);
