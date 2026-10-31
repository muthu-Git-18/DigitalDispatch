package com.united.digitaldispatch.Dispatch.Adaptor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.R


class DispatchSummaryHeaderAdapter(
    private val showValue: Boolean
) : RecyclerView.Adapter<DispatchSummaryHeaderAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val rate: TextView = v.findViewById(R.id.hdr_rate)
        val value: TextView = v.findViewById(R.id.hdr_value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.dispatch_summary_header_row, parent, false)
        )

    override fun onBindViewHolder(h: VH, position: Int) {
        val vis = if (showValue) View.VISIBLE else View.GONE
        h.rate.visibility = vis
        h.value.visibility = vis
    }

    override fun getItemCount(): Int = 1
}