package com.united.digitaldispatch.Dispatch.models

// Dispatch/models/CompleteDispatch.kt
data class CompleteDispatchRequest(
    val shipmentNo: String,
    val totalBales: String
)

data class CompleteDispatchResponse(
    val statusCode: String? = null,
    val message: String? = null,
    val data: CompleteDispatchData? = null
)

data class CompleteDispatchData(
    val shipmentNo: String? = null,
    val result: String? = null,
    val isSuccess: Boolean = false,
    val mainShipmentNo: String? = null,
    val output: String? = null
)
