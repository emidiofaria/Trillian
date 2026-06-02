import { TelemetrySample } from '../../src/types';
import { haversineMetres } from '../../src/lap/lapDetector';

/**
 * Generates synthetic telemetry data simulating laps on a circular track.
 * 
 * @param lapCount - Number of complete laps to generate
 * @param lapDurationMs - Target duration for each lap in milliseconds
 * @param startLat - Starting latitude (center of track)
 * @param startLng - Starting longitude (center of track)
 * @returns Array of TelemetrySample objects
 */
export function generateSyntheticLaps(
  lapCount: number,
  lapDurationMs: number,
  startLat: number,
  startLng: number
): TelemetrySample[] {
  const samples: TelemetrySample[] = [];
  
  // Track configuration
  const TRACK_CIRCUMFERENCE_M = 400; // 400m circular track
  const TRACK_RADIUS_M = TRACK_CIRCUMFERENCE_M / (2 * Math.PI); // ~63.66m
  const BASE_SPEED_MS = 20; // 72 km/h (typical kart speed)
  const SPEED_NOISE = 2; // ±2 m/s variation
  const SAMPLE_RATE_HZ = 10; // 10 samples per second
  
  // Calculate samples per lap
  const samplesPerLap = Math.ceil((lapDurationMs / 1000) * SAMPLE_RATE_HZ);
  const sampleIntervalMs = lapDurationMs / samplesPerLap;
  
  // Generate out-lap (partial lap to establish "outside zone" and return to start)
  // First, drive away from start zone (simulates leaving pit/staging)
  const outLapSamples = Math.ceil(samplesPerLap * 0.6); // 60% of a lap for out-lap
  
  let currentTimestamp = Date.now();
  
  // Out-lap: Start at start line, drive half the track, then back to start
  for (let i = 0; i < outLapSamples; i++) {
    const progress = i / outLapSamples;
    const angle = progress * Math.PI * 1.2; // ~60% of full circle
    
    const sample = createSampleAtAngle(
      startLat,
      startLng,
      TRACK_RADIUS_M,
      angle,
      currentTimestamp,
      BASE_SPEED_MS,
      SPEED_NOISE
    );
    
    samples.push(sample);
    currentTimestamp += sampleIntervalMs;
  }
  
  // Now generate complete laps
  for (let lap = 0; lap < lapCount; lap++) {
    // Add slight variation to lap duration (±3%)
    const lapVariation = 1 + (Math.random() - 0.5) * 0.06;
    const actualLapDuration = lapDurationMs * lapVariation;
    const actualSamplesPerLap = Math.ceil((actualLapDuration / 1000) * SAMPLE_RATE_HZ);
    const actualSampleInterval = actualLapDuration / actualSamplesPerLap;
    
    for (let i = 0; i < actualSamplesPerLap; i++) {
      const progress = i / actualSamplesPerLap;
      const angle = progress * 2 * Math.PI; // Full circle
      
      const sample = createSampleAtAngle(
        startLat,
        startLng,
        TRACK_RADIUS_M,
        angle,
        currentTimestamp,
        BASE_SPEED_MS,
        SPEED_NOISE
      );
      
      samples.push(sample);
      currentTimestamp += actualSampleInterval;
    }
  }
  
  // Add a few samples back at start to ensure last crossing is detected
  for (let i = 0; i < 5; i++) {
    const sample = createSampleAtAngle(
      startLat,
      startLng,
      TRACK_RADIUS_M,
      0.05 * i, // Small angle from start
      currentTimestamp,
      BASE_SPEED_MS * 0.5, // Slower (finishing)
      SPEED_NOISE
    );
    samples.push(sample);
    currentTimestamp += sampleIntervalMs;
  }
  
  return samples;
}

/**
 * Creates a telemetry sample at a specific angle on the circular track.
 */
function createSampleAtAngle(
  centerLat: number,
  centerLng: number,
  radiusM: number,
  angleRad: number,
  timestampMs: number,
  baseSpeedMs: number,
  speedNoise: number
): TelemetrySample {
  // Convert radius to lat/lng offset (approximate)
  // 1 degree latitude ≈ 111,320 meters
  // 1 degree longitude ≈ 111,320 * cos(latitude) meters
  const latOffset = (radiusM / 111320) * Math.cos(angleRad);
  const lngOffset = (radiusM / (111320 * Math.cos(centerLat * Math.PI / 180))) * Math.sin(angleRad);
  
  const latitude = centerLat + latOffset;
  const longitude = centerLng + lngOffset;
  
  // Speed with noise
  const speed = baseSpeedMs + (Math.random() - 0.5) * 2 * speedNoise;
  
  // Heading (tangent to circle)
  const headingDeg = ((angleRad + Math.PI / 2) * 180 / Math.PI) % 360;
  
  // Simulate centripetal acceleration (lateral G-force in circular motion)
  // a = v² / r
  const lateralAccel = (speed * speed) / radiusM;
  
  return {
    timestampMs,
    latitude,
    longitude,
    speedMs: Math.max(0, speed),
    headingDeg: headingDeg >= 0 ? headingDeg : headingDeg + 360,
    accelX: lateralAccel * Math.cos(angleRad) + (Math.random() - 0.5) * 0.5,
    accelY: lateralAccel * Math.sin(angleRad) + (Math.random() - 0.5) * 0.5,
    accelZ: 9.81 + (Math.random() - 0.5) * 0.2, // Gravity with noise
    gyroX: (Math.random() - 0.5) * 0.1,
    gyroY: (Math.random() - 0.5) * 0.1,
    gyroZ: speed / radiusM + (Math.random() - 0.5) * 0.05, // Angular velocity
    gpsAccuracyM: 3 + Math.random() * 2, // 3-5m accuracy
  };
}

/**
 * Converts an array of TelemetrySample to JSONL format.
 */
export function samplesToJsonl(samples: TelemetrySample[]): string {
  return samples.map(s => JSON.stringify(s)).join('\n');
}

/**
 * Generates a single-lap telemetry file (which should fail lap detection).
 */
export function generateSingleLap(
  lapDurationMs: number,
  startLat: number,
  startLng: number
): TelemetrySample[] {
  return generateSyntheticLaps(1, lapDurationMs, startLat, startLng);
}

/**
 * Generates an empty/minimal telemetry file.
 */
export function generateEmptyTelemetry(): TelemetrySample[] {
  return [];
}
