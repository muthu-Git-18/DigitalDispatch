package com.united.digitaldispatch.Receipt.models

import com.google.gson.annotations.SerializedName

data class ReceiptUploadResponse(
    @SerializedName("isSuccess") val isSuccess: Boolean = false,
    @SerializedName("receiptDetailsResponse") val receiptDetailsResponse: List<ReceiptUploadItem>? = null,
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("message") val message: String? = null
)

data class ReceiptUploadItem(
    @SerializedName("baleNumber") val baleNumber: String? = null,
    @SerializedName("processType") val processType: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("attempts") val attempts: Int? = null,
    @SerializedName("changeId") val changeId: Long? = null
)