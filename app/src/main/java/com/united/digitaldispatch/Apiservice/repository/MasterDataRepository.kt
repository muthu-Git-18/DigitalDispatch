package com.united.digitaldispatch.Apiservice.repository

import com.united.digitaldispatch.Apiservice.network.ApiService
import com.united.digitaldispatch.Dispatch.models.BaleDetail
import com.united.digitaldispatch.Dispatch.models.BaleInfo
import com.united.digitaldispatch.Dispatch.models.CreateDispatchHeaderRequest
import com.united.digitaldispatch.Dispatch.models.CreateDispatchHeaderResponse
import com.united.digitaldispatch.Dispatch.models.DispatchHeaderItem
import com.united.digitaldispatch.Dispatch.models.DispatchUploadRecord
import com.united.digitaldispatch.Dispatch.models.DispatchUploadRequest
import com.united.digitaldispatch.Dispatch.models.UploadSummary
import com.united.digitaldispatch.Login.models.MasterDataResponse
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.DispatchHeaderEntity
import com.united.digitaldispatch.data.local.entity.ItemMasterEntity
import com.united.digitaldispatch.data.local.entity.OrganizationEntity
import com.united.digitaldispatch.data.local.entity.StockEntity
import com.united.digitaldispatch.data.local.entity.TransporterEntity
import com.united.digitaldispatch.data.local.entity.TruckMasterEntity
import com.united.digitaldispatch.data.local.entity.UserMasterEntity
import com.united.digitaldispatch.PSWDispatch.models.CreatePSWDispatchHeaderRequest
import com.united.digitaldispatch.PSWDispatch.models.CreatePSWDispatchHeaderResponse
import com.united.digitaldispatch.PSWDispatch.models.PSWDispatchHeaderItem
import com.united.digitaldispatch.data.local.entity.DispatchSyncState
import com.united.digitaldispatch.data.local.entity.PswDispatchHeaderEntity
import com.united.digitaldispatch.sync.DispatchChangeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale


