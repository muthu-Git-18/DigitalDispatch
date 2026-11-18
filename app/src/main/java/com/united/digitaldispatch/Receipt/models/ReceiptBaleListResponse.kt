package com.united.digitaldispatch.Receipt.models

import com.google.gson.annotations.SerializedName

data class ReceiptBaleListResponse(
    @SerializedName("statusCode") val statusCode: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: List<ReceiptBaleDto>?
)

data class ReceiptBaleDto(
    @SerializedName("SHIPMENT_NO", alternate = ["shipmenT_NO"]) val shipmentNo: String?,
    @SerializedName("DETAIL_ID", alternate = ["detaiL_ID"]) val detailId: String?,
    @SerializedName("GPIL_BALE_NUMBER", alternate = ["gpiL_BALE_NUMBER"]) val baleNumber: String?,
    @SerializedName("MARKED_WT", alternate = ["markeD_WT"]) val markedWt: Double?,
    @SerializedName("DISPATCH_WEIGHT", alternate = ["dispatcH_WEIGHT"]) val dispatchWeight: Double?,
    @SerializedName("RECEIPT_WEIGHT", alternate = ["receipT_WEIGHT"]) val receiptWeight: Double?,
    @SerializedName("FROM_SUBINVENTORY_CODE", alternate = [". froM_SUBINVENTORY_CODE"]) val fromSubinventoryCode: String?,
    @SerializedName("STATUS", alternate = ["status"]) val status: String?,
    @SerializedName("HEADER_STATUS", alternate = ["headeR_STATUS"]) val headerStatus: String?,
    @SerializedName("WEIGHT_STATUS", alternate = ["weighT_STATUS"]) val weightStatus: String?,
    @SerializedName("GRADE", alternate = ["grade"]) val grade: String?
)