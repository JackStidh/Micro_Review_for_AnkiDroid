package com.orlikalabs.microreview.ui.aero

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The theme's scene behind [content]: the sky and its still parts drawn once per size into a bitmap, then, every
 * frame, what moves (petals, stars, bubbles, embers, the aurora, the flare). [animate] false: a still picture (theme
 * previews, or the system's animations turned off).
 */
@Composable
fun AeroBackground(
    modifier: Modifier = Modifier,
    palette: AeroPalette = LocalAero.current,
    animate: Boolean = LocalAeroMotion.current,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val time = rememberSceneTime(animate)
    Box(
        modifier.drawWithCache {
            val width = size.width.toInt().coerceAtLeast(1)
            val height = size.height.toInt().coerceAtLeast(1)
            val still = ImageBitmap(width, height)
            CanvasDrawScope().draw(this, layoutDirection, Canvas(still), size) { drawStillScene(palette) }
            val live = LiveScene(palette)
            onDrawBehind {
                drawImage(still)
                live.draw(this, time.value)
            }
        },
        content = content,
    )
}

/** Seconds since the scene appeared, a new value every frame while [animate]. */
@Composable
private fun rememberSceneTime(animate: Boolean): State<Float> = produceState(0f, animate) {
    if (!animate) return@produceState
    var start = -1L
    while (true) {
        androidx.compose.runtime.withFrameNanos { now ->
            if (start < 0) start = now
            value = (now - start) / 1_000_000_000f
        }
    }
}

/** A repeatable random number in 0..1 for [i] and [salt]: the scenes are the same at every frame. */
internal fun hash(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777215f
}

private fun Color.a(alpha: Float) = copy(alpha = alpha)

// The still part of each scene

private fun DrawScope.drawStillScene(p: AeroPalette) {
    drawRect(Brush.verticalGradient(p.sky))
    when (p.scene) {
        Scene.SAKURA -> sakuraStill(p)
        Scene.YORU -> yoruStill(p)
        Scene.MOCHI -> mochiStill(p)
        Scene.TORII -> toriiStill(p)
    }
}

private fun DrawScope.sakuraStill(p: AeroPalette) {
    val w = size.width
    val h = size.height
    // The sun, top left, and its haze.
    val sun = Offset(w * 0.16f, h * 0.07f)
    drawCircle(Brush.radialGradient(listOf(Color.White.a(0.95f), Color.White.a(0.35f), Color.Transparent), sun, w * 0.6f), w * 0.6f, sun)
    // White swooshes, the Aero signature.
    swoosh(h * 0.60f, h * 0.10f, w, h, Color.White.a(0.55f), 0.0f)
    swoosh(h * 0.67f, h * 0.05f, w, h, Color.White.a(0.40f), 0.35f)
    swoosh(h * 0.72f, h * 0.025f, w, h, p.accent.a(0.30f), 0.7f)
    // Glossy water at the bottom.
    val waterTop = h * 0.88f
    drawRect(
        Brush.verticalGradient(listOf(p.accent.a(0.25f), Color.White.a(0.55f), p.accent.a(0.35f)), waterTop, h),
        Offset(0f, waterTop),
        Size(w, h - waterTop),
    )
    drawLine(Color.White.a(0.85f), Offset(0f, waterTop), Offset(w, waterTop), 1.5f.dp.toPx())
    // A blossoming branch from the top right corner.
    branch(w, h)
}

private fun DrawScope.swoosh(y: Float, thickness: Float, w: Float, h: Float, color: Color, shift: Float) {
    val path = Path().apply {
        moveTo(-w * 0.1f, y + h * 0.04f * shift)
        cubicTo(w * 0.3f, y - h * 0.10f, w * 0.62f, y + h * 0.12f, w * 1.1f, y - h * 0.05f)
        lineTo(w * 1.1f, y - h * 0.05f + thickness)
        cubicTo(w * 0.62f, y + h * 0.12f + thickness * 0.4f, w * 0.3f, y - h * 0.10f + thickness, -w * 0.1f, y + h * 0.04f * shift + thickness * 0.7f)
        close()
    }
    drawPath(path, Brush.horizontalGradient(0f to Color.Transparent, 0.45f to color, 1f to Color.Transparent))
}

private fun DrawScope.branch(w: Float, h: Float) {
    val bark = Color(0xFF6B3E2E)
    val main = Path().apply {
        moveTo(w * 1.05f, h * 0.015f)
        cubicTo(w * 0.88f, h * 0.05f, w * 0.78f, h * 0.06f, w * 0.56f, h * 0.15f)
    }
    drawPath(main, bark.a(0.92f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
    val twig = Path().apply {
        moveTo(w * 0.82f, h * 0.055f)
        quadraticTo(w * 0.76f, h * 0.10f, w * 0.70f, h * 0.20f)
    }
    drawPath(twig, bark.a(0.9f), style = Stroke(4.5f.dp.toPx(), cap = StrokeCap.Round))
    val twig2 = Path().apply {
        moveTo(w * 0.68f, h * 0.105f)
        quadraticTo(w * 0.60f, h * 0.08f, w * 0.52f, h * 0.085f)
    }
    drawPath(twig2, bark.a(0.88f), style = Stroke(3.5f.dp.toPx(), cap = StrokeCap.Round))
    listOf(
        Offset(w * 0.95f, h * 0.035f) to 15f, Offset(w * 0.86f, h * 0.06f) to 12f, Offset(w * 0.76f, h * 0.085f) to 16f,
        Offset(w * 0.70f, h * 0.20f) to 13f, Offset(w * 0.73f, h * 0.14f) to 10f, Offset(w * 0.57f, h * 0.15f) to 14f,
        Offset(w * 0.52f, h * 0.085f) to 11f, Offset(w * 0.63f, h * 0.12f) to 9f, Offset(w * 0.90f, h * 0.085f) to 9f,
    ).forEachIndexed { i, (center, radius) -> blossom(center, radius.dp.toPx(), i * 17f) }
}

/** Five petals around a darker heart. */
private fun DrawScope.blossom(center: Offset, radius: Float, turn: Float) {
    val petal = Color(0xFFFFD6E4)
    val edge = Color(0xFFFF9DBF)
    for (k in 0 until 5) {
        rotate(turn + k * 72f, center) {
            drawOval(
                Brush.verticalGradient(listOf(Color.White, petal, edge), center.y - radius, center.y),
                Offset(center.x - radius * 0.42f, center.y - radius),
                Size(radius * 0.84f, radius),
            )
        }
    }
    drawCircle(Color(0xFFE0507E), radius * 0.2f, center)
    drawCircle(Color(0xFFFFE07A), radius * 0.09f, center)
}

private fun DrawScope.yoruStill(p: AeroPalette) {
    val w = size.width
    val h = size.height
    // The moon: a halo and a crescent.
    val moon = Offset(w * 0.78f, h * 0.12f)
    val r = 30.dp.toPx()
    drawCircle(Brush.radialGradient(listOf(p.glow.a(0.30f), p.glow.a(0.08f), Color.Transparent), moon, r * 5f), r * 5f, moon)
    val crescent = Path.combine(
        PathOperation.Difference,
        Path().apply { addOval(Rect(moon, r)) },
        Path().apply { addOval(Rect(moon + Offset(r * 0.45f, -r * 0.3f), r * 0.95f)) },
    )
    drawPath(crescent, Brush.linearGradient(listOf(Color(0xFFFFF8DC), Color(0xFFFFE6A0)), moon - Offset(r, r), moon + Offset(r, r)))
    // Mountains, far and near.
    drawPath(ridge(w, h, h * 0.70f, h * 0.10f, 7, 3), Color(0xFF16336E).a(0.85f))
    // A glass city: towers with lit windows, glossy edges.
    val ground = h * 0.80f
    for (i in 0 until 9) {
        val x = w * (0.05f + i * 0.105f) + hash(i, 2) * w * 0.03f
        val towerWidth = w * (0.05f + hash(i, 3) * 0.04f)
        val top = ground - h * (0.08f + hash(i, 4) * 0.16f)
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF5B8FE6).a(0.55f), Color(0xFF0B1A40).a(0.95f)), top, ground),
            Offset(x, top),
            Size(towerWidth, ground - top),
        )
        drawLine(Color.White.a(0.45f), Offset(x, top), Offset(x, ground), 1.dp.toPx())
        drawLine(p.glow.a(0.6f), Offset(x, top), Offset(x + towerWidth, top), 1.dp.toPx())
        var wy = top + 6.dp.toPx()
        var row = 0
        while (wy < ground - 6.dp.toPx()) {
            var wx = x + 4.dp.toPx()
            var col = 0
            while (wx < x + towerWidth - 6.dp.toPx()) {
                if (hash(i * 97 + row * 13 + col, 5) > 0.55f) {
                    drawRect(Color(0xFFFFE6A0).a(0.75f), Offset(wx, wy), Size(2.5f.dp.toPx(), 3.5f.dp.toPx()))
                }
                wx += 6.dp.toPx()
                col++
            }
            wy += 9.dp.toPx()
            row++
        }
    }
    drawPath(ridge(w, h, h * 0.80f, h * 0.05f, 5, 9), Color(0xFF060E2A))
    // The lake.
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF0C2459), Color(0xFF040A1E)), h * 0.84f, h),
        Offset(0f, h * 0.84f),
        Size(w, h * 0.16f),
    )
    drawLine(p.glow.a(0.5f), Offset(0f, h * 0.84f), Offset(w, h * 0.84f), 1.dp.toPx())
}

