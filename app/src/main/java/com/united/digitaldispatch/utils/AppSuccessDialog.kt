package com.united.digitaldispatch.utils

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.united.digitaldispatch.R

/**
 * Reusable animated success dialog (green tick with ripples + a thin
 * countdown bar). It closes itself after [autoDismissMs], or when tapped,
 * and then calls [onDismiss] - so it is easy to chain a navigation to it.
 *
 *     AppSuccessDialog(
 *         activity = this,
 *         message = "Header Created Successfully",
 *         subMessage = "Shipment No : A01P40260929143704"
 *     ) {
 *         // runs after the dialog has closed
 *     }.show()
 *
 * Use other messages on other screens, e.g. "Receipt Saved Successfully".
 */
class AppSuccessDialog(
    private val activity: Activity,
    private val message: String,
    private val subMessage: String? = null,
    private val autoDismissMs: Long = 3500L,
    private val onDismiss: () -> Unit = {}
) {

    fun show() {

        if (activity.isFinishing || activity.isDestroyed) {
            onDismiss()
            return
        }

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_app_success)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.80f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
        }

        val card = dialog.findViewById<View>(R.id.successCard)
        val circle = dialog.findViewById<View>(R.id.successCircle)
        val check = dialog.findViewById<View>(R.id.successCheck)
        val ripple1 = dialog.findViewById<View>(R.id.successRipple1)
        val ripple2 = dialog.findViewById<View>(R.id.successRipple2)
        val timer = dialog.findViewById<View>(R.id.successTimer)
        val tvSub = dialog.findViewById<TextView>(R.id.tvSuccessSub)

        dialog.findViewById<TextView>(R.id.tvSuccessTitle).text = message

        if (subMessage.isNullOrBlank()) {
            tvSub.visibility = View.GONE
        } else {
            tvSub.text = subMessage
        }

        // initial state
        card.alpha = 0f
        card.scaleX = 0.82f
        card.scaleY = 0.82f

        circle.scaleX = 0f
        circle.scaleY = 0f

        check.scaleX = 0f
        check.scaleY = 0f
        check.rotation = -25f

        ripple1.alpha = 0f
        ripple2.alpha = 0f

        timer.pivotX = 0f

        var handled = false

        fun close() {
            if (handled) return
            handled = true
            card.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    try {
                        dialog.dismiss()
                    } catch (_: Exception) {
                    }
                    onDismiss()
                }
                .start()
        }

        // tap anywhere on the card to skip the wait
        card.setOnClickListener { close() }

        dialog.show()

        // card pops in
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(320)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        // green circle
        circle.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(150)
            .setDuration(420)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()

        // tick
        check.animate()
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setStartDelay(380)
            .setDuration(420)
            .setInterpolator(OvershootInterpolator(2.6f))
            .start()

        // two soft ripples going outwards
        fun ripple(view: View, delay: Long) {
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = 0.55f
            view.animate()
                .scaleX(1.65f)
                .scaleY(1.65f)
                .alpha(0f)
                .setStartDelay(delay)
                .setDuration(900)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        ripple(ripple1, 450)
        ripple(ripple2, 750)

        // countdown bar
        timer.animate()
            .scaleX(0f)
            .setStartDelay(0)
            .setDuration(autoDismissMs)
            .setInterpolator(LinearInterpolator())
            .start()

        card.postDelayed({ close() }, autoDismissMs)
    }
}