package com.united.digitaldispatch.Dispatch.models

/** POST Dispatch/Insert_DispatchBales */
data class DispatchUploadRequest(
    val records: List<DispatchUploadRecord>
)

data class DispatchUploadRecord(
    val shipmenT_NO: String,
    val detaiL_ID: String,
    val gpiL_BALE_NUMBER: String,
    val grade: String,
    val markeD_WT: Double,
    val dispatcH_WEIGHT: Double,
    val froM_SUBINVENTORY_CODE: String,
    val tO_SUBINVENTORY_CODE: String,
    val status: String,
    val headeR_STATUS: String,
    val createD_BY: String,
    val createD_DATE: String,
    val sendeR_ORGN_CODE: String,
    val receiveR_ORGN_CODE: String,
    val dispatcH_WEIGHMENT_STATUS: String,
    val processType: String,
    val devicE_ID: String,

    // NOT in the current API contract: the stored procedure asks for
    // @DISPATCH_WEIGHMENT_STATUS (Y/N). Sent so the backend can pick it up
    // once the field is added to the records model.
    //val dispatcH_WEIGHMENT_STATUS: String
)

data class DispatchUploadResponse(
    val isSuccess: Boolean = false,
    val dispatchDetailsResponse: List<DispatchUploadItem>? = null,
    val requestId: String? = null
)

data class DispatchUploadItem(
    val baleNumber: String? = null,
    val processType: String? = null,
    /** "Success" | "Duplicate" | "Failed" */
    val status: String? = null,
    val reason: String? = null,
    val attempts: Int = 0,
    val changeId: Long = 0
)

/** Result of one upload round. */
data class UploadSummary(
    val uploaded: Int,
    val failed: Int,
    val remaining: Int
)