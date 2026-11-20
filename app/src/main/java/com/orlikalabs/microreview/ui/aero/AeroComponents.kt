package com.orlikalabs.microreview.ui.aero

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** The color of a gel. */
enum class GelTone { ACCENT, PASS, FAIL, GOLD, NEUTRAL }

private fun GelTone.colors(p: AeroPalette): Pair<Color, Color> = when (this) {
    GelTone.ACCENT -> p.accent to p.accentDeep
    GelTone.PASS -> p.pass to p.passDeep
    GelTone.FAIL -> p.fail to p.failDeep
    GelTone.GOLD -> p.gold to p.goldDeep
    GelTone.NEUTRAL -> p.neutral to p.neutralDeep
}

/** Text on a gel: white, except on the pale neutral gel of a light theme. */
private fun GelTone.ink(p: AeroPalette): Color = if (this == GelTone.NEUTRAL && !p.isDark) p.ink else Color.White

/**
 * A glass panel: a translucent fill, a sheen over its top half, a bright edge, a soft shadow tinted by the theme's
 * light. [solid]: the dialogs' near-opaque glass.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    padding: PaddingValues = PaddingValues(18.dp),
    solid: Boolean = false,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalAero.current
    val shape = RoundedCornerShape(corner)
    val top = if (solid) p.dialog else tint?.let { lerp(p.glassTop, it, 0.35f) } ?: p.glassTop
    val bottom = if (solid) lerp(p.dialog, p.sky[1], 0.25f) else tint?.let { lerp(p.glassBottom, it, 0.25f) } ?: p.glassBottom
    Column(
        modifier
            .shadow(if (solid) 24.dp else 14.dp, shape, ambientColor = p.glow.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = if (p.isDark) 0.6f else 0.22f))
            .clip(shape)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(top, bottom)))
                val cr = corner.toPx()
                // The sheen over the top, glass catching the light.
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.isDark) 0.16f else 0.45f), Color.Transparent), 0f, size.height * 0.45f),
                    cornerRadius = CornerRadius(cr, cr),
                    size = Size(size.width, size.height * 0.45f),
                )
                drawLine(Color.White.copy(alpha = if (p.isDark) 0.35f else 0.9f), Offset(cr, 1.dp.toPx()), Offset(size.width - cr, 1.dp.toPx()), 1.dp.toPx())
            }
            .border(1.dp, Brush.verticalGradient(listOf(p.glassEdge, p.glassEdge.copy(alpha = 0.15f))), shape)
            .padding(padding),
        content = content,
    )
}

/**
 * A gel button: a glossy pill, light at the top, deep at the bottom, a gloss over its upper half and a glow under
 * it. [charge] below 1: it fills from the left and can't be tapped yet (the card's first second).
 */
@Composable
fun GelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: GelTone = GelTone.ACCENT,
    enabled: Boolean = true,
    charge: Float = 1f,
    glyph: String? = null,
    detail: String? = null,
    compact: Boolean = false,
) {
    val p = LocalAero.current
    val (light, deep) = tone.colors(p)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "press")
    val usable = enabled && charge >= 1f
    val shape = RoundedCornerShape(if (compact) 22.dp else 30.dp)
    val ink = tone.ink(p)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .heightIn(min = if (compact) 40.dp else 56.dp)
            .shadow(if (usable) 10.dp else 2.dp, shape, ambientColor = deep, spotColor = deep)
            .clip(shape)
            .drawBehind { drawGel(light, deep, charge, pressed, if (compact) 22.dp.toPx() else 30.dp.toPx()) }
            .clickable(interaction, indication = null, enabled = usable, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 6.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (glyph != null) {
                    Text(glyph, style = gelText(ink, if (compact) 16.sp else 22.sp, deep), modifier = Modifier.padding(end = 8.dp))
                }
                Text(text, style = gelText(ink, if (compact) 14.sp else 17.sp, deep), textAlign = TextAlign.Center)
            }
            if (detail != null) {
                Text(detail, style = gelText(ink.copy(alpha = 0.9f), 12.sp, deep).copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}

private fun gelText(color: Color, size: androidx.compose.ui.unit.TextUnit, deep: Color) = TextStyle(
    color = color,
    fontSize = size,
    fontWeight = FontWeight.Bold,
    shadow = if (color == Color.White || color.alpha < 1f && color.red > 0.9f) Shadow(deep.copy(alpha = 0.8f), Offset(0f, 2f), 4f) else null,
)

private fun DrawScope.drawGel(light: Color, deep: Color, charge: Float, pressed: Boolean, maxRadius: Float) {
    val w = size.width
    val h = size.height
    val r = minOf(h / 2, maxRadius)
    val corner = CornerRadius(r, r)
    val body = Brush.verticalGradient(listOf(lerp(light, Color.White, 0.3f), light, deep))
    if (charge < 1f) {
        drawRoundRect(Color.Black.copy(alpha = 0.22f), cornerRadius = corner)
        drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = corner)
        clipRect(right = w * charge.coerceIn(0f, 1f)) { drawRoundRect(body, cornerRadius = corner, alpha = 0.75f) }
    } else {
        drawRoundRect(body, cornerRadius = corner)
    }
    // The glow inside, at the bottom.
    drawOval(
        Brush.radialGradient(listOf(lerp(light, Color.White, 0.55f).copy(alpha = 0.7f), Color.Transparent), Offset(w / 2, h * 1.02f), w * 0.42f),
        Offset(w * 0.08f, h * 0.45f),
        Size(w * 0.84f, h * 0.7f),
    )
    // The gloss over the top half.
    val inset = minOf(r * 0.55f, w * 0.1f)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.12f)), h * 0.05f, h * 0.5f),
        topLeft = Offset(inset, h * 0.05f),
        size = Size(w - inset * 2, h * 0.45f),
        cornerRadius = CornerRadius(r * 0.8f, r * 0.8f),
    )
    drawRoundRect(deep.copy(alpha = 0.9f), cornerRadius = corner, style = Stroke(1.2f.dp.toPx()))
    if (pressed) drawRoundRect(Color.Black.copy(alpha = 0.12f), cornerRadius = corner)
}

