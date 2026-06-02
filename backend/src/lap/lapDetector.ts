import { TelemetrySample } from '../types';

/**
 * Represents a detected lap with timing and sample data.
 */
export interface DetectedLap {
  lapNumber: number;
  startTs: number;
  endTs: number;
  durationMs: number;
  samples: TelemetrySample[];
}

/**
 * Sector timing data for a lap.
 */
export interface SectorTimes {
  sector1Ms: number;
  sector2Ms: number;
  sector3Ms: number;
}

/**
 * Error thrown when lap detection fails.
 */
export class LapDetectionError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'LapDetectionError';
  }
}

// Constants
const START_ZONE_RADIUS_M = 15;
const MIN_DISTANCE_FROM_START_M = 200;
const MIN_LAP_TIME_MS = 30000; // 30 seconds
const START_ZONE_SAMPLE_TIME_MS = 30000; // First 30 seconds
const START_ZONE_MAX_SAMPLES = 300;

/**
 * Calculates the haversine distance between two points in metres.
 */
export function haversineMetres(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number {
  const R = 6371000; // Earth's radius in metres
  
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) *
      Math.cos(toRad(lat2)) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2);
  
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  
  return R * c;
}

/**
 * Computes the centroid of a set of GPS points.
 */
function computeCentroid(samples: TelemetrySample[]): { lat: number; lng: number } {
  if (samples.length === 0) {
    throw new LapDetectionError('Cannot compute centroid of empty samples');
  }
  
  let totalLat = 0;
  let totalLng = 0;
  
  for (const sample of samples) {
    totalLat += sample.latitude;
    totalLng += sample.longitude;
  }
  
  return {
    lat: totalLat / samples.length,
    lng: totalLng / samples.length,
  };
}

/**
 * Detects laps from telemetry samples using start/finish line auto-detection.
 * 
 * Algorithm:
 * 1. Use first 30 seconds to define 'start zone' centroid
 * 2. Detect lap boundaries when driver re-enters the start zone
 *    after being at least 200m away
 * 3. Enforce minimum 30 second lap time
 */
