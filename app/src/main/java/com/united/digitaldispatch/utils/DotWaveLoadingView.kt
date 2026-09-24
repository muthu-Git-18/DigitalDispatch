package com.united.digitaldispatch.utils

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.united.digitaldispatch.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * A "dot wave" loading indicator: a row of dots pulsing in a traveling
 * wave, one after another -- the same visual language as typing
 * indicators in WhatsApp/Messenger/Slack. Pure Canvas + a single
 * ValueAnimator; no drawable asset, no external library. Drop it into
 * any layout like a ProgressBar and it animates itself while attached:
 *
 *   <com.united.digitaldispatch.utils.DotWaveLoadingView
 *       android:layout_width="wrap_content"
 *       android:layout_height="wrap_content"
 *       app:dotColor="@color/..." />
 *
 * or configure it from code via the public vars below before it's shown.
 */
class DotWaveLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var dotCount: Int = 3
        set(value) { field = value; requestLayout() }

    var dotColor: Int
        get() = paint.color
        set(value) { paint.color = value; invalidate() }

    var dotRadiusDp: Float = 6f
        set(value) { field = value; requestLayout() }

    var dotSpacingDp: Float = 10f
        set(value) { field = value; requestLayout() }

    /** Full wave cycle duration; lower = faster. */
    var cycleMillis: Long = 1100
        set(value) { field = value; animator.duration = value }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = cycleMillis
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.DotWaveLoadingView, 0, 0).apply {
            try {
                dotColor = getColor(R.styleable.DotWaveLoadingView_dotColor, 0xFF3F51B5.toInt())
                dotCount = getInt(R.styleable.DotWaveLoadingView_dotCount, dotCount)
                dotRadiusDp = getDimension(
                    R.styleable.DotWaveLoadingView_dotRadius,
                    dpToPx(dotRadiusDp)
                ) / resources.displayMetrics.density
            } finally {
                recycle()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dotRadiusPx = dpToPx(dotRadiusDp)
        val spacingPx = dpToPx(dotSpacingDp)
        val desiredWidth = (dotCount * 2 * dotRadiusPx + (dotCount - 1) * spacingPx).toInt()
        val desiredHeight = (dotRadiusPx * 2 * 1.4f).toInt() // headroom for the scale bump
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val dotRadiusPx = dpToPx(dotRadiusDp)
        val spacingPx = dpToPx(dotSpacingDp)
        val totalWidth = dotCount * 2 * dotRadiusPx + (dotCount - 1) * spacingPx
        var cx = (width - totalWidth) / 2f + dotRadiusPx
        val cy = height / 2f

        val phaseStep = 1f / dotCount
        for (i in 0 until dotCount) {
            var dotPhase = phase - i * phaseStep
            dotPhase -= Math.floor(dotPhase.toDouble()).toFloat() // wrap into [0,1)

            // Smooth pulse: mid -> peak -> mid -> trough -> mid across the cycle.
            val wave = (sin(dotPhase * 2 * PI.toFloat()) + 1f) / 2f
            val scale = 0.5f + 0.5f * wave

            paint.alpha = (130 + 125 * wave).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, dotRadiusPx * scale, paint)
            cx += dotRadiusPx * 2 + spacingPx
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}