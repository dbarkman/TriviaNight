package com.redmesastudios.trivianight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameTest {
    private fun question(text: String, level: Int = 3, category: Int = 0) = Question(category, level, text, "right", listOf("a", "b", "c"))

    @Test
    fun fasterRightAnswersScoreMore() {
        assertEquals(1000, points(true, 20_000))
        assertEquals(750, points(true, 10_000))
        assertEquals(500, points(true, 0))
        assertEquals(0, points(false, 20_000))
        // Clock skew can't push a score out of range.
        assertEquals(1000, points(true, 25_000))
        assertEquals(500, points(true, -300))
    }

    @Test
    fun roundsShuffleAnswersButKnowTheRightOne() {
        val rounds = makeRounds(List(20) { question("q$it") }, Random(7))
        rounds.forEach { assertEquals("right", it.choices[it.correct]) }
        assertEquals((1..20).toList(), rounds.map { it.number })
        assertTrue(rounds.map { it.correct }.toSet().size > 1)
    }

    @Test
    fun trueOrFalseKeepsTrueFirst() {
        val q = Question(0, 3, "The sky is blue.", "False", listOf("True"))
        val round = makeRounds(listOf(q)).single()
        assertEquals(listOf("True", "False"), round.choices)
        assertEquals(1, round.correct)
    }

    @Test
    fun newQuestionsComeBeforeRepeats() {
        val pool = List(6) { question("q$it") }
        val asked = pool.take(4).map { it.id }
        val picked = pickQuestions(pool, 2, asked, Random(1))
        assertEquals(setOf("q4", "q5"), picked.map { it.text }.toSet())
    }

    @Test
    fun whenThePoolRunsShortTheOldestRepeatsFirst() {
        val pool = List(5) { question("q$it") }
        val asked = listOf("q3", "q0", "q1", "q2").map { question(it).id }
        val picked = pickQuestions(pool, 3, asked, Random(1)).map { it.text }.toSet()
        assertEquals(setOf("q4", "q3", "q0"), picked)
    }

    @Test
    fun aSmallPoolGivesWhatItHas() {
        assertEquals(2, pickQuestions(List(2) { question("q$it") }, 10, emptyList()).size)
    }

    @Test
    fun namesAreTidied() {
        assertEquals("Dad", cleanName("  Dad  ", 0))
        assertEquals("Player 3", cleanName("   ", 2))
        assertEquals("Abcdefghijklmn", cleanName("Abcdefghijklmnopq", 0))
        assertFalse(cleanName("Mo\u0007m", 0).contains('\u0007'))
    }
}
