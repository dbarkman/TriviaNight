package com.redmesastudios.trivianight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRoomTest {
    private var now = 0L
    private val room = GroupRoom("1234") { now }
    private val rounds = listOf(
        Round(1, Question(0, 3, "One?", "yes", listOf("no", "maybe", "never")), listOf("no", "yes", "maybe", "never"), 1),
        Round(2, Question(0, 3, "Two?", "yes", listOf("no", "maybe", "never")), listOf("yes", "no", "maybe", "never"), 0),
    )

    @Test
    fun aGameScoresEachRoundAndEnds() {
        val dad = room.join("Dad", 0, null)!!
        val mom = room.join("Mom", 1, null)!!
        room.start(rounds)

        now = 5_000 // 15 s left
        assertTrue(room.answer(dad.id, 1, 1))
        now = 10_000
        assertTrue(room.answer(mom.id, 1, 0))
        assertTrue(room.state.value.allAnswered)
        room.reveal()
        val afterOne = room.state.value
        assertEquals(Phase.Revealed, afterOne.phase)
        assertEquals(875, afterOne.players.first { it.id == dad.id }.score)
        assertEquals(0, afterOne.players.first { it.id == mom.id }.score)

        room.next()
        now = 12_000
        assertEquals(Phase.Asking, room.state.value.phase)
        assertTrue(room.answer(mom.id, 2, 0))
        room.reveal()
        room.next()
        val done = room.state.value
        assertEquals(Phase.Final, done.phase)
        assertEquals(listOf("Mom", "Dad"), done.standings.map { it.name })
        // Round two was asked at 10 s and answered at 12 s: 18 s left.
        assertEquals(950, done.players.first { it.id == mom.id }.score)
    }

    @Test
    fun onlyTheFirstAnswerToTheCurrentQuestionCounts() {
        val dad = room.join("Dad", 0, null)!!
        room.start(rounds)
        assertFalse(room.answer(dad.id, 2, 0))
        assertTrue(room.answer(dad.id, 1, 2))
        assertFalse(room.answer(dad.id, 1, 1))
        assertFalse(room.answer("someone else", 1, 1))
        room.reveal()
        assertFalse(room.answer(dad.id, 1, 1))
    }

    @Test
    fun aPhoneThatComesBackKeepsItsSeat() {
        val first = room.join("Sam", 2, null)!!
        val again = room.join("Sammy", 3, first.id)!!
        assertEquals(first.id, again.id)
        assertEquals(1, room.state.value.players.size)
        assertEquals("Sammy", room.state.value.players.single().name)
    }

    @Test
    fun theLobbyKeepsPlayersButClearsScores() {
        val dad = room.join("Dad", 0, null)!!
        room.start(rounds)
        room.answer(dad.id, 1, 1)
        room.reveal()
        room.backToLobby()
        val lobby = room.state.value
        assertEquals(Phase.Lobby, lobby.phase)
        assertEquals(0, lobby.players.single().score)
    }

    @Test
    fun aSavedPlayerSitsInOneSeatOnly() {
        val first = room.join("Dad", 0, null, profile = "dad")!!
        assertNull(room.join("Dad", 0, null, profile = "dad"))
        // The same phone coming back keeps its seat.
        assertEquals(first.id, room.join("Dad", 0, first.id, profile = "dad")?.id)
        assertEquals(setOf("dad"), room.seatedProfiles())
    }

    @Test
    fun eachStartCountsAsANewGame() {
        room.join("Dad", 0, null)
        room.start(rounds)
        room.backToLobby()
        room.start(rounds)
        assertEquals(2, room.state.value.game)
    }
}
