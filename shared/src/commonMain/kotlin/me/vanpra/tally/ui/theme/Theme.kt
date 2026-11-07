package me.vanpra.tally.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import tally.shared.generated.resources.Res
import tally.shared.generated.resources.archivo
import tally.shared.generated.resources.instrument_sans

/** Colours from the design mocks. Day is built for direct sunlight; Dusk for evenings and reading stats. */
@Immutable
data class TallyColors(
    val bg: Color, val surface: Color, val raise: Color, val ink: Color, val muted: Color, val line: Color,
    val accent: Color, val onAccent: Color, val flag: Color, val warn: Color, val blue: Color,
    val mapRough: Color, val mapFairway: Color, val mapGreen: Color, val mapGreenEdge: Color,
    val mapSand: Color, val mapSandEdge: Color, val mapWater: Color, val mapTee: Color, val mapInk: Color,
    val mapWood: Color, val mapTree: Color, val mapTreeEdge: Color, val mapBuilding: Color, val mapBuildingEdge: Color, val mapPath: Color,
)

val DayColors = TallyColors(
    bg = Color(0xFFF4F6F1), surface = Color(0xFFFFFFFF), raise = Color(0xFFEAEEE7), ink = Color(0xFF101A14),
    muted = Color(0xFF5D6B62), line = Color(0xFFDBE2D9), accent = Color(0xFF17784A), onAccent = Color.White,
    flag = Color(0xFFE0A21A), warn = Color(0xFFB2502A), blue = Color(0xFF2A6FB0),
    mapRough = Color(0xFFC9D8BF), mapFairway = Color(0xFFA9D18F), mapGreen = Color(0xFF7CC66F), mapGreenEdge = Color(0xFF3F8F45),
    mapSand = Color(0xFFEBD49A), mapSandEdge = Color(0xFFA8874A), mapWater = Color(0xFF8FC1DD), mapTee = Color(0xFFB9D8A3), mapInk = Color(0xFF101A14),
    mapWood = Color(0xFFA9C29A), mapTree = Color(0xFF8AAE7B), mapTreeEdge = Color(0xFF5F8A55), mapBuilding = Color(0xFFD3CCBF), mapBuildingEdge = Color(0xFF8E877C), mapPath = Color(0xFFF7F3E8),
)

val DuskColors = TallyColors(
    bg = Color(0xFF0C1210), surface = Color(0xFF151D19), raise = Color(0xFF1F2A24), ink = Color(0xFFE8EFEA),
    muted = Color(0xFF8E9F95), line = Color(0xFF27332C), accent = Color(0xFF5FD99A), onAccent = Color(0xFF05130B),
    flag = Color(0xFFF2B84B), warn = Color(0xFFEF8A5F), blue = Color(0xFF79B8EF),
    mapRough = Color(0xFF1C2D24), mapFairway = Color(0xFF2F6345), mapGreen = Color(0xFF43965F), mapGreenEdge = Color(0xFF8BEBB4),
    mapSand = Color(0xFF9C8A5E), mapSandEdge = Color(0xFFCDB57E), mapWater = Color(0xFF285877), mapTee = Color(0xFF2E5C40), mapInk = Color(0xFFE8EFEA),
    mapWood = Color(0xFF13241B), mapTree = Color(0xFF1F4430), mapTreeEdge = Color(0xFF3A6E4F), mapBuilding = Color(0xFF3B413D), mapBuildingEdge = Color(0xFF616862), mapPath = Color(0xFF46564C),
)

@Immutable
data class TallyType(
    /** Condensed display numbers, e.g. the big yardage. */
    val numberHero: TextStyle,
    val numberLarge: TextStyle,
    val numberMedium: TextStyle,
    val holeNumber: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caps: TextStyle,
)

val LocalTallyColors = staticCompositionLocalOf { DayColors }
val LocalTallyType = staticCompositionLocalOf<TallyType> { error("TallyTheme not set") }

object Tally {
    val colors: TallyColors @Composable get() = LocalTallyColors.current
    val type: TallyType @Composable get() = LocalTallyType.current
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun archivo(weight: Int, width: Float) = Font(
    Res.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

@OptIn(ExperimentalTextApi::class)
@Composable
private fun instrument(weight: Int) = Font(
    Res.font.instrument_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@Composable
fun TallyTheme(dusk: Boolean, content: @Composable () -> Unit) {
    val colors = if (dusk) DuskColors else DayColors
    // Each width needs its own family: a family can only hold one font per weight.
    val hero = FontFamily(archivo(800, 72f))
    val condensed = FontFamily(archivo(600, 80f), archivo(700, 80f), archivo(800, 75f))
    val sans = FontFamily(instrument(400), instrument(500), instrument(600), instrument(700))
    val type = TallyType(
        numberHero = TextStyle(fontFamily = hero, fontWeight = FontWeight(800), fontSize = 84.sp, lineHeight = 76.sp),
        numberLarge = TextStyle(fontFamily = condensed, fontWeight = FontWeight(600), fontSize = 32.sp, lineHeight = 32.sp),
        numberMedium = TextStyle(fontFamily = condensed, fontWeight = FontWeight(700), fontSize = 15.sp),
        holeNumber = TextStyle(fontFamily = condensed, fontWeight = FontWeight(800), fontSize = 42.sp, lineHeight = 40.sp),
        title = TextStyle(fontFamily = sans, fontWeight = FontWeight(650), fontSize = 15.sp),
        body = TextStyle(fontFamily = sans, fontWeight = FontWeight(400), fontSize = 14.sp),
        label = TextStyle(fontFamily = sans, fontWeight = FontWeight(600), fontSize = 12.sp),
        caps = TextStyle(fontFamily = sans, fontWeight = FontWeight(600), fontSize = 10.5.sp, letterSpacing = 1.sp),
    )
    val material = if (dusk) darkColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.bg, surface = colors.surface, onSurface = colors.ink)
    else lightColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.bg, surface = colors.surface, onSurface = colors.ink)
    CompositionLocalProvider(LocalTallyColors provides colors, LocalTallyType provides type) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
