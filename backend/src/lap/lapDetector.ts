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
const MIN_LAP_TIME_MS_CENTROID = 30000; // 30 seconds for centroid-based (wider tolerance)
const MIN_LAP_TIME_MS_LINE = 20000; // 20 seconds for line-based (more precise)
const START_ZONE_SAMPLE_TIME_MS = 30000; // First 30 seconds
const START_ZONE_MAX_SAMPLES = 300;

/**
 * Start/finish line defined by two GPS points.
 */
export interface StartLine {
  lat1: number;
  lng1: number;
  lat2: number;
  lng2: number;
}

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
 * Earth radius in metres for local coordinate conversion.
 */
const EARTH_RADIUS_M = 6371000;

/**
 * Converts GPS coordinates to local Cartesian coordinates (x, y) in metres.
 * Uses equirectangular approximation which is accurate for small distances.
 */
function toLocal(lat: number, lng: number, refLat: number, refLng: number): { x: number; y: number } {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const x = toRad(lng - refLng) * EARTH_RADIUS_M * Math.cos(toRad(refLat));
  const y = toRad(lat - refLat) * EARTH_RADIUS_M;
  return { x, y };
}

/**
 * 2D cross product of vectors (v1x, v1y) and (v2x, v2y).
 */
function crossProduct(v1x: number, v1y: number, v2x: number, v2y: number): number {
  return v1x * v2y - v1y * v2x;
}

/**
 * Determines if a GPS path segment crosses a defined line (start/finish line).
 * Uses 2D line-segment intersection via cross product method.
 */
export function lineIntersection(
  startLine: StartLine,
  prevLat: number, prevLng: number,
  currLat: number, currLng: number
): boolean {
  // Convert to local Cartesian coordinates relative to line midpoint
  const refLat = (startLine.lat1 + startLine.lat2) / 2;
  const refLng = (startLine.lng1 + startLine.lng2) / 2;

  const lineP1 = toLocal(startLine.lat1, startLine.lng1, refLat, refLng);
  const lineP2 = toLocal(startLine.lat2, startLine.lng2, refLat, refLng);
  const segP1 = toLocal(prevLat, prevLng, refLat, refLng);
  const segP2 = toLocal(currLat, currLng, refLat, refLng);

  // Direction vectors
  const abx = lineP2.x - lineP1.x;
  const aby = lineP2.y - lineP1.y;
  const cdx = segP2.x - segP1.x;
  const cdy = segP2.y - segP1.y;

  // Cross products to determine orientation
  const d1 = crossProduct(cdx, cdy, lineP1.x - segP1.x, lineP1.y - segP1.y);
  const d2 = crossProduct(cdx, cdy, lineP2.x - segP1.x, lineP2.y - segP1.y);
  const d3 = crossProduct(abx, aby, segP1.x - lineP1.x, segP1.y - lineP1.y);
  const d4 = crossProduct(abx, aby, segP2.x - lineP1.x, segP2.y - lineP1.y);

  // Segments intersect if points are on opposite sides of each other's lines
  if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
      ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
    return true;
  }

  return false;
}

/**
 * Detects laps from telemetry samples.
 * 
 * If startLine is provided, uses precise line intersection detection.
 * Otherwise, falls back to centroid-based auto-detection.
 * 
 * @param samples Telemetry samples with GPS coordinates
 * @param startLine Optional user-defined start/finish line
 */
export function detectLaps(samples: TelemetrySample[], startLine?: StartLine): DetectedLap[] {
  if (samples.length < 10) {
    throw new LapDetectionError('Insufficient samples for lap detection');
  }
  
  // Sort samples by timestamp
  const sortedSamples = [...samples].sort((a, b) => a.timestampMs - b.timestampMs);
  const firstTimestamp = sortedSamples[0].timestampMs;

  // Use line intersection if startLine provided, otherwise use centroid
  if (startLine) {
    return detectLapsWithLine(sortedSamples, startLine, firstTimestamp);
  } else {
    return detectLapsWithCentroid(sortedSamples, firstTimestamp);
  }
}

/**
 * Detects laps using precise line intersection.
 */
function detectLapsWithLine(
  sortedSamples: TelemetrySample[],
  startLine: StartLine,
  firstTimestamp: number
): DetectedLap[] {
  const lapBoundaries: number[] = [0];
  let wasOutsideStartZone = false;
  let lastBoundaryTime = firstTimestamp;

  // Compute centroid of start line for distance check
  const lineCentroid = {
    lat: (startLine.lat1 + startLine.lat2) / 2,
    lng: (startLine.lng1 + startLine.lng2) / 2,
  };

  for (let i = 1; i < sortedSamples.length; i++) {
    const prev = sortedSamples[i - 1];
    const curr = sortedSamples[i];

    // Check if we're far enough from start to count
    const distance = haversineMetres(curr.latitude, curr.longitude, lineCentroid.lat, lineCentroid.lng);
    if (distance >= MIN_DISTANCE_FROM_START_M) {
      wasOutsideStartZone = true;
    }

    // Check for lap boundary: crossing the line after being outside
    if (
      wasOutsideStartZone &&
      lineIntersection(startLine, prev.latitude, prev.longitude, curr.latitude, curr.longitude) &&
      curr.timestampMs - lastBoundaryTime >= MIN_LAP_TIME_MS_LINE
    ) {
      lapBoundaries.push(i);
      lastBoundaryTime = curr.timestampMs;
      wasOutsideStartZone = false;
    }
  }

  return buildLapsFromBoundaries(sortedSamples, lapBoundaries);
}

/**
 * Detects laps using centroid-based auto-detection (fallback).
 */
function detectLapsWithCentroid(
  sortedSamples: TelemetrySample[],
  firstTimestamp: number
): DetectedLap[] {
  // Step 1: Define start zone from first 30 seconds or 300 samples
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
      sample.timestampMs - lastBoundaryTime >= MIN_LAP_TIME_MS_CENTROID
    ) {
      lapBoundaries.push(i);
      lastBoundaryTime = sample.timestampMs;
      wasOutsideStartZone = false;
    }
  }
  
  return buildLapsFromBoundaries(sortedSamples, lapBoundaries);
}

/**
 * Builds DetectedLap objects from boundary indices.
 */
function buildLapsFromBoundaries(
  sortedSamples: TelemetrySample[],
  lapBoundaries: number[]
): DetectedLap[] {
  // We need at least 3 boundaries to have 1 complete lap:
  // [0 (start), first_crossing, second_crossing] = 1 lap (first_crossing → second_crossing)
  if (lapBoundaries.length < 3) {
    throw new LapDetectionError(
      `Insufficient lap crossings detected: found ${lapBoundaries.length - 1}, need at least 2`
    );
  }
  
  const laps: DetectedLap[] = [];
  
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
