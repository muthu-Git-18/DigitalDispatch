
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

class PSWDispatchViewAdapter(
    private val onClick: (PSWDispatchDetailEntity) -> Unit,
    private val onLongClick: (PSWDispatchDetailEntity) -> Unit = {}
) : ListAdapter<
        PSWDispatchDetailEntity,
        PSWDispatchViewAdapter.VH
        >(Diff) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val sno: TextView = view.findViewById(R.id.txt_sno)
        val baleNo: TextView = view.findViewById(R.id.txt_bale_no)
        val markedWt: TextView = view.findViewById(R.id.txt_mrkd_weight)
        val dispatchWt: TextView = view.findViewById(R.id.txt_dispatch_wt)
        val grade: TextView = view.findViewById(R.id.txt_ship_no)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.psw_dispatch_view_row, parent, false)

        return VH(view)
    }

    override fun onBindViewHolder(
        holder: VH,
        position: Int
    ) {
        val bale = getItem(position)

        holder.sno.text = (position + 1).toString()
        holder.baleNo.text = bale.baleNumber
        holder.markedWt.text = bale.markedWt.toString()
        holder.dispatchWt.text = bale.dispatchWeight.toString()
        holder.grade.text = bale.grade.orEmpty()

        holder.itemView.setOnClickListener {
            onClick(bale)
        }

        holder.itemView.setOnLongClickListener {
            onLongClick(bale)
            true
        }
    }

    private object Diff :
        DiffUtil.ItemCallback<PSWDispatchDetailEntity>() {

        override fun areItemsTheSame(
            oldItem: PSWDispatchDetailEntity,
            newItem: PSWDispatchDetailEntity
        ): Boolean {
            return oldItem.shipmentNo == newItem.shipmentNo &&
                    oldItem.baleNumber == newItem.baleNumber
        }

        override fun areContentsTheSame(
            oldItem: PSWDispatchDetailEntity,
            newItem: PSWDispatchDetailEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}
