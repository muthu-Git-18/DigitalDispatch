package com.united.digitaldispatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Separate table so syncing PSW headers never wipes the normal Dispatch headers
@Entity(tableName = "psw_dispatch_header")
data class PswDispatchHeaderEntity(
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
) {
    /** Lets the PSW list screen reuse the existing DispatchHeaderAdapter. */
    fun toDispatchHeaderEntity() = DispatchHeaderEntity(
        shipmentNo, senderOrgnCode, receiverOrgnCode, senderDate, sentBy,
        senderTruckNo, rcNo, driverName, drivingLicenceNo, transportName,
        typeOfTruck, frieghtCharges, uom, status, attribute1, attribute2,
        attribute3, attribute4, isWmsShipment, weighmentType
    )
}