package com.united.digitaldispatch.Dispatch.models

data class CreateDispatchHeaderRequest(
    val shipmentNo: String,
    val senderOrgnCode: String,
    val receiverOrgnCode: String,
    val senderDate: String,
    val sentBy: String,
    val senderTruckNo: String,
    val rcNo: String,
    val driverName: String,
    val drivingLicenceNo: String,
    val transportName: String,
    val typeOfTruck: String,
    val frieghtCharges: Double,
    val uom: String,
    val status: String,
    val attribute2: String,
    val attribute3: String,
    val isWmsShipment: String,
    val attribute4: String,
    val weighmentType: String
)

data class CreateDispatchHeaderResponse(
    val statusCode: String?,
    val message: String?,
    val data: Any?
)