/** A glossy sphere, a small button: a radial gel, a highlight on top, a rim. */
@Composable
fun Orb(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tone: GelTone = GelTone.NEUTRAL,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalAero.current
    val (light, deep) = tone.colors(p)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.4f, stiffness = 700f), label = "orb")
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .shadow(6.dp, CircleShape, ambientColor = deep, spotColor = deep)
            .clip(CircleShape)
            .drawBehind { drawOrb(light, deep) }
            .then(
                if (onClick != null) {
                    Modifier.clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The color for what is drawn on an [Orb] of [tone]. */
@Composable
fun orbInk(tone: GelTone = GelTone.NEUTRAL): Color = tone.ink(LocalAero.current)

internal fun DrawScope.drawOrb(light: Color, deep: Color) {
    val r = size.minDimension / 2
    val c = center
    drawCircle(Brush.radialGradient(listOf(lerp(light, Color.White, 0.45f), light, deep), c - Offset(r * 0.35f, r * 0.45f), r * 1.7f), r, c)
    drawCircle(Brush.radialGradient(listOf(lerp(light, Color.White, 0.5f).copy(alpha = 0.6f), Color.Transparent), c + Offset(0f, r * 0.8f), r * 0.8f), r, c)
    drawOval(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.1f)), c.y - r * 0.92f, c.y - r * 0.05f),
        Offset(c.x - r * 0.62f, c.y - r * 0.92f),
        Size(r * 1.24f, r * 0.85f),
    )
    drawCircle(deep.copy(alpha = 0.9f), r - 0.6f.dp.toPx(), c, style = Stroke(1.2f.dp.toPx()))
}

/** A glossy on/off switch: a glass track that turns to gel, a pearl that slides. */
@Composable
fun AeroSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalAero.current
    val position by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "switch")
    Box(
        modifier
            .size(56.dp, 32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                val corner = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(if (p.isDark) Color.Black.copy(alpha = 0.35f) else Color(0x33000000), cornerRadius = corner)
                if (position > 0f) {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(lerp(p.pass, Color.White, 0.3f), p.pass, p.passDeep)),
                        cornerRadius = corner,
                        alpha = position,
                    )
                }
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), 0f, size.height * 0.5f),
                    topLeft = Offset(size.height * 0.3f, size.height * 0.06f),
                    size = Size(size.width - size.height * 0.6f, size.height * 0.4f),
                    cornerRadius = corner,
                )
                drawRoundRect(Color.White.copy(alpha = 0.6f), cornerRadius = corner, style = Stroke(1.dp.toPx()))
            }
            .clickable(enabled = enabled, role = Role.Switch, onClick = { onCheckedChange(!checked) }),
    ) {
        Box(
            Modifier
                .offset(x = 3.dp + 24.dp * position, y = 3.dp)
                .size(26.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .drawBehind { drawOrb(Color.White, Color(0xFFB8C6D6)) },
        )
    }
}

