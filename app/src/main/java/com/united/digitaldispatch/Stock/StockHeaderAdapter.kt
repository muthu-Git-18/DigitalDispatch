
package com.united.digitaldispatch.Stock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.R

class StockHeaderAdapter :
    RecyclerView.Adapter<StockHeaderAdapter.HeaderViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HeaderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stock_header, parent, false)

        return HeaderViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: HeaderViewHolder,
        position: Int
    ) = Unit

    override fun getItemCount(): Int = 1

    class HeaderViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView)
}
