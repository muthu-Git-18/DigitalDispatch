package com.united.digitaldispatch.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ring sweeps -> green disc pops -> tick draws itself -> ripples + confetti burst.
 * Call start() to play it.
 */
class CompleteCheckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFF81C784.toInt()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF2E9E5B.toInt()
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFF2E9E5B.toInt()
    }
    private val confettiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private class Particle(
        val angle: Float,
        val reach: Float,     // 0.5..0.95 of radius
        val size: Float,
        val color: Int,
        val spin: Float,
        val round: Boolean
    )

    private val particles: List<Particle> = run {
        val rnd = Random(7)
        val colors = intArrayOf(
            0xFF2E9E5B.toInt(), 0xFFDCB765.toInt(), 0xFF81C784.toInt(),
            0xFFFFC107.toInt(), 0xFF4DB6AC.toInt()
        )
        List(22) { i ->
            Particle(
                angle = (i * (2 * PI / 22) + rnd.nextDouble(-0.12, 0.12)).toFloat(),
                reach = 0.5f + rnd.nextFloat() * 0.45f,
                size = (3f + rnd.nextFloat() * 3f) * density,
                color = colors[rnd.nextInt(colors.size)],
                spin = rnd.nextFloat() * 2f - 1f,
                round = rnd.nextBoolean()
            )
        }
    }

    private var sweep = 0f
    private var fill = 0f
    private var check = 0f
    private var pulse1 = 0f
    private var pulse2 = 0f
    private var burst = 0f

    private val arcRect = RectF()
    private val checkPath = Path()
    private val drawnPath = Path()
    private val measure = PathMeasure()
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private var animator: AnimatorSet? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        radius = minOf(w, h) * 0.27f

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        checkPath.reset()
        checkPath.moveTo(cx - radius * 0.38f, cy + radius * 0.02f)
        checkPath.lineTo(cx - radius * 0.10f, cy + radius * 0.30f)
        checkPath.lineTo(cx + radius * 0.40f, cy - radius * 0.26f)
        measure.setPath(checkPath, false)
    }

    private fun anim(
        from: Float, to: Float, ms: Long,
        interpolator: android.animation.TimeInterpolator,
        onUpdate: (Float) -> Unit
    ) = ValueAnimator.ofFloat(from, to).apply {
        duration = ms
        this.interpolator = interpolator
        addUpdateListener { onUpdate(it.animatedValue as Float); invalidate() }
    }

    fun start(onFinished: (() -> Unit)? = null) {
        animator?.cancel()
        sweep = 0f; fill = 0f; check = 0f; pulse1 = 0f; pulse2 = 0f; burst = 0f
        invalidate()

        val ring = anim(0f, 360f, 550, DecelerateInterpolator()) { sweep = it }
        val disc = anim(0f, 1f, 300, OvershootInterpolator(2f)) { fill = it }
        val tick = anim(0f, 1f, 380, AccelerateDecelerateInterpolator()) { check = it }
        val p1 = anim(0f, 1f, 700, DecelerateInterpolator()) { pulse1 = it }
        val p2 = anim(0f, 1f, 700, DecelerateInterpolator()) { pulse2 = it }.apply { startDelay = 180 }
        val conf = anim(0f, 1f, 950, DecelerateInterpolator()) { burst = it }

        animator = AnimatorSet().apply {
            play(ring).before(disc)
            play(tick).after(disc)
            play(p1).with(tick)
            play(p2).with(tick)
            play(conf).with(tick)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { onFinished?.invoke() }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {

        // ripples
        if (pulse1 > 0f) {
            pulsePaint.alpha = ((1f - pulse1) * 160).toInt()
            canvas.drawCircle(cx, cy, radius * (1f + 0.30f * pulse1), pulsePaint)
        }
        if (pulse2 > 0f) {
            pulsePaint.alpha = ((1f - pulse2) * 110).toInt()
            canvas.drawCircle(cx, cy, radius * (1f + 0.30f * pulse2), pulsePaint)
        }

        // confetti burst
        if (burst > 0f && burst < 1f) {
            val eased = 1f - (1f - burst) * (1f - burst)
            particles.forEach { p ->
                val d = radius * 0.9f + radius * p.reach * eased
                val x = cx + cos(p.angle) * d
                val y = cy + sin(p.angle) * d + 14f * density * burst * burst   // slight fall

                confettiPaint.color = p.color
                confettiPaint.alpha = ((1f - burst) * 255).toInt()

                if (p.round) {
                    canvas.drawCircle(x, y, p.size * (1f - 0.4f * burst), confettiPaint)
                } else {
                    canvas.save()
                    canvas.rotate(p.spin * burst * 360f, x, y)
                    canvas.drawRect(x - p.size, y - p.size / 2f, x + p.size, y + p.size / 2f, confettiPaint)
                    canvas.restore()
                }
            }
        }

        // ring
        canvas.drawArc(arcRect, -90f, sweep, false, ringPaint)

        // disc
        if (fill > 0f) canvas.drawCircle(cx, cy, radius * 0.86f * fill, fillPaint)

        // tick
        if (check > 0f) {
            drawnPath.reset()
            measure.getSegment(0f, measure.length * check, drawnPath, true)
            canvas.drawPath(drawnPath, checkPaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}