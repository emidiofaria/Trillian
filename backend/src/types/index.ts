import { Request } from 'express';

export interface User {
  id: string;
  email: string;
  displayName: string | null;
  passwordHash: string;
  createdAt: Date;
  updatedAt: Date;
}

export interface UserPayload {
  id: string;
  email: string;
  displayName: string | null;
}

export interface AuthRequest extends Request {
  user?: UserPayload;
}

export interface Session {
  id: string;
  userId: string;
  trackName: string | null;
  startedAt: Date;
  endedAt: Date | null;
  rawFilePath: string | null;
  processingStatus: ProcessingStatus;
  createdAt: Date;
  updatedAt: Date;
}

export type ProcessingStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface Lap {
  id: string;
  sessionId: string;
  lapNumber: number;
  startTs: number;
  endTs: number;
  durationMs: number;
  sector1Ms: number | null;
  sector2Ms: number | null;
  sector3Ms: number | null;
  isBestLap: boolean;
  createdAt: Date;
}

export interface CoachingInsight {
  id: string;
  sessionId: string;
  headline: string;
  detail: string;
  generatedAt: Date;
}

export interface TelemetrySample {
  timestampMs: number;
  latitude: number;
  longitude: number;
  speedMs: number;
  headingDeg: number;
  accelX: number;
  accelY: number;
  accelZ: number;
  gyroX: number;
  gyroY: number;
  gyroZ: number;
  gpsAccuracyM: number;
}

export interface ApiResponse<T = unknown> {
  success: boolean;
  data?: T;
  error?: string;
  message?: string;
}
