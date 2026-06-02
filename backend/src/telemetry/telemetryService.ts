import * as fs from 'fs';
import * as readline from 'readline';
import { TelemetrySample } from '../types';

/**
 * Parses a JSONL telemetry file line by line.
 * Skips malformed lines with a warning.
 */
export async function parseJsonlFile(filePath: string): Promise<TelemetrySample[]> {
  const samples: TelemetrySample[] = [];
  
  if (!fs.existsSync(filePath)) {
    console.warn(`Telemetry file not found: ${filePath}`);
    return samples;
  }
  
  const fileStream = fs.createReadStream(filePath);
  const rl = readline.createInterface({
    input: fileStream,
    crlfDelay: Infinity,
  });
  
  let lineNumber = 0;
  
  for await (const line of rl) {
    lineNumber++;
    
    if (!line.trim()) {
      continue;
    }
    
    try {
      const sample = JSON.parse(line) as TelemetrySample;
      
      // Basic validation
      if (
        typeof sample.timestampMs === 'number' &&
        typeof sample.latitude === 'number' &&
        typeof sample.longitude === 'number'
      ) {
        samples.push(sample);
      } else {
        console.warn(`Line ${lineNumber}: Missing required fields`);
      }
    } catch (error) {
      console.warn(`Line ${lineNumber}: Malformed JSON - ${(error as Error).message}`);
    }
  }
  
  console.log(`Parsed ${samples.length} samples from ${filePath}`);
  return samples;
}

/**
 * Gets basic statistics from telemetry samples.
 */
export function getTelemetryStats(samples: TelemetrySample[]): {
  sampleCount: number;
  durationMs: number;
  maxSpeedMs: number;
  avgSpeedMs: number;
  startTime: number;
  endTime: number;
} {
  if (samples.length === 0) {
    return {
      sampleCount: 0,
      durationMs: 0,
      maxSpeedMs: 0,
      avgSpeedMs: 0,
      startTime: 0,
      endTime: 0,
    };
  }
  
  const sortedSamples = [...samples].sort((a, b) => a.timestampMs - b.timestampMs);
  const startTime = sortedSamples[0].timestampMs;
  const endTime = sortedSamples[sortedSamples.length - 1].timestampMs;
  
  let totalSpeed = 0;
  let maxSpeed = 0;
  
  for (const sample of samples) {
    totalSpeed += sample.speedMs;
    if (sample.speedMs > maxSpeed) {
      maxSpeed = sample.speedMs;
    }
  }
  
  return {
    sampleCount: samples.length,
    durationMs: endTime - startTime,
    maxSpeedMs: maxSpeed,
    avgSpeedMs: totalSpeed / samples.length,
    startTime,
    endTime,
  };
}

/**
 * Filters samples within a time range.
 */
export function filterSamplesByTimeRange(
  samples: TelemetrySample[],
  startMs: number,
  endMs: number
): TelemetrySample[] {
  return samples.filter(
    (sample) => sample.timestampMs >= startMs && sample.timestampMs <= endMs
  );
}
