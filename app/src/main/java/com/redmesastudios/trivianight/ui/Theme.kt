package com.redmesastudios.trivianight.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.redmesastudios.trivianight.R

// The design canvas's palette: a night-sky ground, gold for what's chosen, and one color and
// shape per answer so they can be told apart without color.
val Night = Color(0xFF151831)
val Panel = Color(0xFF1F2346)
val PanelDeep = Color(0xFF1B1E3B)
val Line = Color(0xFF2A2F5C)
val LineBright = Color(0xFF3A4078)
val Text = Color(0xFFF5F3FF)
val TextSoft = Color(0xFFB7B9E0)
val TextFaint = Color(0xFF7F82B0)
val Gold = Color(0xFFFFD25E)
val Coral = Color(0xFFFF7A66)
val Sky = Color(0xFF4DB5FF)
val Mint = Color(0xFF4FE0B0)

// Answer A to D: triangle, diamond, circle, square.
val AnswerColors = listOf(Coral, Sky, Gold, Mint)

// Players' colors, in the order the phone page offers them.
val PlayerColors = listOf(Sky, Coral, Mint, Gold, Color(0xFFB79BFF), Color(0xFFFF8FC7), Color(0xFFB8E55C), Color(0xFFFFA44F))

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(res, weight = FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Bricolage = FontFamily(listOf(600, 700, 800).map { variable(R.font.bricolage, it) })
val Figtree = FontFamily(listOf(400, 500, 600, 700, 800).map { variable(R.font.figtree, it) })

// Sizes are the design's 1920x1080 px values halved (the stick runs at 320 dpi).
object Type {
    val logo = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, letterSpacing = (-1.5).sp, color = Text)
    val title = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1).sp, color = Text)
    val cardTitle = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-1).sp)
    val question = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 43.sp, letterSpacing = (-0.75).sp, color = Text)
    val number = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Gold)
    val kicker = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.5.sp, color = Gold)
    val body = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, color = Text)
    val soft = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextSoft)
    val label = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp, color = TextSoft)
    val chip = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val answer = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp)
    val button = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
}
