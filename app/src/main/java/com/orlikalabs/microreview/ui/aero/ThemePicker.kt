package com.orlikalabs.microreview.ui.aero

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orlikalabs.microreview.R

/**
 * The four themes side by side, each a little window on its scene, and a padlock. Shuffling (unlocked), each tile
 * is ticked in or out of the shuffle; locked, the tapped one is the theme, lifted and ringed with light. The theme
 * showing is ringed either way.
 */
@Composable
fun ThemePicker(
    shown: AeroPalette,
    choice: ThemeChoice,
    onTap: (AeroPalette) -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AeroThemes.ALL.forEach { palette ->
                ThemeTile(
                    palette = palette,
                    shown = palette.id == shown.id,
                    lifted = choice.locked && palette.id == shown.id,
                    ticked = if (choice.locked) null else palette.id !in choice.off,
                    onClick = { onTap(palette) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val lockLabel = stringResource(if (choice.locked) R.string.theme_unlock else R.string.theme_lock)
            Orb(
                onClick = onLock,
                size = 46.dp,
                tone = if (choice.locked) GelTone.GOLD else GelTone.NEUTRAL,
                contentDescription = lockLabel,
                modifier = Modifier.semantics { contentDescription = lockLabel },
            ) {
                Padlock(locked = choice.locked, color = orbInk(if (choice.locked) GelTone.GOLD else GelTone.NEUTRAL), modifier = Modifier.size(22.dp))
            }
            Text(
                if (choice.locked) stringResource(R.string.theme_locked_detail, shown.name) else stringResource(R.string.theme_shuffle_detail),
                modifier = Modifier.padding(start = 12.dp).weight(1f),
                style = AeroText.soft(13),
            )
        }
    }
}

@Composable
private fun ThemeTile(
    palette: AeroPalette,
    shown: Boolean,
    lifted: Boolean,
    ticked: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = LocalAero.current
    val lift by animateFloatAsState(if (lifted) 1f else 0f, label = "lift")
    val presence by animateFloatAsState(if (ticked == false) 0.42f else 1f, label = "presence")
    val shape = RoundedCornerShape(20.dp)
    val state = stringResource(
        when (ticked) {
            null -> if (shown) R.string.theme_state_locked else R.string.theme_state_other
            true -> R.string.theme_state_ticked
            false -> R.string.theme_state_off
        }
    )
    Column(
        modifier
            .graphicsLayer {
                translationY = -6.dp.toPx() * lift
                val s = 0.96f + 0.04f * lift
                scaleX = s
                scaleY = s
            }
            .clickable(role = if (ticked == null) Role.RadioButton else Role.Checkbox, onClick = onClick)
            .semantics { stateDescription = state },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .graphicsLayer { alpha = presence }
                .shadow(if (lifted) 14.dp else 4.dp, shape, ambientColor = palette.glow, spotColor = palette.accentDeep)
                .clip(shape)
                .border(if (shown) 3.dp else 1.dp, if (shown) current.accent else Color.White.copy(alpha = 0.6f), shape),
        ) {
            AeroBackground(Modifier.fillMaxSize(), palette = palette, animate = false) {
                Text(
                    palette.kanji,
                    modifier = Modifier.align(Alignment.Center),
                    style = TextStyle(
                        color = palette.ink,
                        fontSize = if (palette.kanji.length > 1) 26.sp else 38.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = palette.headings,
                        shadow = titleShadow(palette),
                    ),
                )
                if (ticked != null) Tick(ticked, palette, Modifier.align(Alignment.TopEnd).padding(7.dp))
            }
        }
        Text(
            palette.name,
            modifier = Modifier.padding(top = 6.dp),
            style = TextStyle(
                color = current.ink.copy(alpha = if (ticked == false) 0.55f else 1f),
                fontSize = 13.sp,
                fontWeight = if (shown) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
        Text(palette.reading, style = TextStyle(color = current.inkSoft, fontSize = 11.sp))
    }
}

/** A little glossy bead in the tile's corner: a check when the theme is in the shuffle, empty when it's out. */
@Composable
private fun Tick(ticked: Boolean, palette: AeroPalette, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .then(
                if (ticked) {
                    Modifier.drawBehind { drawOrb(palette.pass, palette.passDeep) }
                } else {
                    Modifier.background(Color.White.copy(alpha = 0.55f)).border(1.5.dp, Color.White, CircleShape)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (ticked) {
            Canvas(Modifier.size(11.dp)) {
                val w = size.width
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.08f, w * 0.55f)
                    lineTo(w * 0.4f, w * 0.85f)
                    lineTo(w * 0.94f, w * 0.18f)
                }
                drawPath(path, Color.White, style = Stroke(width = w * 0.2f, cap = StrokeCap.Round))
            }
        }
    }
}

/** A padlock drawn in lines: the shackle closed when [locked], swung up and open otherwise. */
@Composable
private fun Padlock(locked: Boolean, color: Color, modifier: Modifier = Modifier) {
    val open by animateFloatAsState(if (locked) 0f else 1f, label = "shackle")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.13f
        val bodyTop = h * 0.46f
        // The shackle: an arch whose right leg lifts out of the body when open.
        val left = w * 0.27f
        val right = w * 0.73f
        val lift = h * 0.16f * open
        val archTop = h * 0.08f - lift
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(left, archTop),
            size = Size(right - left, (right - left)),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val legTop = archTop + (right - left) / 2
        drawLine(color, Offset(left, legTop), Offset(left, bodyTop + stroke), stroke, StrokeCap.Round)
        drawLine(color, Offset(right, legTop), Offset(right, bodyTop - lift * 1.2f + if (open > 0.5f) 0f else stroke), stroke, StrokeCap.Round)
        // The body, with a keyhole.
        drawRoundRect(color, Offset(w * 0.12f, bodyTop), Size(w * 0.76f, h * 0.5f), CornerRadius(w * 0.12f))
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = w * 0.07f, center = Offset(w / 2, bodyTop + h * 0.22f))
    }
}
