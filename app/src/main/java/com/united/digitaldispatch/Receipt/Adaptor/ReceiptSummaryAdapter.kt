package com.united.digitaldispatch.Receipt.Adaptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.entity.ReceiptDetailEntity
import java.util.Locale

data class ReceiptGradeRow(val grade: String, val bales: Int, val qty: Double)

object ReceiptSummaryCalc {
    fun byGrade(bales: List<ReceiptDetailEntity>): List<ReceiptGradeRow> =
        bales.groupBy { it.grade?.takeIf { g -> g.isNotBlank() } ?: "-" }
            .map { (grade, list) ->
                ReceiptGradeRow(
                    grade = grade,
                    bales = list.size,
                    qty = list.sumOf { it.markedWt }   // switch to receiptWeight here if you want received qty
                )
            }
            .sortedBy { it.grade }
}

class ReceiptSummaryAdapter :
    ListAdapter<ReceiptGradeRow, ReceiptSummaryAdapter.VH>(DIFF) {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val grade: TextView = v.findViewById(R.id.txt_grade)
        val bales: TextView = v.findViewById(R.id.txt_bales)
        val qty: TextView = v.findViewById(R.id.txt_qty)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_receipt_summary, parent, false)
    )

    override fun onBindViewHolder(h: VH, position: Int) {
        val row = getItem(position)
        h.grade.text = row.grade
        h.bales.text = row.bales.toString()
        h.qty.text = String.format(Locale.US, "%.2f", row.qty)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReceiptGradeRow>() {
            override fun areItemsTheSame(a: ReceiptGradeRow, b: ReceiptGradeRow) = a.grade == b.grade
            override fun areContentsTheSame(a: ReceiptGradeRow, b: ReceiptGradeRow) = a == b
        }
    }
}