package com.united.digitaldispatch.Apiservice.repository

import androidx.room.withTransaction
import com.united.digitaldispatch.Apiservice.network.ApiService
import com.united.digitaldispatch.Receipt.models.ReceiptHeaderDto
import com.united.digitaldispatch.data.local.AppDatabase
import com.united.digitaldispatch.data.local.entity.ReceiptDetailEntity
import com.united.digitaldispatch.data.local.entity.ReceiptHeaderEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    fun observeHeaders(): Flow<List<ReceiptHeaderEntity>> = dao.observeAll()

    fun observeTotalBales(shipmentNo: String) = detailDao.observeTotal(shipmentNo)
    fun observeReceivedBales(shipmentNo: String) = detailDao.observeReceived(shipmentNo)
    fun observeWeighedBales(shipmentNo: String) = detailDao.observeWeighed(shipmentNo)

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
                        headerStatus = dto.headerStatus ?: "Y",
                        senderOrgnCode = header?.senderOrgnCode,
                        receiverOrgnCode = header?.receiverOrgnCode,
                        recevWeighType = header?.recevWeighType?.takeIf { it.isNotBlank() },
                        weightStatus = dto.weightStatus?.takeIf { it.isNotBlank() },
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
}