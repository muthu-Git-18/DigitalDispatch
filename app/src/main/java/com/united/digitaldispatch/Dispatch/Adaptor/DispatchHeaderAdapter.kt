package com.united.digitaldispatch.Dispatch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.entity.DispatchHeaderEntity

class DispatchHeaderAdapter(
    private val onDeleteClick: (DispatchHeaderEntity) -> Unit
) : ListAdapter<DispatchHeaderEntity, DispatchHeaderAdapter.VH>(DIFF) {

    private var selectedShipmentNo: String? = null

    fun getSelected(): DispatchHeaderEntity? =
        currentList.firstOrNull { it.shipmentNo == selectedShipmentNo }

    private fun select(shipmentNo: String) {

        if (selectedShipmentNo == shipmentNo) return

        val oldPos = currentList.indexOfFirst { it.shipmentNo == selectedShipmentNo }

        selectedShipmentNo = shipmentNo

        val newPos = currentList.indexOfFirst { it.shipmentNo == shipmentNo }

        if (oldPos >= 0) notifyItemChanged(oldPos)
        if (newPos >= 0) notifyItemChanged(newPos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dispatch_header, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {

        private val tvShipment = view.findViewById<TextView>(R.id.tvShipment)
        private val tvRoute = view.findViewById<TextView>(R.id.tvRoute)
        private val tvTruckNo = view.findViewById<TextView>(R.id.tvTruckNo)
        private val tvDate = view.findViewById<TextView>(R.id.tvDate)
        private val tvType = view.findViewById<TextView>(R.id.tvType)
        private val tvFreight = view.findViewById<TextView>(R.id.tvFreight)
        private val tvWeighment = view.findViewById<TextView>(R.id.tvWeighment)
        private val ivDelete = view.findViewById<View>(R.id.ivDelete)
        private val checkWrap = view.findViewById<View>(R.id.checkWrap)
        private val ivCheck = view.findViewById<View>(R.id.ivCheck)

        fun bind(item: DispatchHeaderEntity) {

            tvShipment.text = item.shipmentNo
            tvRoute.text = "${item.senderOrgnCode.orEmpty()}  \u2192  ${item.receiverOrgnCode.orEmpty()}"
            tvTruckNo.text = item.senderTruckNo.orDash()
            tvDate.text = dateOnly(item.senderDate)
            tvType.text = item.typeOfTruck.orDash()
            tvFreight.text = freightText(item)
            tvWeighment.text = when (item.weighmentType) {
                "TWT" -> "20% Weighment"
                "HND" -> "100% Weighment"
                else -> "-"
            }

            val selected = item.shipmentNo == selectedShipmentNo

            itemView.isSelected = selected
            checkWrap.isSelected = selected
            ivCheck.alpha = if (selected) 1f else 0f
            ivCheck.scaleX = if (selected) 1f else 0.4f
            ivCheck.scaleY = if (selected) 1f else 0.4f

            ivDelete.setOnClickListener {
                onDeleteClick(item)
            }

            itemView.setOnClickListener {

                // small press "bounce"
                itemView.animate()
                    .scaleX(0.97f).scaleY(0.97f)
                    .setDuration(90)
                    .withEndAction {
                        itemView.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(220)
                            .setInterpolator(OvershootInterpolator(2.5f))
                            .start()
                    }
                    .start()

                select(item.shipmentNo)
            }
        }

        private fun String?.orDash(): String =
            if (this.isNullOrBlank()) "-" else this

        /** "29-09-2026 15:08:32" (or ISO "...T...") -> "29-09-2026" */
        private fun dateOnly(value: String?): String {
            if (value.isNullOrBlank()) return "-"
            return value.trim()
                .substringBefore(' ')
                .substringBefore('T')
                .ifBlank { "-" }
        }

        private fun freightText(item: DispatchHeaderEntity): String {
            val value = item.frieghtCharges ?: return "-"
            val number =
                if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
            val unit = if (item.uom.equals("KG", ignoreCase = true)) "/Kg" else "/Truck"
            return "$number $unit"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DispatchHeaderEntity>() {
            override fun areItemsTheSame(
                oldItem: DispatchHeaderEntity,
                newItem: DispatchHeaderEntity
            ) = oldItem.shipmentNo == newItem.shipmentNo

            override fun areContentsTheSame(
                oldItem: DispatchHeaderEntity,
                newItem: DispatchHeaderEntity
            ) = oldItem == newItem
        }
    }
}