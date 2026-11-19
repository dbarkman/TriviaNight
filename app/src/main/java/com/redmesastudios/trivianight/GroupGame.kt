package com.redmesastudios.trivianight

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.redmesastudios.trivianight.ui.Line
import com.redmesastudios.trivianight.ui.LineBright
import com.redmesastudios.trivianight.ui.Night
import com.redmesastudios.trivianight.ui.PlayerColors
import com.redmesastudios.trivianight.ui.Text
import com.redmesastudios.trivianight.ui.TextFaint
import com.redmesastudios.trivianight.ui.TextSoft
import com.redmesastudios.trivianight.ui.Type
import kotlinx.coroutines.delay
import java.util.UUID

private const val RevealMillis = 7_000L

// A group game: phones join from the lobby's QR code, everyone answers on their phone while
// the TV shows the question, the TV reveals the answer and who scored, and the final scores
// end it. The web server runs for as long as this screen is open.
@Composable
fun GroupGame(bank: QuestionBank, settings: Settings, onNewSettings: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val room = remember { GroupRoom(newPin()) }
    var address by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf(false) }
    DisposableEffect(room) {
        val server = GroupServer(context.applicationContext, room)
        address = server.start()
        started = true
        onDispose { server.stop() }
    }
    val state by room.state.collectAsState()
    BackHandler {
        if (state.phase == Phase.Lobby) onExit() else room.backToLobby()
    }

    fun begin() {
        val picked = pickQuestions(bank.pool(settings.level, settings.categories), settings.count, Records.state.value.askedOldestFirst)
        if (picked.isEmpty()) return
        Records.markAsked(picked)
        room.start(makeRounds(picked))
    }

    // Every finished game goes on each player's record.
    LaunchedEffect(state.phase, state.game) {
        if (state.phase != Phase.Final) return@LaunchedEffect
        val now = System.currentTimeMillis()
        Records.recordGames(state.players.mapNotNull { player ->
            player.profile?.let { GameRecord(UUID.randomUUID().toString(), it, now, settings.level, state.rounds.size, player.right, player.score, together = true) }
        })
    }

    // Asking ends when everyone has answered or the time is up.
    var millisLeft by remember { mutableLongStateOf(SecondsPerQuestion * 1000L) }
    LaunchedEffect(state.phase, state.index) {
        if (state.phase != Phase.Asking) return@LaunchedEffect
        val allowed = SecondsPerQuestion * 1000L
        while (true) {
            millisLeft = (allowed - (SystemClock.elapsedRealtime() - state.askedAtMillis)).coerceAtLeast(0)
            if (millisLeft == 0L || room.state.value.allAnswered) break
            delay(100)
        }
        // A beat after the last answer, so the last phone sees "Locked in".
        delay(600)
        room.reveal()
    }
    LaunchedEffect(state.phase, state.index) {
        if (state.phase != Phase.Revealed) return@LaunchedEffect
        delay(RevealMillis)
        room.next()
    }

    AnimatedContent(
        targetState = state.phase to state.index,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "groupPhase",
    ) { (phase, _) ->
        when (phase) {
            Phase.Lobby -> Lobby(bank, settings, state, address, started, onStart = ::begin, onNewSettings = onNewSettings)
            Phase.Asking -> GroupQuestion(bank, state, millisLeft)
            Phase.Revealed -> GroupReveal(bank, state, onNext = room::next)
            Phase.Final -> {
                val again = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { again.requestFocus() } }
                val standings = state.standings
                ScoresView(
                    heading = standings.firstOrNull()?.let { winner ->
                        val tied = standings.count { it.score == winner.score }
                        if (tied > 1) "It's a tie!" else "${winner.name} wins!"
                    } ?: "Game over",
                    kicker = "FINAL SCORES",
                    detail = settings.summary(bank),
                    standings = standings.map { Standing(it.name, PlayerColors[it.color], it.score, it.right) },
                    total = state.rounds.size,
                ) {
                    Pill("Play again", Modifier.focusRequester(again), large = true) { begin() }
                    Pill("Back to the lobby", large = true) { room.backToLobby() }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Lobby(
    bank: QuestionBank,
    settings: Settings,
    state: RoomState,
    address: String?,
    started: Boolean,
    onStart: () -> Unit,
    onNewSettings: () -> Unit,
) {
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { start.requestFocus() } }
    Row(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 36.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BasicText("PLAY TOGETHER", style = Type.kicker)
            BasicText("Scan to join", style = Type.title)
            Box(Modifier.size(220.dp).background(Text, RoundedCornerShape(18.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
                when {
                    address != null -> QrCode(address, Modifier.fillMaxSize())
                    started -> BasicText("The TV isn't on Wi-Fi right now.", style = Type.body.copy(color = Night))
                }
            }
            address?.let {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText("Or open this on a phone on the home Wi-Fi", style = Type.soft.copy(fontSize = 13.sp))
                    BasicText(it.substringBefore("/?"), style = Type.button.copy(fontSize = 20.sp, color = Text))
                    BasicText("and enter code ${it.substringAfter("pin=")}", style = Type.soft.copy(fontSize = 13.sp))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText("Players", style = Type.title.copy(fontSize = 26.sp), modifier = Modifier.weight(1f))
                BasicText(
                    when (state.players.size) {
                        0 -> "Nobody yet"
                        1 -> "1 joined"
                        else -> "${state.players.size} joined"
                    },
                    style = Type.soft,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp), maxItemsInEachRow = 2) {
                state.players.forEach { player ->
                    AnimatedVisibility(visible = true, enter = scaleIn(tween(300)) + fadeIn(tween(300)), modifier = Modifier.weight(1f)) {
                        Row(
                            Modifier.background(com.redmesastudios.trivianight.ui.Panel, RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlayerBadge(player.name, PlayerColors[player.color], 36.dp)
                            BasicText(player.name, style = Type.body.copy(fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold))
                        }
                    }
                }
                Box(
                    Modifier.weight(1f).border(1.dp, LineBright, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 18.dp),
                ) { BasicText("Waiting for more…", style = Type.soft) }
            }
            Spacer(Modifier.weight(1f))
            BasicText(settings.summary(bank), style = Type.soft.copy(fontSize = 13.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(if (state.players.isEmpty()) "Start when everyone's in" else "Start the game", Modifier.focusRequester(start), large = true) {
                    if (state.players.isNotEmpty()) onStart()
                }
                Pill("Settings", large = true, onClick = onNewSettings)
            }
        }
    }
}

@Composable
private fun GroupQuestion(bank: QuestionBank, state: RoomState, millisLeft: Long) {
    val round = state.round ?: return
    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 32.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        QuestionHeader(bank, round, state.rounds.size, "${state.answers.size} of ${state.players.size} answered")
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            QuestionText(round, Modifier.weight(1f))
            TimerRing(((millisLeft + 999) / 1000).toInt(), millisLeft / (SecondsPerQuestion * 1000f), Modifier.size(100.dp))
        }
        // Who has answered so far, as their colored badges.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            state.players.forEach { player ->
                val answered = player.id in state.answers
                Box(Modifier.size(28.dp)) {
                    if (answered) PlayerBadge(player.name, PlayerColors[player.color], 28.dp)
                    else Canvas(Modifier.size(28.dp)) {
                        drawCircle(LineBright, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    }
                }
            }
            BasicText("Answer on your phone", style = Type.soft.copy(fontSize = 13.sp), modifier = Modifier.padding(start = 6.dp))
        }
        AnswerGrid(round) { i, modifier -> AnswerTile(i, round.choices[i], TileLook.Asking, modifier) }
    }
}

@Composable
private fun GroupReveal(bank: QuestionBank, state: RoomState, onNext: () -> Unit) {
    val round = state.round ?: return
    val picks = state.answers.values.groupingBy { it.choice }.eachCount()
    Column(
        Modifier
            .fillMaxSize()
            // OK moves on without waiting.
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onNext()
                    true
                } else {
                    false
                }
            }
            .padding(start = 60.dp, end = 60.dp, top = 32.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            BasicText("Question ${round.number} of ${state.rounds.size} · ${bank.categories[round.question.category].name}", style = Type.soft, modifier = Modifier.weight(1f))
            BasicText(if (round.number == state.rounds.size) "Final scores next" else "Next question soon · OK to skip", style = Type.soft)
        }
        QuestionText(round, Modifier.fillMaxWidth(), small = true)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                round.choices.forEachIndexed { i, choice ->
                    val count = picks[i] ?: 0
                    AnswerTile(
                        index = i,
                        text = choice,
                        look = if (i == round.correct) TileLook.Right else TileLook.Faded,
                        modifier = Modifier.fillMaxWidth().height(if (round.choices.size == 2) 80.dp else 58.dp),
                        note = if (count == 1) "1 player" else "$count players",
                    )
                }
            }
            Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("THIS ROUND", style = Type.label)
                state.players.sortedByDescending { it.lastPoints }.take(6).forEach { player ->
                    PlayerLine(player, if (player.lastPoints > 0) "+${player.lastPoints.grouped()}" else "0", if (player.lastPoints > 0) com.redmesastudios.trivianight.ui.Gold else TextFaint)
                }
            }
        }
    }
}

@Composable
private fun QrCode(text: String, modifier: Modifier) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }
    Canvas(modifier) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix.get(x, y)) drawRect(Night, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
