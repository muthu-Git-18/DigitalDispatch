package com.united.digitaldispatch.Receipt.Adaptor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.united.digitaldispatch.R
import com.united.digitaldispatch.data.local.entity.ReceiptHeaderEntity
import java.text.SimpleDateFormat
import java.util.Locale

class ReceiptHeaderAdapter :
    ListAdapter<ReceiptHeaderEntity, ReceiptHeaderAdapter.VH>(DIFF) {

    private var selectedShipmentNo: String? = null

    fun getSelected(): ReceiptHeaderEntity? =
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
            .inflate(R.layout.item_receipt_header, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {

        private val tvShipment = view.findViewById<TextView>(R.id.tvShipment)
        private val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        private val tvRoute = view.findViewById<TextView>(R.id.tvRoute)
        private val tvTruckNo = view.findViewById<TextView>(R.id.tvTruckNo)
        private val tvDriver = view.findViewById<TextView>(R.id.tvDriver)
        private val tvRcNo = view.findViewById<TextView>(R.id.tvRcNo)
        private val tvDate = view.findViewById<TextView>(R.id.tvDate)
        private val tvFreight = view.findViewById<TextView>(R.id.tvFreight)
        private val tvWeighment = view.findViewById<TextView>(R.id.tvWeighment)
        private val checkWrap = view.findViewById<View>(R.id.checkWrap)
        private val ivCheck = view.findViewById<View>(R.id.ivCheck)

        fun bind(item: ReceiptHeaderEntity) {

            tvShipment.text = item.shipmentNo
            tvStatus.text = statusText(item.status)
            tvRoute.text = "${item.senderOrgnCode.orEmpty()}  \u2192  ${item.receiverOrgnCode.orEmpty()}"
            tvTruckNo.text = item.senderTruckNo.orDash()
            tvDriver.text = item.driverName.orDash()
            tvRcNo.text = item.rcNo.orDash()
            tvDate.text = dateOnly(item.senderDate)
            tvFreight.text = freightText(item)
            tvWeighment.text = when (item.recevWeighType) {
                "TWT" -> "20% Weighment"
                "HND" -> "100% Weighment"
                else -> "Not set"
            }

            val selected = item.shipmentNo == selectedShipmentNo

            itemView.isSelected = selected
            checkWrap.isSelected = selected
            ivCheck.alpha = if (selected) 1f else 0f
            ivCheck.scaleX = if (selected) 1f else 0.4f
            ivCheck.scaleY = if (selected) 1f else 0.4f

            itemView.setOnClickListener {
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

        private fun String?.orDash() = if (this.isNullOrBlank()) "-" else this

        private fun statusText(status: String?) = when (status?.uppercase()) {
            "INT" -> "IN TRANSIT"
            null, "" -> "-"
            else -> status.uppercase()
        }

        /** "10/24/2026 4:06:38 PM" -> "24-10-2026" */
        private fun dateOnly(value: String?): String {
            if (value.isNullOrBlank()) return "-"
            return try {
                val parsed = SimpleDateFormat("MM/dd/yyyy h:mm:ss a", Locale.US).parse(value.trim())
                SimpleDateFormat("dd-MM-yyyy", Locale.US).format(parsed!!)
            } catch (e: Exception) {
                value.trim().substringBefore(' ').substringBefore('T').ifBlank { "-" }
            }
        }

        private fun freightText(item: ReceiptHeaderEntity): String {
            val value = item.frieghtCharges ?: return "-"
            val number = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
            val unit = if (item.uom.equals("KG", ignoreCase = true)) "/Kg" else "/Truck"
            return "$number $unit"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReceiptHeaderEntity>() {
            override fun areItemsTheSame(a: ReceiptHeaderEntity, b: ReceiptHeaderEntity) =
                a.shipmentNo == b.shipmentNo

            override fun areContentsTheSame(a: ReceiptHeaderEntity, b: ReceiptHeaderEntity) = a == b
        }
    }
}