/** A glossy check box. */
@Composable
fun AeroCheck(checked: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalAero.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .size(28.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .drawBehind {
                if (checked) {
                    drawGel(p.pass, p.passDeep, 1f, false, 8.dp.toPx())
                } else {
                    drawRoundRect(if (p.isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.7f), cornerRadius = CornerRadius(8.dp.toPx()))
                    drawRoundRect(p.inkSoft.copy(alpha = 0.6f), cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(1.5f.dp.toPx()))
                }
            }
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Beads, one per card of the gate: answered ones in gel, the current one glowing, the rest glass. */
@Composable
fun Beads(done: Int, total: Int, modifier: Modifier = Modifier) {
    val p = LocalAero.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until total) {
            val size = if (i == done) 18.dp else 14.dp
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .drawBehind {
                        when {
                            i < done -> drawOrb(p.pass, p.passDeep)
                            i == done -> drawOrb(p.accent, p.accentDeep)
                            else -> {
                                drawCircle(Color.White.copy(alpha = if (p.isDark) 0.12f else 0.55f))
                                drawCircle(p.inkSoft.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
                            }
                        }
                    },
            )
        }
    }
}

/**
 * A section's title beside its seal: the kanji on a glossy orb, or, in Torii Sengoku, stamped on a red hanko.
 */
@Composable
fun SectionTitle(kanji: String, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val p = LocalAero.current
    Row(modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Seal(kanji, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = TextStyle(color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = p.headings, shadow = titleShadow(p)))
            if (subtitle != null) Text(subtitle, style = TextStyle(color = p.inkSoft, fontSize = 13.sp))
        }
    }
}

/** A kanji on its seal. */
@Composable
fun Seal(kanji: String, size: Dp, modifier: Modifier = Modifier, tone: GelTone = GelTone.ACCENT) {
    val p = LocalAero.current
    val fontSize = (size.value * (if (kanji.length > 1) 0.36f else 0.5f)).sp
    if (p.scene == Scene.TORII) {
        Box(
            modifier
                .size(size)
                .rotate(-4f)
                .clip(RoundedCornerShape(6.dp))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFE5402A), Color(0xFFA9180C))))
                    drawRect(Color(0xFFFFE2C2).copy(alpha = 0.85f), topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = Size(this.size.width - 6.dp.toPx(), this.size.height - 6.dp.toPx()), style = Stroke(1.5f.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(kanji, style = TextStyle(color = Color(0xFFFFF1DE), fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif))
        }
    } else {
        Orb(onClick = null, modifier = modifier, size = size, tone = tone) {
            Text(kanji, style = TextStyle(color = orbInk(tone), fontSize = fontSize, fontWeight = FontWeight.Bold, shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 1.5f), 3f)))
        }
    }
}

/** A small glass label. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val p = LocalAero.current
    val c = color ?: p.inkSoft
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .drawBehind {
                drawRect(c.copy(alpha = if (p.isDark) 0.22f else 0.14f))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), 0f, size.height / 2))
            }
            .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = TextStyle(color = if (color != null && !p.isDark) lerp(c, Color.Black, 0.25f) else c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
    }
}

fun titleShadow(p: AeroPalette): Shadow =
    if (p.isDark) Shadow(p.glow.copy(alpha = 0.55f), Offset.Zero, 12f) else Shadow(Color.White.copy(alpha = 0.9f), Offset(0f, 1f), 3f)

/** A dialog on solid glass. */
@Composable
fun AeroDialog(
    onDismiss: () -> Unit,
    title: String,
    kanji: String? = null,
    buttons: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalAero.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassPanel(Modifier.padding(22.dp).widthIn(max = 480.dp).fillMaxWidth(), solid = true, padding = PaddingValues(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (kanji != null) {
                    Seal(kanji, 34.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = TextStyle(color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = p.headings))
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
            Spacer(Modifier.size(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) { buttons() }
        }
    }
}

/** One value among [options]. */
@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit, kanji: String? = null, cancel: String) {
    val p = LocalAero.current
    AeroDialog(onDismiss, title, kanji, buttons = { GelButton(cancel, onDismiss, tone = GelTone.NEUTRAL, compact = true) }) {
        options.forEachIndexed { index, label ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPick(index) }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).drawBehind {
                    if (index == selected) drawOrb(p.accent, p.accentDeep) else drawCircle(p.inkSoft.copy(alpha = 0.6f), style = Stroke(1.5f.dp.toPx()))
                })
                Spacer(Modifier.width(14.dp))
                Text(label, style = TextStyle(color = p.ink, fontSize = 16.sp, fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal))
            }
        }
    }
}

/** The text styles of the screens, from the palette. */
object AeroText {
    @Composable
    fun title(size: Int = 18) = TextStyle(color = LocalAero.current.ink, fontSize = size.sp, fontWeight = FontWeight.Bold, fontFamily = LocalAero.current.headings)

    @Composable
    fun body(size: Int = 15) = TextStyle(color = LocalAero.current.ink, fontSize = size.sp)

    @Composable
    fun soft(size: Int = 13) = TextStyle(color = LocalAero.current.inkSoft, fontSize = size.sp)
}