/** A soft mountain ridge from [base] up to [rise], with [peaks] peaks. */
private fun ridge(w: Float, h: Float, base: Float, rise: Float, peaks: Int, salt: Int): Path = Path().apply {
    moveTo(0f, h)
    lineTo(0f, base)
    val step = w / peaks
    for (i in 0 until peaks) {
        val x0 = i * step
        val peakX = x0 + step * (0.3f + hash(i, salt) * 0.4f)
        val peakY = base - rise * (0.45f + hash(i, salt + 1) * 0.55f)
        quadraticTo(peakX, peakY, x0 + step, base - rise * 0.15f * hash(i, salt + 2))
    }
    lineTo(w, h)
    close()
}

private fun DrawScope.mochiStill(p: AeroPalette) {
    val w = size.width
    val h = size.height
    val sun = Offset(w * 0.82f, h * 0.10f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3C4), Color(0xFFFFF3C4).a(0.3f), Color.Transparent), sun, w * 0.45f), w * 0.45f, sun)
    cloud(Offset(w * 0.20f, h * 0.10f), w * 0.11f)
    cloud(Offset(w * 0.62f, h * 0.20f), w * 0.08f)
    cloud(Offset(w * 0.90f, h * 0.30f), w * 0.07f)
    cloud(Offset(w * 0.12f, h * 0.34f), w * 0.06f)
    // Rolling hills, glossy on top.
    hill(h * 0.66f, h * 0.10f, Color(0xFFFFD2DF), 0.15f)
    hill(h * 0.74f, h * 0.08f, Color(0xFFFFF1E4), 0.55f)
    hill(h * 0.82f, h * 0.09f, Color(0xFFC3E5AC), 0.85f)
    // Dango on its skewer.
    val a = Offset(w * 0.08f, h * 0.96f)
    val b = Offset(w * 0.34f, h * 0.76f)
    drawLine(Color(0xFFC79A64), a, b, 4.dp.toPx(), cap = StrokeCap.Round)
    val ball = 17.dp.toPx()
    listOf(Color(0xFFB9DC9A), Color(0xFFFFFCF5), Color(0xFFFFB3C8)).forEachIndexed { i, color ->
        val t = 0.32f + i * 0.22f
        mochiBall(Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t), ball, color)
    }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, p.pass.a(0.25f)), h * 0.9f, h))
}

