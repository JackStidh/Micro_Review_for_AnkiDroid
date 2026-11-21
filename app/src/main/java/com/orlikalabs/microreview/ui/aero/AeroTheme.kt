package com.orlikalabs.microreview.ui.aero

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.orlikalabs.microreview.FocusSettings
import com.orlikalabs.microreview.ThemeShuffle

/**
 * The look of the app: a Japanese app of the Frutiger Aero years (glossy gel buttons, glass panels, skies, light,
 * nature), in four themes. Each one is a palette, a scene drawn behind everything ([AeroBackground]) and its own
 * drifting particles.
 */
@Immutable
data class AeroPalette(
    val id: String,
    /** The theme's kanji, its reading and its name. */
    val kanji: String,
    val reading: String,
    val name: String,
    val motto: String,
    val isDark: Boolean,
    /** The sky, top to bottom. */
    val sky: List<Color>,
    /** Glass panels: their fill, top and bottom, their edge, and the solid fill of dialogs. */
    val glassTop: Color,
    val glassBottom: Color,
    val glassEdge: Color,
    val dialog: Color,
    /** Text on glass. */
    val ink: Color,
    val inkSoft: Color,
    /** Gels: each a light and a deep color. */
    val accent: Color,
    val accentDeep: Color,
    val pass: Color,
    val passDeep: Color,
    val fail: Color,
    val failDeep: Color,
    val gold: Color,
    val goldDeep: Color,
    val neutral: Color,
    val neutralDeep: Color,
    /** The light things give off. */
    val glow: Color,
    val particles: Particles,
    val scene: Scene,
    val headings: FontFamily,
)

enum class Particles { STARS, BUBBLES, EMBERS, PETALS }

enum class Scene { YORU, MOCHI, TORII, SAKURA }

object AeroThemes {
    /** 夜, night: a glass city under an aurora, a crescent moon, stars and fireflies. */
    val YORU = AeroPalette(
        id = "yoru",
        kanji = "夜",
        reading = "よる",
        name = "Yoru",
        motto = "Study by moonlight",
        isDark = true,
        sky = listOf(Color(0xFF030716), Color(0xFF0B1840), Color(0xFF173A7A), Color(0xFF2A5FA8)),
        glassTop = Color(0x38B4D2FF),
        glassBottom = Color(0x1A6E8FD8),
        glassEdge = Color(0x80CFE6FF),
        dialog = Color(0xF0101E48),
        ink = Color(0xFFEFF6FF),
        inkSoft = Color(0xFFA9C2EA),
        accent = Color(0xFF52D8FF),
        accentDeep = Color(0xFF0A5FCC),
        pass = Color(0xFF5BEDB4),
        passDeep = Color(0xFF0A8A5E),
        fail = Color(0xFFFF7A9C),
        failDeep = Color(0xFFB0164A),
        gold = Color(0xFFFFE08A),
        goldDeep = Color(0xFFC08A12),
        neutral = Color(0xFF8FA6D6),
        neutralDeep = Color(0xFF2D426E),
        glow = Color(0xFF7FE9FF),
        particles = Particles.STARS,
        scene = Scene.YORU,
        headings = FontFamily.SansSerif,
    )

