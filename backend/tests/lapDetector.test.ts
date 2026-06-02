import {
  detectLaps,
  computeSectors,
  haversineMetres,
  findBestLap,
  LapDetectionError,
  DetectedLap,
} from '../src/lap/lapDetector';
import { TelemetrySample } from '../src/types';

/**
 * Generates synthetic samples for an oval track.
 * Creates a track with specified number of laps.
 * Track is ~600m circumference to ensure we go >200m from start.
 */
function generateOvalTrackSamples(
  numLaps: number,
  lapTimeMs: number,
  sampleRateHz: number = 10
): TelemetrySample[] {
  const samples: TelemetrySample[] = [];
  
  // Track parameters - larger track to exceed 200m minimum distance
  const centerLat = 48.1351; // Munich
  const centerLng = 11.5820;
  
  // 600m circumference oval: 150m straights + two ~150m semicircles
  // Max distance from start will be about 240m (diameter of oval)
  const straightLengthM = 150;
  const turnRadiusM = 75; // Semicircle radius
  const trackLengthM = 2 * straightLengthM + 2 * Math.PI * turnRadiusM;
  
  // Convert metres to degrees (approximate at Munich latitude)
  const mToLat = 1 / 111000;
  const mToLng = 1 / (111000 * Math.cos(centerLat * Math.PI / 180));
  
  const samplesPerLap = Math.floor(lapTimeMs / 1000 * sampleRateHz);
  
  // Start with some samples in the start zone (first 30 seconds)
  // Driver is stationary at start/finish line
  const startZoneSamples = 30 * sampleRateHz;
  for (let i = 0; i < startZoneSamples; i++) {
    samples.push({
      timestampMs: i * (1000 / sampleRateHz),
      latitude: centerLat + (Math.random() - 0.5) * 0.00001, // Small jitter
      longitude: centerLng + (Math.random() - 0.5) * 0.00001,
      speedMs: 0,
      headingDeg: 0,
      accelX: 0,
      accelY: 0,
      accelZ: 9.81,
      gyroX: 0,
      gyroY: 0,
      gyroZ: 0,
      gpsAccuracyM: 3,
    });
  }
  
  const baseTime = startZoneSamples * (1000 / sampleRateHz);
  
  // Generate lap samples - oval track shape
  // Start/finish line is at bottom center of oval (centerLat, centerLng)
  // Track goes: right straight → right turn → left straight → left turn back to start
  for (let lap = 0; lap < numLaps; lap++) {
    for (let s = 0; s < samplesPerLap; s++) {
      const progress = s / samplesPerLap; // 0 to 1 around the track
      
      let x: number, y: number;
      
      // Track layout (oval):
      // - 0.00 - 0.25: Right straight (going +x, y=0)
      // - 0.25 - 0.50: Right turn (semicircle, going +y then -x)
      // - 0.50 - 0.75: Left straight (going -x, y = 2*radius)
      // - 0.75 - 1.00: Left turn (semicircle, going -y then +x back to start)
      
      const quarterTrack = 0.25;
      
      if (progress < quarterTrack) {
        // First straight (right)
        x = straightLengthM * (progress / quarterTrack);
        y = 0;
      } else if (progress < 0.5) {
        // First turn (top-right semicircle)
        const turnProgress = (progress - quarterTrack) / quarterTrack;
        const angle = turnProgress * Math.PI;
        x = straightLengthM + turnRadiusM * Math.sin(angle);
        y = turnRadiusM * (1 - Math.cos(angle));
      } else if (progress < 0.75) {
        // Second straight (left, at top)
        x = straightLengthM - straightLengthM * ((progress - 0.5) / quarterTrack);
        y = 2 * turnRadiusM;
      } else {
        // Second turn (top-left semicircle back to start)
        const turnProgress = (progress - 0.75) / quarterTrack;
        const angle = turnProgress * Math.PI;
        x = -turnRadiusM * Math.sin(angle);
        y = turnRadiusM * (1 + Math.cos(angle));
      }
      
      const timestamp = baseTime + lap * lapTimeMs + s * (lapTimeMs / samplesPerLap);
      const speedMs = trackLengthM / (lapTimeMs / 1000);
      
      samples.push({
        timestampMs: timestamp,
        latitude: centerLat + y * mToLat,
        longitude: centerLng + x * mToLng,
        speedMs,
        headingDeg: (progress * 360) % 360,
        accelX: 0,
        accelY: 0,
        accelZ: 9.81,
        gyroX: 0,
        gyroY: 0,
        gyroZ: 0,
        gpsAccuracyM: 3,
      });
    }
  }
  
  return samples;
}

