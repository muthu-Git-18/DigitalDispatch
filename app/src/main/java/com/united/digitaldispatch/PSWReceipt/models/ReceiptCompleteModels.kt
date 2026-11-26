package com.united.digitaldispatch.Receipt.models

data class ReceiptCompleteRequest(
    val shipmentNo: String,
    val senderOrgnCode: String,
    val receiverOrgnCode: String,
    val senderDate: String,
    val sentBy: String,
    val deviceRcvBalesCount: Int
)

data class ReceiptCompleteResponse(
    val statusCode: String? = null,
    val message: String? = null,
    val data: ReceiptCompleteData? = null
)

data class ReceiptCompleteData(
    val shipmentNo: String? = null,
    val result: String? = null,
    val isSuccess: Boolean = false,
    val output: String? = null
)