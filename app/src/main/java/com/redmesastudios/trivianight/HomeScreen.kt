package com.redmesastudios.trivianight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redmesastudios.trivianight.ui.Coral
import com.redmesastudios.trivianight.ui.Gold
import com.redmesastudios.trivianight.ui.Line
import com.redmesastudios.trivianight.ui.LineBright
import com.redmesastudios.trivianight.ui.Night
import com.redmesastudios.trivianight.ui.Panel
import com.redmesastudios.trivianight.ui.Sky
import com.redmesastudios.trivianight.ui.Text
import com.redmesastudios.trivianight.ui.TextSoft
import com.redmesastudios.trivianight.ui.Type
import java.text.NumberFormat

private val CardShape = RoundedCornerShape(20.dp)

// Solo or together. Back leaves the app.
@Composable
fun HomeScreen(bank: QuestionBank?, onScores: () -> Unit, onPick: (Mode) -> Unit) {
    val activity = LocalContext.current as? android.app.Activity
    BackHandler { activity?.finish() }
    val solo = remember { FocusRequester() }
    LaunchedEffect(bank != null) { if (bank != null) runCatching { solo.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Night)) {
        // Two quiet rings in the corner, like stage lights.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Line, radius = 180.dp.toPx(), center = Offset(size.width - 60.dp.toPx(), 0f), style = Stroke(1.dp.toPx()))
            drawCircle(Line, radius = 120.dp.toPx(), center = Offset(size.width - 60.dp.toPx(), 0f), style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 44.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Logo(Modifier.size(42.dp))
                    BasicText("Trivia Night", style = Type.logo)
                }
                BasicText(
                    bank?.let {
                        val count = NumberFormat.getIntegerInstance().format(it.questions.size)
                        "$count questions in ${it.categories.size} categories, from grade school to expert"
                    } ?: "Shuffling the cards…",
                    style = Type.soft,
                )
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ModeCard("Play solo", "Just you and the remote", Modifier.focusRequester(solo), enabled = bank != null, onClick = { onPick(Mode.Solo) }) { ink ->
                    drawPerson(ink, Offset(size.width / 2, 0f))
                }
                ModeCard("Play together", "Everyone plays on their phone", Modifier, enabled = bank != null, onClick = { onPick(Mode.Group) }) { _ ->
                    drawPerson(Sky, Offset(size.width * 0.32f, 0f))
                    drawPerson(Coral, Offset(size.width * 0.68f, 0f))
                }
                ModeCard("High scores", "Best games, all-time points and players", Modifier, enabled = true, onClick = onScores) { ink ->
                    drawTrophy(ink)
                }
            }
            HintRow("OK to choose", "Back to leave")
        }
    }
}

@Composable
private fun RowScope.ModeCard(
    title: String,
    subtitle: String,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: DrawScope.(Color) -> Unit,
) {
    val (focused, focus) = rememberFocus()
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, tween(220), label = "cardScale")
    val fill by animateColorAsState(if (focused) Gold else Panel, tween(220), label = "cardFill")
    val ink by animateColorAsState(if (focused) Night else Text, tween(220), label = "cardInk")
    val soft by animateColorAsState(if (focused) Night else TextSoft, tween(220), label = "cardSoft")
    Column(
        modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(fill, CardShape)
            .border(1.dp, if (focused) Gold else LineBright, CardShape)
            .then(focus)
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .padding(28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Canvas(Modifier.size(80.dp, 60.dp)) { icon(ink) }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            BasicText(title, style = Type.cardTitle.copy(fontSize = 34.sp), color = { ink }, maxLines = 1, softWrap = false)
            BasicText(subtitle, style = Type.body.copy(fontSize = 15.sp), color = { soft })
        }
    }
}

// A head and shoulders, centered on x.
private fun DrawScope.drawPerson(color: Color, top: Offset) {
    val u = size.height / 60f
    val stroke = Stroke(4 * u, cap = StrokeCap.Round)
    drawCircle(color, radius = 10 * u, center = Offset(top.x, 20 * u), style = stroke)
    drawPath(
        Path().apply {
            moveTo(top.x - 19 * u, 54 * u)
            cubicTo(top.x - 17 * u, 42 * u, top.x - 9 * u, 36 * u, top.x, 36 * u)
            cubicTo(top.x + 9 * u, 36 * u, top.x + 17 * u, 42 * u, top.x + 19 * u, 54 * u)
        },
        color,
        style = stroke,
    )
}

// A cup on a stand, centered.
private fun DrawScope.drawTrophy(color: Color) {
    val u = size.height / 60f
    val x = size.width / 2
    val stroke = Stroke(4 * u, cap = StrokeCap.Round)
    drawPath(
        Path().apply {
            moveTo(x - 14 * u, 6 * u)
            lineTo(x + 14 * u, 6 * u)
            cubicTo(x + 14 * u, 26 * u, x + 8 * u, 34 * u, x, 34 * u)
            cubicTo(x - 8 * u, 34 * u, x - 14 * u, 26 * u, x - 14 * u, 6 * u)
            close()
        },
        color,
        style = stroke,
    )
    // The handles.
    drawArc(color, 90f, 180f, false, Offset(x - 22 * u, 10 * u), androidx.compose.ui.geometry.Size(12 * u, 14 * u), style = stroke)
    drawArc(color, -90f, 180f, false, Offset(x + 10 * u, 10 * u), androidx.compose.ui.geometry.Size(12 * u, 14 * u), style = stroke)
    drawLine(color, Offset(x, 34 * u), Offset(x, 46 * u), 4 * u, StrokeCap.Round)
    drawLine(color, Offset(x - 11 * u, 52 * u), Offset(x + 11 * u, 52 * u), 4 * u, StrokeCap.Round)
}

// The gold question-mark tile from the design canvas.
@Composable
fun Logo(modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 84f
        drawRoundRect(Gold, topLeft = Offset(4 * u, 4 * u), size = androidx.compose.ui.geometry.Size(76 * u, 76 * u), cornerRadius = androidx.compose.ui.geometry.CornerRadius(22 * u))
        drawPath(
            Path().apply {
                moveTo(30 * u, 34 * u)
                cubicTo(30 * u, 27 * u, 35 * u, 22 * u, 42 * u, 22 * u)
                cubicTo(49 * u, 22 * u, 54 * u, 27 * u, 54 * u, 33 * u)
                cubicTo(54 * u, 42 * u, 42 * u, 42 * u, 42 * u, 50 * u)
            },
            Night,
            style = Stroke(7 * u, cap = StrokeCap.Round),
        )
        drawCircle(Night, radius = 4.5f * u, center = Offset(42 * u, 61 * u))
    }
}
