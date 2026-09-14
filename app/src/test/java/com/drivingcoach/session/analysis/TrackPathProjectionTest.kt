package com.drivingcoach.session.analysis

import com.drivingcoach.ui.session.tabs.analysis.SessionAnalysisProcessor
import com.drivingcoach.util.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.abs
import kotlin.math.max

/**
 * The track outline is drawn from GPS alone, with no map tiles, so the
 * projection has to be right on its own: a stretched or clipped outline is not a
 * cosmetic problem, it is the difference between recognising your circuit and
 * not.
 */
internal class TrackPathProjectionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun everyDrawnPointIsInsideTheUnitSquare() {
        val path = analyse().path

        assertTrue("There is an outline to draw", path.points.size > 10)
        assertTrue(
            "Points outside 0..1 would be drawn off the edge of the view",
            path.points.all { it.x in 0f..1f && it.y in 0f..1f }
        )
    }

    @Test
    fun theShapeOfTheTrackIsNotStretched() {
        val fixture = AnalysisFixtures.teste3(tempFolder.newFolder())
        val analysis = analyse(fixture)
        val lap = fixture.lapWindows.getValue(analysis.referenceLapId!!)
        val within = fixture.samples.filter { it.timestampMs in lap }

        val refLat = within.first().latitude
        val refLng = within.first().longitude
        val metres = within.map { GeoUtils.toLocalMetres(it.latitude, it.longitude, refLat, refLng) }
        val realWidth = metres.maxOf { it.first } - metres.minOf { it.first }
        val realHeight = metres.maxOf { it.second } - metres.minOf { it.second }

        val points = analysis.path.points
        val drawnWidth = points.maxOf { it.x } - points.minOf { it.x }
        val drawnHeight = points.maxOf { it.y } - points.minOf { it.y }

        val realAspect = realWidth / realHeight
        val drawnAspect = drawnWidth / drawnHeight
        assertEquals(
            "Aspect ratio must survive projection (real $realAspect, drawn $drawnAspect)",
            realAspect, drawnAspect.toDouble(), 0.05
        )

        // The larger dimension is what fills the view; the smaller is centred.
        assertEquals(1.0, max(drawnWidth, drawnHeight).toDouble(), 0.01)
    }

    @Test
    fun thinningKeepsBothEndsOfTheLap() {
        val fixture = AnalysisFixtures.teste3(tempFolder.newFolder())
        val tenHz = AnalysisFixtures.resample(fixture.samples, 10.0)
        val referenceLapId = fixture.laps.first { it.isBestLap }.lapId

        val analysis = SessionAnalysisProcessor.analyzeSamples(
            tenHz, fixture.laps, referenceLapId, fixture.lapWindows, fixture.startLine
        )
        val lap = fixture.lapWindows.getValue(referenceLapId)
        val withinLap = tenHz.filter { it.timestampMs in lap }

        assertEquals(
            SessionAnalysisProcessor.TRACK_PATH_POINT_BUDGET,
            analysis.path.points.size
        )
        assertEquals(
            "The outline must start where the lap started",
            withinLap.first().timestampMs, analysis.path.points.first().timestampMs
        )
        assertEquals(
            "The outline must end where the lap ended, or it will not close",
            withinLap.last().timestampMs, analysis.path.points.last().timestampMs
        )
    }

    @Test
    fun brakingPointsFallInsideBrakingZones() {
        val analysis = analyse()
        val braking = analysis.path.points.filter { it.isBraking }

        assertTrue("Some of the outline should be drawn as braking", braking.isNotEmpty())
        braking.forEach { point ->
            assertTrue(
                "A point drawn in red at ${point.timestampMs} is not inside any braking zone",
                analysis.brakingZones.any { point.timestampMs in it.startMs..it.endMs }
            )
        }
    }

    @Test
    fun theStartFinishLineIsMarkedWhereItWasCaptured() {
        val fixture = AnalysisFixtures.teste3(tempFolder.newFolder())
        val analysis = analyse(fixture)
        val marker = analysis.path.startFinish

        assertNotNull("The start/finish must be visible on the map", marker)
        assertEquals(SessionAnalysisProcessor.START_FINISH_LABEL, marker!!.label)

        // The captured line is on the circuit, so its marker must land on the
        // drawn outline rather than somewhere off in the corner of the view.
        val nearest = analysis.path.points.minOf { point ->
            abs(point.x - marker.x) + abs(point.y - marker.y)
        }
        assertTrue("Start/finish marker is $nearest away from the track", nearest < 0.05f)
    }

    @Test
    fun everyCornerHasAMarkerOnTheOutline() {
        val analysis = analyse()

        assertEquals(analysis.corners.size, analysis.path.cornerMarkers.size)
        assertEquals(
            analysis.corners.map { it.name },
            analysis.path.cornerMarkers.map { it.label }
        )
        assertTrue(
            analysis.path.cornerMarkers.all { it.x in 0f..1f && it.y in 0f..1f }
        )
    }

    private fun analyse(
        fixture: AnalysisFixtures.Fixture = AnalysisFixtures.teste3(tempFolder.newFolder())
    ) = SessionAnalysisProcessor.analyzeSamples(
        fixture.samples,
        fixture.laps,
        fixture.laps.first { it.isBestLap }.lapId,
        fixture.lapWindows,
        fixture.startLine
    )
}
