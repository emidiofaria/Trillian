package com.drivingcoach.ui.tracksetup

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class TrackSetupViewModelTest {

    private lateinit var viewModel: TrackSetupViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TrackSetupViewModel(mock(com.drivingcoach.data.track.TrackRepository::class.java))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockLocation(lat: Double, lng: Double): Location {
        val location = mock(Location::class.java)
        `when`(location.latitude).thenReturn(lat)
        `when`(location.longitude).thenReturn(lng)
        return location
    }

    @Test
    fun `initial state has no points and is invalid`() {
        val state = viewModel.state.value

        assertNull(state.pointA)
        assertNull(state.pointB)
        assertEquals(0.0, state.distance, 0.001)
        assertFalse(state.isValid)
        assertFalse(state.isGpsReady)
    }

    @Test
    fun `updateGpsStatus marks GPS ready when accuracy and satellites are good`() {
        viewModel.updateGpsStatus(accuracy = 5.0f, satelliteCount = 8)

        val state = viewModel.state.value
        assertEquals(5.0f, state.gpsAccuracy, 0.001f)
        assertEquals(8, state.satelliteCount)
        assertTrue(state.isGpsReady)
    }

    @Test
    fun `updateGpsStatus marks GPS not ready with poor accuracy`() {
        viewModel.updateGpsStatus(accuracy = 15.0f, satelliteCount = 8)

        assertFalse(viewModel.state.value.isGpsReady)
    }

    @Test
    fun `updateGpsStatus marks GPS not ready with few satellites`() {
        viewModel.updateGpsStatus(accuracy = 5.0f, satelliteCount = 2)

        assertFalse(viewModel.state.value.isGpsReady)
    }

    @Test
    fun `setPointA captures location`() = runTest {
        val location = mockLocation(48.12345, 11.56789)

        viewModel.setPointA(location)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.pointA)
        assertEquals(48.12345, state.pointA!!.latitude, 0.00001)
        assertEquals(11.56789, state.pointA!!.longitude, 0.00001)
        assertNull(state.pointB)
        assertFalse(state.isValid)
    }

    @Test
    fun `setPointB calculates distance and validates`() = runTest {
        // Set point A
        viewModel.setPointA(mockLocation(48.12345, 11.56789))
        testDispatcher.scheduler.advanceUntilIdle()

        // Set point B (~8m away, East)
        viewModel.setPointB(mockLocation(48.12345, 11.56800))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.pointA)
        assertNotNull(state.pointB)
        assertTrue("Distance should be > 3m", state.distance >= 3.0)
        assertTrue("Line should be valid", state.isValid)
    }

    @Test
    fun `setPointB with too short distance is invalid`() = runTest {
        // Set point A
        viewModel.setPointA(mockLocation(48.12345, 11.56789))
        testDispatcher.scheduler.advanceUntilIdle()

        // Set point B very close (~1m)
        viewModel.setPointB(mockLocation(48.12345, 11.567895))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.pointB)
        assertTrue("Distance should be < 3m", state.distance < 3.0)
        assertFalse("Line should be invalid", state.isValid)
    }

    @Test
    fun `clear resets all state`() = runTest {
        // Set up valid points
        viewModel.setPointA(mockLocation(48.12345, 11.56789))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setPointB(mockLocation(48.12345, 11.56800))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isValid)

        // Clear
        viewModel.clear()

        val state = viewModel.state.value
        assertNull(state.pointA)
        assertNull(state.pointB)
        assertEquals(0.0, state.distance, 0.001)
        assertFalse(state.isValid)
    }

    @Test
    fun `re-capturing pointA clears pointB`() = runTest {
        // Set both points
        viewModel.setPointA(mockLocation(48.12345, 11.56789))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setPointB(mockLocation(48.12345, 11.56800))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isValid)

        // Re-capture A
        viewModel.setPointA(mockLocation(48.12350, 11.56795))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.pointA)
        assertNull(state.pointB)
        assertFalse(state.isValid)
    }

    @Test
    fun `getStartLineCoords returns null when invalid`() {
        assertNull(viewModel.getStartLineCoords())
    }

    @Test
    fun `getStartLineCoords returns coords when valid`() = runTest {
        viewModel.setPointA(mockLocation(48.12345, 11.56789))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setPointB(mockLocation(48.12345, 11.56800))
        testDispatcher.scheduler.advanceUntilIdle()

        val coords = viewModel.getStartLineCoords()
        assertNotNull(coords)
        assertEquals(48.12345, coords!!.lat1, 0.00001)
        assertEquals(11.56789, coords.lng1, 0.00001)
        assertEquals(48.12345, coords.lat2, 0.00001)
        assertEquals(11.56800, coords.lng2, 0.00001)
    }
}
