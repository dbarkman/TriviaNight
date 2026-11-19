package com.redmesastudios.trivianight

import kotlin.math.roundToInt
import kotlin.random.Random

const val SecondsPerQuestion = 20
val QuestionCounts = listOf(5, 10, 15, 20, 30)

// What a game is played with, chosen on the setup screen.
data class Settings(val level: Int = 3, val categories: Set<Int> = emptySet(), val count: Int = 10)

// One question as it's asked: its answers in shuffled order, and which one is right.
data class Round(val number: Int, val question: Question, val choices: List<String>, val correct: Int)

fun makeRounds(questions: List<Question>, random: Random = Random.Default): List<Round> =
    questions.mapIndexed { index, question ->
        // True/false questions keep True first.
        val choices = if (question.wrong.size == 1 && setOf(question.answer, question.wrong[0]) == setOf("True", "False")) {
            listOf("True", "False")
        } else {
            (question.wrong + question.answer).shuffled(random)
        }
        Round(index + 1, question, choices, choices.indexOf(question.answer))
    }

// A right answer is worth 500, plus up to 500 more the faster it came.
fun points(right: Boolean, millisLeft: Long, millisAllowed: Long = SecondsPerQuestion * 1000L): Int {
    if (!right) return 0
    val share = (millisLeft.coerceIn(0, millisAllowed)).toDouble() / millisAllowed
    return 500 + (500 * share).roundToInt()
}

fun Settings.summary(bank: QuestionBank): String {
    val level = Levels.first { it.number == level }
    val topics = when (categories.size) {
        0 -> "Mix of everything"
        1 -> bank.categories[categories.first()].name
        else -> "${categories.size} categories"
    }
    return "Level ${level.number} · ${level.name} · $topics · $count questions"
}
