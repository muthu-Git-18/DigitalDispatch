package com.united.digitaldispatch.PSWDispatch.Adaptor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.entity.PSWDispatchDetailEntity
import java.util.Locale

data class PSWGradeSummary(
    val grade: String,
    val bales: Int,
    val qty: Double
)

class PSWDispatchSummaryAdapter :
    ListAdapter<PSWGradeSummary, PSWDispatchSummaryAdapter.VH>(Diff) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val grade: TextView = view.findViewById(R.id.txt_grade)
        val bales: TextView = view.findViewById(R.id.txt_bales)
        val qty: TextView = view.findViewById(R.id.txt_qty)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.psw_dispatch_summary_row, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        holder.grade.text = item.grade
        holder.bales.text = item.bales.toString()
        holder.qty.text = String.format(Locale.US, "%.2f", item.qty)
    }

    private object Diff : DiffUtil.ItemCallback<PSWGradeSummary>() {
        override fun areItemsTheSame(
            oldItem: PSWGradeSummary,
            newItem: PSWGradeSummary
        ) = oldItem.grade == newItem.grade

        override fun areContentsTheSame(
            oldItem: PSWGradeSummary,
            newItem: PSWGradeSummary
        ) = oldItem == newItem
    }
}
