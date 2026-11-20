package com.orlikalabs.microreview.ui.aero

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The four themes side by side, each a little window on its scene; the chosen one lifted, ringed with light. */
@Composable
fun ThemePicker(selected: AeroPalette, onPick: (AeroPalette) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AeroThemes.ALL.forEach { palette ->
            ThemeTile(palette, palette.id == selected.id, { onPick(palette) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ThemeTile(palette: AeroPalette, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val current = LocalAero.current
    val lift by animateFloatAsState(if (selected) 1f else 0f, label = "lift")
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .graphicsLayer {
                translationY = -6.dp.toPx() * lift
                val s = 0.96f + 0.04f * lift
                scaleX = s
                scaleY = s
            }
            .clickable(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .shadow(if (selected) 14.dp else 4.dp, shape, ambientColor = palette.glow, spotColor = palette.accentDeep)
                .clip(shape)
                .border(if (selected) 3.dp else 1.dp, if (selected) current.accent else Color.White.copy(alpha = 0.6f), shape),
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
            }
        }
        Text(
            palette.name,
            modifier = Modifier.padding(top = 6.dp),
            style = TextStyle(color = current.ink, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center),
            maxLines = 2,
        )
        Text(palette.reading, style = TextStyle(color = current.inkSoft, fontSize = 11.sp))
    }
}
