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
 * Animated "are you sure you want to delete?" dialog.
 *
 * highlight -> the thing being deleted (e.g. the shipment number)
 * onConfirm -> called after the dialog has animated out
 */
class DeleteConfirmDialog(
    private val activity: Activity,
    private val title: String,
    private val highlight: String,
    private val message: String,
    private val confirmText: String = "Delete",
    private val onConfirm: () -> Unit
) {

    fun show() {

        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_delete_confirm)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.86f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.65f)
        }

        val card = dialog.findViewById<View>(R.id.cardRoot)
        val iconWrap = dialog.findViewById<View>(R.id.iconWrap)
        val ivIcon = dialog.findViewById<View>(R.id.ivDeleteIcon)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)
        val btnDelete = dialog.findViewById<TextView>(R.id.btnDelete)

        dialog.findViewById<TextView>(R.id.tvDeleteTitle).text = title
        dialog.findViewById<TextView>(R.id.tvDeleteHighlight).text = highlight
        dialog.findViewById<TextView>(R.id.tvDeleteMessage).text = message
        btnDelete.text = confirmText

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

        btnCancel.setOnClickListener { close { } }
        btnDelete.setOnClickListener { close { onConfirm() } }

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
            .withEndAction { shake(ivIcon) }
            .start()
    }

    /** quick "wobble" of the bin icon to draw attention */
    private fun shake(view: View) {

        val rotation = PropertyValuesHolder.ofKeyframe(
            "rotation",
            Keyframe.ofFloat(0f, 0f),
            Keyframe.ofFloat(0.2f, -14f),
            Keyframe.ofFloat(0.4f, 12f),
            Keyframe.ofFloat(0.6f, -9f),
            Keyframe.ofFloat(0.8f, 5f),
            Keyframe.ofFloat(1f, 0f)
        )

        ObjectAnimator.ofPropertyValuesHolder(view, rotation).apply {
            duration = 550
            start()
        }
    }
}