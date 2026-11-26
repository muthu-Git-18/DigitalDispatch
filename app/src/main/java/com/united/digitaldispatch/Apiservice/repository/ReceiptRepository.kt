package com.united.digitaldispatch.Apiservice.repository

import androidx.room.withTransaction
import com.united.digitaldispatch.Apiservice.network.ApiService
import com.united.digitaldispatch.Dispatch.models.UploadSummary
import com.united.digitaldispatch.Receipt.models.ReceiptCompleteData
import com.united.digitaldispatch.Receipt.models.ReceiptCompleteRequest
import com.united.digitaldispatch.Receipt.models.ReceiptCompleteResponse
import com.united.digitaldispatch.Receipt.models.ReceiptHeaderDto
import com.united.digitaldispatch.Receipt.models.ReceiptUploadRecord
import com.united.digitaldispatch.Receipt.models.ReceiptUploadRequest
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.DispatchSyncState
import com.united.digitaldispatch.data.local.entity.ReceiptDetailEntity
import com.united.digitaldispatch.data.local.entity.ReceiptHeaderEntity
import com.united.digitaldispatch.sync.ReceiptChangeParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiptRepository(
    private val api: ApiService,
    private val db: AppDatabase
) {

    private val dao get() = db.receiptHeaderDao()

    private val detailDao get() = db.receiptDetailDao()

    private companion object {
        const val MAX_UPLOAD_ATTEMPTS = 5
        const val UPLOAD_BATCH_SIZE = 50
        const val RECEIPT_PROCESS_TYPE = "RECEIPT"   // TODO confirm with backend
        val pullMutex = kotlinx.coroutines.sync.Mutex()
    }


    fun observeHeaders(): Flow<List<ReceiptHeaderEntity>> = dao.observeAll()

    fun observeTotalBales(shipmentNo: String) = detailDao.observeTotal(shipmentNo)
    fun observeReceivedBales(shipmentNo: String) = detailDao.observeReceived(shipmentNo)
    fun observeWeighedBales(shipmentNo: String) = detailDao.observeWeighed(shipmentNo)

    fun observePendingBales(shipmentNo: String) = detailDao.observePending(shipmentNo)

    suspend fun countUnsyncedReceiptDetails(): Int = detailDao.countUnsynced()


    /** Downloads receipt headers for the receiving org and replaces the local copy. */
    suspend fun syncReceiptHeaders(orgCode: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getReceiptHeaders(orgCode)

                // 404 = server says "no receipts for this org" -> clear local data
                if (response.code() == 404) {
                    dao.clear()
                    return@withContext Result.success(0)
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Server error (${response.code()})")
                    )
                }

                val body = response.body()
                    ?: return@withContext Result.failure(Exception("Empty response from server"))

                val isSuccess = body.statusCode.equals("SUCCESS", ignoreCase = true)

                // "no data" style replies (any of these) -> clear local data
                val isNoData = !isSuccess && (
                        body.data.isNullOrEmpty() && body.message.orEmpty().let {
                            it.contains("no ", ignoreCase = true) ||
                                    it.contains("not found", ignoreCase = true)
                        } ||
                                body.statusCode.equals("NOT_FOUND", ignoreCase = true) ||
                                body.statusCode.equals("NO_DATA", ignoreCase = true)
                        )

                if (isNoData) {
                    dao.clear()
                    return@withContext Result.success(0)
                }

                if (!isSuccess) {
                    return@withContext Result.failure(
                        Exception(body.message ?: "Unable to load receipt headers")
                    )
                }

                val localTypes = dao.getAllWeighTypes()
                    .associate { it.shipmentNo to it.recevWeighType.orEmpty() }

                val list = body.data.orEmpty().map { dto ->
                    val e = dto.toEntity()
                    if (e.recevWeighType.isNullOrBlank())
                        e.copy(recevWeighType = localTypes[e.shipmentNo]?.takeIf { it.isNotBlank() })
                    else e                       // server value (set by any device) always wins
                }

                dao.replaceAll(list)
                Result.success(list.size)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // network error -> keep old data, UI shows "Showing saved receipts"
                Result.failure(Exception(e.message ?: "Network error"))
            }
        }
    private fun ReceiptHeaderDto.toEntity() = ReceiptHeaderEntity(
        shipmentNo = shipmentNo,
        senderOrgnCode = senderOrgnCode,
        receiverOrgnCode = receiverOrgnCode,
        senderDate = senderDate,
        sentBy = sentBy,
        senderTruckNo = senderTruckNo,
        rcNo = rcNo,
        driverName = driverName,
        drivingLicenceNo = drivingLicenceNo,
        transportName = transportName,
        frieghtCharges = frieghtCharges,
        uom = uom,
        status = status,
        attribute1 = attribute1,
        attribute2 = attribute2,
        attribute3 = attribute3,
        receiptName = receiptName,
        recevWeighType = recevWeighType
    )


    suspend fun findBale(shipmentNo: String, baleNo: String): ReceiptDetailEntity? =
        detailDao.findBale(shipmentNo, baleNo)

    suspend fun getHeaderWeighType(shipmentNo: String): String? =
        dao.getByShipmentNo(shipmentNo)?.recevWeighType

    /** header type if set, otherwise the type already stored on a received bale */
    suspend fun resolveWeighType(shipmentNo: String): String? =
        getHeaderWeighType(shipmentNo)?.takeIf { it.isNotBlank() }
            ?: detailDao.firstReceivedWeighType(shipmentNo)

    suspend fun updateReceivedWeighType(shipmentNo: String, type: String) =
        detailDao.updateReceivedWeighType(shipmentNo, type)

    /** Saves one received bale locally. Returns true when it was the FIRST received bale. */
    suspend fun saveReceivedBale(
        shipmentNo: String,
        baleNo: String,
        receiptWeight: Double,
        weightStatus: String,       // "Y" weighed / "N" not weighed
        weighType: String,          // "TWT" / "HND"
        userId: String,
        deviceId: String
    ): Boolean = db.withTransaction {
        val before = detailDao.receivedCount(shipmentNo)
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val updated = detailDao.markReceived(
            shipmentNo, baleNo, receiptWeight, weightStatus, weighType, userId, date, deviceId
        )
        if (updated == 0) throw IllegalStateException("Bale not found in this shipment")

        if (before == 0) dao.setWeighTypeIfBlank(shipmentNo, weighType)
        before == 0
    }


    /** Downloads the bales of one shipment. Existing local rows (already scanned) are never overwritten. */
    suspend fun downloadReceiptBales(shipmentNo: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getReceiptBales(shipmentNo)
                if (!response.isSuccessful)
                    return@withContext Result.failure(Exception("Server error (${response.code()})"))

                val body = response.body()
                    ?: return@withContext Result.failure(Exception("Empty response from server"))

                if (!body.statusCode.equals("SUCCESS", ignoreCase = true))
                    return@withContext Result.failure(
                        Exception(body.message ?: "Unable to load bales")
                    )

                // sender / receiver / weigh type are not in the bale API -> take from the local header
                // header is only a fallback now (server sends sender/receiver, but weigh type is often "")
                val header = dao.getByShipmentNo(shipmentNo)

                val entities = body.data.orEmpty().mapNotNull { dto ->
                    val baleNo = dto.baleNumber?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val status = dto.status?.takeIf { it.isNotBlank() } ?: "INT"

                    ReceiptDetailEntity(
                        shipmentNo = shipmentNo,
                        detailId = dto.detailId.orEmpty(),
                        baleNumber = baleNo,
                        grade = dto.grade,
                        markedWt = dto.markedWt ?: 0.0,
                        dispatchWeight = dto.dispatchWeight ?: 0.0,
                        receiptWeight = dto.receiptWeight,
                        fromSubinventoryCode = dto.fromSubinventoryCode,
                        status = status,
                        headerStatus = dto.headerStatus?.takeIf { it.isNotBlank() } ?: "Y",
                        senderOrgnCode = dto.senderOrgnCode?.takeIf { it.isNotBlank() } ?: header?.senderOrgnCode,
                        receiverOrgnCode = dto.receiverOrgnCode?.takeIf { it.isNotBlank() } ?: header?.receiverOrgnCode,
                        recevWeighType = dto.recevWeighType?.takeIf { it.isNotBlank() }
                            ?: header?.recevWeighType?.takeIf { it.isNotBlank() },
                        weightStatus = dto.weightStatus?.takeIf { it.isNotBlank() },
                        processType = dto.processType?.takeIf { it.isNotBlank() },
                        deviceId = dto.deviceId?.takeIf { it.isNotBlank() },
                        toSubinventoryCode = dto.toSubinventoryCode,
                        remarks = dto.remarks,
                        loadingDatetime = dto.loadingDatetime,
                        unloadingDatetime = dto.unloadingDatetime,
                        createdBy = dto.createdBy,
                        createdDate = dto.createdDate,
                        dispatchWeighmentStatus = dto.dispatchWeighmentStatus?.takeIf { it.isNotBlank() },
                        // already received on the server -> must NOT be uploaded again
                        syncStatus = if (status == ReceiptDetailEntity.STATUS_RECEIVED)
                            ReceiptDetailEntity.SYNC_SUCCESS else ReceiptDetailEntity.SYNC_PENDING
                    )
                }

                detailDao.insertAllIgnore(entities)
                Result.success(entities.size)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Network error"))
            }
        }

    suspend fun uploadPendingReceiptDetails(): Result<UploadSummary> = withContext(Dispatchers.IO) {
        try {
            val pending = detailDao.getUnsynced(MAX_UPLOAD_ATTEMPTS, UPLOAD_BATCH_SIZE)
            if (pending.isEmpty()) return@withContext Result.success(UploadSummary(0, 0, 0))

            val response = api.uploadReceiptBales(
                ReceiptUploadRequest(pending.map { it.toUploadRecord() })
            )
            if (!response.isSuccessful) throw Exception("Receipt upload failed: ${response.code()}")

            val body = response.body() ?: throw Exception("Receipt upload response is empty")
            val items = body.receiptDetailsResponse.orEmpty()

            android.util.Log.d(
                "ReceiptRepository",
                "Receipt upload: http=${response.code()} isSuccess=${body.isSuccess} items=${items.size}"
            )

            if (!body.isSuccess && items.isEmpty()) {
                throw Exception(body.message ?: "Receipt upload was not accepted by the server")
            }

            var uploaded = 0
            var failed = 0

            db.withTransaction {
                items.forEach { item ->
                    val bale = item.baleNumber ?: return@forEach
                    val reason = item.reason?.takeIf { it.isNotBlank() }

                    when {
                        // already received on the server (another device or an earlier attempt): synced, do NOT delete
                        item.status.equals("Duplicate", ignoreCase = true) ->
                            detailDao.markSynced(bale, reason ?: "Already received")

                        item.status.equals("Success", ignoreCase = true) -> {
                            detailDao.markSynced(bale, reason ?: "Uploaded successfully")
                            uploaded++
                        }

                        else -> {
                            detailDao.markFailed(bale, reason ?: "Upload failed")
                            failed++
                        }
                    }
                }
            }

            Result.success(UploadSummary(uploaded, failed, detailDao.countUnsynced()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ReceiptRepository", "Receipt upload failed", e)
            Result.failure(e)
        }
    }

    private fun ReceiptDetailEntity.toUploadRecord() = ReceiptUploadRecord(
        shipmenT_NO = shipmentNo,
        detaiL_ID = detailId,
        gpiL_BALE_NUMBER = baleNumber,
        markeD_WT = markedWt,
        dispatcH_WEIGHT = dispatchWeight,
        receipT_WEIGHT = receiptWeight,
        froM_SUBINVENTORY_CODE = fromSubinventoryCode.orEmpty(),
        tO_SUBINVENTORY_CODE = toSubinventoryCode.orEmpty(),
        remarks = remarks.orEmpty(),
        loadinG_DATETIME = loadingDatetime.orEmpty(),
        unloadinG_DATETIME = unloadingDatetime ?: lastUpdatedDate.orEmpty(),
        status = status,
        headeR_STATUS = headerStatus,
        createD_BY = createdBy.orEmpty(),
        createD_DATE = createdDate.orEmpty(),
        grade = grade.orEmpty(),
        receiveR_ORGN_CODE = receiverOrgnCode.orEmpty(),
        sendeR_ORGN_CODE = senderOrgnCode.orEmpty(),
        receV_WEIGH_TYPE = recevWeighType.orEmpty(),
        weighT_STATUS = weightStatus.orEmpty(),
        dispatcH_WEIGHMENT_STATUS = dispatchWeighmentStatus.orEmpty(),
        processType = RECEIPT_PROCESS_TYPE,
        devicE_ID = deviceId.orEmpty()
    )

    private fun receiptStateKey(shipmentNo: String) = "RCPT:$shipmentNo"

    ////Sync

    suspend fun pullReceiptChanges(
        shipmentNo: String,
        hintChangeId: Long? = null
    ): Result<Int> = pullMutex.withLock {

        try {
            val stateDao = db.dispatchSyncStateDao()
            val key = receiptStateKey(shipmentNo)

            val lastId = withContext(Dispatchers.IO) { stateDao.getLastChangeId(key) } ?: 0L

            if (hintChangeId != null && hintChangeId <= lastId) {
                return@withLock Result.success(0)
            }

            val response = api.getReceiptChanges(shipmentNo, lastId)

            if (!response.isSuccessful) throw Exception("Receipt changes API failed: ${response.code()}")

            val raw = withContext(Dispatchers.IO) { response.body()?.string().orEmpty() }

            android.util.Log.d("ReceiptRepository", "Receipt changes raw: ${raw.take(500)}")

            val parsed = ReceiptChangeParser.parse(raw)

            if (!parsed.isSuccess) {
                val msg = parsed.message.lowercase()
                // "NO_DATA / No new changes found." is not an error
                if (msg.contains("no new changes") || msg.contains("no changes") || msg.contains("no data")) {
                    return@withLock Result.success(0)
                }
                throw Exception(parsed.message.ifBlank { "Receipt changes were not accepted" })
            }

            var applied = 0
            var maxId = lastId

            withContext(Dispatchers.IO) {
                db.withTransaction {
                    parsed.changes.sortedBy { it.changeId }.forEach { change ->
                        if (applyReceiptChange(change, shipmentNo)) applied++
                        if (change.changeId > maxId) maxId = change.changeId
                    }
                    if (maxId > lastId) stateDao.save(DispatchSyncState(key, maxId))
                }
            }

            android.util.Log.d("ReceiptRepository", "Receipt changes: $applied applied, last change id $maxId")

            Result.success(applied)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ReceiptRepository", "Receipt changes failed", e)
            Result.failure(e)
        }
    }

    /** one server change -> local row. Returns true when a bale was updated. */
    private suspend fun applyReceiptChange(
        change: ReceiptChangeParser.Change,
        shipmentNo: String
    ): Boolean {

        val p = change.payload ?: return false

        val bale = ReceiptChangeParser.str(p, "BaleNumber") ?: return false
        val status = ReceiptChangeParser.str(p, "Status") ?: return false

        if (status != ReceiptDetailEntity.STATUS_RECEIVED) return false

        // the bale row comes from the bale download; a change for an unknown bale is skipped
        val existing = detailDao.findBale(shipmentNo, bale) ?: return false

        // this device has its own unsent receive of the same bale -> keep it, the upload settles it
        if (existing.status == ReceiptDetailEntity.STATUS_RECEIVED &&
            existing.syncStatus != ReceiptDetailEntity.SYNC_SUCCESS
        ) return false

        val weighType = ReceiptChangeParser.str(p, "RecevWeighType")?.takeIf { it.isNotBlank() }

        detailDao.applyRemoteReceipt(
            shipmentNo = shipmentNo,
            bale = bale,
            receiptWeight = ReceiptChangeParser.dbl(p, "ReceiptWeight"),
            status = status,
            weightStatus = ReceiptChangeParser.str(p, "WeightStatus")?.takeIf { it.isNotBlank() },
            weighType = weighType,
            unloading = ReceiptChangeParser.str(p, "UnloadingDateTime") ,
            processType = ReceiptChangeParser.str(p,"ProcessType"),
            deviceId=ReceiptChangeParser.str(p,"DeviceId")
        )

        // first receive on the server decides the weighment type for the shipment
        if (weighType != null) dao.setWeighTypeIfBlank(shipmentNo, weighType)

        return true
    }


    fun observeReceiptDetails(shipmentNo: String): Flow<List<ReceiptDetailEntity>> =
        detailDao.observeByShipment(shipmentNo)


    // ---------------- COMPLETE RECEIPT ----------------

    suspend fun completeReceipt(shipmentNo: String): Result<ReceiptCompleteData> =
        withContext(Dispatchers.IO) {
            try {
                // every received bale must be on the server first
                val pending = detailDao.countUnsyncedByShipment(shipmentNo)
                if (pending > 0) {
                    throw Exception("$pending bale(s) not uploaded yet. Connect to the network and try again.")
                }

                val header = dao.getByShipmentNo(shipmentNo)
                    ?: throw Exception("Receipt header not found on this device")

                val request = ReceiptCompleteRequest(
                    shipmentNo = shipmentNo,
                    senderOrgnCode = header.senderOrgnCode.orEmpty(),
                    receiverOrgnCode = header.receiverOrgnCode.orEmpty(),
                    senderDate = header.senderDate.orEmpty(),
                    sentBy = header.sentBy.orEmpty(),
                    deviceRcvBalesCount = detailDao.receivedCount(shipmentNo)
                )

                val response = api.completeReceipt(request)

                // success and failure (409) both come back in the same JSON shape
                val body = response.body() ?: parseReceiptCompleteError(response.errorBody()?.string())
                val data = body?.data

                if (!response.isSuccessful || data == null || !data.isSuccess) {
                    throw Exception(
                        data?.output?.takeIf { it.isNotBlank() }
                            ?: body?.message?.takeIf { it.isNotBlank() }
                            ?: "Complete receipt failed: ${response.code()}"
                    )
                }

                // server accepted -> finished on this device too
                removeReceiptShipmentLocally(shipmentNo)

                Result.success(data)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ReceiptRepository", "Complete receipt failed", e)
                Result.failure(e)
            }
        }

    private fun parseReceiptCompleteError(raw: String?): ReceiptCompleteResponse? =
        try { com.google.gson.Gson().fromJson(raw, ReceiptCompleteResponse::class.java) }
        catch (_: Exception) { null }

    /** Used by both "I completed it" and "another device completed it". */
    suspend fun removeReceiptShipmentLocally(shipmentNo: String) {
        withContext(Dispatchers.IO) {
            db.withTransaction {
                detailDao.deleteByShipment(shipmentNo)
                db.dispatchSyncStateDao().deleteByShipment(receiptStateKey(shipmentNo))
                dao.deleteByShipmentNo(shipmentNo)
            }
        }
    }
}