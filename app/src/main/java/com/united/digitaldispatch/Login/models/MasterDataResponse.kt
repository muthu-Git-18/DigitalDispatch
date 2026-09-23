package com.united.digitaldispatch.Login.models

data class MasterDataResponse(
    val message: String?,
    val data: MasterData?
)

data class MasterData(
    val organizations: List<OrganizationDto>?,
    val transporters: List<TransporterDto>?,
    val users: List<UserDto>?
)

data class OrganizationDto(
    val organizationCode: String?,
    val organizationName: String?,
    val organizationType: String?,
    val organizationAddress5: String?,
    val organizationAddress6: String?,
    val insuranceValue: String?,
    val status: String?,
    val attribute1: String?,
    val attribute2: String?,
    val attribute3: String?,
    val attribute4: String?,
    val port: String?,
    val syncID: String?,
    val syncPassword: String?,
    val variety: String?
)

data class TransporterDto(
    val transporterCode: String?,
    val transporterName: String?,
    val destination: String?,
    val amount: String?,
    val status: String?
)

data class UserDto(
    val userId: String?,
    val empCode: String?,
    val userName: String?,
    val password: String?,
    val userErpName: String?,
    val designation: String?,
    val department: String?,
    val userRights: String?,
    val syncId: String?,
    val mobileNo: String?,
    val attribute1: String?,
    val attribute2: String?
)
