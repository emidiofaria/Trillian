package com.drivingcoach.coaching

import android.util.Log
import com.drivingcoach.data.db.dao.CoachingInsightDao
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.entity.CoachingInsightEntity
import com.drivingcoach.data.db.entity.LapEntity
import com.drivingcoach.data.telemetry.TelemetrySample
import com.drivingcoach.lap.LapDiagnosticsWriter
import com.drivingcoach.lap.MergedLapCaveat
import com.drivingcoach.lap.SectorSplitter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings a session's stored sector times up to date with the current measurement.
 *
 * ### Why sessions need repairing at all
 *
 * Sectors used to be measured with the clock starting at the start/finish line and
 * the ruler starting at the first GPS fix after it. At the reference device's 1 Hz
 * that is fifteen to twenty metres of road counted by one and not the other, so
 * every boundary sat late on the circuit by an amount that varied lap to lap with
 * nothing but the GPS clock's phase. Sector 1 carried roughly half a second of
 * artefact. Those numbers are in the database of every session recorded so far.
 *
 * ### Why repair on read rather than migrate
 *
 * A Room migration cannot do this work: it would have to re-read every telemetry file
 * from inside a schema upgrade, on a device that may be low on time and battery, for
 * sessions the driver may never open again. Repairing when a session is opened does
 * the same work lazily, costs nothing for sessions nobody looks at, and needs no
 * schema change - the database stays at version 5.
 *
 * It also makes the repair naturally idempotent. There is no "has been migrated"
 * flag to get out of step with reality, because the check *is* the recomputation:
 * recompute, compare, and write only on difference.
 *
 * ### Why insights are refreshed on every open (OC-32)
 *
 * Insights are *sentences* persisted once, at stop. Refreshing them only after a sector
 * correction left every session recorded before v2.96 - or whose sectors were already
 * right - with the wording of the version that recorded it: no Dream Lap, Outlier or
 * Pace Trend, and none of the later fixes. So every open regenerates them, compares
 * `(headline, detail)` with the stored local ones, and writes only on difference.
 *
 * The dream lap must still not be resurrected over a merged lap (OC-14). When the
 * diagnostics sidecar is readable, its caveat is passed in. When it is not - an older
 * session, or a failed write - the engine builds the dream lap from laps within ±15 %
 * of the median only (OC-33), which neither a merged lap nor a half-lap survives.
 * Only `isLocalOnly` insights are replaced, and never by an empty result.
 */
