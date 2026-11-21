package com.orlikalabs.microreview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.orlikalabs.microreview.MoveFigures
import com.orlikalabs.microreview.MoveFigures.P
import com.orlikalabs.microreview.MoveFigures.Prop
import com.orlikalabs.microreview.MovePlan
import com.orlikalabs.microreview.ui.aero.LocalAero
import com.orlikalabs.microreview.ui.aero.LocalAeroMotion
import kotlin.math.min

/**
 * The exercise, shown by a glossy little figure going from pose to pose ([MoveFigures]), near limbs bright, far ones
 * faded. Still (on its main pose) when animations are off.
 */
@Composable
fun MoveFigure(move: MovePlan.Move, modifier: Modifier = Modifier) {
    val figure = remember(move.id) { MoveFigures.of(move) } ?: return
    val animate = LocalAeroMotion.current
    var ms by remember(move.id) { mutableLongStateOf(figure.holds[0].toLong() + MoveFigures.MOVE_MS + 10) }
    if (animate) {
        LaunchedEffect(move.id) {
            val start = withFrameMillis { it }
            while (true) withFrameMillis { ms = it - start }
        }
    }
    val p = LocalAero.current
    val view = remember(move.id) { MoveFigures.viewBox(figure) }
    Canvas(modifier) {
        val scale = min(size.width / view[2], size.height / view[3])
        val dx = (size.width - view[2] * scale) / 2 - view[0] * scale
        val dy = (size.height - view[3] * scale) / 2 - view[1] * scale
        val pose = MoveFigures.at(figure, if (animate) ms else figure.holds[0].toLong() + MoveFigures.MOVE_MS + 10)
        val body = MoveFigures.body(pose, figure.front)
        val first = MoveFigures.body(figure.frames[0], figure.front)
        val last = MoveFigures.body(figure.frames.last(), figure.front)
        withTransform({
            translate(dx, dy)
            scale(scale, scale, Offset.Zero)
        }) {
            val bright = p.accent
            val deep = p.accentDeep
            val ink = if (p.isDark) Color.White else p.ink
            // The floor: a glossy line and the figure's shadow on it.
            drawLine(ink.copy(alpha = 0.35f), Offset(view[0], MoveFigures.FLOOR), Offset(view[0] + view[2], MoveFigures.FLOOR), 0.6f)
            drawOval(
                Color.Black.copy(alpha = if (p.isDark) 0.35f else 0.12f),
                topLeft = Offset(body.hip.x - 14f, MoveFigures.FLOOR - 0.8f),
                size = Size(28f, 1.6f),
            )
            drawProp(figure.prop, body, first, last, ink, view)
            val far = bright.copy(alpha = 0.42f)
            limb(body.shoulder, body.elbowFar, 2.2f, far)
            limb(body.elbowFar, body.handFar, 2.0f, far)
            limb(body.hip, body.kneeFar, 2.6f, far)
            limb(body.kneeFar, body.ankleFar, 2.3f, far)
            limb(body.ankleFar, body.toeFar, 1.6f, far)
            // The torso, a gel capsule.
            drawLine(Brush.linearGradient(listOf(bright, deep), start(body.hip), start(body.shoulder)), start(body.hip), start(body.shoulder), 4.6f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.45f), lerp(body.hip, body.shoulder, 0.15f), lerp(body.shoulder, body.hip, 0.15f), 1.1f, StrokeCap.Round)
            // The head, an orb with its highlight.
            drawCircle(Brush.radialGradient(listOf(bright, deep), Offset(body.head.x - 1f, body.head.y - 1.2f), MoveFigures.HEAD_R * 1.6f), MoveFigures.HEAD_R, start(body.head))
            drawCircle(Color.White.copy(alpha = 0.6f), MoveFigures.HEAD_R * 0.38f, Offset(body.head.x - 1.1f, body.head.y - 1.3f))
            limb(body.hip, body.kneeNear, 2.9f, bright)
            limb(body.kneeNear, body.ankleNear, 2.6f, bright)
            limb(body.ankleNear, body.toeNear, 1.8f, bright)
            limb(body.shoulder, body.elbowNear, 2.5f, bright)
            limb(body.elbowNear, body.handNear, 2.2f, bright)
        }
    }
}

private fun start(p: P) = Offset(p.x, p.y)

private fun lerp(a: P, b: P, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** A limb: a round-capped stroke with a white glint along it. */
private fun DrawScope.limb(a: P, b: P, width: Float, color: Color) {
    drawLine(color, start(a), start(b), width, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = color.alpha * 0.35f), lerp(a, b, 0.2f), lerp(a, b, 0.75f), width * 0.25f, StrokeCap.Round)
}

private fun DrawScope.drawProp(prop: Prop, body: MoveFigures.Body, first: MoveFigures.Body, last: MoveFigures.Body, ink: Color, view: FloatArray) {
    val line = ink.copy(alpha = 0.5f)
    val fill = ink.copy(alpha = 0.12f)
    val floor = MoveFigures.FLOOR
    when (prop) {
        Prop.NONE -> Unit
        Prop.MAT -> drawRoundRect(fill, Offset(view[0] + 2f, floor - 1f), Size(view[2] - 4f, 1.6f), CornerRadius(0.8f))
        Prop.CUSHION -> drawRoundRect(fill.copy(alpha = 0.3f), Offset(first.kneeFar.x - 4f, floor - 2f), Size(8f, 2.2f), CornerRadius(1f))
        Prop.DOOR -> {
            val x = first.elbowNear.x - 1.5f
            drawLine(line, Offset(x, floor), Offset(x, view[1] + 2f), 1.6f)
        }
        Prop.WALL_BACK -> drawRoundRect(fill, Offset(body.hip.x - 20f, view[1] + 1f), Size(40f, floor - view[1] - 1f), CornerRadius(2f))
        Prop.CHAIR, Prop.CHAIR_BACK -> {
            // A stool under (or behind, for the squat) the seated hip.
            val seated = if (prop == Prop.CHAIR) body.hip else last.hip
            val seatY = seated.y + 2.2f
            drawRoundRect(line, Offset(seated.x - 7f, seatY), Size(10f, 1.6f), CornerRadius(0.8f))
            drawLine(line, Offset(seated.x - 6f, seatY), Offset(seated.x - 6f, floor), 1f)
            drawLine(line, Offset(seated.x + 2f, seatY), Offset(seated.x + 2f, floor), 1f)
        }
        Prop.BAND -> {
            val anchor = Offset(view[0] + view[2] - 2f, first.handNear.y)
            drawLine(Color(0xFFE53935).copy(alpha = 0.8f), start(body.handNear), anchor, 0.8f)
            drawCircle(line, 1.2f, anchor)
        }
        Prop.BAG_HANDS -> drawRoundRect(line, Offset(body.handNear.x - 3.5f, body.handNear.y - 1f), Size(7f, 5f), CornerRadius(1.2f))
        Prop.BAG_NEAR -> drawRoundRect(line, Offset(body.handNear.x - 3f, body.handNear.y), Size(6f, 7f), CornerRadius(1.2f))
    }
}
