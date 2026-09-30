package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dispatch_header")
data class DispatchHeaderEntity(
    @PrimaryKey
    val shipmentNo: String,
    val senderOrgnCode: String? = null,
    val receiverOrgnCode: String? = null,
    val senderDate: String? = null,
    val sentBy: String? = null,
    val senderTruckNo: String? = null,
    val rcNo: String? = null,
    val driverName: String? = null,
    val drivingLicenceNo: String? = null,
    val transportName: String? = null,
    val typeOfTruck: String? = null,
    val frieghtCharges: Double? = null,
    val uom: String? = null,
    val status: String? = null,
    val attribute1: String? = null,
    val attribute2: String? = null,
    val attribute3: String? = null,
    val attribute4: String? = null,
    val isWmsShipment: String? = null,
    val weighmentType: String? = null
)