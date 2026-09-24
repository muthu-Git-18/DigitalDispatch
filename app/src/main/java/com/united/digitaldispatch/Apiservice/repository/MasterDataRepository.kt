package com.united.digitaldispatch.Apiservice.repository

import com.united.digitaldispatch.Apiservice.network.ApiService
import com.united.digitaldispatch.Login.models.MasterDataResponse
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.ItemMasterEntity
import com.united.digitaldispatch.data.local.entity.OrganizationEntity
import com.united.digitaldispatch.data.local.entity.TransporterEntity
import com.united.digitaldispatch.data.local.entity.UserMasterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



class MasterDataRepository(
    private val apiService: ApiService,
    private val database: AppDatabase


) {

    private companion object {
        const val ITEM_PAGE_SIZE = 200
    }

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

            // Sync item master also
            val itemResult = syncItemMaster()

            if (itemResult.isFailure) {
                return Result.failure(
                    itemResult.exceptionOrNull()
                        ?: Exception("Item master sync failed")
                )
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


    suspend fun syncItemMaster(): Result<Unit> {

        return try {

            var pageNumber = 1
            var totalPages = 1

            val allItems = mutableListOf<ItemMasterEntity>()

            do {

                val response = apiService.getItems(
                    pageNumber = pageNumber,
                    pageSize = ITEM_PAGE_SIZE
                )

                if (!response.isSuccessful) {
                    throw Exception("Item API failed: ${response.code()}")
                }

                val body = response.body()
                    ?: throw Exception("Item API returned empty response")

                val page = body.data
                    ?: throw Exception("Item API returned empty data")

                val items = page.data.map { item ->
                    ItemMasterEntity(
                        id = item.id,
                        itemCode = item.itemCode,
                        itemCodeGrp = item.itemCodeGrp,
                        itemGrp = item.itemGrp,
                        itemType = item.itemType,
                        itemDescription = item.itemDescription,
                        crop = item.crop,
                        variety = item.variety,
                        costCategory = item.costCategory,
                        orgnType = item.orgnType,
                        status = item.status,
                        flag = item.flag,
                        attribute2 = item.attribute2,
                        attribute3 = item.attribute3
                    )
                }

                allItems.addAll(items)

                totalPages = page.totalPages

                android.util.Log.d(
                    "MasterDataRepository",
                    "Item page $pageNumber/$totalPages downloaded: ${items.size}"
                )

                pageNumber++

            } while (pageNumber <= totalPages)

            // Only replace local data after ALL pages downloaded successfully
            withContext(Dispatchers.IO) {
                database.runInTransaction {
                    database.itemMasterDao().deleteAll()
                    database.itemMasterDao().insertAll(allItems)
                }
            }

            android.util.Log.d(
                "MasterDataRepository",
                "Item master sync completed: ${allItems.size} records"
            )

            Result.success(Unit)

        } catch (e: Exception) {

            android.util.Log.e(
                "MasterDataRepository",
                "Item master sync failed",
                e
            )

            Result.failure(e)
        }
    }
}