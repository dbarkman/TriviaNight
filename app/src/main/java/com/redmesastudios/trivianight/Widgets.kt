package com.redmesastudios.trivianight

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redmesastudios.trivianight.ui.Gold
import com.redmesastudios.trivianight.ui.Line
import com.redmesastudios.trivianight.ui.LineBright
import com.redmesastudios.trivianight.ui.Night
import com.redmesastudios.trivianight.ui.Panel
import com.redmesastudios.trivianight.ui.Text
import com.redmesastudios.trivianight.ui.TextSoft
import com.redmesastudios.trivianight.ui.Type

// Focus, as the remote moves it: a gentle grow plus a ring.
@Composable
fun rememberFocus(): Pair<Boolean, Modifier> {
    var focused by remember { mutableStateOf(false) }
    return focused to Modifier.onFocusChanged { focused = it.isFocused }
}

fun Modifier.pressable(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)

// A rounded button: gold when focused, filled softly when chosen, outlined otherwise.
@Composable
fun Pill(label: String, modifier: Modifier = Modifier, selected: Boolean = false, large: Boolean = false, onClick: () -> Unit) {
    val (focused, focus) = rememberFocus()
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, tween(200), label = "pillScale")
    val fill by animateColorAsState(
        when {
            focused -> Gold
            selected -> Color(0xFF4DB5FF)
            else -> Panel
        },
        tween(200),
        label = "pillFill",
    )
    val ink by animateColorAsState(if (focused || selected) Night else Text, tween(200), label = "pillInk")
    val border by animateColorAsState(if (focused || selected) Color.Transparent else LineBright, tween(200), label = "pillBorder")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(fill, CircleShape)
            .border(1.dp, border, CircleShape)
            .then(focus)
            .pressable(onClick)
            .padding(horizontal = if (large) 36.dp else 14.dp, vertical = if (large) 12.dp else 6.dp),
    ) {
        BasicText(label, style = if (large) Type.button else Type.chip, color = { ink }, maxLines = 1, softWrap = false)
    }
}

// A quiet label in a rounded outline, for facts like the level.
@Composable
fun Tag(label: String, modifier: Modifier = Modifier, color: Color = Text) {
    Box(modifier.background(Panel, CircleShape).border(1.dp, LineBright, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp)) {
        BasicText(label, style = Type.chip.copy(fontSize = 13.sp, color = color))
    }
}

// A player's colored circle with their first letter.
@Composable
fun PlayerBadge(name: String, color: Color, size: Dp) {
    Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        BasicText(name.take(1).uppercase(), style = Type.button.copy(fontSize = (size.value * 0.45f).sp, color = Night))
    }
}

// The shape that goes with answer A to D: triangle, diamond, circle, square.
@Composable
fun AnswerShape(index: Int, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (index) {
            0 -> drawPath(Path().apply { moveTo(w / 2, h * 0.08f); lineTo(w * 0.95f, h * 0.88f); lineTo(w * 0.05f, h * 0.88f); close() }, color)
            1 -> drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w, h / 2); lineTo(w / 2, h); lineTo(0f, h / 2); close() }, color)
            2 -> drawCircle(color, radius = w * 0.45f)
            else -> drawRect(color, topLeft = Offset(w * 0.1f, h * 0.1f), size = Size(w * 0.8f, h * 0.8f))
        }
    }
}

// The countdown: a gold ring that empties, with the seconds left in the middle.
@Composable
fun TimerRing(secondsLeft: Int, fraction: Float, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = size.width * 0.08f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(Gold, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        BasicText(secondsLeft.toString(), style = Type.title.copy(fontSize = 38.sp))
    }
}

@Composable
fun HintRow(vararg hints: String) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)) {
        hints.forEach { BasicText(it, style = Type.soft.copy(fontSize = 13.sp, color = TextSoft)) }
    }
}
