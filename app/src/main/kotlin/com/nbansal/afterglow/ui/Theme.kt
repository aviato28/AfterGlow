package com.nbansal.afterglow.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.afterglow.R

@OptIn(ExperimentalTextApi::class)
/** Design tokens (see docs/design and the roadmap's Design section). */
object Ag {
    val Base = Color(0xFF0B0908)
    val Ink = Color(0xFFF3E9DF)
    val Muted = Color(0xFFB8ADA3)
    val Amber = Color(0xFFFFB070)
    val Rose = Color(0xFFE8607A)
    val Violet = Color(0xFF5B3F8C)
    val Ring = Color(0xFFFFD9B0)
    val PillFill = Ink.copy(alpha = 0.08f)
    val PillLine = Ink.copy(alpha = 0.14f)

    val MarginX = 96.dp
    val MarginY = 54.dp

    private fun inter(weight: FontWeight) = Font(
        R.font.inter,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

    val Inter = FontFamily(
        inter(FontWeight.Light),
        inter(FontWeight.Normal),
        inter(FontWeight.Medium),
        inter(FontWeight.SemiBold),
    )

    val Greeting = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 84.sp, color = Ink)
    val Clock = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 44.sp, color = Ink)
    val Date = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 26.sp, color = Muted)
    val Label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 24.sp, color = Ink)
    val SectionCaps = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = 2.sp, color = Muted,
    )
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 24.sp, color = Ink)
}
