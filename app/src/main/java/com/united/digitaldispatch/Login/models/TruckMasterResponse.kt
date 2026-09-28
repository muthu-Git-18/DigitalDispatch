package com.united.digitaldispatch.Login.models

data class TruckMasterResponse(
    val message: String?,
    val data: List<TruckMasterDto>?
)

data class TruckMasterDto(
    val fromOrgn: String?,
    val toOrgn: String?,
    val truckType: String?,
    val freightValue: Double?
)
