package com.geminidict.app.domain.srs

import com.geminidict.app.data.local.entity.WordbookEntryEntity
import kotlin.math.max
import kotlin.math.roundToInt

enum class ReviewRating(val value: Int, val label: String, val hint: String) {
    AGAIN(1, "忘记", "10分钟后 / 明天重学"),
    HARD(2, "模糊", "较短间隔"),
    GOOD(3, "记得", "标准遗忘曲线"),
    EASY(4, "太简单", "大幅延长间隔")
}

data class ScheduleResult(
    val repetitions: Int,
    val intervalDays: Int,
    val easeFactor: Double,
    val lapses: Int,
    val masteryLevel: Int,
    val nextReviewAt: Long
)

/**
 * SM-2 + Ebbinghaus Forgetting Curve Spaced Repetition Scheduler.
 * Pure Kotlin, deterministic with injectable [nowMillis] for unit testing.
 */
object Sm2Scheduler {

    private const val MIN_EASE_FACTOR = 1.3
    private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
    private const val TEN_MINUTES_MILLIS = 10L * 60L * 1000L

    fun schedule(
        entry: WordbookEntryEntity,
        rating: ReviewRating,
        nowMillis: Long = System.currentTimeMillis()
    ): ScheduleResult {
        // Map 4-grade UI rating to classic SM-2 quality (0..5)
        val quality = when (rating) {
            ReviewRating.AGAIN -> 1
            ReviewRating.HARD -> 3
            ReviewRating.GOOD -> 4
            ReviewRating.EASY -> 5
        }

        val currentEf = entry.easeFactor.coerceAtLeast(MIN_EASE_FACTOR)
        val newEf = max(
            MIN_EASE_FACTOR,
            currentEf + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
        )

        var reps = entry.repetitions
        var lapses = entry.lapses
        val nextIntervalDays: Int
        val nextReviewAt: Long

        if (rating == ReviewRating.AGAIN) {
            reps = 0
            lapses += 1
            nextIntervalDays = 0
            // Re-queue in 10 minutes for active recall reinforcement
            nextReviewAt = nowMillis + TEN_MINUTES_MILLIS
        } else {
            reps += 1
            nextIntervalDays = when (reps) {
                1 -> when (rating) {
                    ReviewRating.HARD -> 1
                    ReviewRating.GOOD -> 1
                    ReviewRating.EASY -> 4
                    else -> 1
                }
                2 -> when (rating) {
                    ReviewRating.HARD -> 3
                    ReviewRating.GOOD -> 6
                    ReviewRating.EASY -> 10
                    else -> 6
                }
                else -> {
                    val base = max(1, entry.intervalDays)
                    val multiplier = when (rating) {
                        ReviewRating.HARD -> 1.2
                        ReviewRating.GOOD -> newEf
                        ReviewRating.EASY -> newEf * 1.3
                        else -> newEf
                    }
                    max(base + 1, (base * multiplier).roundToInt())
                }
            }
            nextReviewAt = nowMillis + nextIntervalDays * ONE_DAY_MILLIS
        }

        val masteryLevel = computeMasteryLevel(reps, nextIntervalDays)
        return ScheduleResult(
            repetitions = reps,
            intervalDays = nextIntervalDays,
            easeFactor = (newEf * 100.0).roundToInt() / 100.0,
            lapses = lapses,
            masteryLevel = masteryLevel,
            nextReviewAt = nextReviewAt
        )
    }

    /**
     * Maps repetition count & interval length to Youdao-compatible 0..5 mastery scale.
     */
    fun computeMasteryLevel(repetitions: Int, intervalDays: Int): Int {
        return when {
            repetitions <= 0 -> 0
            intervalDays < 2 -> 1
            intervalDays < 7 -> 2
            intervalDays < 21 -> 3
            intervalDays < 60 -> 4
            else -> 5
        }
    }

    /**
     * Preview string shown on rating buttons before user taps.
     */
    fun previewIntervalLabel(entry: WordbookEntryEntity, rating: ReviewRating): String {
        val result = schedule(entry, rating, nowMillis = 0L)
        return when {
            result.intervalDays == 0 -> "10分钟"
            result.intervalDays == 1 -> "1天"
            result.intervalDays < 30 -> "${result.intervalDays}天"
            else -> String.format("%.1f个月", result.intervalDays / 30.0)
        }
    }
}