export function detectLaps(samples: TelemetrySample[]): DetectedLap[] {
  if (samples.length < 10) {
    throw new LapDetectionError('Insufficient samples for lap detection');
  }
  
  // Sort samples by timestamp
  const sortedSamples = [...samples].sort((a, b) => a.timestampMs - b.timestampMs);
  
  // Step 1: Define start zone from first 30 seconds or 300 samples
  const firstTimestamp = sortedSamples[0].timestampMs;
  const startZoneCutoff = firstTimestamp + START_ZONE_SAMPLE_TIME_MS;
  
  const startZoneSamples = sortedSamples.filter(
    (s, i) => s.timestampMs <= startZoneCutoff && i < START_ZONE_MAX_SAMPLES
  );
  
  if (startZoneSamples.length < 5) {
    throw new LapDetectionError('Insufficient samples in start zone');
  }
  
  // Step 2: Compute start zone centroid
  const centroid = computeCentroid(startZoneSamples);
  
  // Step 3: Detect lap boundaries
  const lapBoundaries: number[] = [0]; // First boundary is start of session
  let wasOutsideStartZone = false;
  let lastBoundaryTime = firstTimestamp;
  
  for (let i = 0; i < sortedSamples.length; i++) {
    const sample = sortedSamples[i];
    const distance = haversineMetres(
      sample.latitude,
      sample.longitude,
      centroid.lat,
      centroid.lng
    );
    
    // Check if we're far enough from start to count
    if (distance >= MIN_DISTANCE_FROM_START_M) {
      wasOutsideStartZone = true;
    }
    
    // Check for lap boundary: re-entering start zone after being outside
    if (
      wasOutsideStartZone &&
      distance <= START_ZONE_RADIUS_M &&
      sample.timestampMs - lastBoundaryTime >= MIN_LAP_TIME_MS
    ) {
      lapBoundaries.push(i);
      lastBoundaryTime = sample.timestampMs;
      wasOutsideStartZone = false;
    }
  }
  
  // Note: We don't add a final boundary at end of session.
  // Only complete laps (crossing to crossing) are counted.
  // The final "incomplete" lap after the last crossing is ignored.
  
  // Step 4: Build lap objects from crossings
  // lapBoundaries contains [0, crossing1, crossing2, ..., crossingN]
  // We want laps: crossing1→crossing2, crossing2→crossing3, etc.
  // The segment 0→crossing1 is the "out lap" and is skipped
  const laps: DetectedLap[] = [];
  
  // We need at least 3 boundaries to have 1 complete lap:
  // [0 (start), first_crossing, second_crossing] = 1 lap (first_crossing → second_crossing)
  if (lapBoundaries.length < 3) {
    throw new LapDetectionError(
      `Insufficient lap crossings detected: found ${lapBoundaries.length - 1}, need at least 2`
    );
  }
  
  // Build laps from crossing to crossing (skip the out lap at index 0)
  for (let i = 1; i < lapBoundaries.length - 1; i++) {
    const startIdx = lapBoundaries[i];
    const endIdx = lapBoundaries[i + 1];
    
    const lapSamples = sortedSamples.slice(startIdx, endIdx + 1);
    
    if (lapSamples.length < 2) {
      continue;
    }
    
    const startTs = lapSamples[0].timestampMs;
    const endTs = lapSamples[lapSamples.length - 1].timestampMs;
    const durationMs = endTs - startTs;
    
    // Warn if lap is suspiciously short
    if (durationMs < 20000) {
      console.warn(`Lap ${laps.length + 1}: Duration ${durationMs}ms is under 20 seconds`);
    }
    
    laps.push({
      lapNumber: laps.length + 1,
      startTs,
      endTs,
      durationMs,
      samples: lapSamples,
    });
  }
  
  // Validation
  if (laps.length < 2) {
    throw new LapDetectionError(
      `Insufficient laps detected: found ${laps.length}, need at least 2`
    );
  }
  
  return laps;
}

/**
 * Computes sector times for a lap by splitting into three equal-time thirds.
 */
export function computeSectors(lap: DetectedLap): SectorTimes {
  if (lap.samples.length < 3) {
    // Not enough samples for sectors, distribute evenly
    const third = Math.floor(lap.durationMs / 3);
    return {
      sector1Ms: third,
      sector2Ms: third,
      sector3Ms: lap.durationMs - 2 * third,
    };
  }
  
  const sortedSamples = [...lap.samples].sort((a, b) => a.timestampMs - b.timestampMs);
  
  const startTime = sortedSamples[0].timestampMs;
  const endTime = sortedSamples[sortedSamples.length - 1].timestampMs;
  const totalDuration = endTime - startTime;
  
  // Find indices at 1/3 and 2/3 of the lap
  const oneThirdTime = startTime + totalDuration / 3;
  const twoThirdTime = startTime + (2 * totalDuration) / 3;
  
  // Find actual sample timestamps closest to the third marks
  let sector1EndTime = startTime;
  let sector2EndTime = startTime;
  
  for (const sample of sortedSamples) {
    if (sample.timestampMs <= oneThirdTime) {
      sector1EndTime = sample.timestampMs;
    }
    if (sample.timestampMs <= twoThirdTime) {
      sector2EndTime = sample.timestampMs;
    }
  }
  
  const sector1Ms = sector1EndTime - startTime;
  const sector2Ms = sector2EndTime - sector1EndTime;
  const sector3Ms = endTime - sector2EndTime;
  
  return {
    sector1Ms: Math.max(0, sector1Ms),
    sector2Ms: Math.max(0, sector2Ms),
    sector3Ms: Math.max(0, sector3Ms),
  };
}

/**
 * Finds the best (fastest) lap from a list of detected laps.
 */
export function findBestLap(laps: DetectedLap[]): DetectedLap | null {
  if (laps.length === 0) {
    return null;
  }
  
  return laps.reduce((best, current) =>
    current.durationMs < best.durationMs ? current : best
  );
}
