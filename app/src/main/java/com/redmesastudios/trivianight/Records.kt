package com.redmesastudios.trivianight

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

private const val Tag = "TriviaNight.Records"

// Someone who plays on this household's TVs: a first name and a favorite color. A removed
// player stays as a "gone" note, so the removal reaches the other TVs too. `at` is when it
// last changed; the newest copy wins.
data class Profile(val id: String, val name: String, val color: Int, val at: Long, val gone: Boolean = false)

// One player's finished game. Never changed once saved, so the TVs only ever add to the list.
data class GameRecord(
    val id: String,
    val player: String,
    val playedAt: Long,
    val level: Int,
    val count: Int,
    val right: Int,
    val score: Int,
    val together: Boolean,
) {
    // The best-games board ranks by this, so a short game and a long one compare fairly.
    val perQuestion: Int get() = if (count == 0) 0 else score / count
}

// Everything kept about players: who they are, every game, and when each question was last
// asked on any of the household's TVs.
data class Saved(val profiles: List<Profile> = emptyList(), val games: List<GameRecord> = emptyList(), val asked: Map<Int, Long> = emptyMap()) {
    val players: List<Profile> get() = profiles.filterNot { it.gone }.sortedBy { it.name.lowercase() }

    fun player(id: String?): Profile? = profiles.firstOrNull { it.id == id && !it.gone }

    // Question ids, the one asked longest ago first.
    val askedOldestFirst: List<Int> get() = asked.entries.sortedBy { it.value }.map { it.key }
}

// Two copies as one: each player at their newest, every game once, each question at its
// latest asking.
fun mergeSaved(a: Saved, b: Saved): Saved = Saved(
    profiles = (a.profiles + b.profiles).groupBy { it.id }.values.map { same -> same.maxBy { it.at } },
    games = (a.games + b.games).distinctBy { it.id }.sortedBy { it.playedAt },
    asked = (a.asked.keys + b.asked.keys).associateWith { maxOf(a.asked[it] ?: 0L, b.asked[it] ?: 0L) },
)

data class PlayerStats(val profile: Profile, val games: Int, val points: Long, val right: Int, val asked: Int, val best: GameRecord?) {
    val percentRight: Int get() = if (asked == 0) 0 else right * 100 / asked
}

// Every player still here with their totals, most points first.
fun Saved.stats(): List<PlayerStats> {
    val byPlayer = games.groupBy { it.player }
    return players.map { profile ->
        val mine = byPlayer[profile.id].orEmpty()
        PlayerStats(profile, mine.size, mine.sumOf { it.score.toLong() }, mine.sumOf { it.right }, mine.sumOf { it.count }, mine.bestGame())
    }.sortedWith(compareByDescending<PlayerStats> { it.points }.thenBy { it.profile.name.lowercase() })
}

// Each player's best game, the best first: most points a question, then most right out of
// the questions asked, then whoever got there first.
fun Saved.bestGames(): List<Pair<Profile, GameRecord>> =
    stats().mapNotNull { stats -> stats.best?.let { stats.profile to it } }.sortedWith { a, b -> compareBest(a.second, b.second) }

private fun List<GameRecord>.bestGame(): GameRecord? = maxWithOrNull { a, b -> -compareBest(a, b) }

// Negative when a is the better game.
private fun compareBest(a: GameRecord, b: GameRecord): Int = when {
    a.perQuestion != b.perQuestion -> b.perQuestion.compareTo(a.perQuestion)
    a.right * b.count != b.right * a.count -> (b.right * a.count).compareTo(a.right * b.count)
    else -> a.playedAt.compareTo(b.playedAt)
}

// The saved players, games and asked questions, in files/records.json. Changes save in the
// background and go to the household's other TVs and games through the Game Bridge (GameBridge.kt).
object Records {
    private val lock = Any()
    private val mutable = MutableStateFlow(Saved())
    val state: StateFlow<Saved> = mutable.asStateFlow()
    private val io = Executors.newSingleThreadExecutor()
    private var file: File? = null

