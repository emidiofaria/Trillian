import Anthropic from '@anthropic-ai/sdk';
import { query } from '../db';
import { Lap } from '../types';

// Initialize Anthropic client
const anthropic = new Anthropic({
  apiKey: process.env.ANTHROPIC_API_KEY,
});

/**
 * Coaching insight structure from AI response.
 */
interface CoachingTip {
  headline: string;
  detail: string;
}

/**
 * Session statistics for coaching analysis.
 */
interface SessionStats {
  trackName: string;
  lapCount: number;
  bestLapMs: number;
  bestLapNumber: number;
  avgLapMs: number;
  consistencyScore: number;
  laps: LapWithDeltas[];
}

/**
 * Lap data with sector deltas vs best lap.
 */
interface LapWithDeltas {
  lapNumber: number;
  durationMs: number;
  sector1Ms: number;
  sector2Ms: number;
  sector3Ms: number;
  deltaSector1Ms: number;
  deltaSector2Ms: number;
  deltaSector3Ms: number;
  worstSector: 1 | 2 | 3;
}

/**
 * Generates AI coaching insights for a completed session.
 * Does NOT throw on error - coaching failure should not break the session.
 */
export async function generateCoaching(sessionId: string): Promise<void> {
  try {
    console.log(`Generating AI coaching for session: ${sessionId}`);
    
    // Step 1: Load session from DB
    const sessionResult = await query<{ trackName: string }>(
      'SELECT track_name as "trackName" FROM sessions WHERE id = $1',
      [sessionId]
    );
    
    if (sessionResult.rows.length === 0) {
      console.error(`Session not found: ${sessionId}`);
      return;
    }
    
    const trackName = sessionResult.rows[0].trackName || 'Unknown Track';
    
    // Step 2: Load all laps for session
    const lapsResult = await query<Lap>(
      `SELECT 
         lap_number as "lapNumber",
         duration_ms as "durationMs",
         sector_1_ms as "sector1Ms",
         sector_2_ms as "sector2Ms",
         sector_3_ms as "sector3Ms",
         is_best_lap as "isBestLap"
       FROM laps
       WHERE session_id = $1
       ORDER BY lap_number`,
      [sessionId]
    );
    
    const laps = lapsResult.rows;
    
    if (laps.length < 2) {
      console.log(`Not enough laps for coaching: ${laps.length}`);
      return;
    }
    
    // Step 3: Compute session stats
    const stats = computeSessionStats(trackName, laps);
    
    // Step 4: Build the coaching prompt
    const { systemPrompt, userPrompt } = buildCoachingPrompt(stats);
    
    // Step 5: Call Anthropic API
    const response = await anthropic.messages.create({
      model: 'claude-sonnet-4-20250514',
      max_tokens: 600,
      system: systemPrompt,
      messages: [{ role: 'user', content: userPrompt }],
    });
    
    // Step 6: Parse the response
    const responseText = response.content[0].type === 'text' 
      ? response.content[0].text 
      : '';
    
    const tips = parseCoachingResponse(responseText);
    
    if (!tips || tips.length === 0) {
      console.error('Failed to parse coaching response');
      return;
    }
    
    // Step 7: Validate tips
    const validTips = tips.filter(
      (tip) => 
        typeof tip.headline === 'string' && 
        typeof tip.detail === 'string' &&
        tip.headline.length > 0 &&
        tip.detail.length > 0
    );
    
    if (validTips.length < 3 || validTips.length > 5) {
      console.warn(`Unexpected number of coaching tips: ${validTips.length}`);
    }
    
    // Step 8: Insert into coaching_insights table
    for (const tip of validTips) {
      await query(
        `INSERT INTO coaching_insights (session_id, headline, detail)
         VALUES ($1, $2, $3)`,
        [sessionId, tip.headline.slice(0, 200), tip.detail.slice(0, 1000)]
      );
    }
    
    console.log(`Generated ${validTips.length} coaching insights for session: ${sessionId}`);
  } catch (error) {
    // Step 9: Log error but do NOT throw
    console.error(`Error generating coaching for session ${sessionId}:`, error);
  }
}

/**
 * Computes session statistics from lap data.
 */
