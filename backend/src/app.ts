import express, { Express } from 'express';
import { config } from './config';
import { errorHandler, notFoundHandler } from './middleware/errorHandler';
import authRouter from './auth/authRouter';
import telemetryRouter from './telemetry/telemetryRouter';

// Create Express app
const app: Express = express();

// Middleware
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Ensure upload directory exists
import * as fs from 'fs';
if (!fs.existsSync(config.upload.dir)) {
  fs.mkdirSync(config.upload.dir, { recursive: true });
}

// Health check
app.get('/health', (_req, res) => {
  res.json({
    success: true,
    data: {
      status: 'healthy',
      timestamp: new Date().toISOString(),
      version: '1.0.0',
    },
  });
});

// API Routes
app.use('/auth', authRouter);
app.use('/telemetry', telemetryRouter);
app.use('/sessions', telemetryRouter);

// Legacy endpoint for Android client compatibility
app.use('/api/v1/sessions', telemetryRouter);

// Error handling
app.use(notFoundHandler);
app.use(errorHandler);

export default app;
