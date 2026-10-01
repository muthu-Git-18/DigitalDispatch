package com.united.digitaldispatch.utils

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.united.digitaldispatch.R

/**
 * Reusable animated loading dialog (spinning gold ring + pulsing truck +
 * animated dots). Use it anywhere a blocking "loading" is needed and just
 * change the message:
 *
 *     private val loader by lazy { AppLoader(this) }
 *
 *     loader.show("Dispatch Header Loading")
 *     loader.show("Receipt Header Loading")      // future screens
 *     loader.updateMessage("Almost done")        // optional
 *     loader.dismiss()
 *
 * The "..." after the message is added by the loader itself.
 */
class AppLoader(private val activity: Activity) {

    private var dialog: Dialog? = null

    private var ringAnimator: ObjectAnimator? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var dotsAnimator: ValueAnimator? = null

    private var tvMessage: TextView? = null
    private var baseMessage: String = ""
    private var lastDots = -1

    val isShowing: Boolean
        get() = dialog?.isShowing == true

    fun show(message: String) {

        if (activity.isFinishing || activity.isDestroyed) return

        if (isShowing) {
            updateMessage(message)
            return
        }

        baseMessage = message

        val d = Dialog(activity)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setCancelable(false)
        d.setCanceledOnTouchOutside(false)
        d.setContentView(R.layout.dialog_app_loader)

        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.72f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }

        val card = d.findViewById<View>(R.id.loaderCard)
        val ring = d.findViewById<View>(R.id.loaderRing)
        val truck = d.findViewById<View>(R.id.loaderTruck)
        tvMessage = d.findViewById(R.id.tvLoaderMessage)

        lastDots = -1
        renderMessage(0)

        // entrance
        card.alpha = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f

        dialog = d
        d.show()

        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        // spinning ring
        ringAnimator = ObjectAnimator.ofFloat(ring, "rotation", 0f, 360f).apply {
            duration = 1100
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // pulsing truck
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            truck,
            PropertyValuesHolder.ofFloat("scaleX", 0.88f, 1.08f),
            PropertyValuesHolder.ofFloat("scaleY", 0.88f, 1.08f)
        ).apply {
            duration = 700
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // "..." animation
        dotsAnimator = ValueAnimator.ofInt(0, 4).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val dots = (it.animatedValue as Int).coerceAtMost(3)
                if (dots != lastDots) {
                    lastDots = dots
                    renderMessage(dots)
                }
            }
            start()
        }
    }

    fun updateMessage(message: String) {
        baseMessage = message
        renderMessage(lastDots.coerceAtLeast(0))
    }

    fun dismiss() {

        ringAnimator?.cancel()
        pulseAnimator?.cancel()
        dotsAnimator?.cancel()

        ringAnimator = null
        pulseAnimator = null
        dotsAnimator = null

        try {
            dialog?.dismiss()
        } catch (_: Exception) {
            // window already gone
        }

        dialog = null
        tvMessage = null
    }

    /** Always lays out "message..." but hides the dots that aren't "on" yet, so the text never jitters. */
    private fun renderMessage(visibleDots: Int) {

        val full = "$baseMessage..."
        val span = SpannableString(full)

        val start = baseMessage.length + visibleDots

        if (start < full.length) {
            span.setSpan(
                ForegroundColorSpan(Color.TRANSPARENT),
                start,
                full.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        tvMessage?.text = span
    }
}