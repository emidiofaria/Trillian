package com.drivingcoach.ui.home

import android.content.Context
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.entity.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var sessionDao: SessionDao
    private lateinit var lapDao: LapDao
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionDao = mock()
        lapDao = mock()
        context = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTestSession(
        id: Long = 1L,
        trackName: String = "Test Track",
        rawFilePath: String = "/test/path.jsonl"
    ): SessionEntity {
        return SessionEntity(
            id = id,
            userId = "user1",
            trackName = trackName,
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            rawFilePath = rawFilePath,
            uploadStatus = "PENDING",
            processingStatus = "PENDING",
            remoteSessionId = null,
            startLineLat1 = null,
            startLineLng1 = null,
            startLineLat2 = null,
            startLineLng2 = null
        )
    }

    // --- Delete Session Tests ---

    @Test
    fun `deleteSession calls DAO deleteById`() = runTest {
        // Setup mocks
        whenever(sessionDao.getAllSessions()).thenReturn(flowOf(emptyList()))
        whenever(sessionDao.getStaleUploadSessions(any())).thenReturn(emptyList())
        
        val session = createTestSession(id = 42L, trackName = "Track to Delete")
        whenever(sessionDao.getSessionByIdSync(42L)).thenReturn(session)

        val viewModel = HomeViewModel(sessionDao, lapDao, context)
        advanceUntilIdle()

        viewModel.deleteSession(42L)
        advanceUntilIdle()

        verify(sessionDao).deleteById(42L)
    }

    @Test
    fun `deleteSession handles missing session gracefully`() = runTest {
        // Setup mocks
        whenever(sessionDao.getAllSessions()).thenReturn(flowOf(emptyList()))
        whenever(sessionDao.getStaleUploadSessions(any())).thenReturn(emptyList())
        whenever(sessionDao.getSessionByIdSync(999L)).thenReturn(null)

        val viewModel = HomeViewModel(sessionDao, lapDao, context)
        advanceUntilIdle()

        viewModel.deleteSession(999L)
        advanceUntilIdle()

        // Should still call deleteById even if session not found
        verify(sessionDao).deleteById(999L)
    }

    // --- Rename Session Tests ---

    @Test
    fun `renameSession calls DAO updateTrackName with trimmed name`() = runTest {
        // Setup mocks
        whenever(sessionDao.getAllSessions()).thenReturn(flowOf(emptyList()))
        whenever(sessionDao.getStaleUploadSessions(any())).thenReturn(emptyList())

        val viewModel = HomeViewModel(sessionDao, lapDao, context)
        advanceUntilIdle()

        viewModel.renameSession(42L, "  New Track Name  ")
        advanceUntilIdle()

        verify(sessionDao).updateTrackName(42L, "New Track Name")
    }

    @Test
    fun `renameSession rejects empty name`() = runTest {
        // Setup mocks
        whenever(sessionDao.getAllSessions()).thenReturn(flowOf(emptyList()))
        whenever(sessionDao.getStaleUploadSessions(any())).thenReturn(emptyList())

        val viewModel = HomeViewModel(sessionDao, lapDao, context)
        advanceUntilIdle()

        viewModel.renameSession(42L, "   ")
        advanceUntilIdle()

        verify(sessionDao, never()).updateTrackName(any(), any())
    }

    @Test
    fun `renameSession rejects name longer than 100 chars`() = runTest {
        // Setup mocks
        whenever(sessionDao.getAllSessions()).thenReturn(flowOf(emptyList()))
        whenever(sessionDao.getStaleUploadSessions(any())).thenReturn(emptyList())

        val viewModel = HomeViewModel(sessionDao, lapDao, context)
        advanceUntilIdle()

        val longName = "A".repeat(101)
        viewModel.renameSession(42L, longName)
        advanceUntilIdle()

        verify(sessionDao, never()).updateTrackName(any(), any())
    }

    @Test
    fun `renameSession accepts name with exactly 100 chars`() = runTest {
        // Setup mocks
        whenever(sessionDao.getAllSessions()).thenReturn(flowOf(emptyList()))
        whenever(sessionDao.getStaleUploadSessions(any())).thenReturn(emptyList())

        val viewModel = HomeViewModel(sessionDao, lapDao, context)
        advanceUntilIdle()

        val maxLengthName = "A".repeat(100)
        viewModel.renameSession(42L, maxLengthName)
        advanceUntilIdle()

        verify(sessionDao).updateTrackName(42L, maxLengthName)
    }
}
