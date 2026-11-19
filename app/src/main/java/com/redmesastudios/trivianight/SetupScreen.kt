package com.redmesastudios.trivianight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redmesastudios.trivianight.ui.Gold
import com.redmesastudios.trivianight.ui.Line
import com.redmesastudios.trivianight.ui.Night
import com.redmesastudios.trivianight.ui.Panel
import com.redmesastudios.trivianight.ui.Text
import com.redmesastudios.trivianight.ui.TextSoft
import com.redmesastudios.trivianight.ui.Type
import java.text.NumberFormat

private val RowShape = RoundedCornerShape(11.dp)

// Level on the left, categories on the right (only those with questions at that level),
// how many questions and Start along the bottom.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(bank: QuestionBank, mode: Mode, initial: Settings, onBack: () -> Unit, onStart: (Settings) -> Unit) {
    BackHandler(onBack = onBack)
    var level by remember { mutableStateOf(initial.level) }
    var chosen by remember { mutableStateOf(initial.categories) }
    var count by remember { mutableStateOf(initial.count) }
    val counts = remember(level) { bank.counts(level) }
    // Categories with nothing at this level drop out of the choice.
    val available = chosen.filter { (counts[it] ?: 0) > 0 }.toSet()
    val poolSize = if (available.isEmpty()) counts.values.sum() else available.sumOf { counts[it] ?: 0 }
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { start.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 36.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText(if (mode == Mode.Solo) "PLAY SOLO" else "PLAY TOGETHER", style = Type.kicker)
            BasicText("Choose a level and categories", style = Type.title)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                BasicText("LEVEL", style = Type.label)
                Levels.forEach { option ->
                    LevelRow(option, selected = option.number == level) { level = option.number }
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(11.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp, start = 6.dp, end = 6.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText("CATEGORIES · pick any, or a mix of everything", style = Type.label)
                        Pill("Mix of everything", selected = available.isEmpty()) { chosen = emptySet() }
                    }
                }
                Groups.forEach { group ->
                    val inGroup = bank.categories.indices.filter { bank.categories[it].group == group && (counts[it] ?: 0) > 0 }
                        .sortedBy { bank.categories[it].name }
                    if (inGroup.isNotEmpty()) item(key = group) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicText(group, style = Type.label.copy(fontSize = 11.sp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                inGroup.forEach { index ->
                                    Pill(bank.categories[index].name, selected = index in available) {
                                        chosen = if (index in available) available - index else available + index
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("Questions", style = Type.soft.copy(fontSize = 14.sp), modifier = Modifier.padding(end = 6.dp))
            QuestionCounts.forEach { option -> Pill(option.toString(), selected = option == count) { count = option } }
            Spacer(Modifier.weight(1f))
            BasicText("${NumberFormat.getIntegerInstance().format(poolSize)} to pick from", style = Type.soft.copy(fontSize = 13.sp), modifier = Modifier.padding(end = 14.dp))
            Pill(if (mode == Mode.Solo) "Start" else "Open the game", Modifier.focusRequester(start), large = true) {
                onStart(Settings(level, available, count))
            }
        }
    }
}

@Composable
private fun LevelRow(level: Level, selected: Boolean, onClick: () -> Unit) {
    val (focused, focus) = rememberFocus()
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, tween(200), label = "levelScale")
    val fill by animateColorAsState(if (selected) Gold else Panel, tween(200), label = "levelFill")
    val ink by animateColorAsState(if (selected) Night else Text, tween(200), label = "levelInk")
    val border by animateColorAsState(if (focused) Text else if (selected) Gold else Line, tween(200), label = "levelBorder")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(fill, RowShape)
            .border(if (focused) 2.dp else 1.dp, border, RowShape)
            .then(focus)
            .pressable(onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(if (selected) Night else Line, CircleShape), contentAlignment = Alignment.Center) {
            BasicText(level.number.toString(), style = Type.button.copy(fontSize = 17.sp, color = if (selected) Gold else Text))
        }
        Column {
            BasicText(level.name, style = Type.body.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = { ink })
            BasicText(level.note, style = Type.body.copy(fontSize = 11.sp), color = { if (selected) Night else TextSoft })
        }
    }
}
