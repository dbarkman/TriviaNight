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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redmesastudios.trivianight.ui.Gold
import com.redmesastudios.trivianight.ui.Line
import com.redmesastudios.trivianight.ui.LineBright
import com.redmesastudios.trivianight.ui.Night
import com.redmesastudios.trivianight.ui.Panel
import com.redmesastudios.trivianight.ui.PlayerColors
import com.redmesastudios.trivianight.ui.Text
import com.redmesastudios.trivianight.ui.TextSoft
import com.redmesastudios.trivianight.ui.Type

private val CardShape = RoundedCornerShape(16.dp)

// Solo starts here: pick yourself from everyone who has played on the household's TVs, or
// add a new player.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhoScreen(lastPlayer: String?, onBack: () -> Unit, onPick: (Profile) -> Unit) {
    val saved by Records.state.collectAsState()
    LaunchedEffect(Unit) { Records.sync() }
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        PlayerEditor(
            heading = "New player",
            initial = null,
            onCancel = { adding = false },
            onSave = { name, color -> onPick(Records.addPlayer(name, color)) },
        )
        return
    }
    BackHandler(onBack = onBack)
    val stats = saved.stats().sortedBy { it.profile.name.lowercase() }
    val first = remember { FocusRequester() }
    val focusId = stats.firstOrNull { it.profile.id == lastPlayer }?.profile?.id ?: stats.firstOrNull()?.profile?.id
    LaunchedEffect(focusId) { runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 36.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText("PLAY SOLO", style = Type.kicker)
            BasicText("Who's playing?", style = Type.title)
        }
        FlowRow(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            maxItemsInEachRow = 4,
        ) {
            stats.forEach { player ->
                PlayerCard(
                    name = player.profile.name,
                    color = PlayerColors[player.profile.color],
                    note = when (player.games) {
                        0 -> "No games yet"
                        1 -> "1 game"
                        else -> "${player.games} games"
                    },
                    modifier = if (player.profile.id == focusId) Modifier.focusRequester(first) else Modifier,
                ) { onPick(player.profile) }
            }
            PlayerCard(name = "New player", color = null, note = "Add your first name", modifier = if (focusId == null) Modifier.focusRequester(first) else Modifier) {
                adding = true
            }
        }
        HintRow("OK to choose", "Back to go back", "Change names and colors under High scores")
    }
}

@Composable
private fun PlayerCard(name: String, color: Color?, note: String, modifier: Modifier, onClick: () -> Unit) {
    val (focused, focus) = rememberFocus()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(200), label = "playerScale")
    val fill by animateColorAsState(if (focused) Gold else Panel, tween(200), label = "playerFill")
    val ink by animateColorAsState(if (focused) Night else Text, tween(200), label = "playerInk")
    Column(
        modifier
            .width(196.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(fill, CardShape)
            .border(1.dp, if (focused) Gold else LineBright, CardShape)
            .then(focus)
            .pressable(onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (color != null) {
            PlayerBadge(name, color, 56.dp)
        } else {
            Box(Modifier.size(56.dp).border(2.dp, if (focused) Night else LineBright, CircleShape), contentAlignment = Alignment.Center) {
                BasicText("+", style = Type.title.copy(fontSize = 30.sp), color = { ink })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(name, style = Type.body.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = { ink }, maxLines = 1)
            BasicText(note, style = Type.body.copy(fontSize = 13.sp), color = { if (focused) Night else TextSoft }, maxLines = 1)
        }
    }
}

// A first name and a color, for a new player or one being changed. Removing is offered
// only for an existing player.
@Composable
fun PlayerEditor(heading: String, initial: Profile?, onCancel: () -> Unit, onSave: (String, Int) -> Unit, onRemove: (() -> Unit)? = null) {
    BackHandler(onBack = onCancel)
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableIntStateOf(initial?.color ?: 0) }
    var confirmRemove by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val firstColor = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    // A new player starts typing; changing one starts on Save, without the keyboard in the way.
    LaunchedEffect(Unit) { runCatching { (if (initial == null) field else save).requestFocus() } }
    val clean = name.trim()

    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 36.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText("PLAYERS", style = Type.kicker)
            BasicText(heading, style = Type.title)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerBadge(clean.ifEmpty { "?" }, PlayerColors[color], 96.dp)
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("FIRST NAME", style = Type.label)
                val (focused, focus) = rememberFocus()
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(14) },
                    singleLine = true,
                    textStyle = Type.title.copy(fontSize = 30.sp),
                    cursorBrush = SolidColor(Gold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { runCatching { firstColor.requestFocus() } }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(field)
                        .then(focus)
                        .background(Panel, RoundedCornerShape(14.dp))
                        .border(2.dp, if (focused) Gold else Line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText("FAVORITE COLOR", style = Type.label)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlayerColors.forEachIndexed { i, swatch ->
                    ColorSwatch(swatch, selected = i == color, modifier = if (i == 0) Modifier.focusRequester(firstColor) else Modifier) { color = i }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (initial == null) "Add and play" else "Save", Modifier.focusRequester(save), large = true) {
                if (clean.isEmpty()) runCatching { field.requestFocus() } else onSave(clean, color)
            }
            Pill("Cancel", large = true, onClick = onCancel)
            if (onRemove != null) {
                Spacer(Modifier.width(30.dp))
                Pill(if (confirmRemove) "Yes, remove ${initial?.name}" else "Remove player", large = true) {
                    if (confirmRemove) onRemove() else confirmRemove = true
                }
            }
        }
        if (confirmRemove) BasicText("Their games come off the high scores on every TV.", style = Type.soft.copy(fontSize = 13.sp))
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (focused, focus) = rememberFocus()
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, tween(200), label = "swatchScale")
    Box(
        modifier
            .size(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(3.dp, if (focused) Text else if (selected) TextSoft else Color.Transparent, CircleShape)
            .then(focus)
            .pressable(onClick)
            .padding(7.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(14.dp).background(Night, CircleShape))
    }
}

// Keeps the line of a board readable: "8 of 10 · Level 3 · Nov 1".
fun GameRecord.describe(): String {
    val date = java.time.Instant.ofEpochMilli(playedAt).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
    return "$right of $count · Level $level · $date"
}
