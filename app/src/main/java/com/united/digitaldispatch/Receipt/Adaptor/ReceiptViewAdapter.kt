package com.united.digitaldispatch.Receipt

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.entity.ReceiptDetailEntity
import java.util.Locale

class ReceiptViewAdapter(
    private val onLongClick: (ReceiptDetailEntity) -> Unit
) : ListAdapter<ReceiptDetailEntity, ReceiptViewAdapter.VH>(DIFF) {

    inner class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val slNo: TextView = v.findViewById(R.id.txt_sl_no)
        val baleNo: TextView = v.findViewById(R.id.txt_bale_no)
        val status: TextView = v.findViewById(R.id.txt_status)
        val weight: TextView = v.findViewById(R.id.txt_received_weight)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_receipt_view, parent, false)
    )

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = getItem(position)
        h.slNo.text = (position + 1).toString()
        h.baleNo.text = item.baleNumber

        if (item.status == ReceiptDetailEntity.STATUS_RECEIVED) {
            h.status.text = item.status
            h.status.setTextColor(Color.parseColor("#2E7D32"))
            h.weight.text = item.receiptWeight?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        } else {
            h.status.text = "NOT RECEIVED"
            h.status.setTextColor(Color.parseColor("#C0392B"))
            h.weight.text = ""
        }

        h.itemView.setOnLongClickListener { onLongClick(item); true }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReceiptDetailEntity>() {
            override fun areItemsTheSame(a: ReceiptDetailEntity, b: ReceiptDetailEntity) = a.id == b.id
            override fun areContentsTheSame(a: ReceiptDetailEntity, b: ReceiptDetailEntity) = a == b
        }
    }
}