private fun DrawScope.cloud(center: Offset, size: Float) {
    val parts = listOf(Offset(-0.9f, 0.15f) to 0.55f, Offset(-0.35f, -0.2f) to 0.75f, Offset(0.35f, -0.1f) to 0.7f, Offset(0.9f, 0.18f) to 0.5f, Offset(0f, 0.25f) to 0.6f)
    parts.forEach { (o, r) -> drawCircle(Color(0xFFE9D9DE).a(0.45f), size * r, center + Offset(o.x * size, o.y * size + size * 0.12f)) }
    parts.forEach { (o, r) -> drawCircle(Color.White.a(0.95f), size * r, center + Offset(o.x * size, o.y * size)) }
}

private fun DrawScope.hill(top: Float, rise: Float, color: Color, shift: Float) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(0f, h)
        lineTo(0f, top + rise * 0.3f)
        cubicTo(w * (0.2f + shift * 0.2f), top - rise, w * (0.5f + shift * 0.1f), top + rise * 0.6f, w * 0.75f, top)
        cubicTo(w * 0.88f, top - rise * 0.4f, w * 0.96f, top + rise * 0.2f, w, top + rise * 0.1f)
        lineTo(w, h)
        close()
    }
    drawPath(path, color)
    drawPath(path, Brush.verticalGradient(listOf(Color.White.a(0.55f), Color.Transparent), top - rise, top + rise * 0.8f))
}

