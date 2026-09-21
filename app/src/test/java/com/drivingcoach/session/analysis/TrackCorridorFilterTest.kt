package com.drivingcoach.session.analysis

import com.drivingcoach.data.track.BundledTrackCatalog
import com.drivingcoach.ui.session.tabs.analysis.SessionAnalysisProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The corridor filter, measured against the session that motivated it.
 *
 * The Baltar driver spent sixteen of his twenty-six minutes queuing in the pit
 * area with the recording running. Those samples are real telemetry of a real
 * phone, and nothing in the data marks them as not-driving; only knowing where
 * the circuit is separates them. That knowledge now exists, so the session
 * statistics no longer have to average the queue into the lap.
 *
 * The filter deliberately does not touch lap detection. Laps are decided by
 * crossings, and a filter that removed a sample either side of the line would be
 * changing lap times to tidy a chart.
 */
class TrackCorridorFilterTest {

    private fun centreline(): List<Pair<Double, Double>> =
        BundledTrackCatalog.parse(File("src/main/assets/tracks/tracks.json").readText())
            .first { it.id == "baltar" }
            .centreline!!
            .points
            .map { it.latitude to it.longitude }

    private fun baltarSamples(): List<SessionAnalysisProcessor.Point> =
        File("src/test/resources/lapfixtures/baltar2/telemetry.jsonl")
            .readLines()
            .mapNotNull { line ->
                if (!line.contains("\"latitude\"")) return@mapNotNull null
                val o = org.json.JSONObject(line)
                SessionAnalysisProcessor.Point(
                    timestampMs = o.getLong("timestampMs"),
                    latitude = o.getDouble("latitude"),
                    longitude = o.getDouble("longitude"),
                    speedKmh = o.getDouble("speedMs") * 3.6
                )
            }

    @Test
    fun withoutACentrelineNothingIsFiltered() {
        val samples = baltarSamples()

        assertSame(
            "a session on an uncatalogued circuit must behave exactly as before",
            samples, SessionAnalysisProcessor.onTrackSamples(samples, null)
        )
        assertSame(
            "a degenerate centreline is no centreline",
            samples, SessionAnalysisProcessor.onTrackSamples(samples, listOf(41.1878 to -8.3956))
        )
    }

    @Test
    fun theQueueIsRemovedAndTheDrivingIsKept() {
        val samples = baltarSamples()
        val kept = SessionAnalysisProcessor.onTrackSamples(samples, centreline())

        assertTrue("the filter should remove a meaningful amount", kept.size < samples.size)

        val drivingBefore = samples.count { it.speedKmh >= 28.8 }
        val drivingAfter = kept.count { it.speedKmh >= 28.8 }
        assertTrue(
            "driving samples must survive: $drivingAfter of $drivingBefore",
            drivingAfter >= drivingBefore * 0.95
        )

        val queueBefore = samples.count { it.speedKmh < 10.8 }
        val queueAfter = kept.count { it.speedKmh < 10.8 }
        assertTrue(
            "most of the queue should be gone: $queueAfter of $queueBefore",
            queueAfter <= queueBefore * 0.25
        )
    }

    /**
     * The guard against being wrong about which circuit this is. A centreline
     * somewhere else entirely must not silently delete the session.
     */
    @Test
    fun aCentrelineForAnotherPlaceIsIgnoredRatherThanObeyed() {
        val samples = baltarSamples()
        val elsewhere = centreline().map { (lat, lng) -> lat + 1.0 to lng + 1.0 }

        assertSame(
            "keeping nothing is not a believable answer, so the filter stands down",
            samples, SessionAnalysisProcessor.onTrackSamples(samples, elsewhere)
        )
    }

    @Test
    fun aSampleOnTheStartFinishSurvives() {
        // The midpoint of the surveyed line, which is where lap timing happens.
        val onLine = SessionAnalysisProcessor.Point(
            timestampMs = 1L,
            latitude = 41.187804,
            longitude = -8.395713,
            speedKmh = 50.0
        )
        val padding = (2..60).map { onLine.copy(timestampMs = it.toLong()) }

        val kept = SessionAnalysisProcessor.onTrackSamples(listOf(onLine) + padding, centreline())

        assertEquals("the start/finish is on the circuit by definition", 60, kept.size)
    }
}
