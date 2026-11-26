package com.united.digitaldispatch.PSWReceipt.models

data class PswReceiptHeaderListResponse(
    val statusCode: String? = null,
    val message: String? = null,
    val data: List<PswReceiptHeaderDto>? = null
)

data class PswReceiptHeaderDto(
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
    val frieghtCharges: Double? = null,
    val uom: String? = null,
    val status: String? = null,
    val attribute1: String? = null,
    val attribute2: String? = null,
    val attribute3: String? = null,
    val receiptName: String? = null,
    val recevWeighType: String? = null
)