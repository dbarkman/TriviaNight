package com.redmesastudios.trivianight

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redmesastudios.trivianight.ui.AnswerColors
import com.redmesastudios.trivianight.ui.Gold
import com.redmesastudios.trivianight.ui.Line
import com.redmesastudios.trivianight.ui.Night
import com.redmesastudios.trivianight.ui.Panel
import com.redmesastudios.trivianight.ui.PanelDeep
import com.redmesastudios.trivianight.ui.PlayerColors
import com.redmesastudios.trivianight.ui.Text
import com.redmesastudios.trivianight.ui.TextFaint
import com.redmesastudios.trivianight.ui.TextSoft
import com.redmesastudios.trivianight.ui.Type
import java.text.NumberFormat

private val TileShape = RoundedCornerShape(16.dp)
private val number = NumberFormat.getIntegerInstance()

fun Int.grouped(): String = number.format(this)

// The page around a question: what it's from on the left, where the game stands on the right.
@Composable
fun QuestionHeader(bank: QuestionBank, round: Round, total: Int, right: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Tag(bank.categories[round.question.category].name)
        Levels.firstOrNull { it.number == round.question.level }?.let { Tag("Level ${it.number} · ${it.name}", color = TextSoft) }
        Spacer(Modifier.weight(1f))
        BasicText("Question ${round.number} of $total", style = Type.soft.copy(fontSize = 15.sp), modifier = Modifier.padding(end = 18.dp))
        AnimatedContent(right, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) }, label = "headerRight") {
            BasicText(it, style = Type.number)
        }
    }
}

// How a tile looks: while asking, every tile in its own color; once revealed, the right one
// stays bright and the rest step back.
enum class TileLook { Asking, Right, Faded }

@Composable
fun AnswerTile(
    index: Int,
    text: String,
    look: TileLook,
    modifier: Modifier,
    note: String = "",
    focusRequester: FocusRequester? = null,
    onPick: (() -> Unit)? = null,
) {
    val (focused, focus) = rememberFocus()
    val color = AnswerColors[index]
    val fill by animateColorAsState(if (look == TileLook.Faded) PanelDeep else color, tween(350), label = "tileFill")
    val ink by animateColorAsState(if (look == TileLook.Faded) TextFaint else Night, tween(350), label = "tileInk")
    val scale by animateFloatAsState(if (focused && onPick != null) 1.03f else 1f, tween(180), label = "tileScale")
    val ring by animateColorAsState(if (focused && onPick != null) Text else Color.Transparent, tween(180), label = "tileRing")
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val outset = 4.dp.toPx() + 1.5.dp.toPx()
                drawRoundRect(ring, Offset(-outset, -outset), Size(size.width + 2 * outset, size.height + 2 * outset), CornerRadius(16.dp.toPx() + outset), style = Stroke(3.dp.toPx()))
            }
            .background(fill, TileShape)
            .border(1.5.dp, if (look == TileLook.Faded) Line else Color.Transparent, TileShape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (onPick != null) focus.pressable(onPick) else Modifier)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnswerShape(index, ink, Modifier.size(28.dp))
        BasicText(text, style = Type.answer, color = { ink }, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (note.isNotEmpty()) BasicText(note, style = Type.chip.copy(fontSize = 14.sp), color = { ink })
    }
}

// The answers, two by two (or side by side for true or false).
@Composable
fun AnswerGrid(round: Round, tile: @Composable (index: Int, modifier: Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        round.choices.indices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { index -> tile(index, Modifier.weight(1f).height(72.dp)) }
            }
        }
    }
}

@Composable
fun QuestionText(round: Round, modifier: Modifier, small: Boolean = false) {
    BasicText(
        round.question.text,
        style = if (small) Type.question.copy(fontSize = 26.sp, lineHeight = 31.sp, color = TextSoft) else Type.question,
        modifier = modifier,
    )
}

// One line in a list of players: badge, name, and a number on the right.
@Composable
fun PlayerLine(player: Player, value: String, valueColor: Color = Gold) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerBadge(player.name, PlayerColors[player.color], 30.dp)
        BasicText(player.name, style = Type.body.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), modifier = Modifier.weight(1f))
        BasicText(value, style = Type.number.copy(fontSize = 18.sp, color = valueColor))
    }
}

data class Standing(val name: String, val color: Color, val score: Int, val right: Int)

// Final scores: a bar per player, longest for the winner.
@Composable
fun ScoresView(heading: String, kicker: String, detail: String, standings: List<Standing>, total: Int, buttons: @Composable () -> Unit) {
    val top = standings.maxOfOrNull { it.score }?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 36.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                BasicText(kicker, style = Type.kicker)
                BasicText(heading, style = Type.title.copy(fontSize = 44.sp, lineHeight = 48.sp))
            }
            BasicText(detail, style = Type.soft.copy(fontSize = 14.sp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            standings.take(8).forEachIndexed { place, standing ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("${place + 1}", style = Type.title.copy(fontSize = 26.sp, color = TextSoft), modifier = Modifier.width(30.dp))
                    PlayerBadge(standing.name, standing.color, 40.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row {
                            BasicText(standing.name, style = Type.body.copy(fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), modifier = Modifier.weight(1f))
                            BasicText("${standing.right} of $total right", style = Type.soft.copy(fontSize = 13.sp))
                        }
                        val share by animateFloatAsState(standing.score.toFloat() / top, tween(900), label = "bar")
                        Box(Modifier.fillMaxWidth().height(12.dp).background(Panel, CircleShape)) {
                            Box(Modifier.fillMaxWidth(share.coerceAtLeast(0.02f)).fillMaxHeight().background(standing.color, CircleShape))
                        }
                    }
                    BasicText(standing.score.grouped(), style = Type.number.copy(fontSize = 28.sp), modifier = Modifier.width(100.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { buttons() }
    }
}
