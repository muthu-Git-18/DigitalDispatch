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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.united.digitaldispatch.R

/**
 * Read-only "information" dialog, same look as ConfirmDispatchDialog.
 * Reusable in every module (Dispatch, PSW Dispatch, Receipt ...):
 * pass your own title, header label/value and label-value rows.
 *
 * highlight -> row label -> colour for that row's value (e.g. sync status)
 */
class InfoDetailsDialog(
    private val activity: Activity,
    private val title: String,
    private val headerLabel: String,
    private val headerValue: String,
    private val rows: List<Pair<String, String>>,
    private val highlight: Map<String, Int> = emptyMap(),
    private val iconRes: Int = R.drawable.monitoring
) {

    fun show() {

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)                       // back / outside tap closes it
        dialog.setContentView(R.layout.dialog_info_details)

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
        val ivIcon = dialog.findViewById<ImageView>(R.id.ivIcon)
        val container = dialog.findViewById<LinearLayout>(R.id.rowsContainer)
        val btnClose = dialog.findViewById<TextView>(R.id.btnClose)

        dialog.findViewById<TextView>(R.id.tvTitle).text = title
        dialog.findViewById<TextView>(R.id.tvHeaderLabel).text = headerLabel
        dialog.findViewById<TextView>(R.id.tvHeaderValue).text = headerValue
        ivIcon.setImageResource(iconRes)

        // ---- build rows ----
        val rowViews = rows.mapIndexed { index, (label, value) ->

            val row = LayoutInflater.from(activity)
                .inflate(R.layout.item_confirm_row, container, false)

            row.findViewById<TextView>(R.id.tvLabel).text = label

            val tvValue = row.findViewById<TextView>(R.id.tvValue)
            tvValue.text = value.ifBlank { "-" }
            highlight[label]?.let { tvValue.setTextColor(it) }

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
        ivIcon.translationX = -70f

        btnClose.alpha = 0f

        // ---- close ----
        var closing = false

        fun close() {
            if (closing) return
            closing = true
            card.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { dialog.dismiss() }
                .start()
        }

        btnClose.setOnClickListener { close() }

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

        ivIcon.animate()
            .translationX(0f)
            .setStartDelay(320)
            .setDuration(520)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()

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

        btnClose.animate().alpha(1f).setStartDelay(afterRows + 60).setDuration(250).start()
    }
}

/** One-line helper for any module's "details" popup. */
fun Activity.showInfoDetails(
    title: String,
    headerLabel: String,
    headerValue: String,
    rows: List<Pair<String, String>>,
    highlight: Map<String, Int> = emptyMap()
) {
    InfoDetailsDialog(
        activity = this,
        title = title,
        headerLabel = headerLabel,
        headerValue = headerValue,
        rows = rows,
        highlight = highlight
    ).show()
}