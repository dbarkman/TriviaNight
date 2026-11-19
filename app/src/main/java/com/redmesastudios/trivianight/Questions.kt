package com.redmesastudios.trivianight

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

// The question bank: built by scripts/build_questions.py into assets/questions.json from Open
// Trivia DB and the questions written for this app (data/written).

data class Category(val name: String, val group: String)

data class Question(val category: Int, val level: Int, val text: String, val answer: String, val wrong: List<String>) {
    // Stable across rebuilds of the bank, so "already asked" survives an update.
    val id: Int get() = text.hashCode()
}

data class Level(val number: Int, val name: String, val note: String)

val Levels = listOf(
    Level(1, "Grade school", "Ages 7 to 10"),
    Level(2, "Middle school", "Ages 11 to 14"),
    Level(3, "High school", "Everyday adult knowledge"),
    Level(4, "College", "For keen trivia players"),
    Level(5, "Expert", "Good luck"),
)

// The groups the category buttons are shown under, in order.
val Groups = listOf("General", "Science & Nature", "Places & History", "Movies & TV", "Arts, Words & Music", "Sports & Games")

class QuestionBank(val categories: List<Category>, val questions: List<Question>) {
    // How many questions each category has at a level.
    fun counts(level: Int): Map<Int, Int> = questions.filter { it.level == level }.groupingBy { it.category }.eachCount()

    // Every question at a level in the chosen categories; no categories means all of them.
    fun pool(level: Int, chosen: Set<Int>): List<Question> =
        questions.filter { it.level == level && (chosen.isEmpty() || it.category in chosen) }

    companion object {
        fun load(context: Context): QuestionBank {
            val json = JSONObject(context.assets.open("questions.json").bufferedReader().use { it.readText() })
            val categories = json.getJSONArray("categories").let { array ->
                List(array.length()) { array.getJSONObject(it).let { c -> Category(c.getString("name"), c.getString("group")) } }
            }
            val questions = json.getJSONArray("questions").let { array ->
                List(array.length()) {
                    val q = array.getJSONArray(it)
                    Question(q.getInt(0), q.getInt(1), q.getString(2), q.getString(3), List(q.length() - 4) { w -> q.getString(4 + w) })
                }
            }
            return QuestionBank(categories, questions)
        }
    }
}

// Picks a game's questions, preferring ones not asked before. When the pool runs short, the
// ones asked longest ago come back first, so nothing repeats until everything has been seen.
fun pickQuestions(pool: List<Question>, count: Int, asked: List<Int>, random: Random = Random.Default): List<Question> {
    val askedAt = asked.withIndex().associate { (index, id) -> id to index }
    val fresh = pool.filter { it.id !in askedAt }.shuffled(random)
    if (fresh.size >= count) return fresh.take(count)
    val oldest = pool.filter { it.id in askedAt }.sortedBy { askedAt.getValue(it.id) }
    return (fresh + oldest.take(count - fresh.size)).shuffled(random)
}
