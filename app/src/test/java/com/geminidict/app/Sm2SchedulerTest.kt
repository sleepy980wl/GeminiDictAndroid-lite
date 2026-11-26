package com.geminidict.app

import com.geminidict.app.data.local.entity.WordbookEntryEntity
import com.geminidict.app.domain.srs.ReviewRating
import com.geminidict.app.domain.srs.ReviewSessionQueue
import com.geminidict.app.domain.srs.Sm2Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2SchedulerTest {

    private fun sampleWord(id: Long = 1) = WordbookEntryEntity(
        id = id,
        word = "resilient",
        wordLower = "resilient",
        translation = "a. 有韧性的"
    )

    @Test
    fun goodRatingsFollowEbbinghausProgression() {
        val now = 1_700_000_000_000L
        var entry = sampleWord()

        val r1 = Sm2Scheduler.schedule(entry, ReviewRating.GOOD, now)
        assertEquals(1, r1.repetitions)
        assertEquals(1, r1.intervalDays)
        assertEquals(1, r1.masteryLevel)

        entry = entry.copy(
            repetitions = r1.repetitions,
            intervalDays = r1.intervalDays,
            easeFactor = r1.easeFactor
        )
        val r2 = Sm2Scheduler.schedule(entry, ReviewRating.GOOD, now)
        assertEquals(2, r2.repetitions)
        assertEquals(6, r2.intervalDays)
        assertEquals(2, r2.masteryLevel)

        entry = entry.copy(
            repetitions = r2.repetitions,
            intervalDays = r2.intervalDays,
            easeFactor = r2.easeFactor
        )
        val r3 = Sm2Scheduler.schedule(entry, ReviewRating.GOOD, now)
        assertEquals(3, r3.repetitions)
        assertTrue(r3.intervalDays >= 12)
        assertEquals(3, r3.masteryLevel)
    }

    @Test
    fun againResetsRepetitionsAndIncrementsLapses() {
        val now = 1_700_000_000_000L
        val learned = sampleWord().copy(repetitions = 4, intervalDays = 20, easeFactor = 2.5, lapses = 0)
        val res = Sm2Scheduler.schedule(learned, ReviewRating.AGAIN, now)

        assertEquals(0, res.repetitions)
        assertEquals(0, res.intervalDays)
        assertEquals(1, res.lapses)
        assertEquals(0, res.masteryLevel)
        assertEquals(now + 10L * 60L * 1000L, res.nextReviewAt)
    }

    @Test
    fun easeFactorNeverDropsBelowFloor() {
        var entry = sampleWord().copy(easeFactor = 1.35)
        repeat(10) {
            val res = Sm2Scheduler.schedule(entry, ReviewRating.AGAIN, 0L)
            entry = entry.copy(easeFactor = res.easeFactor)
        }
        assertTrue(entry.easeFactor >= 1.3)
    }

    @Test
    fun lastCardAgainStaysInSessionAndSurvivesRefresh() {
        val lastCard = sampleWord(id = 42)
        val snapshot = ReviewSessionQueue.onRated(
            queue = listOf(lastCard),
            currentIndex = 0,
            relearnPool = emptyMap(),
            updatedCard = lastCard.copy(lapses = 1),
            rating = ReviewRating.AGAIN
        )
        // Last card is immediately available at currentIndex (not dropped)
        assertEquals(1, snapshot.currentIndex)
        assertEquals(2, snapshot.queue.size)
        assertEquals(42L, snapshot.queue[snapshot.currentIndex].id)
        assertTrue(snapshot.relearnPool.containsKey(42L))

        // Even when DB returns empty due list (because nextReviewAt = now+10min), refresh preserves it
        val refreshed = ReviewSessionQueue.mergeWithDue(emptyList(), snapshot.relearnPool)
        assertEquals(1, refreshed.size)
        assertEquals(42L, refreshed[0].id)

        // Once recalled with GOOD, it leaves the relearn pool
        val passed = ReviewSessionQueue.onRated(
            queue = refreshed,
            currentIndex = 0,
            relearnPool = snapshot.relearnPool,
            updatedCard = refreshed[0].copy(repetitions = 1),
            rating = ReviewRating.GOOD
        )
        assertTrue(passed.relearnPool.isEmpty())
    }
}