export function computeSessionStats(trackName: string, laps: Lap[]): SessionStats {
  const durations = laps.map((l) => l.durationMs);
  const bestLapMs = Math.min(...durations);
  const bestLap = laps.find((l) => l.durationMs === bestLapMs)!;
  const avgLapMs = Math.round(durations.reduce((a, b) => a + b, 0) / durations.length);
  
  // Compute standard deviation
  const variance = durations.reduce((sum, d) => sum + Math.pow(d - avgLapMs, 2), 0) / durations.length;
  const stdDev = Math.sqrt(variance);
  const consistencyScore = Math.round((1 - stdDev / avgLapMs) * 1000) / 10; // 1 decimal place
  
  // Best lap sector times (default to 0 if null)
  const bestS1 = bestLap.sector1Ms ?? 0;
  const bestS2 = bestLap.sector2Ms ?? 0;
  const bestS3 = bestLap.sector3Ms ?? 0;
  
  // Compute deltas vs best lap
  const lapsWithDeltas: LapWithDeltas[] = laps.map((lap) => {
    const s1 = lap.sector1Ms ?? 0;
    const s2 = lap.sector2Ms ?? 0;
    const s3 = lap.sector3Ms ?? 0;
    
    const deltaSector1Ms = s1 - bestS1;
    const deltaSector2Ms = s2 - bestS2;
    const deltaSector3Ms = s3 - bestS3;
    
    // Find worst sector (relative to best lap)
    const deltas = [deltaSector1Ms, deltaSector2Ms, deltaSector3Ms];
    const maxDeltaIndex = deltas.indexOf(Math.max(...deltas));
    const worstSector = (maxDeltaIndex + 1) as 1 | 2 | 3;
    
    return {
      lapNumber: lap.lapNumber,
      durationMs: lap.durationMs,
      sector1Ms: s1,
      sector2Ms: s2,
      sector3Ms: s3,
      deltaSector1Ms,
      deltaSector2Ms,
      deltaSector3Ms,
      worstSector,
    };
  });
  
  return {
    trackName,
    lapCount: laps.length,
    bestLapMs,
    bestLapNumber: bestLap.lapNumber,
    avgLapMs,
    consistencyScore,
    laps: lapsWithDeltas,
  };
}

/**
 * Builds the system and user prompts for AI coaching.
 */
export function buildCoachingPrompt(stats: SessionStats): { systemPrompt: string; userPrompt: string } {
  const systemPrompt = 
    'You are a professional motorsport driving coach. You analyse telemetry data and provide precise, actionable coaching feedback. Be direct. Use specific numbers. Reference sectors and laps by number. You are coaching a driver who just completed a track session.';
  
  // Build sector table
  const lapSectorTable = stats.laps.map((lap) => {
    const s1Sign = lap.deltaSector1Ms >= 0 ? '+' : '';
    const s2Sign = lap.deltaSector2Ms >= 0 ? '+' : '';
    const s3Sign = lap.deltaSector3Ms >= 0 ? '+' : '';
    
    return `  Lap ${lap.lapNumber}: S1 ${s1Sign}${lap.deltaSector1Ms}ms, S2 ${s2Sign}${lap.deltaSector2Ms}ms, S3 ${s3Sign}${lap.deltaSector3Ms}ms (worst: S${lap.worstSector})`;
  }).join('\n');
  
  const userPrompt = `Session analysis for ${stats.trackName}:
- Total laps: ${stats.lapCount}
- Best lap: ${stats.bestLapMs}ms (Lap ${stats.bestLapNumber})
- Average lap: ${stats.avgLapMs}ms
- Consistency score: ${stats.consistencyScore}%
- Sector breakdown vs best lap (delta in ms, positive = slower than best):
${lapSectorTable}

Provide exactly 4 coaching tips. Each tip must:
- Start with a headline of maximum 8 words
- Follow with a detail sentence of maximum 35 words
- Be specific: reference lap numbers, sector numbers, or time values
- Be actionable: tell the driver exactly what to do differently

Respond ONLY with a JSON array, no other text:
[{"headline": "...", "detail": "..."}, ...]`;
  
  return { systemPrompt, userPrompt };
}

/**
 * Parses the AI response to extract coaching tips.
 */
export function parseCoachingResponse(responseText: string): CoachingTip[] | null {
  try {
    // Strip markdown code fences if present
    let jsonText = responseText.trim();
    
    // Remove ```json ... ``` or ``` ... ```
    if (jsonText.startsWith('```')) {
      const startIndex = jsonText.indexOf('\n') + 1;
      const endIndex = jsonText.lastIndexOf('```');
      if (endIndex > startIndex) {
        jsonText = jsonText.slice(startIndex, endIndex).trim();
      }
    }
    
    // Parse JSON
    const parsed = JSON.parse(jsonText);
    
    // Validate structure
    if (!Array.isArray(parsed)) {
      console.error('Coaching response is not an array');
      return null;
    }
    
    return parsed as CoachingTip[];
  } catch (error) {
    console.error('Failed to parse coaching response:', error);
    console.error('Response text:', responseText.slice(0, 500));
    return null;
  }
}

/**
 * Format milliseconds as readable lap time.
 */
export function formatLapTime(ms: number): string {
  const totalSeconds = ms / 1000;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  
  if (minutes > 0) {
    return `${minutes}:${seconds.toFixed(3).padStart(6, '0')}`;
  }
  return `${seconds.toFixed(3)}s`;
}
