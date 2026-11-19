package com.redmesastudios.trivianight

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redmesastudios.trivianight.ui.Gold
import com.redmesastudios.trivianight.ui.PlayerColors
import com.redmesastudios.trivianight.ui.Type
import kotlinx.coroutines.delay
import java.util.UUID

private const val RevealMillis = 4_000L

// Ten (or however many) questions answered with the remote: Left, Right, Up and Down move
// between answers, OK picks one. Each answer is shown for a few seconds (OK moves on sooner),
// then the next question comes up; at the end, the score.
@Composable
fun SoloGame(bank: QuestionBank, settings: Settings, player: Profile, onNewSettings: () -> Unit, onExit: () -> Unit) {
    BackHandler(onBack = onExit)
    var game by remember { mutableIntStateOf(0) }
    val rounds = remember(game) {
        val picked = pickQuestions(bank.pool(settings.level, settings.categories), settings.count, Records.state.value.askedOldestFirst)
        Records.markAsked(picked)
        makeRounds(picked)
    }
    // The player's best before this game, to say so when they beat it.
    val bestBefore = remember(game) { Records.state.value.games.filter { it.player == player.id }.maxOfOrNull { it.perQuestion } }
    var index by remember(game) { mutableIntStateOf(0) }
    var picked by remember(game) { mutableStateOf<Int?>(null) }
    var revealed by remember(game) { mutableStateOf(false) }
    var score by remember(game) { mutableIntStateOf(0) }
    var right by remember(game) { mutableIntStateOf(0) }
    var lastPoints by remember(game) { mutableIntStateOf(0) }
    var millisLeft by remember(game) { mutableLongStateOf(SecondsPerQuestion * 1000L) }
    val finished = index >= rounds.size
    LaunchedEffect(game, finished) {
        if (finished && rounds.isNotEmpty()) {
            Records.recordGames(listOf(GameRecord(UUID.randomUUID().toString(), player.id, System.currentTimeMillis(), settings.level, rounds.size, right, score, together = false)))
        }
    }

    fun reveal(choice: Int?) {
        if (revealed) return
        val round = rounds[index]
        val earned = points(choice == round.correct, millisLeft)
        picked = choice
        revealed = true
        lastPoints = earned
        score += earned
        if (choice == round.correct) right += 1
    }

    fun next() {
        index += 1
        picked = null
        revealed = false
    }

    // The countdown; running out reveals the answer with nothing picked.
    LaunchedEffect(game, index, revealed) {
        if (revealed || finished) return@LaunchedEffect
        val started = SystemClock.elapsedRealtime()
        val allowed = SecondsPerQuestion * 1000L
        while (true) {
            millisLeft = (allowed - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(0)
            if (millisLeft == 0L) break
            delay(100)
        }
        reveal(null)
    }
    LaunchedEffect(game, index, revealed) {
        if (!revealed) return@LaunchedEffect
        delay(RevealMillis)
        next()
    }

    AnimatedContent(finished, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "soloFinished") { done ->
        if (done) {
            val again = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { again.requestFocus() } }
            ScoresView(
                heading = soloVerdict(right, rounds.size),
                kicker = if (bestBefore != null && rounds.isNotEmpty() && score / rounds.size > bestBefore) "NEW PERSONAL BEST" else "${player.name.uppercase()}'S SCORE",
                detail = settings.summary(bank),
                standings = listOf(Standing(player.name, PlayerColors[player.color], score, right)),
                total = rounds.size,
            ) {
                Pill("Play again", Modifier.focusRequester(again), large = true) { game += 1 }
                Pill("New settings", large = true, onClick = onNewSettings)
            }
        } else {
            AnimatedContent(index, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "soloQuestion") { shownIndex ->
                val round = rounds.getOrNull(shownIndex) ?: return@AnimatedContent
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                Column(
                    Modifier
                        .fillMaxSize()
                        // OK during the reveal moves straight on.
                        .onPreviewKeyEvent {
                            if (revealed && it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                                next()
                                true
                            } else {
                                false
                            }
                        }
                        .padding(start = 60.dp, end = 60.dp, top = 32.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    QuestionHeader(bank, round, rounds.size, if (revealed && lastPoints > 0) "+${lastPoints.grouped()}" else score.grouped())
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        QuestionText(round, Modifier.weight(1f))
                        if (!revealed) {
                            TimerRing(((millisLeft + 999) / 1000).toInt(), millisLeft / (SecondsPerQuestion * 1000f), Modifier.size(100.dp))
                        } else {
                            BasicText(
                                when (picked) {
                                    round.correct -> "Right!"
                                    null -> "Time's up"
                                    else -> "Not quite"
                                },
                                style = Type.title.copy(fontSize = 34.sp, color = if (picked == round.correct) Gold else Type.title.color),
                            )
                        }
                    }
                    AnswerGrid(round) { i, modifier ->
                        AnswerTile(
                            index = i,
                            text = round.choices[i],
                            look = if (!revealed || i == round.correct) TileLook.Asking else TileLook.Faded,
                            modifier = modifier,
                            note = if (revealed && i == picked) "Your answer" else "",
                            focusRequester = if (i == 0) first else null,
                            onPick = { if (!revealed) reveal(i) },
                        )
                    }
                }
            }
        }
    }
}

private fun soloVerdict(right: Int, total: Int): String {
    val share = if (total == 0) 0.0 else right.toDouble() / total
    return when {
        share >= 1.0 -> "A perfect game!"
        share >= 0.8 -> "$right of $total. Brilliant!"
        share >= 0.5 -> "$right of $total. Nicely done."
        else -> "$right of $total. Try another level?"
    }
}
