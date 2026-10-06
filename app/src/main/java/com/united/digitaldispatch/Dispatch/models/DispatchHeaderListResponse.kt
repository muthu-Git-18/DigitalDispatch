package com.united.digitaldispatch.Dispatch.models

data class DispatchHeaderListResponse(
    val statusCode: String? = null,
    val message: String? = null,
    val data: List<DispatchHeaderItem>? = null
)

data class DispatchHeaderItem(
    val shipmentNo: String? = null,
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
    val type_Of_Dispatch: String? = null,
    val weighmentType: String? = null
)