import { formatLapTime } from '../src/lap/lapProcessor';

describe('lapProcessor', () => {
  describe('formatLapTime', () => {
    it('should format sub-minute time correctly', () => {
      expect(formatLapTime(45500)).toBe('45.500s');
      expect(formatLapTime(59999)).toBe('59.999s');
      expect(formatLapTime(1234)).toBe('1.234s');
    });

    it('should format multi-minute time correctly', () => {
      expect(formatLapTime(60000)).toBe('1:00.000');
      expect(formatLapTime(83456)).toBe('1:23.456');
      expect(formatLapTime(120000)).toBe('2:00.000');
      expect(formatLapTime(125789)).toBe('2:05.789');
    });

    it('should handle edge cases', () => {
      expect(formatLapTime(0)).toBe('0.000s');
      expect(formatLapTime(1)).toBe('0.001s');
      expect(formatLapTime(600000)).toBe('10:00.000');
    });
  });
});
