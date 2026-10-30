package com.united.digitaldispatch.Dispatch.Adaptor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.R

class DispatchViewHeaderAdapter(
    private val showPurchaseDate: Boolean
) : RecyclerView.Adapter<DispatchViewHeaderAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val date: TextView = v.findViewById(R.id.txt_created_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.dispatch_view_header_row, parent, false)
        )

    override fun onBindViewHolder(h: VH, position: Int) {
        h.date.visibility = if (showPurchaseDate) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = 1
}