package com.orlikalabs.microreview

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The little figure that shows each of Move's exercises, side on (front on for the wall angel): poses given as joint
 * angles, turned into points by forward kinematics, eased from one pose to the next and held. Pure: drawn by
 * `ui/MoveFigure.kt`. The poses were authored with a preview script; units are a 100 × 60 box, the floor at y = 56.
 *
 * Angles, in degrees: 0 points down, 90 forward (+x, the way the figure faces), 180 up, -90 back. The torso goes from
 * the hip to the shoulders; the head tilts from the torso's line; each limb is its first segment's angle and the
 * second's turn from it; a foot points at its own angle, or square to the shin.
 */
object MoveFigures {
    const val FLOOR = 56f
    const val HEAD_R = 3.6f
    private const val TORSO = 18f
    private const val NECK = 5f
    private const val UPPER = 9.5f
    private const val FORE = 8.5f
    private const val THIGH = 12.5f
    private const val SHIN = 12f
    private const val FOOT = 4f

    /** What's drawn with the figure. */
    enum class Prop { NONE, MAT, CUSHION, DOOR, WALL_BACK, CHAIR, CHAIR_BACK, BAND, BAG_HANDS, BAG_NEAR }

    /**
     * [frames]: per pose, hip x, hip y, torso, head, near arm (2), far arm (2), near leg (2 + foot), far leg
     * (2 + foot); a NaN foot is square to the shin. [holds]: how long each pose is held, ms. [view]: x, y, width,
     * height to show, or the whole figure. [front]: seen from the front, the far side mirrored.
     */
    class Figure(
        val frames: List<FloatArray>,
        val holds: IntArray,
        val prop: Prop = Prop.NONE,
        val view: FloatArray? = null,
        val front: Boolean = false,
    ) {
        /** One pass through every pose and back to the first. */
        val cycleMs: Int get() = holds.sum() + frames.size * MOVE_MS
    }

    /** How long going from one pose to the next takes, ms. */
    const val MOVE_MS = 650

    /** The points of a pose. Near and far limbs: elbow, hand, knee, ankle, toe. */
    class Body(
        val hip: P, val shoulder: P, val head: P,
        val elbowNear: P, val handNear: P, val elbowFar: P, val handFar: P,
        val kneeNear: P, val ankleNear: P, val toeNear: P,
        val kneeFar: P, val ankleFar: P, val toeFar: P,
    ) {
        val points: List<P> get() = listOf(hip, shoulder, head, elbowNear, handNear, elbowFar, handFar, kneeNear, ankleNear, toeNear, kneeFar, ankleFar, toeFar)
    }

    data class P(val x: Float, val y: Float)

    private fun step(from: P, angle: Float, length: Float): P {
        val r = Math.toRadians(angle.toDouble())
        return P(from.x + (sin(r) * length).toFloat(), from.y + (cos(r) * length).toFloat())
    }

    /** Forward kinematics of one pose ([Figure.frames]' layout). */
    fun body(f: FloatArray, front: Boolean = false): Body {
        val hip = P(f[0], f[1])
        val t = f[2]
        val shoulder = step(hip, t, TORSO)
        val head = step(shoulder, t + f[3], NECK + HEAD_R)
        fun arm(a1: Float, a2: Float): Pair<P, P> {
            val elbow = step(shoulder, a1, UPPER)
            return elbow to step(elbow, a1 + a2, FORE)
        }
        fun leg(l1: Float, l2: Float, foot: Float): Triple<P, P, P> {
            val knee = step(hip, l1, THIGH)
            val ankle = step(knee, l1 + l2, SHIN)
            return Triple(knee, ankle, step(ankle, if (foot.isNaN()) l1 + l2 + 90f else foot, FOOT))
        }
        val (en, hn) = arm(f[4], f[5])
        var (ef, hf) = arm(f[6], f[7])
        val (kn, an, tn) = leg(f[8], f[9], f[10])
        var (kf, af, tf) = leg(f[11], f[12], f[13])
        if (front) {
            fun mirror(p: P) = P(2 * hip.x - p.x, p.y)
            ef = mirror(ef); hf = mirror(hf); kf = mirror(kf); af = mirror(af); tf = mirror(tf)
        }
        return Body(hip, shoulder, head, en, hn, ef, hf, kn, an, tn, kf, af, tf)
    }

