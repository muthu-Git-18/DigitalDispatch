package com.united.digitaldispatch.utils

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.united.digitaldispatch.R

/**
 * Animated completion dialog, one for every module.
 *
 * title         -> heading ("Dispatch Completed", "Receipt Completed" ...)
 * message       -> one line under the heading
 * detail        -> optional green chip (shipment / receipt number)
 * buttonText    -> text of the green button
 * autoDismissMs -> > 0 closes it by itself and calls onDone
 * onDone        -> called after the dialog has animated out
 */
class CompletionDialog(
    private val activity: Activity,
    private val title: String,
    private val message: String,
    private val detail: String? = null,
    private val buttonText: String = "OK",
    private val autoDismissMs: Long = 0L,
    private val onDone: () -> Unit = {}
) {

    fun show() {

        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_complete)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.65f)
        }

        val card = dialog.findViewById<View>(R.id.completeCard)
        val check = dialog.findViewById<CompleteCheckView>(R.id.completeCheck)
        val txtTitle = dialog.findViewById<TextView>(R.id.txtCompleteTitle)
        val txtMessage = dialog.findViewById<TextView>(R.id.txtCompleteMessage)
        val txtDetail = dialog.findViewById<TextView>(R.id.txtCompleteDetail)
        val btn = dialog.findViewById<TextView>(R.id.txtCompleteOk)

        txtTitle.text = title
        txtMessage.text = message
        btn.text = buttonText

        val hasDetail = !detail.isNullOrBlank()
        if (hasDetail) {
            txtDetail.text = detail
            txtDetail.visibility = View.VISIBLE
        }

        // ---- initial state ----
        val slide = 420f
        card.alpha = 0f
        card.translationY = slide

        btn.scaleX = 0.85f
        btn.scaleY = 0.85f

        // ---- close (single use) ----
        var handled = false

        fun close() {
            if (handled) return
            handled = true
            card.animate()
                .translationY(slide * 0.6f)
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    if (dialog.isShowing) dialog.dismiss()
                    onDone()
                }
                .start()
        }

        btn.setOnClickListener { close() }

        dialog.show()

        // ---- step 1: card slides up with a soft bounce ----
        card.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(430)
            .setInterpolator(OvershootInterpolator(0.9f))
            .start()

        // ---- step 2: tick animation, then text rises line by line ----
        card.postDelayed({
            check.start {

                val lines = buildList {
                    add(txtTitle)
                    add(txtMessage)
                    if (hasDetail) add(txtDetail)
                }

                var delay = 0L
                lines.forEach { v ->
                    v.translationY = 26f
                    v.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(delay)
                        .setDuration(280)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    delay += 100L
                }

                // ---- step 3: button bounces in last ----
                btn.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(delay + 40)
                    .setDuration(380)
                    .setInterpolator(OvershootInterpolator(2.4f))
                    .start()

                if (autoDismissMs > 0) {
                    btn.postDelayed({ close() }, delay + 40 + 380 + autoDismissMs)
                }
            }
        }, 200)
    }
}