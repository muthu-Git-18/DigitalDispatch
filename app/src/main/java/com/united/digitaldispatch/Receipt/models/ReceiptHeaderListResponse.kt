package com.united.digitaldispatch.Receipt.models

// GET Receipt/receipt-headers?recOrgnCode=P14
data class ReceiptHeaderListResponse(
    val statusCode: String? = null,
    val message: String? = null,
    val data: List<ReceiptHeaderDto>? = null
)

data class ReceiptHeaderDto(
    val shipmentNo: String,
    val senderOrgnCode: String? = null,
    val receiverOrgnCode: String? = null,
    val senderDate: String? = null,          // "10/24/2026 4:06:38 PM"
    val sentBy: String? = null,
    val senderTruckNo: String? = null,
    val rcNo: String? = null,
    val driverName: String? = null,
    val drivingLicenceNo: String? = null,
    val transportName: String? = null,
    val frieghtCharges: Double? = null,      // (sic) same spelling as the API
    val uom: String? = null,
    val status: String? = null,
    val attribute1: String? = null,
    val attribute2: String? = null,
    val attribute3: String? = null,
    val receiptName: String? = null,
    val recevWeighType: String? = null
)