    /** The pose at [ms] into the cycle: held, then eased into the next one. */
    fun at(figure: Figure, ms: Long): FloatArray {
        val n = figure.frames.size
        var left = Math.floorMod(ms, figure.cycleMs.toLong()).toInt()
        for (i in 0 until n) {
            if (left < figure.holds[i]) return figure.frames[i]
            left -= figure.holds[i]
            if (left < MOVE_MS) {
                val x = left / MOVE_MS.toFloat()
                val eased = (1 - cos(Math.PI * x).toFloat()) / 2
                return lerp(figure.frames[i], figure.frames[(i + 1) % n], eased)
            }
            left -= MOVE_MS
        }
        return figure.frames[0]
    }

    fun lerp(a: FloatArray, b: FloatArray, t: Float) = FloatArray(a.size) { i ->
        if (a[i].isNaN() || b[i].isNaN()) (if (t < 0.5f) a[i] else b[i]) else a[i] + (b[i] - a[i]) * t
    }

    /** The box to show: [Figure.view], or every pose with the floor, with a margin. x, y, width, height. */
    fun viewBox(figure: Figure): FloatArray {
        figure.view?.let { return it }
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = FLOOR
        for (frame in figure.frames) {
            for (p in body(frame, figure.front).points) {
                x0 = min(x0, p.x); x1 = max(x1, p.x); y0 = min(y0, p.y); y1 = max(y1, p.y)
            }
        }
        val margin = 6f
        return floatArrayOf(x0 - margin, y0 - HEAD_R - margin, x1 - x0 + 2 * margin, y1 - y0 + HEAD_R + 2 * margin)
    }

    fun of(move: MovePlan.Move): Figure? = ALL[move.id]

