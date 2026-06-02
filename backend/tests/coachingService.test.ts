import {
  computeSessionStats,
  buildCoachingPrompt,
  parseCoachingResponse,
} from '../src/coaching/coachingService';
import { Lap } from '../src/types';

// Mock Anthropic SDK
jest.mock('@anthropic-ai/sdk', () => {
  return jest.fn().mockImplementation(() => ({
    messages: {
      create: jest.fn(),
    },
  }));
});

describe('Coaching Service', () => {
  // Sample lap data for testing
  const sampleLaps: Lap[] = [
    {
      id: '1',
      sessionId: 'session-1',
      lapNumber: 1,
      startTs: 0,
      endTs: 65000,
      durationMs: 65000,
      sector1Ms: 22000,
      sector2Ms: 21500,
      sector3Ms: 21500,
      isBestLap: false,
      createdAt: new Date(),
    },
    {
      id: '2',
      sessionId: 'session-1',
      lapNumber: 2,
      startTs: 65000,
      endTs: 125000,
      durationMs: 60000,
      sector1Ms: 20000,
      sector2Ms: 20000,
      sector3Ms: 20000,
      isBestLap: true,
      createdAt: new Date(),
    },
    {
      id: '3',
      sessionId: 'session-1',
      lapNumber: 3,
      startTs: 125000,
      endTs: 188000,
      durationMs: 63000,
      sector1Ms: 21000,
      sector2Ms: 21000,
      sector3Ms: 21000,
      isBestLap: false,
      createdAt: new Date(),
    },
    {
      id: '4',
      sessionId: 'session-1',
      lapNumber: 4,
      startTs: 188000,
      endTs: 250000,
      durationMs: 62000,
      sector1Ms: 20500,
      sector2Ms: 20800,
      sector3Ms: 20700,
      isBestLap: false,
      createdAt: new Date(),
    },
  ];

  describe('computeSessionStats', () => {
    it('should compute correct session statistics', () => {
      const stats = computeSessionStats('Nürburgring', sampleLaps);
      
      expect(stats.trackName).toBe('Nürburgring');
      expect(stats.lapCount).toBe(4);
      expect(stats.bestLapMs).toBe(60000);
      expect(stats.bestLapNumber).toBe(2);
      expect(stats.avgLapMs).toBe(62500); // (65000+60000+63000+62000)/4
    });

    it('should compute consistency score correctly', () => {
      const stats = computeSessionStats('Test Track', sampleLaps);
      
      // Consistency should be high for similar lap times
      expect(stats.consistencyScore).toBeGreaterThan(90);
      expect(stats.consistencyScore).toBeLessThan(100);
    });

    it('should compute sector deltas vs best lap', () => {
      const stats = computeSessionStats('Test Track', sampleLaps);
      
      // Lap 1 vs best (Lap 2)
      const lap1 = stats.laps[0];
      expect(lap1.deltaSector1Ms).toBe(2000); // 22000 - 20000
      expect(lap1.deltaSector2Ms).toBe(1500); // 21500 - 20000
      expect(lap1.deltaSector3Ms).toBe(1500); // 21500 - 20000
      
      // Lap 2 (best lap) should have all zeros
      const lap2 = stats.laps[1];
      expect(lap2.deltaSector1Ms).toBe(0);
      expect(lap2.deltaSector2Ms).toBe(0);
      expect(lap2.deltaSector3Ms).toBe(0);
    });

    it('should identify worst sector for each lap', () => {
      const stats = computeSessionStats('Test Track', sampleLaps);
      
      // Lap 1: S1 has highest delta (2000ms), so worst sector is 1
      expect(stats.laps[0].worstSector).toBe(1);
      
      // Lap 4: S2 has highest delta (800ms vs 500ms and 700ms)
      expect(stats.laps[3].worstSector).toBe(2);
    });
  });

  describe('buildCoachingPrompt', () => {
    it('should build correctly formatted system prompt', () => {
      const stats = computeSessionStats('Spa-Francorchamps', sampleLaps);
      const { systemPrompt } = buildCoachingPrompt(stats);
      
      expect(systemPrompt).toContain('professional motorsport driving coach');
      expect(systemPrompt).toContain('actionable coaching feedback');
      expect(systemPrompt).toContain('Reference sectors and laps by number');
    });

    it('should include session data in user prompt', () => {
      const stats = computeSessionStats('Spa-Francorchamps', sampleLaps);
      const { userPrompt } = buildCoachingPrompt(stats);
      
      expect(userPrompt).toContain('Spa-Francorchamps');
      expect(userPrompt).toContain('Total laps: 4');
      expect(userPrompt).toContain('Best lap: 60000ms (Lap 2)');
      expect(userPrompt).toContain('Average lap: 62500ms');
    });

    it('should include sector breakdown table', () => {
      const stats = computeSessionStats('Test Track', sampleLaps);
      const { userPrompt } = buildCoachingPrompt(stats);
      
      // Check for lap entries
      expect(userPrompt).toContain('Lap 1:');
      expect(userPrompt).toContain('Lap 2:');
      expect(userPrompt).toContain('Lap 3:');
      expect(userPrompt).toContain('Lap 4:');
      
      // Check for sector deltas
      expect(userPrompt).toContain('S1 +2000ms'); // Lap 1 sector 1
      expect(userPrompt).toContain('S1 +0ms'); // Lap 2 (best lap)
    });

    it('should request JSON array response format', () => {
      const stats = computeSessionStats('Test Track', sampleLaps);
      const { userPrompt } = buildCoachingPrompt(stats);
      
      expect(userPrompt).toContain('Provide exactly 4 coaching tips');
      expect(userPrompt).toContain('Respond ONLY with a JSON array');
      expect(userPrompt).toContain('[{"headline": "...", "detail": "..."}');
    });
  });

  describe('parseCoachingResponse', () => {
    it('should parse valid JSON array response', () => {
      const response = `[
        {"headline": "Focus on Sector 1", "detail": "You lost 2 seconds in S1 on Lap 1."},
        {"headline": "Brake Later in Turn 3", "detail": "Your best sector times came when braking 10m later."},
        {"headline": "Maintain Consistency", "detail": "Your lap times varied by 2.5s, aim for under 1s."},
        {"headline": "Smooth Throttle Application", "detail": "Lap 2 shows optimal throttle trace in S3."}
      ]`;
      
      const tips = parseCoachingResponse(response);
      
      expect(tips).not.toBeNull();
      expect(tips).toHaveLength(4);
      expect(tips![0].headline).toBe('Focus on Sector 1');
      expect(tips![1].detail).toContain('braking 10m later');
    });

    it('should handle JSON wrapped in markdown code fences', () => {
      const response = `\`\`\`json
[
  {"headline": "Test Tip", "detail": "Test detail."}
]
\`\`\``;
      
      const tips = parseCoachingResponse(response);
      
      expect(tips).not.toBeNull();
      expect(tips).toHaveLength(1);
      expect(tips![0].headline).toBe('Test Tip');
    });

    it('should handle JSON wrapped in plain code fences', () => {
      const response = `\`\`\`
[
  {"headline": "Another Tip", "detail": "Another detail."}
]
\`\`\``;
      
      const tips = parseCoachingResponse(response);
      
      expect(tips).not.toBeNull();
      expect(tips).toHaveLength(1);
    });

    it('should return null for malformed JSON', () => {
      const response = 'This is not valid JSON at all';
      
      // Suppress console.error for this test
      const consoleSpy = jest.spyOn(console, 'error').mockImplementation();
      
      const tips = parseCoachingResponse(response);
      
      expect(tips).toBeNull();
      expect(consoleSpy).toHaveBeenCalledWith(
        'Failed to parse coaching response:',
        expect.any(Error)
      );
      
      consoleSpy.mockRestore();
    });

    it('should return null for non-array JSON', () => {
      const response = '{"headline": "Single object"}';
      
      const consoleSpy = jest.spyOn(console, 'error').mockImplementation();
      
      const tips = parseCoachingResponse(response);
      
      expect(tips).toBeNull();
      expect(consoleSpy).toHaveBeenCalledWith('Coaching response is not an array');
      
      consoleSpy.mockRestore();
    });

    it('should handle empty array', () => {
      const response = '[]';
      
      const tips = parseCoachingResponse(response);
      
      expect(tips).not.toBeNull();
      expect(tips).toHaveLength(0);
    });

    it('should handle extra whitespace', () => {
      const response = `  
      [{"headline": "Whitespace Test", "detail": "Should work."}]
      `;
      
      const tips = parseCoachingResponse(response);
      
      expect(tips).not.toBeNull();
      expect(tips).toHaveLength(1);
    });
  });
});