    /** 餅, mochi: pastel, soft and round. Sakura mochi pink, matcha green, rice-cake white, rising bubbles. */
    val MOCHI = AeroPalette(
        id = "mochi",
        kanji = "餅",
        reading = "もち",
        name = "Mochi",
        motto = "Soft, round, a little sweet",
        isDark = false,
        sky = listOf(Color(0xFFFFF8F1), Color(0xFFFFE6EE), Color(0xFFF4EEDF), Color(0xFFDDF0D2)),
        glassTop = Color(0xC8FFFFFF),
        glassBottom = Color(0x8CFFF4F7),
        glassEdge = Color(0xFFFFFFFF),
        dialog = Color(0xF7FFF9F6),
        ink = Color(0xFF4B3940),
        inkSoft = Color(0xFF8E7480),
        accent = Color(0xFFFFA9C4),
        accentDeep = Color(0xFFDB5C88),
        pass = Color(0xFFAEDD8E),
        passDeep = Color(0xFF5E9E43),
        fail = Color(0xFFE79A8E),
        failDeep = Color(0xFFA9453F),
        gold = Color(0xFFFFE3A0),
        goldDeep = Color(0xFFC79A35),
        neutral = Color(0xFFF2E6DF),
        neutralDeep = Color(0xFFB59C93),
        glow = Color(0xFFFFFFFF),
        particles = Particles.BUBBLES,
        scene = Scene.MOCHI,
        headings = FontFamily.SansSerif,
    )

    /** 鳥居・戦国, torii in the Warring States: black lacquer, vermilion, gold leaf, a red sun, embers. */
    val TORII = AeroPalette(
        id = "torii",
        kanji = "鳥居",
        reading = "とりい",
        name = "Torii Sengoku",
        motto = "Lacquer, vermilion and gold",
        isDark = true,
        sky = listOf(Color(0xFF0C0503), Color(0xFF2B0A06), Color(0xFF6E1409), Color(0xFFD9481F)),
        glassTop = Color(0x4D2A0C06),
        glassBottom = Color(0x33100403),
        glassEdge = Color(0xB3E8C26A),
        dialog = Color(0xF21A0806),
        ink = Color(0xFFFFF3DC),
        inkSoft = Color(0xFFE2B98A),
        accent = Color(0xFFFF5A36),
        accentDeep = Color(0xFF9C130A),
        pass = Color(0xFF4FCB95),
        passDeep = Color(0xFF0F6145),
        fail = Color(0xFFFF4A3D),
        failDeep = Color(0xFF7A0B08),
        gold = Color(0xFFF7D27A),
        goldDeep = Color(0xFFA9741A),
        neutral = Color(0xFF6B4A3A),
        neutralDeep = Color(0xFF241510),
        glow = Color(0xFFFFB45C),
        particles = Particles.EMBERS,
        scene = Scene.TORII,
        headings = FontFamily.Serif,
    )

    /** 桜, cherry blossom: the classic Aero sky, a lens flare, white swooshes, a blossoming branch, falling petals. */
    val SAKURA = AeroPalette(
        id = "sakura",
        kanji = "桜",
        reading = "さくら",
        name = "Sakura",
        motto = "Blue sky, falling petals",
        isDark = false,
        sky = listOf(Color(0xFF2AA9F2), Color(0xFF7FD3FF), Color(0xFFD6F1FF), Color(0xFFFFE1EC)),
        glassTop = Color(0xB3FFFFFF),
        glassBottom = Color(0x66E8F6FF),
        glassEdge = Color(0xFFFFFFFF),
        dialog = Color(0xF5F4FBFF),
        ink = Color(0xFF16304F),
        inkSoft = Color(0xFF4F6B8C),
        accent = Color(0xFF45C1FF),
        accentDeep = Color(0xFF0A6ED6),
        pass = Color(0xFF7BDC5C),
        passDeep = Color(0xFF26902A),
        fail = Color(0xFFFF7DA0),
        failDeep = Color(0xFFD02B5C),
        gold = Color(0xFFFFD86A),
        goldDeep = Color(0xFFD08F0B),
        neutral = Color(0xFFE3EEF8),
        neutralDeep = Color(0xFF8AA3BF),
        glow = Color(0xFFFFFFFF),
        particles = Particles.PETALS,
        scene = Scene.SAKURA,
        headings = FontFamily.SansSerif,
    )

    val ALL = listOf(SAKURA, YORU, MOCHI, TORII)

    fun byId(id: String?): AeroPalette = ALL.firstOrNull { it.id == id } ?: SAKURA