private fun DrawScope.mochiBall(center: Offset, radius: Float, color: Color) {
    drawCircle(Color.Black.a(0.08f), radius, center + Offset(0f, radius * 0.12f))
    drawCircle(Brush.radialGradient(listOf(Color.White, color, lerp(color, Color.Black, 0.12f)), center - Offset(radius * 0.35f, radius * 0.4f), radius * 1.4f), radius, center)
    drawOval(Color.White.a(0.85f), center - Offset(radius * 0.55f, radius * 0.7f), Size(radius * 0.6f, radius * 0.35f))
}

private fun DrawScope.toriiStill(p: AeroPalette) {
    val w = size.width
    val h = size.height
    // The red sun and its glow.
    val sun = Offset(w * 0.5f, h * 0.55f)
    val r = w * 0.36f
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF7A3A).a(0.55f), Color.Transparent), sun, r * 1.9f), r * 1.9f, sun)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE19A), Color(0xFFFF6A2A), Color(0xFFC0200C)), sun, r), r, sun)
    // Long cloud stripes across it.
    listOf(0.47f to 0.55f, 0.52f to 0.35f, 0.585f to 0.6f, 0.43f to 0.3f).forEachIndexed { i, (y, length) ->
        val left = w * (0.08f + hash(i, 21) * 0.35f)
        drawRoundRect(Color(0xFF2B0A06).a(0.55f), Offset(left, h * y), Size(w * length, 7.dp.toPx()), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
    }
    // Ink mountains in three layers, mist between them.
    drawPath(ridge(w, h, h * 0.70f, h * 0.12f, 4, 31), Color(0xFF5A1408).a(0.9f))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.a(0.07f), Color.Transparent), h * 0.66f, h * 0.76f))
    drawPath(ridge(w, h, h * 0.78f, h * 0.08f, 6, 37), Color(0xFF2E0805))
    drawPath(ridge(w, h, h * 0.86f, h * 0.04f, 3, 43), Color(0xFF110302))
    // A stone path to the gate.
    val path = Path().apply {
        moveTo(w * 0.44f, h * 0.86f)
        lineTo(w * 0.56f, h * 0.86f)
        lineTo(w * 0.72f, h)
        lineTo(w * 0.28f, h)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Color(0xFF8A5A40).a(0.25f), Color(0xFFE8C26A).a(0.18f)), h * 0.86f, h))
    torii(Offset(w * 0.5f, h * 0.86f), w * 0.66f, h * 0.36f, p)
}

