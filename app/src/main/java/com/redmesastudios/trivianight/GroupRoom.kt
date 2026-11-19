package com.redmesastudios.trivianight

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// One phone's seat. `profile` is the saved player it plays as, whose records the game adds to.
data class Player(val id: String, val name: String, val color: Int, val score: Int = 0, val right: Int = 0, val lastPoints: Int = 0, val profile: String? = null)

data class Answer(val choice: Int, val atMillis: Long)

enum class Phase { Lobby, Asking, Revealed, Final }

data class RoomState(
    val phase: Phase = Phase.Lobby,
    val players: List<Player> = emptyList(),
    val rounds: List<Round> = emptyList(),
    val index: Int = 0,
    val askedAtMillis: Long = 0,
    val answers: Map<String, Answer> = emptyMap(),
    // Counts games started, so each finished one is saved once.
    val game: Int = 0,
) {
    val round: Round? get() = rounds.getOrNull(index)
    val allAnswered: Boolean get() = players.isNotEmpty() && players.all { it.id in answers }
    val standings: List<Player> get() = players.sortedWith(compareByDescending<Player> { it.score }.thenByDescending { it.right })
}

// A group game's shared state. Phones change it from the web server's threads (join, answer)
// and the TV from the main thread (start, reveal, next), so every change happens under one
// lock and is published as a new immutable RoomState for the TV to draw.
class GroupRoom(val pin: String, private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private val lock = Any()
    private val mutable = MutableStateFlow(RoomState())
    val state: StateFlow<RoomState> = mutable.asStateFlow()

    private inline fun update(change: (RoomState) -> RoomState) = synchronized(lock) { mutable.value = change(mutable.value) }

    // A phone that already joined (it remembers its id) gets its old seat back. A saved player
    // can only sit in one seat; null means another phone already has them.
    fun join(name: String, color: Int, id: String?, profile: String? = null): Player? = synchronized(lock) {
        val current = mutable.value
        if (profile != null && current.players.any { it.profile == profile && it.id != id }) return null
        current.players.firstOrNull { it.id == id }?.let { existing ->
            val renamed = existing.copy(name = name.ifBlank { existing.name }, color = color, profile = profile ?: existing.profile)
            mutable.value = current.copy(players = current.players.map { if (it.id == existing.id) renamed else it })
            return renamed
        }
        val player = Player(UUID.randomUUID().toString(), cleanName(name, current.players.size), color.coerceIn(0, 7), profile = profile)
        mutable.value = current.copy(players = current.players + player)
        player
    }

    fun remove(id: String) = update { it.copy(players = it.players.filterNot { p -> p.id == id }) }

    // Only the first answer to the question being asked counts.
    fun answer(id: String, round: Int, choice: Int): Boolean = synchronized(lock) {
        val current = mutable.value
        val asking = current.round ?: return false
        if (current.phase != Phase.Asking || asking.number != round || id in current.answers) return false
        if (current.players.none { it.id == id } || choice !in asking.choices.indices) return false
        mutable.value = current.copy(answers = current.answers + (id to Answer(choice, clock())))
        true
    }

    fun start(rounds: List<Round>) = update { state ->
        RoomState(
            phase = Phase.Asking,
            players = state.players.map { it.copy(score = 0, right = 0, lastPoints = 0) },
            rounds = rounds,
            index = 0,
            askedAtMillis = clock(),
            game = state.game + 1,
        )
    }

    // Time's up (or everyone answered): score the question.
    fun reveal() = update { state ->
        val round = state.round ?: return@update state
        if (state.phase != Phase.Asking) return@update state
        val allowed = SecondsPerQuestion * 1000L
        state.copy(
            phase = Phase.Revealed,
            players = state.players.map { player ->
                val answer = state.answers[player.id]
                val right = answer?.choice == round.correct
                val earned = points(right, allowed - ((answer?.atMillis ?: 0) - state.askedAtMillis))
                player.copy(score = player.score + earned, right = player.right + if (right) 1 else 0, lastPoints = earned)
            },
        )
    }

    fun next() = update { state ->
        if (state.phase != Phase.Revealed) return@update state
        if (state.index + 1 >= state.rounds.size) {
            state.copy(phase = Phase.Final)
        } else {
            state.copy(phase = Phase.Asking, index = state.index + 1, askedAtMillis = clock(), answers = emptyMap())
        }
    }

    fun backToLobby() = update { RoomState(players = it.players.map { p -> p.copy(score = 0, right = 0, lastPoints = 0) }, game = it.game) }

    // Saved players a phone can join as; the ones already in a seat can't be picked.
    fun seatedProfiles(): Set<String> = synchronized(lock) { mutable.value.players.mapNotNull { it.profile }.toSet() }

    // What one phone shows, as JSON for its page.
    fun phoneView(id: String?): JSONObject = synchronized(lock) {
        val state = mutable.value
        val me = state.players.firstOrNull { it.id == id }
            ?: return JSONObject().put("phase", "join")
        val round = state.round
        val json = JSONObject()
            .put("name", me.name)
            .put("color", me.color)
            .put("score", me.score)
            .put("players", state.players.size)
        when (state.phase) {
            Phase.Lobby -> json.put("phase", "lobby")
            Phase.Asking -> json.put("phase", if (me.id in state.answers) "answered" else "question")
                .put("round", round!!.number)
                .put("total", state.rounds.size)
                .put("choices", JSONArray(round.choices))
                .put("mine", state.answers[me.id]?.choice ?: -1)
            Phase.Revealed -> json.put("phase", "reveal")
                .put("round", round!!.number)
                .put("total", state.rounds.size)
                .put("choices", JSONArray(round.choices))
                .put("correct", round.correct)
                .put("mine", state.answers[me.id]?.choice ?: -1)
                .put("points", me.lastPoints)
                .put("place", state.standings.indexOfFirst { it.id == me.id } + 1)
            Phase.Final -> json.put("phase", "final")
                .put("right", me.right)
                .put("total", state.rounds.size)
                .put("place", state.standings.indexOfFirst { it.id == me.id } + 1)
        }
        json
    }
}

// Names show on the TV, so keep them short and printable; a blank one gets a stand-in.
fun cleanName(raw: String, seat: Int): String =
    raw.filter { !it.isISOControl() }.trim().take(14).ifEmpty { "Player ${seat + 1}" }
