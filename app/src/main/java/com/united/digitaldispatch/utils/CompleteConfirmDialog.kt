package com.united.digitaldispatch.utils

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.united.digitaldispatch.R

/**
 * "Are you sure you want to complete?" dialog, one for every module.
 *
 * title      -> "Complete Dispatch?"
 * message    -> one line of warning
 * detail     -> optional small line (e.g. "SHIPMENT  P27P4026...")
 * stats      -> optional chips, e.g. listOf("Bales" to "4", "Qty" to "300.0")
 * onConfirm  -> called after the dialog has animated out
 * onCancel   -> optional
 */
class CompleteConfirmDialog(
    private val activity: Activity,
    private val title: String,
    private val message: String,
    private val detail: String? = null,
    private val stats: List<Pair<String, String>> = emptyList(),
    private val confirmText: String = "Yes, Complete",
    private val cancelText: String = "Cancel",
    private val onCancel: () -> Unit = {},
    private val onConfirm: () -> Unit
) {

    fun show() {

        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_complete_confirm)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.65f)
        }

        val card = dialog.findViewById<View>(R.id.askCard)
        val icon = dialog.findViewById<ConfirmQuestionView>(R.id.askIcon)
        val txtTitle = dialog.findViewById<TextView>(R.id.txtAskTitle)
        val txtMessage = dialog.findViewById<TextView>(R.id.txtAskMessage)
        val txtDetail = dialog.findViewById<TextView>(R.id.txtAskDetail)
        val statsRow = dialog.findViewById<LinearLayout>(R.id.askStats)
        val btnNo = dialog.findViewById<TextView>(R.id.btnAskNo)
        val btnYes = dialog.findViewById<TextView>(R.id.btnAskYes)

        txtTitle.text = title
        txtMessage.text = message
        btnNo.text = cancelText
        btnYes.text = confirmText

        val hasDetail = !detail.isNullOrBlank()
        if (hasDetail) {
            txtDetail.text = detail
            txtDetail.visibility = View.VISIBLE
        }

        val hasStats = stats.isNotEmpty()
        if (hasStats) {
            statsRow.visibility = View.VISIBLE
            stats.forEach { (label, value) ->
                val chip = LayoutInflater.from(activity)
                    .inflate(R.layout.item_complete_stat, statsRow, false)
                chip.findViewById<TextView>(R.id.tvStatValue).text = value.ifBlank { "-" }
                chip.findViewById<TextView>(R.id.tvStatLabel).text = label
                statsRow.addView(chip)
            }
        }

        // ---- initial state: card hangs from its top edge, folded away ----
        card.cameraDistance = 9000f * activity.resources.displayMetrics.density
        card.pivotX = card.width / 2f
        card.post { card.pivotY = 0f; card.pivotX = card.width / 2f }
        card.pivotY = 0f
        card.rotationX = -90f
        card.alpha = 0f

        btnNo.translationX = -90f
        btnYes.translationX = 90f

        // ---- close (single use) ----
        var handled = false

        fun close(after: () -> Unit) {
            if (handled) return
            handled = true
            card.animate()
                .rotationX(-90f)
                .alpha(0f)
                .setDuration(240)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    if (dialog.isShowing) dialog.dismiss()
                    after()
                }
                .start()
        }

        btnNo.setOnClickListener { close { onCancel() } }
        btnYes.setOnClickListener { close { onConfirm() } }

        dialog.show()

        // ---- step 1: card swings down ----
        card.animate()
            .rotationX(0f)
            .alpha(1f)
            .setDuration(520)
            .setInterpolator(OvershootInterpolator(1.3f))
            .start()

        // ---- step 2: question mark animation ----
        card.postDelayed({ icon.start() }, 260)

        // ---- step 3: text rises, then chips, then buttons slide in ----
        var delay = 700L

        val lines = buildList {
            add(txtTitle); add(txtMessage)
            if (hasDetail) add(txtDetail)
        }
        lines.forEach { v ->
            v.translationY = 22f
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay).setDuration(260)
                .setInterpolator(DecelerateInterpolator()).start()
            delay += 90L
        }

        if (hasStats) {
            statsRow.scaleX = 0.9f
            statsRow.scaleY = 0.9f
            statsRow.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(delay).setDuration(320)
                .setInterpolator(OvershootInterpolator(1.8f)).start()
            delay += 110L
        }

        btnNo.animate().alpha(1f).translationX(0f)
            .setStartDelay(delay).setDuration(360)
            .setInterpolator(DecelerateInterpolator(1.6f)).start()

        btnYes.animate().alpha(1f).translationX(0f)
            .setStartDelay(delay + 60).setDuration(380)
            .setInterpolator(OvershootInterpolator(1.5f)).start()
    }
}