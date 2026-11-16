package com.drivingcoach.progression

import com.drivingcoach.data.repository.SessionRewards
import com.drivingcoach.ui.rewards.RewardPopup
import com.drivingcoach.ui.rewards.RewardsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionGoalsTest {

    private fun session(id: Long, laps: List<Long>, distanceM: Double = 6_000.0, track: String = "Baltar",
                        trusted: Boolean = true) =
        SessionFacts(id, id * 1000, TrackKey.of(null, track), track, distanceM, laps, trusted)

    private fun trophy(sessionId: Long, type: TrophyType, at: Long = sessionId * 1000) =
        TrophyRecord(sessionId, type, "name:baltar", 0, at)

    private fun counts(bronze: Int = 0, silver: Int = 0, gold: Int = 0) = TrophyCounts(mapOf(
        TrophyType.FINISHER_BRONZE to bronze, TrophyType.PB_SILVER to silver, TrophyType.TRACK_MASTER_GOLD to gold))

    @Test
    fun nextGoalIsKmWhenKmShort() {
        val goal = ProgressionGoals.nextGoal(LevelLadder.progress(10.0, counts()))
        assertEquals(NextGoal.DriveKm(2, 15.0), goal)
    }

    @Test
    fun nextGoalIsMissingTrophyWhenKmMet() {
        val goal = ProgressionGoals.nextGoal(LevelLadder.progress(120.0, counts(bronze = 1)))
        assertEquals(NextGoal.EarnTrophies(3, TrophyType.FINISHER_BRONZE, 2), goal)
    }

    @Test
    fun nextGoalPrefersKmWhenBothMissing() {
        val goal = ProgressionGoals.nextGoal(LevelLadder.progress(60.0, counts(bronze = 3)))
        assertEquals(NextGoal.DriveKm(4, 40.0), goal)
    }

    @Test
    fun nextGoalAtMaxLevel() {
        val goal = ProgressionGoals.nextGoal(LevelLadder.progress(400.0, counts(3, 3, 1)))
        assertEquals(NextGoal.MaxLevel, goal)
    }

    @Test
    fun careerSumsDistanceAndCountsOnlyMainTrophies() {
        val sessions = listOf(session(1, emptyList(), 20_000.0), session(2, emptyList(), 6_000.0))
        val trophies = listOf(trophy(1, TrophyType.FINISHER_BRONZE), trophy(2, TrophyType.CONSISTENCY_GOLD))
        val career = ProgressionGoals.career(sessions, trophies)
        assertEquals(26.0, career.lifetimeKm, 1e-9)
        assertEquals(2, career.progress.current.number)
        assertEquals(1, career.counts[TrophyType.CONSISTENCY_GOLD])
    }

    @Test
    fun consistencyTrophiesDoNotUnlockLevels() {
        val sessions = listOf(session(1, emptyList(), 30_000.0))
        val trophies = listOf(trophy(1, TrophyType.CONSISTENCY_BRONZE))
        assertEquals(1, ProgressionGoals.career(sessions, trophies).progress.current.number)
    }

    @Test
    fun cabinetHasSixTilesWithLastAward() {
        val sessions = listOf(session(1, emptyList(), track = "Baltar"), session(2, emptyList(), track = "Leça"))
        val trophies = listOf(trophy(1, TrophyType.FINISHER_BRONZE), trophy(2, TrophyType.FINISHER_BRONZE))
        val cabinet = ProgressionGoals.cabinet(sessions, trophies)
        assertEquals(6, cabinet.size)
        val finisher = cabinet.single { it.type == TrophyType.FINISHER_BRONZE }
        assertEquals(2, finisher.count)
        assertEquals(2000L, finisher.lastAwardedAt)
        assertEquals("Leça", finisher.lastTrackName)
        assertTrue(cabinet.filter { it.type != TrophyType.FINISHER_BRONZE }.none { it.earned })
    }

    @Test
    fun finisherHintIsBestAttempt() {
        val hints = ProgressionGoals.hints(listOf(
            session(1, List(2) { 60_000L }, 3_400.0),
            session(2, List(5) { 60_000L }, 3_400.0),
            session(3, List(1) { 60_000L }, 4_000.0)
        ))
        val hint = hints[TrophyType.FINISHER_BRONZE] as TrophyHint.Finisher
        assertEquals(3.4, hint.bestDistanceKm!!, 1e-9)
        assertEquals(5, hint.bestLaps)
    }

    @Test
    fun finisherHintEmptyWithoutSessions() {
        val hint = ProgressionGoals.hints(emptyList())[TrophyType.FINISHER_BRONZE] as TrophyHint.Finisher
        assertNull(hint.bestDistanceKm)
    }

    @Test
    fun consistencyHintUsesSteadiestTrustedWindow() {
        val steady = listOf(60_000L, 61_000L, 59_000L, 60_500L, 59_500L)
        val steadier = listOf(60_000L, 60_010L, 59_990L, 60_000L, 60_000L)
        val hints = ProgressionGoals.hints(listOf(session(1, steady), session(2, steadier, trusted = false)))
        val hint = hints[TrophyType.CONSISTENCY_SILVER] as TrophyHint.Consistency
        assertEquals(TrophyRules.steadiestWindowStdDevMs(steady)!!, hint.steadiestMs!!, 1e-9)
        assertEquals(1_500.0, hint.thresholdMs, 0.0)
    }

    @Test
    fun personalBestHintIsBestAtMostRecentTrack() {
        val hints = ProgressionGoals.hints(listOf(
            session(1, listOf(52_341L, 53_000L), track = "Baltar"),
            session(2, listOf(40_000L), track = "Leça"),
            session(3, listOf(53_500L, 54_000L), track = "Baltar")
        ))
        val pb = hints[TrophyType.PB_SILVER] as TrophyHint.PersonalBest
        assertEquals("Baltar", pb.trackName)
        assertEquals(52_341L, pb.bestMs)
        val gold = hints[TrophyType.TRACK_MASTER_GOLD] as TrophyHint.TrackMaster
        assertEquals(pb, gold.personalBest)
    }

    @Test
    fun recentProgressFasterSlowerFirst() {
        val first = session(1, listOf(60_000L))
        assertEquals(RecentProgress.FirstTime("Baltar"), ProgressionGoals.recentProgress(first, emptyList()))

        val faster = session(2, listOf(59_200L))
        assertEquals(RecentProgress.Faster("Baltar", 800L), ProgressionGoals.recentProgress(faster, listOf(first)))

        // Compared with the most recent previous session, not the all-time best.
        val slower = session(3, listOf(59_500L))
        assertEquals(RecentProgress.Slower("Baltar", 300L), ProgressionGoals.recentProgress(slower, listOf(first, faster)))

        assertEquals(RecentProgress.NoLaps, ProgressionGoals.recentProgress(session(4, emptyList()), listOf(first)))
    }

    /**
     * Two Finishers and 20 km is a Rookie; one long session adding 31 km and a third
     * Finisher clears both Novice (25 km, 1 Bronze) and Challenger (50 km, 3 Bronze).
     * The level after is the final one, and the driver sees one LEVEL UP for it, not two.
     */
    @Test
    fun oneSessionCanCrossTwoLevels() {
        val earlier = listOf(session(1, emptyList(), 10_000.0), session(2, emptyList(), 10_000.0))
        val earlierTrophies = listOf(trophy(1, TrophyType.FINISHER_BRONZE), trophy(2, TrophyType.FINISHER_BRONZE))
        val long = session(3, emptyList(), 31_000.0)
        val allTrophies = earlierTrophies + trophy(3, TrophyType.FINISHER_BRONZE)

        val before = ProgressionGoals.career(earlier, earlierTrophies)
        val after = ProgressionGoals.career(earlier + long, allTrophies)
        assertEquals(1, before.progress.current.number)
        assertEquals(3, after.progress.current.number)
        assertEquals(NextGoal.DriveKm(4, 49.0), after.nextGoal)

        val rewards = SessionRewards(
            sessionId = 3, trackName = "Baltar", distanceKm = 31.0, lapCount = 0, bestLapMs = null,
            consistencyStdDevMs = null, newTrophies = listOf(TrophyType.FINISHER_BRONZE),
            before = before, after = after, recentProgress = RecentProgress.NoLaps
        )
        assertTrue(rewards.levelUp)
        val popups = RewardsViewModel.popupsFor(rewards)
        assertEquals(
            listOf(RewardPopup.Trophy(TrophyType.FINISHER_BRONZE), RewardPopup.LevelUp(LevelLadder.levels[2])),
            popups
        )
    }

    @Test
    fun nextGoalAtLevelSixSaysMax() {
        val sessions = listOf(session(1, emptyList(), 400_000.0))
        val trophies = List(3) { trophy(1, TrophyType.FINISHER_BRONZE) } +
            List(3) { trophy(1, TrophyType.PB_SILVER) } + trophy(1, TrophyType.TRACK_MASTER_GOLD)
        val career = ProgressionGoals.career(sessions, trophies)
        assertEquals(6, career.progress.current.number)
        assertEquals(NextGoal.MaxLevel, career.nextGoal)

        // Without the Gold the same driver is Level 5 and is told exactly that one is missing.
        val noGold = ProgressionGoals.career(sessions, trophies.filter { it.type != TrophyType.TRACK_MASTER_GOLD })
        assertEquals(NextGoal.EarnTrophies(6, TrophyType.TRACK_MASTER_GOLD, 1), noGold.nextGoal)
    }
}
