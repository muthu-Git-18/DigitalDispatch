package com.united.digitaldispatch.Dispatch.Adaptor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.Dispatch.models.GradeSummary
import com.united.digitaldispatch.R
import java.util.Locale

class DispatchSummaryAdapter(
    private val showValue: Boolean          // true for TAP: Rate + Value columns
) : ListAdapter<GradeSummary, DispatchSummaryAdapter.VH>(Diff) {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val grade: TextView = v.findViewById(R.id.txt_grade)
        val bales: TextView = v.findViewById(R.id.txt_bales)
        val qty: TextView = v.findViewById(R.id.txt_qty)
        val rate: TextView = v.findViewById(R.id.txt_rate)
        val value: TextView = v.findViewById(R.id.txt_value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.dispatch_summary_row, parent, false)
        )

    override fun onBindViewHolder(h: VH, position: Int) {
        val g = getItem(position)

        h.grade.text = g.grade
        h.bales.text = g.bales.toString()
        h.qty.text = String.format(Locale.US, "%.2f", g.qty)

        val vis = if (showValue) View.VISIBLE else View.GONE
        h.rate.visibility = vis
        h.value.visibility = vis

        if (showValue) {
            h.rate.text = String.format(Locale.US, "%.2f", g.avgRate)
            h.value.text = String.format(Locale.US, "%.2f", g.value)
        }
    }

    private object Diff : DiffUtil.ItemCallback<GradeSummary>() {
        override fun areItemsTheSame(a: GradeSummary, b: GradeSummary) = a.grade == b.grade
        override fun areContentsTheSame(a: GradeSummary, b: GradeSummary) = a == b
    }
}
