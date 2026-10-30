package com.united.digitaldispatch.Dispatch.Adaptor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.Dispatch.models.BaleDetail
import com.united.digitaldispatch.R

class DispatchViewAdapter(
    private val onClick: (BaleDetail) -> Unit,
    private val onLongClick: (BaleDetail) -> Unit = {}
) : ListAdapter<BaleDetail, DispatchViewAdapter.VH>(Diff) {

    var showPurchaseDate = false

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val sno: TextView = v.findViewById(R.id.txt_sno)
        val baleNo: TextView = v.findViewById(R.id.txt_bale_no)
        val markedWt: TextView = v.findViewById(R.id.txt_mrkd_weight)
        val dispatchWt: TextView = v.findViewById(R.id.txt_dispatch_wt)
        val grade: TextView = v.findViewById(R.id.txt_ship_no)
        val date: TextView = v.findViewById(R.id.txt_created_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.dispatch_view_row, parent, false)
        )

    override fun onBindViewHolder(h: VH, position: Int) {
        val b = getItem(position)
        h.sno.text = (position + 1).toString()
        h.baleNo.text = b.gpilBaleNumber
        h.markedWt.text = b.markedWeight
        h.dispatchWt.text = b.dispatchWeight
        h.grade.text = b.grade
        h.date.visibility = if (showPurchaseDate) View.VISIBLE else View.GONE
        if (showPurchaseDate) h.date.text = b.purchaseDate
        h.itemView.setOnClickListener { onClick(b) }

        h.itemView.setOnLongClickListener {
            onLongClick(b)
            true
        }
    }

    private object Diff : DiffUtil.ItemCallback<BaleDetail>() {
        override fun areItemsTheSame(a: BaleDetail, b: BaleDetail) = a.detailId == b.detailId
        override fun areContentsTheSame(a: BaleDetail, b: BaleDetail) = a == b
    }
}