package com.united.digitaldispatch.Login.models


data class ItemMasterResponse(
    val message: String?,
    val data: ItemMasterPage?
)

data class ItemMasterPage(
    val pageNumber: Int,
    val pageSize: Int,
    val totalRecords: Int,
    val totalPages: Int,
    val data: List<ItemMasterDto>
)

data class ItemMasterDto(
    val id: Int,
    val itemCode: String?,
    val itemCodeGrp: String?,
    val itemGrp: String?,
    val itemType: String?,
    val itemDescription: String?,
    val crop: String?,
    val variety: String?,
    val costCategory: String?,
    val orgnType: String?,
    val status: String?,
    val flag: String?,
    val attribute2: String?,
    val attribute3: String?
)
