package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipt_headers")
data class ReceiptHeaderEntity(
    @PrimaryKey val shipmentNo: String,
    val senderOrgnCode: String?,
    val receiverOrgnCode: String?,
    val senderDate: String?,
    val sentBy: String?,
    val senderTruckNo: String?,
    val rcNo: String?,
    val driverName: String?,
    val drivingLicenceNo: String?,
    val transportName: String?,
    val frieghtCharges: Double?,
    val uom: String?,
    val status: String?,
    val attribute1: String?,
    val attribute2: String?,
    val attribute3: String?,
    val receiptName: String?,
    val recevWeighType: String?
)

