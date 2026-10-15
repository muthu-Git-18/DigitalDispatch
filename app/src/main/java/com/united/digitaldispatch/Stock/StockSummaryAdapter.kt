package com.united.digitaldispatch.Stock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.R
import java.util.Locale

data class StockGradeSummary(
    val grade: String,
    val baleCount: Int,
    val quantity: Double
)

class StockSummaryAdapter :
    RecyclerView.Adapter<StockSummaryAdapter.SummaryViewHolder>() {

    private val items = mutableListOf<StockGradeSummary>()

    fun submitList(newItems: List<StockGradeSummary>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SummaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stock_summary, parent, false)

        return SummaryViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: SummaryViewHolder,
        position: Int
    ) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    class SummaryViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        private val sno: TextView =
            itemView.findViewById(R.id.txt_summary_sno)

        private val grade: TextView =
            itemView.findViewById(R.id.txt_summary_grade)

        private val bales: TextView =
            itemView.findViewById(R.id.txt_summary_bales)

        private val quantity: TextView =
            itemView.findViewById(R.id.txt_summary_quantity)

        fun bind(item: StockGradeSummary, position: Int) {
            sno.text = (position + 1).toString()
            grade.text = item.grade
            bales.text = item.baleCount.toString()
            quantity.text = String.format(
                Locale.getDefault(),
                "%.2f",
                item.quantity
            )
        }
    }
}