    fun load(context: Context) {
        val app = context.applicationContext
        val saved = File(app.filesDir, "records.json")
        synchronized(lock) {
            if (file != null) return
            file = saved
            val loaded = runCatching { if (saved.exists()) savedFromJson(JSONObject(saved.readText())) else Saved() }
                .onFailure { Log.w(Tag, "Couldn't read the records", it) }
                .getOrDefault(Saved())
            // Questions asked before players were saved were kept in order, without times.
            val prefs = app.getSharedPreferences("asked", Context.MODE_PRIVATE)
            val earlier = prefs.getString("ids", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
            val now = System.currentTimeMillis()
            mutable.value = loaded.copy(asked = earlier.withIndex().associate { (i, id) -> id to now - earlier.size + i } + loaded.asked)
            if (earlier.isNotEmpty()) {
                prefs.edit().clear().apply()
                writeLater(mutable.value)
            }
        }
    }

    fun addPlayer(name: String, color: Int): Profile = synchronized(lock) {
        val clean = name.filter { !it.isISOControl() }.trim().take(14)
        // The same first name again is the same person.
        mutable.value.players.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val profile = Profile(UUID.randomUUID().toString(), clean, color.coerceIn(0, 7), System.currentTimeMillis())
        change { it.copy(profiles = it.profiles + profile) }
        profile
    }

    fun updatePlayer(id: String, name: String, color: Int) = synchronized(lock) {
        val clean = name.filter { !it.isISOControl() }.trim().take(14)
        change { saved ->
            saved.copy(profiles = saved.profiles.map {
                if (it.id == id) it.copy(name = clean.ifEmpty { it.name }, color = color.coerceIn(0, 7), at = System.currentTimeMillis()) else it
            })
        }
    }

    fun removePlayer(id: String) = synchronized(lock) {
        change { saved -> saved.copy(profiles = saved.profiles.map { if (it.id == id) it.copy(gone = true, at = System.currentTimeMillis()) else it }) }
    }

    fun recordGames(games: List<GameRecord>) {
        if (games.isNotEmpty()) synchronized(lock) { change { it.copy(games = it.games + games) } }
    }

    fun markAsked(questions: List<Question>) = synchronized(lock) {
        val now = System.currentTimeMillis()
        change { it.copy(asked = it.asked + questions.associate { q -> q.id to now }) }
    }

    // Swaps records with SimpleTV, which shares them with the household's other TVs.
    fun sync() {
        io.execute {
            val mine = state.value
            val household = runCatching { GameBridge.exchange(mine) }
                .onFailure { Log.i(Tag, "No household sync: ${it.message}") }
                .getOrNull() ?: return@execute
            synchronized(lock) {
                val merged = mergeSaved(mutable.value, household)
                if (merged != mutable.value) {
                    mutable.value = merged
                    writeLater(merged)
                }
            }
        }
    }

    private fun change(edit: (Saved) -> Saved) {
        mutable.value = edit(mutable.value)
        writeLater(mutable.value)
        sync()
    }

    private fun writeLater(saved: Saved) {
        val target = file ?: return
        io.execute {
            runCatching {
                val temp = File(target.parentFile, "records.json.tmp")
                temp.writeText(savedToJson(saved).toString())
                temp.renameTo(target)
            }.onFailure { Log.w(Tag, "Couldn't save the records", it) }
        }
    }
}

// The records as the Game Bridge shares them: three collections of {"id", "at", fields}. Every
// Red Mesa Studios game on the stick shares "players"; games and asked questions are Trivia's.
const val PlayersCollection = "players"
const val GamesCollection = "trivia-games"
const val AskedCollection = "trivia-asked"

fun savedToJson(saved: Saved): JSONObject = JSONObject()
    .put(PlayersCollection, JSONArray(saved.profiles.map {
        JSONObject().put("id", it.id).put("at", it.at).put("name", it.name).put("color", it.color).put("gone", it.gone)
    }))
    .put(GamesCollection, JSONArray(saved.games.map {
        JSONObject().put("id", it.id).put("at", it.playedAt).put("player", it.player).put("level", it.level)
            .put("count", it.count).put("right", it.right).put("score", it.score).put("together", it.together)
    }))
    .put(AskedCollection, JSONArray(saved.asked.map { (id, at) -> JSONObject().put("id", id.toString()).put("at", at) }))

fun savedFromJson(json: JSONObject): Saved = Saved(
    profiles = json.optJSONArray(PlayersCollection).objects().map {
        Profile(it.getString("id"), it.getString("name"), it.optInt("color"), it.getLong("at"), it.optBoolean("gone"))
    },
    games = json.optJSONArray(GamesCollection).objects().map {
        GameRecord(
            it.getString("id"), it.getString("player"), it.getLong("at"), it.getInt("level"),
            it.getInt("count"), it.getInt("right"), it.getInt("score"), it.optBoolean("together"),
        )
    }.sortedBy { it.playedAt },
    asked = json.optJSONArray(AskedCollection).objects().mapNotNull { o -> o.getString("id").toIntOrNull()?.let { it to o.getLong("at") } }.toMap(),
)

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
