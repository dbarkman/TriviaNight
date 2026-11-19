package com.redmesastudios.trivianight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redmesastudios.trivianight.ui.Gold
import com.redmesastudios.trivianight.ui.Line
import com.redmesastudios.trivianight.ui.Night
import com.redmesastudios.trivianight.ui.Panel
import com.redmesastudios.trivianight.ui.PlayerColors
import com.redmesastudios.trivianight.ui.Text
import com.redmesastudios.trivianight.ui.TextSoft
import com.redmesastudios.trivianight.ui.Type

private val RowShape = RoundedCornerShape(12.dp)

// Two boards: each player's best game, ranked by points a question so a short game and a
// long one compare fairly, and all-time points, which grow with every game played. OK on a
// player changes their name or color, or removes them.
@Composable
fun HighScoresScreen(onBack: () -> Unit) {
    val saved by Records.state.collectAsState()
    LaunchedEffect(Unit) { Records.sync() }
    var editing by remember { mutableStateOf<Profile?>(null) }
    editing?.let { profile ->
        PlayerEditor(
            heading = "Change ${profile.name}",
            initial = profile,
            onCancel = { editing = null },
            onSave = { name, color ->
                Records.updatePlayer(profile.id, name, color)
                editing = null
            },
            onRemove = {
                Records.removePlayer(profile.id)
                editing = null
            },
        )
        return
    }
    BackHandler(onBack = onBack)
    val best = saved.bestGames()
    val stats = saved.stats()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(start = 60.dp, end = 60.dp, top = 36.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText("HIGH SCORES", style = Type.kicker)
            BasicText(if (stats.isEmpty()) "No players yet" else "Who's on top", style = Type.title)
        }
        if (stats.isEmpty()) {
            BasicText("Play a game and pick a name; scores from every TV in the house show up here.", style = Type.soft)
            Pill("Back", Modifier.focusRequester(first), large = true, onClick = onBack)
            return@Column
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Board("BEST GAMES · points a question", Modifier.weight(1f)) {
                if (best.isEmpty()) item { BasicText("Nobody has finished a game yet.", style = Type.soft) }
                itemsIndexed(best, key = { _, it -> it.first.id }) { place, (profile, game) ->
                    BoardRow(place, profile, game.perQuestion.grouped(), game.describe(), if (place == 0) Modifier.focusRequester(first) else Modifier) { editing = profile }
                }
            }
            Board("ALL-TIME POINTS", Modifier.weight(1f)) {
                itemsIndexed(stats, key = { _, it -> it.profile.id }) { place, player ->
                    val games = if (player.games == 1) "1 game" else "${player.games} games"
                    BoardRow(
                        place,
                        player.profile,
                        player.points.toInt().grouped(),
                        if (player.games == 0) "No games yet" else "$games · ${player.percentRight}% right",
                        if (best.isEmpty() && place == 0) Modifier.focusRequester(first) else Modifier,
                    ) { editing = player.profile }
                }
            }
        }
        HintRow("OK on a player to change their name or color", "Back to go back")
    }
}

@Composable
private fun Board(heading: String, modifier: Modifier, rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(heading, style = Type.label)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp), content = rows)
    }
}

@Composable
private fun BoardRow(place: Int, profile: Profile, value: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
    val (focused, focus) = rememberFocus()
    val fill by animateColorAsState(if (focused) Gold else Panel, tween(200), label = "rowFill")
    val ink by animateColorAsState(if (focused) Night else Text, tween(200), label = "rowInk")
    Row(
        modifier
            .fillMaxWidth()
            .background(fill, RowShape)
            .border(1.dp, if (focused) Gold else Line, RowShape)
            .then(focus)
            .pressable(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(26.dp)) {
            BasicText("${place + 1}", style = Type.title.copy(fontSize = 22.sp), color = { if (focused) Night else if (place == 0) Gold else TextSoft })
        }
        PlayerBadge(profile.name, PlayerColors[profile.color], 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(profile.name, style = Type.body.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = { ink }, maxLines = 1)
            BasicText(detail, style = Type.body.copy(fontSize = 12.sp), color = { if (focused) Night else TextSoft }, maxLines = 1)
        }
        BasicText(value, style = Type.number.copy(fontSize = 26.sp), color = { ink })
    }
}
