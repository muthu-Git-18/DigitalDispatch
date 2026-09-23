package com.united.digitaldispatch.Apiservice.repository

import com.united.digitaldispatch.Apiservice.network.ApiService
import com.united.digitaldispatch.Login.models.MasterDataResponse
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.OrganizationEntity
import com.united.digitaldispatch.data.local.entity.TransporterEntity
import com.united.digitaldispatch.data.local.entity.UserMasterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MasterDataRepository(
    private val apiService: ApiService,
    private val database: AppDatabase
) {

    suspend fun syncMasterData(): Result<MasterDataResponse> {
        return try {

            val response = apiService.getMasterData()

            if (!response.isSuccessful) {
                return Result.failure(
                    Exception("Master data API failed: ${response.code()}")
                )
            }

            val masterDataResponse = response.body()
                ?: return Result.failure(
                    Exception("Master data response is empty")
                )

            val data = masterDataResponse.data
                ?: return Result.failure(
                    Exception("Master data is empty")
                )

            val organizations = data.organizations.orEmpty().map { organization ->
                OrganizationEntity(
                    organizationCode = organization.organizationCode.orEmpty(),
                    organizationName = organization.organizationName,
                    organizationType = organization.organizationType,
                    organizationAddress5 = organization.organizationAddress5,
                    organizationAddress6 = organization.organizationAddress6,
                    insuranceValue = organization.insuranceValue,
                    status = organization.status,
                    attribute1 = organization.attribute1,
                    attribute2 = organization.attribute2,
                    attribute3 = organization.attribute3,
                    attribute4 = organization.attribute4,
                    port = organization.port,
                    syncID = organization.syncID,
                    syncPassword = organization.syncPassword,
                    variety = organization.variety
                )
            }

            val users = data.users.orEmpty().map { user ->
                UserMasterEntity(
                    userId = user.userId.orEmpty(),
                    empCode = user.empCode,
                    userName = user.userName,
                    password = user.password,
                    userErpName = user.userErpName,
                    designation = user.designation,
                    department = user.department,
                    userRights = user.userRights,
                    syncId = user.syncId,
                    mobileNo = user.mobileNo,
                    attribute1 = user.attribute1,
                    attribute2 = user.attribute2
                )
            }

            val transporters = data.transporters.orEmpty().map { transporter ->
                TransporterEntity(
                    transporterCode = transporter.transporterCode.orEmpty(),
                    transporterName = transporter.transporterName,
                    destination = transporter.destination,
                    amount = transporter.amount,
                    status = transporter.status
                )
            }

            withContext(Dispatchers.IO) {
                database.runInTransaction {
                    database.organizationDao().insertAll(organizations)
                    database.userDao().insertAll(users)
                    database.transporterDao().insertAll(transporters)
                }
            }
            Result.success(masterDataResponse)

        }catch (e: Exception) {

            android.util.Log.e(
                "MasterDataRepository",
                "Master data sync failed",
                e
            )

            Result.failure(e)
        }
    }
}