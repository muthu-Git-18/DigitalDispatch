package com.united.digitaldispatch.Dispatch.models

/**
 * One scanned bale of a dispatch shipment.
 * Same fields the OLD app posted in "DispatchDetails" - ready to be mapped
 * to the details API / local table later.
 */
data class BaleDetail(
    val shipmentNo: String,
    val detailId: String,
    val gpilBaleNumber: String,
    val markedWeight: String,
    val dispatchWeight: String,
    val fromSubinventoryCode: String,
    val toSubinventoryCode: String,
    val status: String = "INT",
    val headerStatus: String = "Y",
    val grade: String,
    val createdBy: String,
    val createdDate: String,
    val senderOrgnCode: String,
    val receiverOrgnCode: String,
    val price: String,
    val tbLotNumber: String,
    val purchaseDate: String,
    /** "Y" when the bale was weighed (weight checkbox ticked), else "N" */
    val dispatchWeighmentStatus: String
)

/** Bale data read from the local stock table. */
data class BaleInfo(
    val baleNumber: String,
    val tbLotNumber: String,
    val markedWeight: String,
    /** classification grade (may be blank) */
    val grade: String,
    val buyerGrade: String,
    val subInventoryCode: String,
    val price: String,
    val status: String,
    val processStatus: String,
    val currentOrgnCode: String,
    val purchaseDate: String
)