package com.united.digitaldispatch.PSWReceipt.models

import com.google.gson.annotations.SerializedName

data class PswReceiptBaleListResponse(
    @SerializedName("statusCode") val statusCode: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: List<PswReceiptBaleDto>?
)

data class PswReceiptBaleDto(
    @SerializedName("SHIPMENT_NO") val shipmentNo: String?,
    @SerializedName("DETAIL_ID") val detailId: String?,
    @SerializedName("GPIL_BALE_NUMBER") val baleNumber: String?,
    @SerializedName("MARKED_WT") val markedWt: Double?,
    @SerializedName("DISPATCH_WEIGHT") val dispatchWeight: Double?,
    @SerializedName("RECEIPT_WEIGHT") val receiptWeight: Double?,
    @SerializedName("FROM_SUBINVENTORY_CODE") val fromSubinventoryCode: String?,
    @SerializedName("TO_SUBINVENTORY_CODE") val toSubinventoryCode: String?,
    @SerializedName("REMARKS") val remarks: String?,
    @SerializedName("LOADING_DATETIME") val loadingDatetime: String?,
    @SerializedName("UNLOADING_DATETIME") val unloadingDatetime: String?,
    @SerializedName("STATUS") val status: String?,
    @SerializedName("HEADER_STATUS") val headerStatus: String?,
    @SerializedName("CREATED_BY") val createdBy: String?,
    @SerializedName("CREATED_DATE") val createdDate: String?,
    @SerializedName("GRADE") val grade: String?,
    @SerializedName("SENDER_ORGN_CODE") val senderOrgnCode: String?,
    @SerializedName("RECEIVER_ORGN_CODE") val receiverOrgnCode: String?,
    @SerializedName("WEIGHT_STATUS") val weightStatus: String?,
    @SerializedName("RECEV_WEIGH_TYPE") val recevWeighType: String?,
    @SerializedName("DISPATCH_WEIGHMENT_STATUS") val dispatchWeighmentStatus: String?,
    @SerializedName("PROCESS_TYPE") val processType: String?,
    @SerializedName("DEVICE_ID") val deviceId: String?
)