@Singleton
class SectorRepair @Inject constructor(
    private val lapDao: LapDao,
    private val coachingInsightDao: CoachingInsightDao,
    private val lapDiagnosticsWriter: LapDiagnosticsWriter
) {

    companion object {
        private const val TAG = "SectorRepair"

        /**
         * Smallest difference, in milliseconds, worth rewriting a row for.
         *
         * Recomputation is deterministic, so a correctly measured lap recomputes
         * bit-for-bit and this threshold never fires for it. It exists only so that a
         * future change to rounding somewhere in the chain cannot turn every session
         * open into three database writes.
         */
        const val TOLERANCE_MS = 1L
    }

    /** What the repair did, for logging and for tests to assert on. */
    data class Outcome(
        val lapsExamined: Int,
        val lapsCorrected: Int,
        /** True when the stored local insights differed from today's and were replaced. */
        val insightsRegenerated: Boolean
    ) {
        val changedAnything: Boolean get() = lapsCorrected > 0
    }

    /**
     * Recomputes [laps] from [samples], persists any that have moved, then refreshes
     * the session's local insights.
     *
     * @param samples the session's telemetry, already in memory. This is deliberately
     *   taken as a parameter rather than read here: the caller loads the file anyway to
     *   draw the map, and reading it twice to save one argument would double the cost
     *   of opening a session.
     * @param telemetryFile used to find the diagnostics sidecar and the top speed. Null
     *   means the merged-lap signal is unknown, so the OC-33 median guard applies.
     */
    suspend fun repair(
        sessionId: Long,
        laps: List<LapEntity>,
        samples: List<TelemetrySample>,
        telemetryFile: File?
    ): Outcome {
        if (laps.isEmpty() || samples.isEmpty()) {
            return Outcome(laps.size, 0, insightsRegenerated = false)
        }

        val corrected = ArrayList<LapEntity>(laps.size)
        var changed = 0

        for (lap in laps) {
            val sectors = SectorSplitter.split(samples, lap.startTs, lap.endTs)
            if (sectors == null) {
                // The lap cannot be divided honestly now. Its stored sectors are not
                // thereby proven wrong, and blanking them would lose information to no
                // one's benefit, so it is left alone and excluded from the comparison.
                corrected.add(lap)
                continue
            }

            val moved = differs(lap.sector1Ms, sectors.sector1Ms) ||
                differs(lap.sector2Ms, sectors.sector2Ms) ||
                differs(lap.sector3Ms, sectors.sector3Ms)

            if (!moved) {
                corrected.add(lap)
                continue
            }

            lapDao.updateSectors(lap.id, sectors.sector1Ms, sectors.sector2Ms, sectors.sector3Ms)
            corrected.add(
                lap.copy(
                    sector1Ms = sectors.sector1Ms,
                    sector2Ms = sectors.sector2Ms,
                    sector3Ms = sectors.sector3Ms
                )
            )
            changed++
        }

        if (changed > 0) {
            Log.d(TAG, "Corrected sectors on $changed of ${laps.size} laps for session $sessionId")
        }

        val regenerated = refreshInsights(sessionId, corrected, telemetryFile)
        return Outcome(
            lapsExamined = laps.size,
            lapsCorrected = changed,
            insightsRegenerated = regenerated
        )
    }

    private fun differs(stored: Long, computed: Long): Boolean =
        Math.abs(stored - computed) >= TOLERANCE_MS

    /**
     * Rebuilds the locally generated insights with today's engine and replaces the
     * stored ones only when the wording differs (OC-32).
     *
     * Without a diagnostics sidecar the merged-lap signal is unknown, so the engine is
     * told so and builds the dream lap only from laps near the median (OC-33).
     *
     * @return true when the stored insights were replaced.
     */
    private suspend fun refreshInsights(
        sessionId: Long,
        laps: List<LapEntity>,
        telemetryFile: File?
    ): Boolean {
        return try {
            val diagnostics = telemetryFile?.let { lapDiagnosticsWriter.read(it) }
            val insights = OfflineCoachingEngine.generateInsights(
                laps = laps,
                telemetryFilePath = telemetryFile?.takeIf { it.exists() }?.absolutePath,
                mergedLapCaveat = diagnostics?.let { MergedLapCaveat.of(it) },
                mergedSignalKnown = diagnostics != null
            )
            if (insights.isEmpty()) {
                // Replacing real insights with none would be a visible loss, and an
                // empty result here means the laps no longer support any statement
                // rather than that the stored ones were wrong.
                return false
            }

            val stored = coachingInsightDao.getInsightsForSessionOnce(sessionId)
                .filter { it.isLocalOnly }
                .map { it.headline to it.detail }
            val fresh = insights.map { it.headline to it.detail }
            if (stored == fresh) return false

            val now = System.currentTimeMillis()
            coachingInsightDao.deleteLocalInsightsForSession(sessionId)
            coachingInsightDao.insertInsights(
                insights.map {
                    CoachingInsightEntity(
                        sessionId = sessionId,
                        headline = it.headline,
                        detail = it.detail,
                        generatedAt = now,
                        isLocalOnly = true
                    )
                }
            )
            Log.d(TAG, "Regenerated ${insights.size} local insights for session $sessionId")
            true
        } catch (e: Exception) {
            // Opening a session must not fail because its coaching text could not be
            // rebuilt. The corrected sector times are already persisted and are the
            // part that matters.
            Log.w(TAG, "Could not regenerate insights for session $sessionId", e)
            false
        }
    }
}
