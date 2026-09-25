package com.united.digitaldispatch.Login.models

data class StockResponse(
    val message: String?,
    val data: StockPage?
)

data class StockPage(
    val pageNumber: Int,
    val pageSize: Int,
    val totalRecords: Int,
    val totalPages: Int,
    val data: List<StockDto>
)

data class StockDto(
    val sno: Long,
    val gpiL_BALE_NUMBER: String?,
    val tB_LOT_NO: String?,
    val tbgR_NO: String?,
    val tB_GRADE: String?,
    val buyeR_GRADE: String?,
    val grade: String?,
    val markeD_WT: Double?,
    val curR_WT: Double?,
    val origN_LOCN: String?,
    val origN_ORGN_CODE: String?,
    val curR_LOCN: String?,
    val curR_ORGN_CODE: String?,
    val crop: String?,
    val variety: String?,
    val price: Double?,
    val subinventorY_CODE: String?,
    val createD_BY: String?,
    val createD_DATE: String?,
    val lasT_UPDATED_BY: String?,
    val weighT_FLAG: String?,
    val balE_CARD_TYPE: String?,
    val producT_TYPE: String?,
    val procesS_STATUS: String?,
    val batcH_NO: String?,
    val status: String?
)
