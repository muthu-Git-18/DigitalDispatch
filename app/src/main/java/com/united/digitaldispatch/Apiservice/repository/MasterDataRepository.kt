package com.united.digitaldispatch.Apiservice.repository

import com.united.digitaldispatch.Apiservice.network.ApiService
import com.united.digitaldispatch.Login.models.MasterDataResponse
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.ItemMasterEntity
import com.united.digitaldispatch.data.local.entity.OrganizationEntity
import com.united.digitaldispatch.data.local.entity.StockEntity
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
        const val STOCK_PAGE_SIZE = 10000
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


    //Stock Master
    //api/Master/stock
    /**
     * Downloads stock in pages and replaces the local table once ALL pages
     * succeed. [onProgress], if given, is called after every page with
     * (percent, recordsDownloadedSoFar, totalRecords) -- percent is based
     * on the API's own totalRecords count (from the very first page), not
     * on page count, so it stays accurate even with a single huge page
     * (STOCK_PAGE_SIZE is 10000, so most syncs are just 1 page).
     *
     * NOTE: onProgress is invoked on whatever dispatcher the CALLER used
     * to call this suspend function (e.g. Dispatchers.IO if that's what
     * wraps the call) -- if you touch views in it, hop back to the main
     * thread yourself (runOnUiThread / Dispatchers.Main), same as
     * Dashboard.kt does.
     *
     * Returns the total number of records synced on success.
     */
    suspend fun syncStock(
        orgnType: String,
        orgnCode: String,
        onProgress: ((percent: Int, current: Int, total: Int) -> Unit)? = null
    ): Result<Int> {

        return try {

            var pageNumber = 1
            var totalPages = 1

            val allStock = mutableListOf<StockEntity>()

            do {

                val response = apiService.getStock(
                    orgnType = orgnType,
                    orgnCode = orgnCode,
                    pageNumber = pageNumber,
                    pageSize = STOCK_PAGE_SIZE
                )

                if (!response.isSuccessful) {
                    throw Exception(
                        "Stock API failed: ${response.code()}"
                    )
                }

                val body = response.body()
                    ?: throw Exception(
                        "Stock API returned empty response"
                    )

                val page = body.data
                    ?: throw Exception(
                        "Stock API returned empty data"
                    )

                val stockItems = page.data.map { stock ->

                    StockEntity(
                        sno = stock.sno,
                        gpiL_BALE_NUMBER = stock.gpiL_BALE_NUMBER,
                        tB_LOT_NO = stock.tB_LOT_NO,
                        tbgR_NO = stock.tbgR_NO,
                        tB_GRADE = stock.tB_GRADE,
                        buyeR_GRADE = stock.buyeR_GRADE,
                        grade = stock.grade,
                        markeD_WT = stock.markeD_WT,
                        curR_WT = stock.curR_WT,
                        origN_LOCN = stock.origN_LOCN,
                        origN_ORGN_CODE = stock.origN_ORGN_CODE,
                        curR_LOCN = stock.curR_LOCN,
                        curR_ORGN_CODE = stock.curR_ORGN_CODE,
                        crop = stock.crop,
                        variety = stock.variety,
                        price = stock.price,
                        subinventorY_CODE = stock.subinventorY_CODE,
                        createD_BY = stock.createD_BY,
                        createD_DATE = stock.createD_DATE,
                        lasT_UPDATED_BY = stock.lasT_UPDATED_BY,
                        weighT_FLAG = stock.weighT_FLAG,
                        balE_CARD_TYPE = stock.balE_CARD_TYPE,
                        producT_TYPE = stock.producT_TYPE,
                        procesS_STATUS = stock.procesS_STATUS,
                        batcH_NO = stock.batcH_NO,
                        status = stock.status
                    )
                }

                allStock.addAll(stockItems)

                totalPages = page.totalPages

                // Progress is based on records-downloaded / totalRecords * 100
                // (both come straight from the API's own page response),
                // which stays meaningful even when everything fits on one
                // page -- page-count-based progress would just jump 0->100.
                val percent = if (page.totalRecords > 0) {
                    ((allStock.size * 100) / page.totalRecords).coerceIn(0, 100)
                } else {
                    100
                }

                onProgress?.invoke(percent, allStock.size, page.totalRecords)

                android.util.Log.d(
                    "MasterDataRepository",
                    "Stock page $pageNumber/$totalPages downloaded: ${stockItems.size} ($percent%)"
                )

                pageNumber++

            } while (pageNumber <= totalPages)


            // Replace old stock only after ALL pages succeed
            withContext(Dispatchers.IO) {

                database.runInTransaction {

                    database.stockDao().deleteAll()

                    database.stockDao().insertAll(allStock)
                }
            }

            // Make sure the caller sees a clean 100% even if the last
            // page's own count fell slightly short due to rounding.
            onProgress?.invoke(100, allStock.size, allStock.size)

            android.util.Log.d(
                "MasterDataRepository",
                "Stock sync completed: ${allStock.size} records"
            )

            Result.success(allStock.size)

        } catch (e: Exception) {

            android.util.Log.e(
                "MasterDataRepository",
                "Stock sync failed",
                e
            )

            Result.failure(e)
        }
    }


}