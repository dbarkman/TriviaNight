package com.redmesastudios.trivianight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsTest {
    private val dad = Profile("dad", "David", 3, at = 100)
    private val mom = Profile("mom", "Tess", 1, at = 100)

    private fun game(id: String, player: String, count: Int, right: Int, score: Int, at: Long = 1) =
        GameRecord(id, player, at, level = 3, count = count, right = right, score = score, together = false)

    @Test
    fun eightOfTenBeatsTwelveOfThirty() {
        val saved = Saved(
            profiles = listOf(dad, mom),
            games = listOf(game("a", "dad", 10, 8, 6_120), game("b", "mom", 30, 12, 9_300)),
        )
        assertEquals(listOf("David", "Tess"), saved.bestGames().map { it.first.name })
        // All-time points still reward the longer game.
        assertEquals(listOf("Tess", "David"), saved.stats().map { it.profile.name })
    }

    @Test
    fun aPlayersBestIsTheirHighestPointsAQuestion() {
        val saved = Saved(profiles = listOf(dad), games = listOf(game("a", "dad", 10, 9, 7_000), game("b", "dad", 5, 5, 4_500)))
        val stats = saved.stats().single()
        assertEquals("b", stats.best?.id)
        assertEquals(2, stats.games)
        assertEquals(11_500L, stats.points)
        assertEquals(93, stats.percentRight)
    }

    @Test
    fun mergingKeepsTheNewestPlayerEveryGameAndTheLatestAsking() {
        val here = Saved(profiles = listOf(dad), games = listOf(game("a", "dad", 10, 8, 6_000)), asked = mapOf(1 to 50L, 2 to 10L))
        val there = Saved(
            profiles = listOf(dad.copy(name = "Dave", at = 200), mom),
            games = listOf(game("a", "dad", 10, 8, 6_000), game("b", "mom", 10, 5, 3_000)),
            asked = mapOf(1 to 20L, 3 to 30L),
        )
        val merged = mergeSaved(here, there)
        assertEquals(setOf("Dave", "Tess"), merged.players.map { it.name }.toSet())
        assertEquals(listOf("a", "b"), merged.games.map { it.id })
        assertEquals(mapOf(1 to 50L, 2 to 10L, 3 to 30L), merged.asked)
        assertEquals(listOf(2, 3, 1), merged.askedOldestFirst)
        assertEquals(merged, mergeSaved(there, here))
    }

    @Test
    fun aRemovedPlayerLeavesTheBoards() {
        val saved = Saved(profiles = listOf(dad.copy(gone = true, at = 300), mom), games = listOf(game("a", "dad", 10, 8, 6_000)))
        assertEquals(listOf("Tess"), saved.players.map { it.name })
        assertTrue(saved.bestGames().isEmpty())
    }
}