    val ALL: Map<String, Figure> = mapOf(
        "press_up" to Figure(listOf(floatArrayOf(38f, 52.5f, 90f, 0f, -120f, 140f, -118f, 140f, -90f, 0f, 0f, -91f, 0f, 0f), floatArrayOf(38f, 52.5f, 140f, -40f, 17f, 0f, 19f, 0f, -90f, 0f, 0f, -91f, 0f, 0f)), intArrayOf(700, 1000), Prop.MAT, null, front = false),
        "bird_dog" to Figure(listOf(floatArrayOf(36f, 43.5f, 108f, -18f, 0f, 0f, 2f, 0f, 0f, -90f, -90f, 2f, -92f, -90f), floatArrayOf(36f, 43.5f, 108f, -18f, 100f, 0f, 2f, 0f, 0f, -90f, -90f, -88f, 0f, -10f)), intArrayOf(800, 1800), Prop.MAT, null, front = false),
        "side_plank_knees" to Figure(listOf(floatArrayOf(40f, 54.5f, 104f, 0f, 0f, 90f, 0f, 90f, -72f, -22f, -90f, -72f, -22f, -90f), floatArrayOf(40f, 50.5f, 106f, 0f, 0f, 90f, 0f, 90f, -66f, -28f, -90f, -66f, -28f, -90f)), intArrayOf(800, 1800), Prop.MAT, null, front = false),
        "curl_up" to Figure(listOf(floatArrayOf(40f, 53f, 90f, 0f, -100f, 0f, -100f, 0f, -140f, 135f, -90f, -90f, 0f, 180f), floatArrayOf(40f, 53f, 98f, 0f, -104f, 0f, -104f, 0f, -140f, 135f, -90f, -90f, 0f, 180f)), intArrayOf(800, 1800), Prop.MAT, null, front = false),
        "bridge" to Figure(listOf(floatArrayOf(38f, 53f, 90f, 0f, -90f, 0f, -90f, 0f, -145f, 142f, -90f, -143f, 140f, -90f), floatArrayOf(39f, 45.5f, 62f, 28f, -118f, 0f, -118f, 0f, -100f, 108f, -90f, -98f, 106f, -90f)), intArrayOf(700, 1400), Prop.MAT, null, front = false),
        "hinge" to Figure(listOf(floatArrayOf(50f, 31.5f, 180f, 0f, 4f, 6f, -4f, 6f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN), floatArrayOf(45f, 33.5f, 128f, -12f, 2f, 0f, -2f, 0f, 14f, -20f, Float.NaN, 12f, -18f, Float.NaN)), intArrayOf(700, 1000), Prop.NONE, null, front = false),
        "chin_tuck" to Figure(listOf(floatArrayOf(50f, 31.5f, 180f, 20f, 4f, 6f, -4f, 6f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN), floatArrayOf(50f, 31.5f, 180f, -8f, 4f, 6f, -4f, 6f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN)), intArrayOf(800, 1400), Prop.NONE, floatArrayOf(30f, 0f, 40f, 34f), front = false),
        "scap_squeeze" to Figure(listOf(floatArrayOf(50f, 31.5f, 180f, 0f, 5f, 85f, 3f, 85f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN), floatArrayOf(50f, 31.5f, 182f, 0f, -28f, 112f, -30f, 112f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN)), intArrayOf(700, 1200), Prop.NONE, floatArrayOf(28f, 0f, 44f, 40f), front = false),
        "pec_stretch" to Figure(listOf(floatArrayOf(50f, 31.5f, 180f, 0f, -85f, -95f, 5f, 0f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN), floatArrayOf(55f, 32f, 176f, -4f, -118f, -62f, 8f, 0f, 22f, -14f, Float.NaN, -14f, 0f, Float.NaN)), intArrayOf(800, 1800), Prop.DOOR, null, front = false),
        "wall_angel" to Figure(listOf(floatArrayOf(50f, 31.5f, 180f, 0f, 120f, 60f, 120f, 60f, 8f, 0f, Float.NaN, 8f, 0f, Float.NaN), floatArrayOf(50f, 31.5f, 180f, 0f, 142f, 4f, 142f, 4f, 8f, 0f, Float.NaN, 8f, 0f, Float.NaN)), intArrayOf(900, 900), Prop.WALL_BACK, null, front = true),
        "hip_flexor" to Figure(listOf(floatArrayOf(47f, 43.5f, 180f, 0f, 4f, 6f, -4f, 6f, 85f, -85f, Float.NaN, -6f, -84f, -90f), floatArrayOf(51f, 44.5f, 182f, 0f, 4f, 6f, -4f, 6f, 72f, -72f, Float.NaN, -22f, -68f, -90f)), intArrayOf(800, 1800), Prop.CUSHION, null, front = false),
        "hip_abduction" to Figure(listOf(floatArrayOf(42f, 52.5f, 90f, 10f, 80f, 10f, 150f, -70f, -90f, 0f, 0f, -60f, -60f, -90f), floatArrayOf(42f, 52.5f, 90f, 10f, 80f, 10f, 150f, -70f, -110f, 0f, 0f, -60f, -60f, -90f)), intArrayOf(700, 900), Prop.MAT, null, front = false),
        "short_foot" to Figure(listOf(floatArrayOf(46f, 38f, 180f, 0f, 30f, 60f, 28f, 60f, 90f, -90f, 90f, 88f, -88f, 90f), floatArrayOf(46f, 38f, 180f, 0f, 30f, 60f, 28f, 60f, 90f, -90f, 82f, 88f, -88f, 90f)), intArrayOf(900, 1400), Prop.CHAIR, floatArrayOf(30f, 20f, 44f, 40f), front = false),
        "standing_extension" to Figure(listOf(floatArrayOf(50f, 31.5f, 180f, 0f, -30f, 70f, -32f, 70f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN), floatArrayOf(52f, 31.8f, 198f, 10f, -12f, 70f, -14f, 70f, -6f, 0f, Float.NaN, -8f, 0f, Float.NaN)), intArrayOf(700, 1200), Prop.NONE, null, front = false),
        "walk_break" to Figure(listOf(floatArrayOf(50f, 32.2f, 178f, 0f, -22f, 20f, 20f, 30f, 22f, -8f, Float.NaN, -20f, -14f, Float.NaN), floatArrayOf(50f, 32.2f, 178f, 0f, 20f, 30f, -22f, 20f, -20f, -14f, Float.NaN, 22f, -8f, Float.NaN)), intArrayOf(450, 450), Prop.NONE, null, front = false),
        "calf_raise" to Figure(listOf(floatArrayOf(50f, 31.5f, 180f, 0f, 4f, 6f, -4f, 6f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN), floatArrayOf(50f, 28.5f, 180f, 0f, 4f, 6f, -4f, 6f, 2f, 0f, 40f, -2f, 0f, 40f)), intArrayOf(700, 900), Prop.NONE, null, front = false),
        "standing_hip_flexor" to Figure(listOf(floatArrayOf(50f, 32.5f, 180f, 0f, 30f, 50f, 28f, 50f, 22f, -18f, Float.NaN, -22f, 0f, 40f), floatArrayOf(53f, 34f, 182f, 0f, 30f, 50f, 28f, 50f, 34f, -32f, Float.NaN, -30f, 0f, 40f)), intArrayOf(800, 1800), Prop.NONE, null, front = false),
        "side_plank" to Figure(listOf(floatArrayOf(38f, 54.5f, 104f, 0f, 0f, 90f, 0f, 90f, -82f, 0f, -90f, -83f, 0f, -90f), floatArrayOf(38f, 48.5f, 107f, 0f, 0f, 90f, 0f, 90f, -73f, 0f, -90f, -74f, 0f, -90f)), intArrayOf(800, 1800), Prop.MAT, null, front = false),
        "push_up" to Figure(listOf(floatArrayOf(38f, 44f, 104f, -14f, 2f, 0f, 4f, 0f, -76f, 0f, 10f, -77f, 0f, 10f), floatArrayOf(38f, 47f, 94f, -6f, -40f, 104f, -38f, 104f, -82f, 0f, 10f, -83f, 0f, 10f)), intArrayOf(600, 800), Prop.MAT, null, front = false),
        "box_squat" to Figure(listOf(floatArrayOf(50f, 31.5f, 180f, 0f, 4f, 6f, -4f, 6f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN), floatArrayOf(42f, 40.5f, 145f, -15f, 60f, 20f, 58f, 20f, 84f, -84f, Float.NaN, 82f, -82f, Float.NaN)), intArrayOf(700, 900), Prop.CHAIR_BACK, null, front = false),
        "split_squat" to Figure(listOf(floatArrayOf(50f, 32.5f, 180f, 0f, 4f, 6f, -4f, 6f, 22f, -18f, Float.NaN, -22f, 0f, 40f), floatArrayOf(49f, 41f, 180f, 0f, 4f, 6f, -4f, 6f, 70f, -72f, Float.NaN, -12f, -70f, 20f)), intArrayOf(700, 900), Prop.NONE, null, front = false),
        "row" to Figure(listOf(floatArrayOf(50f, 31.5f, 176f, 0f, 80f, 0f, -4f, 6f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN), floatArrayOf(50f, 31.5f, 180f, 0f, -28f, 112f, -4f, 6f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN)), intArrayOf(700, 900), Prop.BAND, null, front = false),
        "single_leg_bridge" to Figure(listOf(floatArrayOf(38f, 53f, 90f, 0f, -90f, 0f, -90f, 0f, -160f, 70f, Float.NaN, -143f, 140f, -90f), floatArrayOf(39f, 45.5f, 62f, 28f, -118f, 0f, -118f, 0f, -118f, 40f, Float.NaN, -98f, 106f, -90f)), intArrayOf(700, 1200), Prop.MAT, null, front = false),
        "loaded_hinge" to Figure(listOf(floatArrayOf(50f, 31.5f, 180f, 0f, 4f, 6f, -4f, 6f, 2f, 0f, Float.NaN, -2f, 0f, Float.NaN), floatArrayOf(45f, 33.5f, 128f, -12f, 2f, 0f, -2f, 0f, 14f, -20f, Float.NaN, 12f, -18f, Float.NaN)), intArrayOf(700, 1000), Prop.BAG_HANDS, null, front = false),
        "carry" to Figure(listOf(floatArrayOf(50f, 32.2f, 180f, 0f, 0f, 0f, 20f, 30f, 18f, -8f, Float.NaN, -16f, -12f, Float.NaN), floatArrayOf(50f, 32.2f, 180f, 0f, 0f, 0f, -20f, 20f, -16f, -12f, Float.NaN, 18f, -8f, Float.NaN)), intArrayOf(450, 450), Prop.BAG_NEAR, null, front = false),
    )
}