    /** The theme shown now (the locked one when locked). */
    fun current(context: Context): AeroPalette = byId(FocusSettings.themeName(context))

    fun choose(context: Context, palette: AeroPalette) = FocusSettings.setThemeName(context, palette.id)

    fun choice(context: Context) = ThemeChoice(FocusSettings.themeLocked(context), FocusSettings.themesOff(context))

    /**
     * A screen opens: unless the theme is locked, another one at random among the ticked ones ([ThemeShuffle]).
     * Returns the theme to show.
     */
    fun shuffle(context: Context): AeroPalette {
        if (FocusSettings.themeLocked(context)) return current(context)
        val next = ThemeShuffle.next(ALL.map { it.id }, FocusSettings.themesOff(context), FocusSettings.themeName(context))
        FocusSettings.setThemeName(context, next)
        return byId(next)
    }

    /**
     * A theme tapped in the picker: locked, it becomes the one shown; shuffling, it's ticked or unticked (the last
     * ticked one stays). Returns the theme to show now.
     */
    fun tap(context: Context, palette: AeroPalette): AeroPalette {
        if (FocusSettings.themeLocked(context)) {
            choose(context, palette)
        } else {
            FocusSettings.setThemesOff(context, ThemeShuffle.toggled(ALL.map { it.id }, FocusSettings.themesOff(context), palette.id))
        }
        return current(context)
    }

    /** Locks the theme shown now, or goes back to shuffling. */
    fun toggleLock(context: Context) = FocusSettings.setThemeLocked(context, !FocusSettings.themeLocked(context))
}

val LocalAero = staticCompositionLocalOf { AeroThemes.SAKURA }

/** Whether things may move: the system's animations aren't turned off (accessibility). */
val LocalAeroMotion = staticCompositionLocalOf { true }

fun motionAllowed(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

/** The theme for everything inside: the palette, and Material's colors from it for the few Material parts. */
@Composable
fun AeroTheme(palette: AeroPalette, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val motion = remember { motionAllowed(context) }
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.pass,
            tertiary = palette.gold,
            background = palette.sky[1],
            surface = palette.dialog,
            onSurface = palette.ink,
            onSurfaceVariant = palette.inkSoft,
            error = palette.fail,
        )
    } else {
        lightColorScheme(
            primary = palette.accentDeep,
            onPrimary = Color.White,
            secondary = palette.passDeep,
            tertiary = palette.goldDeep,
            background = palette.sky[2],
            surface = palette.dialog,
            onSurface = palette.ink,
            onSurfaceVariant = palette.inkSoft,
            error = palette.failDeep,
        )
    }
    CompositionLocalProvider(LocalAero provides palette, LocalAeroMotion provides motion) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** How the theme is picked: [locked] on the current one, or shuffled among all but [off] (theme ids). */
data class ThemeChoice(val locked: Boolean = false, val off: Set<String> = emptySet())

/**
 * The app's visits, for the shuffle: a new theme when the app comes into view after a moment away
 * ([ThemeShuffle.isNewVisit]), the same one across its screens (home, Settings, a card session) and a rotation.
 * Each activity calls [arrive] before reading its theme (in onCreate and onStart), then [started] and [stopped].
 */
object ThemeVisit {
    private var visible = 0
    private var hiddenAt = -1L
    private var arriving = false

    /** The theme for a screen coming into view: shuffled if that's a new visit. Main thread. */
    fun arrive(context: Context): AeroPalette {
        if (visible == 0 && !arriving && ThemeShuffle.isNewVisit(hiddenAt, System.currentTimeMillis())) {
            arriving = true
            return AeroThemes.shuffle(context)
        }
        return AeroThemes.current(context)
    }

    fun started() {
        visible++
        arriving = false
    }

    fun stopped() {
        visible = maxOf(0, visible - 1)
        if (visible == 0) hiddenAt = System.currentTimeMillis()
    }
}