class MasterDataRepository(
    private val apiService: ApiService,
    private val database: AppDatabase


) {

    private companion object {
        const val ITEM_PAGE_SIZE = 200
        const val STOCK_PAGE_SIZE = 10000

        const val MAX_UPLOAD_ATTEMPTS = 5
        const val UPLOAD_BATCH_SIZE = 50

        // TODO: confirm with the backend. Chosen by the same pattern as the
        // purchase app (P2 = purchase upload, W2 = weighment upload).
        const val DISPATCH_PROCESS_TYPE = "D2"

        // Return-sync endpoint, exactly as given (note: port 85).
        // If it is really on the same server as the other APIs, change the port here.
        const val DISPATCH_CHANGES_URL =
            "http://103.165.4.170:85/LorryHireAndroidApplicationApiPublish/api/Dispatch/sync-dispatch-changes"

        // one download at a time (screen open + SignalR + network-back can overlap)
        val pullMutex = Mutex()

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


    suspend fun syncTruckMaster(
        fromOrgn: String
    ): Result<Unit> {
        return try {

            val response = apiService.getTruckMaster(fromOrgn)

            if (!response.isSuccessful) {
                throw Exception(
                    "Truck master API failed: ${response.code()}"
                )
            }

            val body = response.body()
                ?: throw Exception("Truck master response is empty")

            val trucks = body.data.orEmpty().map { truck ->
                TruckMasterEntity(
                    fromOrgn = truck.fromOrgn,
                    toOrgn = truck.toOrgn,
                    truckType = truck.truckType,
                    freightValue = truck.freightValue
                )
            }

            withContext(Dispatchers.IO) {
                database.runInTransaction {

                    database.transporterDao().deleteTruckMaster()

                    database.transporterDao().insertTruckMaster(trucks)
                }
            }

            android.util.Log.d(
                "MasterDataRepository",
                "Truck master sync completed: ${trucks.size} records"
            )

            Result.success(Unit)

        } catch (e: Exception) {

            android.util.Log.e(
                "MasterDataRepository",
                "Truck master sync failed",
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


    suspend fun createDispatchHeader(
        request: CreateDispatchHeaderRequest
    ): Result<CreateDispatchHeaderResponse> {

        return try {

            val response = apiService.createDispatchHeader(request)

            if (!response.isSuccessful) {
                val serverMessage = parseServerMessage(response.errorBody()?.string())

                throw Exception(
                    serverMessage ?: "Create dispatch API failed: ${response.code()}"
                )
            }

            val body = response.body()
                ?: throw Exception(
                    "Create dispatch API returned empty response"
                )

            Result.success(body)

        } catch (e: Exception) {

            android.util.Log.e(
                "MasterDataRepository",
                "Create dispatch header failed",
                e
            )

            Result.failure(e)
        }
    }

    //Create PSW Dispatch Header - vikram
    suspend fun createPswDispatchHeader(
        request: CreatePSWDispatchHeaderRequest
    ): Result<CreatePSWDispatchHeaderResponse> {

        return try {

            val response =
                apiService.createPswDispatchHeader(request)

            if (!response.isSuccessful) {

                val serverMessage =
                    parseServerMessage(
                        response.errorBody()?.string()
                    )

                throw Exception(
                    serverMessage
                        ?: "Create PSW dispatch API failed: ${response.code()}"
                )
            }

            val body = response.body()
                ?: throw Exception(
                    "Create PSW dispatch API returned empty response"
                )

            Result.success(body)

        } catch (e: Exception) {

            android.util.Log.e(
                "MasterDataRepository",
                "Create PSW dispatch header failed",
                e
            )

            Result.failure(e)
        }
    }


    private fun parseServerMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        return try {
            JSONObject(raw).optString("message").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null   // body was not JSON
        }
    }
    suspend fun getTruckTypes(
        fromOrgn: String,
        toOrgn: String?
    ): List<TruckMasterEntity> {

        return withContext(Dispatchers.IO) {
            database
                .transporterDao()
                .getTruckMasterByOrganizations(
                    fromOrgn = fromOrgn,
                    toOrgn = toOrgn
                )
        }
    }

    suspend fun getTruckMasterOrganizations(): List<String> {
        return withContext(Dispatchers.IO) {
            database
                .transporterDao()
                .getDistinctToOrganizations()
        }
    }


    suspend fun syncDispatchHeaders(orgnCode: String): Result<Int> {
        return try {

            val response = apiService.getDispatchHeaders(orgnCode)

            if (!response.isSuccessful) {
                val serverMessage = parseServerMessage(response.errorBody()?.string())
                throw Exception(
                    serverMessage ?: "Dispatch headers API failed: ${response.code()}"
                )
            }

            val body = response.body()
                ?: throw Exception("Dispatch headers response is empty")

            val headers = body.data.orEmpty().mapNotNull { it.toEntity() }

            withContext(Dispatchers.IO) {
                database.runInTransaction {
                    database.dispatchHeaderDao().deleteAll()
                    database.dispatchHeaderDao().insertAll(headers)
                }
            }

            android.util.Log.d(
                "MasterDataRepository",
                "Dispatch headers sync completed: ${headers.size} records"
            )

            Result.success(headers.size)

        } catch (e: Exception) {

            android.util.Log.e("MasterDataRepository", "Dispatch headers sync failed", e)

            Result.failure(e)
        }
    }

    //SyncPSWDispatchHeaders - vikram
    suspend fun syncPswDispatchHeaders(
        orgnCode: String
    ): Result<Int> {

        return try {

            val response =
                apiService.getPswDispatchHeaders(orgnCode)

            if (!response.isSuccessful) {

                val serverMessage =
                    parseServerMessage(
                        response.errorBody()?.string()
                    )

                throw Exception(
                    serverMessage
                        ?: "PSW dispatch headers API failed: ${response.code()}"
                )
            }

            val body =
                response.body()
                    ?: throw Exception(
                        "PSW dispatch headers response is empty"
                    )

            val headers = body.data.orEmpty().mapNotNull { it.toEntity() }

            withContext(Dispatchers.IO) {
                database.runInTransaction {
                    database.pswDispatchHeaderDao().deleteAll()
                    database.pswDispatchHeaderDao().insertAll(headers)
                }
            }

            android.util.Log.d(
                "MasterDataRepository",
                "PSW dispatch headers received: ${headers.size}"
            )

            Result.success(headers.size)

        } catch (e: Exception) {

            android.util.Log.e(
                "MasterDataRepository",
                "PSW dispatch headers sync failed",
                e
            )

            Result.failure(e)
        }
    }

    /** Local headers, newest first. */
    suspend fun getLocalDispatchHeaders(): List<DispatchHeaderEntity> {
        return withContext(Dispatchers.IO) {
            database.dispatchHeaderDao()
                .getAll()
                .sortedByDescending { parseHeaderDate(it.senderDate) }
        }
    }

    suspend fun getLocalPswDispatchHeaders():
            List<PswDispatchHeaderEntity> {
        return withContext(Dispatchers.IO) {
            database.pswDispatchHeaderDao()
                .getAll()
                .sortedByDescending {
                    parseHeaderDate(it.senderDate)
                }
        }
    }

    /** Store a header we just created, so it shows in the list without a re-sync. */
    suspend fun saveCreatedHeaderLocally(request: CreateDispatchHeaderRequest) {
        withContext(Dispatchers.IO) {
            database.dispatchHeaderDao().insert(
                DispatchHeaderEntity(
                    shipmentNo = request.shipmentNo.orEmpty(),
                    senderOrgnCode = request.senderOrgnCode,
                    receiverOrgnCode = request.receiverOrgnCode,
                    senderDate = normalizeHeaderDate(request.senderDate),
                    sentBy = request.sentBy,
                    senderTruckNo = request.senderTruckNo,
                    rcNo = request.rcNo,
                    driverName = request.driverName,
                    drivingLicenceNo = request.drivingLicenceNo,
                    transportName = request.transportName,
                    typeOfTruck = request.typeOfTruck,
                    frieghtCharges = request.frieghtCharges,
                    uom = request.uom,
                    status = request.status,
                    attribute1 = request.frieghtCharges?.toString(),
                    attribute2 = request.attribute2,
                    attribute3 = request.attribute3,
                    attribute4 = request.attribute4,
                    isWmsShipment = request.isWmsShipment,
                    weighmentType = request.weighmentType
                )
            )
        }
    }

    suspend fun saveCreatedPswHeaderLocally(
        request: CreatePSWDispatchHeaderRequest
    ) {
        withContext(Dispatchers.IO) {
            database.pswDispatchHeaderDao().insert(
                PswDispatchHeaderEntity(
                    shipmentNo = request.shipmentNo,
                    senderOrgnCode = request.senderOrgnCode,
                    receiverOrgnCode = request.receiverOrgnCode,
                    senderDate = normalizeHeaderDate(
                        request.senderDate
                    ),
                    sentBy = request.sentBy,
                    senderTruckNo = request.senderTruckNo,
                    rcNo = request.rcNo,
                    driverName = request.driverName,
                    drivingLicenceNo = request.drivingLicenceNo,
                    transportName = request.transportName,
                    typeOfTruck = request.typeOfTruck,
                    frieghtCharges = request.frieghtCharges,
                    uom = request.uom,
                    status = request.status,
                    attribute1 = request.frieghtCharges.toString(),
                    attribute2 = request.attribute2,
                    attribute3 = request.attribute3,
                    attribute4 = request.attribute4,
                    isWmsShipment = request.isWmsShipment,
                    weighmentType = request.weighmentType
                )
            )
        }
    }

    private fun DispatchHeaderItem.toEntity(): DispatchHeaderEntity? {
        val no = shipmentNo?.trim()
        if (no.isNullOrEmpty()) return null

        return DispatchHeaderEntity(
            shipmentNo = no,
            senderOrgnCode = senderOrgnCode,
            receiverOrgnCode = receiverOrgnCode,
            senderDate = senderDate,
            sentBy = sentBy,
            senderTruckNo = senderTruckNo,
            rcNo = rcNo,
            driverName = driverName,
            drivingLicenceNo = drivingLicenceNo,
            transportName = transportName,
            typeOfTruck = typeOfTruck,
            frieghtCharges = frieghtCharges,
            uom = uom,
            status = status,
            attribute1 = attribute1,
            attribute2 = attribute2,
            attribute3 = attribute3,
            attribute4 = attribute4,
            isWmsShipment = isWmsShipment,
            weighmentType = weighmentType
        )
    }

    private fun PSWDispatchHeaderItem.toEntity():
            PswDispatchHeaderEntity? {
        val no = shipmentNo?.trim()
        if (no.isNullOrEmpty()) {
            return null
        }
        return PswDispatchHeaderEntity(
            shipmentNo = no,
            senderOrgnCode = senderOrgnCode,
            receiverOrgnCode = receiverOrgnCode,
            senderDate = senderDate,
            sentBy = sentBy,
            senderTruckNo = senderTruckNo,
            rcNo = rcNo,
            driverName = driverName,
            drivingLicenceNo = drivingLicenceNo,
            transportName = transportName,
            typeOfTruck = typeOfTruck,
            frieghtCharges = frieghtCharges,
            uom = uom,
            status = status,
            attribute1 = attribute1,
            attribute2 = attribute2,
            attribute3 = attribute3,
            attribute4 = attribute4,
            isWmsShipment = isWmsShipment,
            weighmentType = weighmentType
        )
    }

    // server format: "29-09-2026 15:08:32"
    private val headerDatePatterns = listOf("dd-MM-yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss")

    private fun parseHeaderDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        for (pattern in headerDatePatterns) {
            try {
                val parsed = SimpleDateFormat(pattern, Locale.US).parse(value)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    private fun normalizeHeaderDate(value: String?): String? {
        if (value.isNullOrBlank()) return value
        val time = parseHeaderDate(value)
        if (time == 0L) return value
        return SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).format(java.util.Date(time))
    }


    //Dispatch Details

    /** One dispatch header from the local table (weighment type, sender, receiver ...). */
    suspend fun getLocalDispatchHeader(shipmentNo: String): DispatchHeaderEntity? {
        return withContext(Dispatchers.IO) {
            database.dispatchHeaderDao().getByShipmentNo(shipmentNo)
        }
    }

    /** Looks a bale up in the local stock table (synced on the dashboard). */
    suspend fun findBaleInStock(baleNo: String): BaleInfo? {
        return withContext(Dispatchers.IO) {
            database.stockDao().getByBaleNumber(baleNo)?.let { s ->
                BaleInfo(
                    baleNumber = s.gpiL_BALE_NUMBER?.toString().orEmpty(),
                    tbLotNumber = s.tB_LOT_NO?.toString().orEmpty(),
                    markedWeight = s.markeD_WT?.toString().orEmpty(),
                    grade = s.grade?.toString().orEmpty(),
                    buyerGrade = s.buyeR_GRADE?.toString().orEmpty(),
                    subInventoryCode = s.subinventorY_CODE?.toString().orEmpty(),
                    price = s.price?.toString().orEmpty(),
                    status = s.status?.toString().orEmpty(),
                    processStatus = s.procesS_STATUS?.toString().orEmpty(),
                    currentOrgnCode = s.curR_ORGN_CODE?.toString().orEmpty(),
                    purchaseDate = s.createD_DATE?.toString().orEmpty()
                )
            }
        }
    }


    // Read available crops from locally stored stock
    suspend fun getLocalStockCrops(): List<String> {
        return withContext(Dispatchers.IO) {
            database.stockDao().getDistinctCrops()
        }
    }

    // Read available varieties, optionally filtered by crop
    suspend fun getLocalStockVarieties(
        crop: String?
    ): List<String> {
        return withContext(Dispatchers.IO) {
            database.stockDao().getDistinctVarieties(crop)
        }
    }

    // Read stock filtered by crop and variety
    suspend fun getLocalFilteredStock(
        crop: String?,
        variety: String?
    ): List<StockEntity> {
        return withContext(Dispatchers.IO) {
            database.stockDao().getFilteredStock(
                crop = crop,
                variety = variety
            )
        }
    }




    // ------------------------------
    // DISPATCH DETAILS (local table)
    // ------------------------------

    suspend fun getLocalDetails(shipmentNo: String): List<BaleDetail> {
        return withContext(Dispatchers.IO) {
            database.dispatchDetailDao().getByShipment(shipmentNo)
        }
    }

    /** the bale in any shipment, or null */
    suspend fun findDetailByBale(baleNo: String): BaleDetail? {
        return withContext(Dispatchers.IO) {
            database.dispatchDetailDao().getByBale(baleNo)
        }
    }

    suspend fun saveDispatchDetail(detail: BaleDetail) {
        withContext(Dispatchers.IO) {
            database.dispatchDetailDao().insert(detail)
        }
    }

    suspend fun deleteDispatchDetail(detailId: String) {
        withContext(Dispatchers.IO) {
            database.dispatchDetailDao().deleteByDetailId(detailId)
        }
    }

    suspend fun countUnsyncedDetails(): Int {
        return withContext(Dispatchers.IO) {
            database.dispatchDetailDao().countUnsynced()
        }
    }

    // ------------------------------
    // DISPATCH DETAILS UPLOAD (offline-first)
    // ------------------------------

    /**
     * Sends the bales that are not on the server yet (PENDING, or FAILED with
     * fewer than MAX_UPLOAD_ATTEMPTS tries) in one batch.
     *
     *  - "Success" / "Duplicate" -> marked SUCCESS
     *  - "Failed"                -> marked FAILED (+1 attempt, reason saved)
     *  - network / HTTP error    -> Result.failure, nothing is changed, so the
     *                               worker simply tries again later
     */
    suspend fun uploadPendingDispatchDetails(deviceId: String): Result<UploadSummary> {

        return try {

            val dao = database.dispatchDetailDao()

            val pending = withContext(Dispatchers.IO) {
                dao.getUnsynced(MAX_UPLOAD_ATTEMPTS, UPLOAD_BATCH_SIZE)
            }

            if (pending.isEmpty()) {
                return Result.success(UploadSummary(0, 0, 0))
            }

            val request = DispatchUploadRequest(
                records = pending.map { it.toUploadRecord(deviceId) }
            )

            val response = apiService.uploadDispatchBales(request)

            if (!response.isSuccessful) {
                throw Exception("Dispatch upload failed: ${response.code()}")
            }

            val body = response.body()
                ?: throw Exception("Dispatch upload response is empty")

            val items = body.dispatchDetailsResponse.orEmpty()

            if (!body.isSuccess && items.isEmpty()) {
                throw Exception("Dispatch upload was not accepted by the server")
            }

            var uploaded = 0
            var failed = 0

            withContext(Dispatchers.IO) {
                database.runInTransaction {

                    items.forEach { item ->

                        val bale = item.baleNumber ?: return@forEach

                        when {
                            item.status.equals("Success", ignoreCase = true) ||
                                    item.status.equals("Duplicate", ignoreCase = true) -> {

                                dao.markSynced(bale, item.reason ?: "Uploaded successfully")
                                uploaded++
                            }

                            else -> {

                                dao.markFailed(bale, item.reason ?: "Upload failed")
                                failed++
                            }
                        }
                    }
                }
            }

            val remaining = withContext(Dispatchers.IO) { dao.countUnsynced() }

            android.util.Log.d(
                "MasterDataRepository",
                "Dispatch upload: $uploaded uploaded, $failed failed, $remaining remaining"
            )

            Result.success(UploadSummary(uploaded, failed, remaining))

        } catch (e: Exception) {

            android.util.Log.e("MasterDataRepository", "Dispatch upload failed", e)

            Result.failure(e)
        }
    }

    private fun BaleDetail.toUploadRecord(deviceId: String) = DispatchUploadRecord(
        shipmenT_NO = shipmentNo,
        detaiL_ID = detailId,
        gpiL_BALE_NUMBER = gpilBaleNumber,
        grade = grade,
        markeD_WT = markedWeight.toDoubleOrNull() ?: 0.0,
        dispatcH_WEIGHT = dispatchWeight.toDoubleOrNull() ?: 0.0,
        froM_SUBINVENTORY_CODE = fromSubinventoryCode,
        tO_SUBINVENTORY_CODE = toSubinventoryCode,
        status = status,
        headeR_STATUS = headerStatus,
        createD_BY = createdBy,
        createD_DATE = createdDate,
        attributE4 = "",
        attributE5 = "",
        sendeR_ORGN_CODE = senderOrgnCode,
        receiveR_ORGN_CODE = receiverOrgnCode,
        processType = DISPATCH_PROCESS_TYPE,
        devicE_ID = deviceId
        //dispatcH_WEIGHMENT_STATUS = dispatchWeighmentStatus
    )



    // ------------------------------
    // DISPATCH DETAILS DOWNLOAD (return sync)
    // ------------------------------

    /**
     * Downloads the changes of ONE shipment that happened after the last change id
     * we applied (saved in dispatch_sync_state), applies them to dispatch_detail,
     * then moves the bookmark forward. Returns how many bales were changed.
     *
     * [hintChangeId] = change id announced by SignalR; nothing is downloaded when
     * we already have it.
     */
    suspend fun pullDispatchChanges(
        shipmentNo: String,
        hintChangeId: Long? = null
    ): Result<Int> = pullMutex.withLock {

        try {

            val stateDao = database.dispatchSyncStateDao()

            val lastId = withContext(Dispatchers.IO) {
                stateDao.getLastChangeId(shipmentNo)
            } ?: 0L

            if (hintChangeId != null && hintChangeId <= lastId) {
                return@withLock Result.success(0)
            }

            val response = apiService.getDispatchChanges(
                url = DISPATCH_CHANGES_URL,
                shipmentNo = shipmentNo,
                fromChangeId = lastId
            )

            if (!response.isSuccessful) {
                throw Exception("Dispatch changes API failed: ${response.code()}")
            }

            val raw = withContext(Dispatchers.IO) {
                response.body()?.string().orEmpty()
            }

            android.util.Log.d("MasterDataRepository", "Dispatch changes raw: ${raw.take(500)}")

            val parsed = DispatchChangeParser.parse(raw)

            if (!parsed.isSuccess) {
                if (parsed.message.contains("no changes", ignoreCase = true)) {
                    return@withLock Result.success(0)
                }
                throw Exception(parsed.message.ifBlank { "Dispatch changes were not accepted" })
            }

            var applied = 0
            var maxId = lastId

            withContext(Dispatchers.IO) {
                database.runInTransaction {

                    parsed.changes.sortedBy { it.changeId }.forEach { change ->

                        if (applyDispatchChange(change, shipmentNo)) applied++

                        if (change.changeId > maxId) maxId = change.changeId
                    }

                    if (maxId > lastId) {
                        stateDao.save(DispatchSyncState(shipmentNo, maxId))
                    }
                }
            }

            android.util.Log.d(
                "MasterDataRepository",
                "Dispatch changes: $applied applied, last change id $maxId"
            )

            Result.success(applied)

        } catch (e: Exception) {

            android.util.Log.e("MasterDataRepository", "Dispatch changes failed", e)

            Result.failure(e)
        }
    }

    /** one server change -> local table. Returns true when a bale was added / updated / removed */
    private fun applyDispatchChange(
        change: DispatchChangeParser.ParsedChange,
        shipmentNo: String
    ): Boolean {

        val dao = database.dispatchDetailDao()

        val payload = change.payload ?: return false
        val bale = DispatchChangeParser.baleOf(payload) ?: return false

        // assumption: a delete change has "DELETE" in its process name - confirm with the backend
        if (change.processName.uppercase().contains("DELETE")) {
            dao.deleteByBale(bale)
            return true
        }

        val existing = dao.getByBale(bale)

        val detail = DispatchChangeParser.toBaleDetail(payload, shipmentNo, existing)
            ?: return false

        // server data wins and is marked as "on the server"
        dao.insert(detail)

        return true
    }


}