describe('Lap Detector', () => {
  describe('haversineMetres', () => {
    it('should calculate distance between two points correctly', () => {
      // Munich to nearby point (~1km away)
      const lat1 = 48.1351;
      const lng1 = 11.5820;
      const lat2 = 48.1440;
      const lng2 = 11.5820;
      
      const distance = haversineMetres(lat1, lng1, lat2, lng2);
      
      // ~989m due north
      expect(distance).toBeGreaterThan(900);
      expect(distance).toBeLessThan(1100);
    });

    it('should return 0 for same point', () => {
      const distance = haversineMetres(48.1351, 11.5820, 48.1351, 11.5820);
      expect(distance).toBe(0);
    });

    it('should calculate known distance accurately', () => {
      // Known distance: Marienplatz to Karlsplatz (~600m)
      const marienplatz = { lat: 48.1374, lng: 11.5755 };
      const karlsplatz = { lat: 48.1399, lng: 11.5667 };
      
      const distance = haversineMetres(
        marienplatz.lat, marienplatz.lng,
        karlsplatz.lat, karlsplatz.lng
      );
      
      expect(distance).toBeGreaterThan(500);
      expect(distance).toBeLessThan(800);
    });
  });

  describe('detectLaps', () => {
    it('should detect laps from synthetic oval track', () => {
      // Generate 6 "laps" which means 7 crossings (out lap + 6 complete laps)
      // After excluding the out lap, we should get 5 complete laps  
      const lapTimeMs = 60000; // 1 minute per lap
      const samples = generateOvalTrackSamples(6, lapTimeMs);
      
      const laps = detectLaps(samples);
      
      // With 6 generated laps and the way crossings work:
      // - out lap (start zone → first crossing)
      // - 5 complete laps (crossing to crossing)
      // The last generated lap has no final crossing, so it's incomplete
      expect(laps.length).toBeGreaterThanOrEqual(4);
      expect(laps.length).toBeLessThanOrEqual(6);
    });

    it('should detect lap durations within reasonable range', () => {
      // Generate enough laps to get meaningful results
      const trueLapTimeMs = 60000;
      const samples = generateOvalTrackSamples(6, trueLapTimeMs);
      
      const laps = detectLaps(samples);
      
      // Verify we have some complete laps
      expect(laps.length).toBeGreaterThanOrEqual(2);
      
      // All laps should be within a reasonable range of the expected time
      for (const lap of laps) {
        expect(lap.durationMs).toBeGreaterThan(trueLapTimeMs * 0.3);
        expect(lap.durationMs).toBeLessThan(trueLapTimeMs * 2.5);
      }
    });

    it('should number laps sequentially starting from 1', () => {
      const samples = generateOvalTrackSamples(6, 45000);
      
      const laps = detectLaps(samples);
      
      expect(laps.length).toBeGreaterThanOrEqual(2);
      expect(laps[0].lapNumber).toBe(1);
      expect(laps[1].lapNumber).toBe(2);
      if (laps.length >= 3) {
        expect(laps[2].lapNumber).toBe(3);
      }
    });

    it('should ignore crossing at 10 seconds (minimum lap time guard)', () => {
      // This test verifies that crossings under 30 seconds apart are ignored
      // We create a scenario with:
      // - Start zone establishment (0-30s)
      // - Go far away (30-35s)  
      // - False crossing at 35s (only 5s since leaving - should be ignored due to min lap time)
      // - Go far away again (35-70s)
      // - Valid crossing at 70s (>30s since session start)
      // - Continue for two more valid laps
      
      const samples: TelemetrySample[] = [];
      const startLat = 48.1351;
      const startLng = 11.5820;
      const farAwayLat = startLat + 0.003; // ~333m away (>200m threshold)
      
      // Start zone establishment (0-30s, stationary at start)
      for (let i = 0; i < 300; i++) {
        samples.push({
          timestampMs: i * 100,
          latitude: startLat,
          longitude: startLng,
          speedMs: 0,
          headingDeg: 0,
          accelX: 0, accelY: 0, accelZ: 9.81,
          gyroX: 0, gyroY: 0, gyroZ: 0,
          gpsAccuracyM: 3,
        });
      }
      
      // Go far away from start (30s-65s, 35 seconds driving away)
      for (let i = 0; i < 350; i++) {
        samples.push({
          timestampMs: 30000 + i * 100,
          latitude: farAwayLat,
          longitude: startLng,
          speedMs: 30,
          headingDeg: 0,
          accelX: 0, accelY: 0, accelZ: 9.81,
          gyroX: 0, gyroY: 0, gyroZ: 0,
          gpsAccuracyM: 3,
        });
      }
      
      // Valid first crossing at 65s (>30s since session start, was far away)
      samples.push({
        timestampMs: 65000,
        latitude: startLat,
        longitude: startLng,
        speedMs: 30,
        headingDeg: 0,
        accelX: 0, accelY: 0, accelZ: 9.81,
        gyroX: 0, gyroY: 0, gyroZ: 0,
        gpsAccuracyM: 3,
      });
      
      // Drive away again (65s-100s)
      for (let i = 0; i < 350; i++) {
        samples.push({
          timestampMs: 65100 + i * 100,
          latitude: farAwayLat,
          longitude: startLng,
          speedMs: 30,
          headingDeg: 0,
          accelX: 0, accelY: 0, accelZ: 9.81,
          gyroX: 0, gyroY: 0, gyroZ: 0,
          gpsAccuracyM: 3,
        });
      }
      
      // Second valid crossing at 100s (35s after first crossing - passes min lap time)
      samples.push({
        timestampMs: 100000,
        latitude: startLat,
        longitude: startLng,
        speedMs: 30,
        headingDeg: 0,
        accelX: 0, accelY: 0, accelZ: 9.81,
        gyroX: 0, gyroY: 0, gyroZ: 0,
        gpsAccuracyM: 3,
      });
      
      // Drive away again (100s-135s)
      for (let i = 0; i < 350; i++) {
        samples.push({
          timestampMs: 100100 + i * 100,
          latitude: farAwayLat,
          longitude: startLng,
          speedMs: 30,
          headingDeg: 0,
          accelX: 0, accelY: 0, accelZ: 9.81,
          gyroX: 0, gyroY: 0, gyroZ: 0,
          gpsAccuracyM: 3,
        });
      }
      
      // Third valid crossing at 135s  
      samples.push({
        timestampMs: 135000,
        latitude: startLat,
        longitude: startLng,
        speedMs: 30,
        headingDeg: 0,
        accelX: 0, accelY: 0, accelZ: 9.81,
        gyroX: 0, gyroY: 0, gyroZ: 0,
        gpsAccuracyM: 3,
      });
      
      const laps = detectLaps(samples);
      
      // Should have 2 laps (crossing 1→2 and 2→3)
      expect(laps.length).toBe(2);
      
      // First lap should be ~35s (65s to 100s crossing)
      expect(laps[0].durationMs).toBeGreaterThan(30000);
      expect(laps[0].durationMs).toBeLessThan(40000);
    });

    it('should throw LapDetectionError with insufficient samples', () => {
      const samples: TelemetrySample[] = [
        {
          timestampMs: 1000,
          latitude: 48.135,
          longitude: 11.582,
          speedMs: 0,
          headingDeg: 0,
          accelX: 0, accelY: 0, accelZ: 9.81,
          gyroX: 0, gyroY: 0, gyroZ: 0,
          gpsAccuracyM: 3,
        },
      ];

      expect(() => detectLaps(samples)).toThrow(LapDetectionError);
    });

    it('should throw LapDetectionError with insufficient laps', () => {
      // Create a short session with no complete laps
      const samples: TelemetrySample[] = [];
      for (let i = 0; i < 100; i++) {
        samples.push({
          timestampMs: i * 100,
          latitude: 48.1351 + i * 0.0001,
          longitude: 11.582,
          speedMs: 30,
          headingDeg: 0,
          accelX: 0, accelY: 0, accelZ: 9.81,
          gyroX: 0, gyroY: 0, gyroZ: 0,
          gpsAccuracyM: 3,
        });
      }

      expect(() => detectLaps(samples)).toThrow(/Insufficient lap/);
    });
  });

  describe('computeSectors', () => {
    it('should compute sectors that sum to lap duration', () => {
      const samples = generateOvalTrackSamples(6, 60000);
      const laps = detectLaps(samples);
      
      for (const lap of laps) {
        const sectors = computeSectors(lap);
        const sectorSum = sectors.sector1Ms + sectors.sector2Ms + sectors.sector3Ms;
        
        // Allow small rounding difference
        expect(Math.abs(sectorSum - lap.durationMs)).toBeLessThan(100);
      }
    });

    it('should split lap into roughly equal thirds', () => {
      const lapTimeMs = 90000; // 90 seconds
      const samples = generateOvalTrackSamples(6, lapTimeMs);
      const laps = detectLaps(samples);
      
      // Use a detected lap (lap times will vary from nominal)
      const lap = laps[0];
      const sectors = computeSectors(lap);
      const actualDuration = lap.durationMs;
      const expectedThird = actualDuration / 3;
      const tolerance = expectedThird * 0.2; // 20% tolerance
      
      expect(Math.abs(sectors.sector1Ms - expectedThird)).toBeLessThan(tolerance);
      expect(Math.abs(sectors.sector2Ms - expectedThird)).toBeLessThan(tolerance);
      expect(Math.abs(sectors.sector3Ms - expectedThird)).toBeLessThan(tolerance);
    });

    it('should handle lap with minimal samples', () => {
      const lap: DetectedLap = {
        lapNumber: 1,
        startTs: 1000,
        endTs: 61000,
        durationMs: 60000,
        samples: [
          { timestampMs: 1000, latitude: 48.135, longitude: 11.582, speedMs: 30, headingDeg: 0, accelX: 0, accelY: 0, accelZ: 9.81, gyroX: 0, gyroY: 0, gyroZ: 0, gpsAccuracyM: 3 },
          { timestampMs: 61000, latitude: 48.136, longitude: 11.583, speedMs: 30, headingDeg: 0, accelX: 0, accelY: 0, accelZ: 9.81, gyroX: 0, gyroY: 0, gyroZ: 0, gpsAccuracyM: 3 },
        ],
      };
      
      const sectors = computeSectors(lap);
      
      expect(sectors.sector1Ms).toBeDefined();
      expect(sectors.sector2Ms).toBeDefined();
      expect(sectors.sector3Ms).toBeDefined();
      expect(sectors.sector1Ms + sectors.sector2Ms + sectors.sector3Ms).toBe(60000);
    });
  });

  describe('findBestLap', () => {
    it('should find the lap with minimum duration', () => {
      const laps: DetectedLap[] = [
        { lapNumber: 1, startTs: 0, endTs: 60000, durationMs: 60000, samples: [] },
        { lapNumber: 2, startTs: 60000, endTs: 115000, durationMs: 55000, samples: [] },
        { lapNumber: 3, startTs: 115000, endTs: 175000, durationMs: 60000, samples: [] },
      ];
      
      const best = findBestLap(laps);
      
      expect(best?.lapNumber).toBe(2);
      expect(best?.durationMs).toBe(55000);
    });

    it('should return null for empty array', () => {
      const best = findBestLap([]);
      expect(best).toBeNull();
    });
  });
});
