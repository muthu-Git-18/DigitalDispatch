package com.united.digitaldispatch.Receipt.models

data class ReceiptUploadRequest(val records: List<ReceiptUploadRecord>)

data class ReceiptUploadRecord(
    val shipmenT_NO: String,
    val detaiL_ID: String,
    val gpiL_BALE_NUMBER: String,
    val markeD_WT: Double,
    val dispatcH_WEIGHT: Double,
    val receipT_WEIGHT: Double?,
    val froM_SUBINVENTORY_CODE: String,
    val tO_SUBINVENTORY_CODE: String,
    val remarks: String,
    val loadinG_DATETIME: String,
    val unloadinG_DATETIME: String,
    val status: String,
    val headeR_STATUS: String,
    val createD_BY: String,
    val createD_DATE: String,
    val grade: String,
    val receiveR_ORGN_CODE: String,
    val sendeR_ORGN_CODE: String,
    val receV_WEIGH_TYPE: String,
    val weighT_STATUS: String,
    val dispatcH_WEIGHMENT_STATUS: String,
    val processType: String,
    val devicE_ID: String
)
