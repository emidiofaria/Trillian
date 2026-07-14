package com.drivingcoach.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    companion object {
        // Known GPS coordinates for testing
        // San Francisco: 37.7749, -122.4194
        // Los Angeles: 34.0522, -118.2437
        // Expected distance: ~559 km

        const val SF_LAT = 37.7749
        const val SF_LNG = -122.4194
        const val LA_LAT = 34.0522
        const val LA_LNG = -118.2437

        // Track simulation: ~10m wide finish line
        const val FINISH_LEFT_LAT = 48.12345
        const val FINISH_LEFT_LNG = 11.56789
        const val FINISH_RIGHT_LAT = 48.12345
        const val FINISH_RIGHT_LNG = 11.56800  // ~8m east

        const val TOLERANCE_PERCENT = 0.01 // 1% tolerance for haversine
    }

    // ==================== Haversine Distance Tests ====================

    @Test
    fun `haversine - SF to LA is approximately 559 km`() {
        val distance = GeoUtils.haversineDistance(SF_LAT, SF_LNG, LA_LAT, LA_LNG)
        val expectedKm = 559.0
        val toleranceKm = expectedKm * TOLERANCE_PERCENT

        assertEquals(expectedKm * 1000, distance, toleranceKm * 1000)
    }

    @Test
    fun `haversine - same point returns zero`() {
        val distance = GeoUtils.haversineDistance(SF_LAT, SF_LNG, SF_LAT, SF_LNG)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `haversine - short distance is accurate`() {
        // Two points ~100m apart (approx 0.0009 degrees latitude)
        val lat1 = 48.12345
        val lng1 = 11.56789
        val lat2 = 48.12435  // ~100m north
        val lng2 = 11.56789

        val distance = GeoUtils.haversineDistance(lat1, lng1, lat2, lng2)

        // Should be close to 100m (within 5m tolerance)
        assertEquals(100.0, distance, 5.0)
    }

    @Test
    fun `haversine - symmetric`() {
        val d1 = GeoUtils.haversineDistance(SF_LAT, SF_LNG, LA_LAT, LA_LNG)
        val d2 = GeoUtils.haversineDistance(LA_LAT, LA_LNG, SF_LAT, SF_LNG)

        assertEquals(d1, d2, 0.001)
    }

    // ==================== Line Intersection Tests ====================

    @Test
    fun `lineIntersection - path crosses finish line returns true`() {
        // Path goes from south to north across the finish line
        val prevLat = 48.12340  // South of line
        val prevLng = 11.56795
        val currLat = 48.12350  // North of line
        val currLng = 11.56795

        val crosses = GeoUtils.lineIntersection(
            FINISH_LEFT_LAT, FINISH_LEFT_LNG,
            FINISH_RIGHT_LAT, FINISH_RIGHT_LNG,
            prevLat, prevLng,
            currLat, currLng
        )

        assertTrue("Path should cross finish line", crosses)
    }

    @Test
    fun `lineIntersection - parallel path does not cross`() {
        // Path runs parallel to the finish line (east-west)
        val prevLat = 48.12340  // South of line
        val prevLng = 11.56780
        val currLat = 48.12340  // Still south, moved east
        val currLng = 11.56810

        val crosses = GeoUtils.lineIntersection(
            FINISH_LEFT_LAT, FINISH_LEFT_LNG,
            FINISH_RIGHT_LAT, FINISH_RIGHT_LNG,
            prevLat, prevLng,
            currLat, currLng
        )

        assertFalse("Parallel path should not cross finish line", crosses)
    }

    @Test
    fun `lineIntersection - path ends before reaching line`() {
        // Both points are south of the line
        val prevLat = 48.12335
        val prevLng = 11.56795
        val currLat = 48.12340  // Still south of 48.12345
        val currLng = 11.56795

        val crosses = GeoUtils.lineIntersection(
            FINISH_LEFT_LAT, FINISH_LEFT_LNG,
            FINISH_RIGHT_LAT, FINISH_RIGHT_LNG,
            prevLat, prevLng,
            currLat, currLng
        )

        assertFalse("Path ending before line should not cross", crosses)
    }

    @Test
    fun `lineIntersection - path misses line to the side`() {
        // Path crosses north-south but outside the line width
        val prevLat = 48.12340
        val prevLng = 11.56850  // East of finish line
        val currLat = 48.12350
        val currLng = 11.56850

        val crosses = GeoUtils.lineIntersection(
            FINISH_LEFT_LAT, FINISH_LEFT_LNG,
            FINISH_RIGHT_LAT, FINISH_RIGHT_LNG,
            prevLat, prevLng,
            currLat, currLng
        )

        assertFalse("Path missing line to the side should not cross", crosses)
    }

    @Test
    fun `lineIntersection - diagonal crossing works`() {
        // Diagonal path crossing the finish line
        val prevLat = 48.12340
        val prevLng = 11.56788
        val currLat = 48.12350
        val currLng = 11.56798

        val crosses = GeoUtils.lineIntersection(
            FINISH_LEFT_LAT, FINISH_LEFT_LNG,
            FINISH_RIGHT_LAT, FINISH_RIGHT_LNG,
            prevLat, prevLng,
            currLat, currLng
        )

        assertTrue("Diagonal crossing should be detected", crosses)
    }

    @Test
    fun `lineIntersection - stationary point on line`() {
        // Edge case: GPS point exactly on the line (not moving)
        val pointLat = 48.12345
        val pointLng = 11.56795

        val crosses = GeoUtils.lineIntersection(
            FINISH_LEFT_LAT, FINISH_LEFT_LNG,
            FINISH_RIGHT_LAT, FINISH_RIGHT_LNG,
            pointLat, pointLng,
            pointLat, pointLng  // Same point
        )

        // A point ON the line counts as touching it (collinear case)
        // The lap detector will filter duplicate crossings by time
        assertTrue("Point on line should be detected as touching", crosses)
    }

    @Test
    fun `lineIntersection - crossing in reverse direction`() {
        // Path goes from north to south (reverse lap)
        val prevLat = 48.12350  // North of line
        val prevLng = 11.56795
        val currLat = 48.12340  // South of line
        val currLng = 11.56795

        val crosses = GeoUtils.lineIntersection(
            FINISH_LEFT_LAT, FINISH_LEFT_LNG,
            FINISH_RIGHT_LAT, FINISH_RIGHT_LNG,
            prevLat, prevLng,
            currLat, currLng
        )

        assertTrue("Reverse direction crossing should be detected", crosses)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `lineIntersection - very short segment crossing line`() {
        // Tiny movement across the line (high GPS frequency)
        val prevLat = 48.123449
        val prevLng = 11.56795
        val currLat = 48.123451
        val currLng = 11.56795

        val crosses = GeoUtils.lineIntersection(
            FINISH_LEFT_LAT, FINISH_LEFT_LNG,
            FINISH_RIGHT_LAT, FINISH_RIGHT_LNG,
            prevLat, prevLng,
            currLat, currLng
        )

        assertTrue("Very short crossing segment should be detected", crosses)
    }

    @Test
    fun `haversine - handles negative coordinates`() {
        // South America
        val lat1 = -34.6037  // Buenos Aires
        val lng1 = -58.3816
        val lat2 = -22.9068  // Rio de Janeiro
        val lng2 = -43.1729

        val distance = GeoUtils.haversineDistance(lat1, lng1, lat2, lng2)

        // Expected: ~1968 km
        assertTrue("Distance should be positive", distance > 0)
        assertEquals(1968000.0, distance, 50000.0) // 50km tolerance
    }
}