/** A torii: two pillars, the lacquered top beam with upturned ends, the tie beam and the plaque. */
private fun DrawScope.torii(base: Offset, width: Float, height: Float, p: AeroPalette) {
    val vermilion = Color(0xFFD8321C)
    val shade = Color(0xFF8E1408)
    val lacquer = Color(0xFF140504)
    val top = base.y - height
    val pillarX = width * 0.30f
    val pillarBottom = width * 0.06f
    val pillarTop = width * 0.048f
    for (side in listOf(-1f, 1f)) {
        val cx = base.x + side * pillarX
        val pillar = Path().apply {
            moveTo(cx - pillarBottom / 2, base.y)
            lineTo(cx - pillarTop / 2, top + height * 0.08f)
            lineTo(cx + pillarTop / 2, top + height * 0.08f)
            lineTo(cx + pillarBottom / 2, base.y)
            close()
        }
        drawPath(pillar, Brush.horizontalGradient(listOf(Color(0xFFFF6A45), vermilion, shade), cx - pillarBottom / 2, cx + pillarBottom / 2))
        drawRect(lacquer, Offset(cx - pillarBottom * 0.62f, base.y - height * 0.05f), Size(pillarBottom * 1.24f, height * 0.05f))
    }
    // Nuki, the tie beam, through the pillars.
    val nukiY = top + height * 0.30f
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFF6A45), vermilion, shade), nukiY, nukiY + height * 0.055f), Offset(base.x - width * 0.40f, nukiY), Size(width * 0.80f, height * 0.055f))
    // Shimaki, under the top beam.
    val shimakiY = top + height * 0.075f
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFF6A45), vermilion, shade), shimakiY, shimakiY + height * 0.05f), Offset(base.x - width * 0.44f, shimakiY), Size(width * 0.88f, height * 0.05f))
    // Gakuzuka, the plaque between the beams.
    val plaque = Rect(Offset(base.x - width * 0.045f, shimakiY + height * 0.05f), Size(width * 0.09f, nukiY - shimakiY - height * 0.05f))
    drawRect(lacquer, plaque.topLeft, plaque.size)
    drawRect(p.gold, plaque.topLeft, plaque.size, style = Stroke(1.5f.dp.toPx()))
    // Kasagi, the lacquered top beam, its ends turned up.
    val half = width * 0.52f
    val beam = Path().apply {
        moveTo(base.x - half, top - height * 0.06f)
        quadraticTo(base.x, top + height * 0.04f, base.x + half, top - height * 0.06f)
        lineTo(base.x + half * 0.96f, top + height * 0.02f)
        quadraticTo(base.x, top + height * 0.10f, base.x - half * 0.96f, top + height * 0.02f)
        close()
    }
    drawPath(beam, Brush.verticalGradient(listOf(Color(0xFF3A1410), lacquer), top - height * 0.06f, top + height * 0.1f))
    val edge = Path().apply {
        moveTo(base.x - half, top - height * 0.06f)
        quadraticTo(base.x, top + height * 0.04f, base.x + half, top - height * 0.06f)
    }
    drawPath(edge, p.gold.a(0.9f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
}

// What moves, every frame

private class LiveScene(private val p: AeroPalette) {
    private val petal = Path().apply {
        moveTo(0f, -1f)
        cubicTo(0.7f, -0.9f, 0.75f, 0.3f, 0f, 1f)
        cubicTo(-0.75f, 0.3f, -0.7f, -0.9f, 0f, -1f)
        close()
    }
    private val aurora = Path()

    fun draw(scope: DrawScope, t: Float) = with(scope) {
        when (p.scene) {
            Scene.SAKURA -> sakura(t)
            Scene.YORU -> yoru(t)
            Scene.MOCHI -> mochi(t)
            Scene.TORII -> torii(t)
        }
    }

    private fun DrawScope.sakura(t: Float) {
        val w = size.width
        val h = size.height
        // The flare: ghosts along the line from the sun, breathing.
        val sun = Offset(w * 0.16f, h * 0.07f)
        val toward = Offset(w * 0.75f, h * 0.55f)
        val breath = 0.85f + 0.15f * sin(t * 0.8f)
        listOf(0.25f to 18f, 0.45f to 34f, 0.62f to 12f, 0.9f to 46f).forEachIndexed { i, (k, radius) ->
            val c = sun + (toward - sun) * k
            val color = if (i % 2 == 0) p.accent else p.fail
            drawCircle(color.a(0.10f * breath), radius.dp.toPx(), c)
            drawCircle(Color.White.a(0.18f * breath), radius.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        }
        // The sun's sparkle, slowly turning.
        rotate(t * 6f, sun) {
            drawLine(Color.White.a(0.8f), sun - Offset(70.dp.toPx(), 0f), sun + Offset(70.dp.toPx(), 0f), 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color.White.a(0.8f), sun - Offset(0f, 70.dp.toPx()), sun + Offset(0f, 70.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        }
        // Falling petals.
        for (i in 0 until 24) {
            val speed = 0.035f + hash(i, 1) * 0.04f
            val y = ((hash(i, 2) + t * speed) % 1.15f - 0.08f) * h
            val sway = sin(t * (0.6f + hash(i, 3)) + hash(i, 4) * 6.28f) * 28.dp.toPx()
            val x = ((hash(i, 5) * w + t * 12.dp.toPx() * (0.5f + hash(i, 6)) + sway) % (w + 40.dp.toPx()) + w + 40.dp.toPx()) % (w + 40.dp.toPx()) - 20.dp.toPx()
            val scale = (6f + hash(i, 7) * 7f).dp.toPx()
            val turn = t * (40f + hash(i, 8) * 120f) + hash(i, 9) * 360f
            val color = if (i % 3 == 0) Color(0xFFFFB7CF) else Color(0xFFFFD9E6)
            withTransform({
                translate(x, y)
                rotate(turn, Offset.Zero)
                scale(scale * (0.55f + 0.45f * abs(sin(t * 1.3f + i))), scale, Offset.Zero)
            }) {
                drawPath(petal, color.a(0.9f))
            }
        }
    }

    private fun DrawScope.yoru(t: Float) {
        val w = size.width
        val h = size.height
        // The aurora, two swaying ribbons.
        listOf(Triple(0.24f, p.pass, 0f), Triple(0.30f, Color(0xFF9F7BFF), 2f)).forEach { (yk, color, phase) ->
            aurora.reset()
            val segments = 24
            val base = h * yk
            val amp = h * 0.035f
            aurora.moveTo(0f, base)
            for (s in 0..segments) {
                val x = w * s / segments
                aurora.lineTo(x, base + sin(s * 0.5f + t * 0.35f + phase) * amp + sin(s * 0.17f - t * 0.2f) * amp)
            }
            for (s in segments downTo 0) {
                val x = w * s / segments
                aurora.lineTo(x, base + h * 0.09f + sin(s * 0.45f + t * 0.3f + phase) * amp * 0.6f)
            }
            aurora.close()
            drawPath(aurora, Brush.verticalGradient(listOf(color.a(0.0f), color.a(0.22f), color.a(0.0f)), base - amp, base + h * 0.1f))
        }
        // Stars, twinkling.
        for (i in 0 until 70) {
            val x = hash(i, 11) * w
            val y = hash(i, 12) * h * 0.62f
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (1f + hash(i, 13) * 2.5f) + hash(i, 14) * 6.28f))
            drawCircle(Color.White.a(twinkle * 0.9f), (0.7f + hash(i, 15) * 1.4f).dp.toPx(), Offset(x, y))
        }
        // A shooting star every nine seconds.
        val cycle = t % 9f
        if (cycle < 0.9f) {
            val k = cycle / 0.9f
            val start = Offset(w * (0.15f + hash((t / 9f).toInt(), 16) * 0.5f), h * 0.06f)
            val head = start + Offset(w * 0.35f * k, h * 0.14f * k)
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, Color.White.a(0.9f * (1 - k))), head - Offset(60.dp.toPx(), 24.dp.toPx()), head),
                head - Offset(60.dp.toPx(), 24.dp.toPx()),
                head,
                2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        // The moon's reflection, shimmering.
        val reflectionX = w * 0.78f
        for (k in 0 until 6) {
            val y = h * (0.86f + k * 0.022f)
            val half = (18f - k * 2f).dp.toPx() * (0.8f + 0.2f * sin(t * 2f + k))
            drawLine(Color(0xFFFFF0C0).a(0.35f - k * 0.04f), Offset(reflectionX - half, y), Offset(reflectionX + half, y), 2.dp.toPx(), cap = StrokeCap.Round)
        }
        // Fireflies.
        for (i in 0 until 9) {
            val x = (hash(i, 17) * w + sin(t * 0.3f + i) * 40.dp.toPx() + w) % w
            val y = h * (0.6f + hash(i, 18) * 0.3f) + cos(t * 0.45f + i * 1.7f) * 22.dp.toPx()
            val glow = 0.4f + 0.6f * max(0f, sin(t * (0.8f + hash(i, 19)) + i))
            val c = Offset(x, y)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFD9FF8A).a(0.55f * glow), Color.Transparent), c, 14.dp.toPx()), 14.dp.toPx(), c)
            drawCircle(Color(0xFFF2FFC2).a(glow), 1.8f.dp.toPx(), c)
        }
    }

    private fun DrawScope.mochi(t: Float) {
        val w = size.width
        val h = size.height
        // Soft bubbles rising.
        for (i in 0 until 20) {
            val speed = 0.02f + hash(i, 31) * 0.03f
            val y = (1.1f - (hash(i, 32) + t * speed) % 1.25f) * h
            val radius = (6f + hash(i, 33) * 18f).dp.toPx()
            val x = hash(i, 34) * w + sin(t * (0.5f + hash(i, 35)) + i) * 16.dp.toPx()
            val c = Offset(x, y)
            val tint = if (i % 3 == 0) p.accent else if (i % 3 == 1) p.pass else Color.White
            drawCircle(Brush.radialGradient(listOf(tint.a(0.05f), tint.a(0.22f)), c, radius), radius, c)
            drawCircle(Color.White.a(0.75f), radius, c, style = Stroke(1.dp.toPx()))
            drawOval(Color.White.a(0.85f), c - Offset(radius * 0.55f, radius * 0.6f), Size(radius * 0.5f, radius * 0.3f))
        }
        // Sparkles over the hills.
        for (i in 0 until 8) {
            val c = Offset(hash(i, 36) * w, h * (0.6f + hash(i, 37) * 0.25f))
            val k = max(0f, sin(t * 1.4f + i * 1.9f))
            val arm = 7.dp.toPx() * k
            drawLine(Color.White.a(k), c - Offset(arm, 0f), c + Offset(arm, 0f), 1.5f.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color.White.a(k), c - Offset(0f, arm), c + Offset(0f, arm), 1.5f.dp.toPx(), cap = StrokeCap.Round)
        }
    }

    private fun DrawScope.torii(t: Float) {
        val w = size.width
        val h = size.height
        // Embers rising and flickering.
        for (i in 0 until 28) {
            val speed = 0.03f + hash(i, 41) * 0.05f
            val y = (1.05f - (hash(i, 42) + t * speed) % 1.15f) * h
            val x = hash(i, 43) * w + sin(t * (0.9f + hash(i, 44)) + i) * 20.dp.toPx()
            val flicker = 0.4f + 0.6f * abs(sin(t * (3f + hash(i, 45) * 4f) + i))
            val c = Offset(x, y)
            val color = if (i % 2 == 0) Color(0xFFFFB347) else Color(0xFFFF6A2A)
            drawCircle(Brush.radialGradient(listOf(color.a(0.5f * flicker), Color.Transparent), c, 9.dp.toPx()), 9.dp.toPx(), c)
            drawCircle(Color(0xFFFFE7B0).a(flicker), (1f + hash(i, 46) * 1.4f).dp.toPx(), c)
        }
        // Gold leaf, drifting down and turning.
        for (i in 0 until 10) {
            val speed = 0.02f + hash(i, 47) * 0.025f
            val y = ((hash(i, 48) + t * speed) % 1.1f - 0.05f) * h
            val x = hash(i, 49) * w + sin(t * 0.7f + i) * 30.dp.toPx()
            val side = (4f + hash(i, 50) * 4f).dp.toPx()
            val turn = t * (60f + hash(i, 51) * 90f) + i * 40f
            withTransform({
                translate(x, y)
                rotate(turn, Offset.Zero)
                scale(abs(cos(t * 1.7f + i)) * 0.8f + 0.2f, 1f, Offset.Zero)
            }) {
                drawRect(Brush.linearGradient(listOf(Color(0xFFFFF0B8), p.gold, p.goldDeep), Offset(-side, -side), Offset(side, side)), Offset(-side / 2, -side / 2), Size(side, side))
            }
        }
    }
}
