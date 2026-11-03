package com.united.digitaldispatch.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.BounceInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * Amber disc pops in -> "?" drops and bounces -> wiggles -> ring keeps pulsing.
 * Call start(); the pulse stops by itself when the view is detached.
 */
class ConfirmQuestionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFF5A623.toInt()
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x33F5A623
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFFF5A623.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private var disc = 0f      // 0..1 pop-in
    private var drop = 0f      // 0..1 question mark falling
    private var wiggle = 0f    // 0..1
    private var pulse = 0f     // 0..1 looping

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private var intro: AnimatorSet? = null
    private var pulseAnimator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        radius = minOf(w, h) * 0.30f
        textPaint.textSize = radius * 1.35f
    }

    private fun anim(
        ms: Long,
        interpolator: android.animation.TimeInterpolator,
        onUpdate: (Float) -> Unit
    ) = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = ms
        this.interpolator = interpolator
        addUpdateListener { onUpdate(it.animatedValue as Float); invalidate() }
    }

    fun start() {
        intro?.cancel()
        pulseAnimator?.cancel()
        disc = 0f; drop = 0f; wiggle = 0f; pulse = 0f
        invalidate()

        val discAnim = anim(380, OvershootInterpolator(2.2f)) { disc = it }
        val dropAnim = anim(650, BounceInterpolator()) { drop = it }
        val wiggleAnim = anim(700, LinearInterpolator()) { wiggle = it }

        intro = AnimatorSet().apply {
            playSequentially(discAnim, dropAnim, wiggleAnim)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { wiggle = 0f; invalidate() }
            })
            start()
        }

        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            startDelay = 500
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {

        // looping pulse ring
        if (disc >= 1f) {
            pulsePaint.alpha = ((1f - pulse) * 150).toInt()
            canvas.drawCircle(cx, cy, radius * (1.05f + 0.55f * pulse), pulsePaint)
        }

        // soft halo + disc
        if (disc > 0f) {
            canvas.drawCircle(cx, cy, radius * 1.22f * disc, haloPaint)
            canvas.drawCircle(cx, cy, radius * disc, discPaint)
        }

        // question mark: falls from above, then wiggles
        if (drop > 0f) {
            val fm = textPaint.fontMetrics
            val baseline = cy - (fm.ascent + fm.descent) / 2f
            val offsetY = -(1f - drop) * radius * 1.6f
            val angle = sin(wiggle * PI * 4).toFloat() * 14f * (1f - wiggle)

            textPaint.alpha = (255 * minOf(1f, drop * 2f)).toInt()

            canvas.save()
            canvas.rotate(angle, cx, cy)
            canvas.drawText("?", cx, baseline + offsetY, textPaint)
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        intro?.cancel()
        pulseAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}