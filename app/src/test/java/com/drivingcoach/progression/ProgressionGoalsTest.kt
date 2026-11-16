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
        SessionFacts.ofLapTimes(id, id * 1000, TrackKey.of(null, track), track, distanceM, laps, trusted)

    private fun trophy(sessionId: Long, type: TrophyType, at: Long = sessionId * 1000) =
        TrophyRecord(sessionId, type, "name:baltar", 0, at)

    private fun counts(bronze: Int = 0, silver: Int = 0, gold: Int = 0) = TrophyCounts(mapOf(
        TrophyType.FINISHER to bronze, TrophyType.METRONOME to silver, TrophyType.SURGICAL to gold))

    // ---------------------------------------------------------------- next goal (PG-18)

    @Test
    fun nextGoalIsKmWhenKmShort() {
        assertEquals(NextGoal.DriveKm(2, 5.0), ProgressionGoals.nextGoal(LevelLadder.progress(3.0, counts())))
    }

    @Test
    fun nextGoalIsMissingTrophyWhenKmMet() {
        assertEquals(NextGoal.EarnTrophies(4, TrophyType.Metal.BRONZE, 2),
            ProgressionGoals.nextGoal(LevelLadder.progress(100.0, counts(bronze = 1))))
    }

    @Test
    fun nextGoalTrophyCountUsesUpwardCompatibility() {
        // L5 needs one Silver; a Gold already covers it, so the missing piece is distance.
        assertEquals(NextGoal.DriveKm(5, 20.0),
            ProgressionGoals.nextGoal(LevelLadder.progress(100.0, counts(bronze = 2, gold = 1))))
    }

    @Test
    fun nextGoalPrefersKmWhenBothMissing() {
        assertEquals(NextGoal.DriveKm(4, 16.0), ProgressionGoals.nextGoal(LevelLadder.progress(40.0, counts(bronze = 1))))
    }

    @Test
    fun nextGoalAtMaxLevel() {
        assertEquals(NextGoal.MaxLevel, ProgressionGoals.nextGoal(LevelLadder.progress(450.0, counts(3, 3, 1))))
    }

    // ---------------------------------------------------------------- career

    @Test
    fun careerSumsDistanceAndCountsOnlyLevelTrophies() {
        val sessions = listOf(session(1, emptyList(), 20_000.0), session(2, emptyList(), 6_000.0))
        val trophies = listOf(trophy(1, TrophyType.FINISHER), trophy(2, TrophyType.FULL_STINT),
            trophy(2, TrophyType.CARBON_COPY))
        val career = ProgressionGoals.career(sessions, trophies)
        assertEquals(26.0, career.lifetimeKm, 1e-9)
        assertEquals(3, career.progress.current.number)
        assertEquals(1, career.counts[TrophyType.FULL_STINT])
    }

    @Test
    fun medalsAloneDoNotUnlockTrophyLevels() {
        val sessions = listOf(session(1, emptyList(), 30_000.0))
        val trophies = List(5) { trophy(1, TrophyType.TOP_GUN) }
        assertEquals(2, ProgressionGoals.career(sessions, trophies).progress.current.number)
    }

    @Test
    fun cabinetHasEveryTrophyAndMedalWithLastAward() {
        val sessions = listOf(session(1, emptyList(), track = "Baltar"), session(2, emptyList(), track = "Leça"))
        val trophies = listOf(trophy(1, TrophyType.FINISHER), trophy(2, TrophyType.FINISHER))
        val cabinet = ProgressionGoals.cabinet(sessions, trophies)
        assertEquals(14, cabinet.size)
        assertEquals(TrophyType.trophies + TrophyType.medals, cabinet.map { it.type })
        val finisher = cabinet.single { it.type == TrophyType.FINISHER }
        assertEquals(2, finisher.count)
        assertEquals(2000L, finisher.lastAwardedAt)
        assertEquals("Leça", finisher.lastTrackName)
        assertTrue(cabinet.filter { it.type != TrophyType.FINISHER }.none { it.earned })
    }

    // ---------------------------------------------------------------- personal hints (PG-16)

    @Test
    fun everyLevelTrophyHasAHint() {
        assertEquals(TrophyType.trophies.toSet(), ProgressionGoals.hints(emptyList()).keys)
    }

    @Test
    fun finisherHintIsBestAttempt() {
        val hints = ProgressionGoals.hints(listOf(
            session(1, List(2) { 60_000L }, 3_400.0),
            session(2, List(5) { 60_000L }, 3_400.0),
            session(3, List(1) { 60_000L }, 4_000.0)
        ))
        val hint = hints[TrophyType.FINISHER] as TrophyHint.Finisher
        assertEquals(3.4, hint.bestDistanceKm!!, 1e-9)
        assertEquals(5, hint.bestValidLaps)
    }

    @Test
    fun finisherHintEmptyWithoutSessions() {
        val hint = ProgressionGoals.hints(emptyList())[TrophyType.FINISHER] as TrophyHint.Finisher
        assertNull(hint.bestDistanceKm)
        assertNull(hint.bestValidLaps)
    }

    @Test
    fun consistencyHintIsBestTrustedCvAgainstEachThreshold() {
        val steady = listOf(98_000L, 102_000L, 98_000L, 102_000L, 100_000L)
        val steadier = listOf(60_000L, 60_010L, 59_990L, 60_000L, 60_000L)
        val hints = ProgressionGoals.hints(listOf(session(1, steady), session(2, steadier, trusted = false)))
        val metronome = hints[TrophyType.METRONOME] as TrophyHint.Consistency
        assertEquals(SessionMetrics.cvPercent(steady), metronome.bestCvPercent!!, 1e-9)
        assertEquals(1.5, metronome.thresholdPercent, 0.0)
        assertEquals(3.0, (hints[TrophyType.STEADY_HAND] as TrophyHint.Consistency).thresholdPercent, 0.0)
        assertEquals(0.75, (hints[TrophyType.SURGICAL] as TrophyHint.Consistency).thresholdPercent, 0.0)
    }

    @Test
    fun personalBestHintIsBestAtMostRecentTrack() {
        val hints = ProgressionGoals.hints(listOf(
            session(1, listOf(52_341L, 53_000L), track = "Baltar"),
            session(2, listOf(40_000L), track = "Leça"),
            session(3, listOf(53_500L, 54_000L), track = "Baltar")
        ))
        val pb = hints[TrophyType.PACE_SETTER] as TrophyHint.PersonalBest
        assertEquals("Baltar", pb.trackName)
        assertEquals(52_341L, pb.bestMs)
    }

    @Test
    fun plateauHintCountsValidLapsSinceThePb() {
        val hints = ProgressionGoals.hints(listOf(session(1, listOf(60_000L) + List(6) { 61_000L })))
        val plateau = hints[TrophyType.PLATEAU_BREAKER] as TrophyHint.Plateau
        assertEquals(6, plateau.lapsSincePb)
        assertEquals(15, plateau.needed)
    }

    @Test
    fun optimalHintCountsLifetimeValidLapsAtTheLatestTrack() {
        val hints = ProgressionGoals.hints(listOf(session(1, List(7) { 60_000L }), session(2, List(4) { 61_000L })))
        val optimal = hints[TrophyType.OPTIMAL_LAP] as TrophyHint.Optimal
        assertEquals(11, optimal.validLaps)
        assertEquals(20, optimal.neededLaps)
        assertNull("no sector data", optimal.gapPercent)
        assertEquals(0.5, optimal.thresholdPercent, 0.0)
    }

    @Test
    fun apexHintWithoutSpeedsIsEmpty() {
        val hint = ProgressionGoals.hints(listOf(session(1, List(6) { 60_000L })))[TrophyType.APEX_EXIT] as TrophyHint.Apex
        assertNull(hint.bestSpreadPercent)
        assertEquals(1.0, hint.thresholdPercent, 0.0)
    }

    // ---------------------------------------------------------------- recent progress (PG-20)

    @Test
    fun recentProgressFasterSlowerFirst() {
        val first = session(1, listOf(60_000L))
        assertEquals(RecentProgress.FirstTime("Baltar"), ProgressionGoals.recentProgress(first, emptyList()))
        val faster = session(2, listOf(59_200L))
        assertEquals(RecentProgress.Faster("Baltar", 800L), ProgressionGoals.recentProgress(faster, listOf(first)))
        val slower = session(3, listOf(59_500L))
        assertEquals(RecentProgress.Slower("Baltar", 300L), ProgressionGoals.recentProgress(slower, listOf(first, faster)))
        assertEquals(RecentProgress.NoLaps, ProgressionGoals.recentProgress(session(4, emptyList()), listOf(first)))
    }

    // ---------------------------------------------------------------- pop-ups (PG-19, PG-31)

    private fun rewards(
        before: CareerSnapshot,
        after: CareerSnapshot,
        trophies: List<TrophyType>,
        medals: List<TrophyType> = emptyList(),
        milestones: List<Int> = emptyList()
    ) = SessionRewards(
        sessionId = 3, trackName = "Baltar", distanceKm = 31.0, lapCount = 0, bestLapMs = null,
        cvPercent = null, newTrophies = trophies, newMedals = medals, milestonesKm = milestones,
        before = before, after = after, recentProgress = RecentProgress.NoLaps
    )

    /**
     * 20 km and no trophy is Level 2. One long session adding 40 km and three Bronzes clears
     * Level 3 (24 km, 1 Bronze) and Level 4 (56 km, 3 Bronze): one LEVEL UP, for Level 4.
     */
    @Test
    fun oneSessionCanCrossTwoLevels() {
        val earlier = listOf(session(1, emptyList(), 10_000.0), session(2, emptyList(), 10_000.0))
        val long = session(3, emptyList(), 40_000.0)
        val won = listOf(TrophyType.FINISHER, TrophyType.PACE_SETTER, TrophyType.STEADY_HAND)

        val before = ProgressionGoals.career(earlier, emptyList())
        val after = ProgressionGoals.career(earlier + long, won.map { trophy(3, it) })
        assertEquals(2, before.progress.current.number)
        assertEquals(4, after.progress.current.number)

        val r = rewards(before, after, won, medals = listOf(TrophyType.FULL_STINT), milestones = listOf(24, 32, 40, 48, 56))
        assertTrue(r.levelUp)
        assertEquals(
            won.map { RewardPopup.Trophy(it) } + RewardPopup.Milestone(56) + RewardPopup.LevelUp(LevelLadder.levels[3]),
            RewardsViewModel.popupsFor(r)
        )
    }

    @Test
    fun medalsAndNoLevelUpGiveNoPopups() {
        val career = ProgressionGoals.career(listOf(session(1, emptyList(), 1_000.0)), emptyList())
        assertEquals(emptyList<RewardPopup>(),
            RewardsViewModel.popupsFor(rewards(career, career, emptyList(), medals = TrophyType.medals)))
    }

    @Test
    fun nextGoalAtLevelSevenSaysMax() {
        val sessions = listOf(session(1, emptyList(), 450_000.0))
        val trophies = List(3) { trophy(1, TrophyType.FINISHER) } +
            List(3) { trophy(1, TrophyType.APEX_EXIT) } + trophy(1, TrophyType.OPTIMAL_LAP)
        val career = ProgressionGoals.career(sessions, trophies)
        assertEquals(7, career.progress.current.number)
        assertEquals(NextGoal.MaxLevel, career.nextGoal)

        val noGold = ProgressionGoals.career(sessions, trophies.filter { it.type != TrophyType.OPTIMAL_LAP })
        assertEquals(NextGoal.EarnTrophies(7, TrophyType.Metal.GOLD, 1), noGold.nextGoal)
    }
}
