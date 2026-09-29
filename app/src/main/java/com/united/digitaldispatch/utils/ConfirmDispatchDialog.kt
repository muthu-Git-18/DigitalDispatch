package com.united.digitaldispatch.utils

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.united.digitaldispatch.R

/**
 * Animated confirmation dialog.
 *
 * rows        -> label / value pairs shown in the body
 * freightText -> highlighted freight line (e.g. "2.0 /Kg")
 * onConfirm   -> called after the dialog has animated out
 */
class ConfirmDispatchDialog(
    private val activity: Activity,
    private val shipmentNo: String,
    private val rows: List<Pair<String, String>>,
    private val freightText: String,
    private val onConfirm: () -> Unit
) {

    fun show() {

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setContentView(R.layout.dialog_confirm_dispatch)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.65f)
        }

        val card = dialog.findViewById<View>(R.id.cardRoot)
        val iconWrap = dialog.findViewById<View>(R.id.iconWrap)
        val ivTruck = dialog.findViewById<View>(R.id.ivTruck)
        val freightChip = dialog.findViewById<View>(R.id.freightChip)
        val container = dialog.findViewById<LinearLayout>(R.id.rowsContainer)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)
        val btnConfirm = dialog.findViewById<TextView>(R.id.btnConfirm)

        dialog.findViewById<TextView>(R.id.tvShipment).text = shipmentNo
        dialog.findViewById<TextView>(R.id.tvFreight).text = freightText

        // ---- build rows ----
        val rowViews = rows.mapIndexed { index, (label, value) ->

            val row = LayoutInflater.from(activity)
                .inflate(R.layout.item_confirm_row, container, false)

            row.findViewById<TextView>(R.id.tvLabel).text = label
            row.findViewById<TextView>(R.id.tvValue).text = value.ifBlank { "-" }

            if (index == rows.lastIndex) {
                row.findViewById<View>(R.id.divider).visibility = View.GONE
            }

            row.alpha = 0f
            row.translationY = 28f

            container.addView(row)
            row
        }

        // ---- initial state for animation ----
        card.alpha = 0f
        card.scaleX = 0.82f
        card.scaleY = 0.82f

        iconWrap.scaleX = 0f
        iconWrap.scaleY = 0f
        ivTruck.translationX = -70f

        freightChip.alpha = 0f
        freightChip.scaleX = 0.85f
        freightChip.scaleY = 0.85f

        btnCancel.alpha = 0f
        btnConfirm.alpha = 0f

        // ---- click handling (single use) ----
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
        btnConfirm.setOnClickListener { close { onConfirm() } }

        dialog.show()

        // ---- animate in ----
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(340)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        iconWrap.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(180)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()

        // truck "drives in"
        ivTruck.animate()
            .translationX(0f)
            .setStartDelay(320)
            .setDuration(520)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()

        // rows slide up one after another
        rowViews.forEachIndexed { i, row ->
            row.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(260L + i * 55L)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        val afterRows = 260L + rowViews.size * 55L

        freightChip.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(afterRows)
            .setDuration(320)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()

        btnCancel.animate().alpha(1f).setStartDelay(afterRows + 80).setDuration(250).start()
        btnConfirm.animate().alpha(1f).setStartDelay(afterRows + 140).setDuration(250).start()
    }
}