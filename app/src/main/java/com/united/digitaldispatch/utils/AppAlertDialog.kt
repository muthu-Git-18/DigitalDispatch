package com.united.digitaldispatch.utils

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.united.digitaldispatch.R

/**
 * Reusable animated alert / confirm dialog.
 *
 * One button (OK):
 *     AppAlertDialog(this, "Invalid Bale Number", "Please check the bale number").show()
 *
 * Two buttons (confirm):
 *     AppAlertDialog(
 *         activity = this,
 *         title = "Weight Loss",
 *         message = "Weight loss is more than 1.5%. Accept it?",
 *         type = AppAlertDialog.Type.WARNING,
 *         positiveText = "Accept",
 *         negativeText = "Cancel"
 *     ) { /* accepted */ }.show()
 */
class AppAlertDialog(
    private val activity: Activity,
    private val title: String,
    private val message: String,
    private val type: Type = Type.ERROR,
    private val positiveText: String = "OK",
    private val negativeText: String? = null,
    private val onNegative: () -> Unit = {},
    private val onPositive: () -> Unit = {}
) {

    enum class Type { ERROR, WARNING, INFO }

    fun show() {

        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_app_alert)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.86f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.65f)
        }

        val card = dialog.findViewById<View>(R.id.alertCard)
        val iconWrap = dialog.findViewById<View>(R.id.alertIconWrap)
        val icon = dialog.findViewById<View>(R.id.ivAlertIcon)
        val btnPositive = dialog.findViewById<TextView>(R.id.btnAlertPositive)
        val btnNegative = dialog.findViewById<TextView>(R.id.btnAlertNegative)

        dialog.findViewById<TextView>(R.id.tvAlertTitle).text = title
        dialog.findViewById<TextView>(R.id.tvAlertMessage).text = message

        iconWrap.setBackgroundResource(
            when (type) {
                Type.ERROR -> R.drawable.bg_delete_icon
                Type.WARNING -> R.drawable.bg_alert_warning
                Type.INFO -> R.drawable.bg_confirm_icon
            }
        )

        btnPositive.text = positiveText

        if (negativeText == null) {
            btnNegative.visibility = View.GONE
        } else {
            btnNegative.text = negativeText
        }

        // initial state
        card.alpha = 0f
        card.scaleX = 0.82f
        card.scaleY = 0.82f
        iconWrap.scaleX = 0f
        iconWrap.scaleY = 0f

        var handled = false

        fun close(after: () -> Unit) {
            if (handled) return
            handled = true
            card.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    dialog.dismiss()
                    after()
                }
                .start()
        }

        btnPositive.setOnClickListener { close { onPositive() } }
        btnNegative.setOnClickListener { close { onNegative() } }

        dialog.show()

        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(320)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        iconWrap.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(150)
            .setDuration(420)
            .setInterpolator(OvershootInterpolator(2.4f))
            .withEndAction { if (type != Type.INFO) shake(icon) }
            .start()
    }

    private fun shake(view: View) {

        val rotation = PropertyValuesHolder.ofKeyframe(
            "rotation",
            Keyframe.ofFloat(0f, 0f),
            Keyframe.ofFloat(0.2f, -12f),
            Keyframe.ofFloat(0.4f, 10f),
            Keyframe.ofFloat(0.6f, -7f),
            Keyframe.ofFloat(0.8f, 4f),
            Keyframe.ofFloat(1f, 0f)
        )

        ObjectAnimator.ofPropertyValuesHolder(view, rotation).apply {
            duration = 500
            start()
        }
    }
}