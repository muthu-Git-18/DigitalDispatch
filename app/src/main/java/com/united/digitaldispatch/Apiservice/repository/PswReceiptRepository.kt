package com.united.digitaldispatch.Apiservice.repository

import com.united.digitaldispatch.Apiservice.network.ApiService
import com.united.digitaldispatch.PSWReceipt.models.PswReceiptHeaderDto
import com.united.digitaldispatch.data.local.dao.PswReceiptHeaderDao
import com.united.digitaldispatch.data.local.entity.PswReceiptHeaderEntity
import kotlinx.coroutines.flow.Flow
import com.united.digitaldispatch.PSWReceipt.models.PswReceiptBaleDto
import com.united.digitaldispatch.data.local.dao.PswReceiptDetailDao
import com.united.digitaldispatch.data.local.entity.PswReceiptDetailEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PswReceiptRepository(
    private val api: ApiService,
    private val dao: PswReceiptHeaderDao,
    private val detailDao: PswReceiptDetailDao
) {

    fun observeHeaders(): Flow<List<PswReceiptHeaderEntity>> {
        return dao.observeAll()
    }

    suspend fun syncPswReceiptHeaders(orgCode: String): Result<Int> {
        return try {

            val response = api.getPswReceiptHeaders(orgCode)

            if (response.code() == 404) {
                dao.clear()
                return Result.success(0)
            }

            if (!response.isSuccessful) {
                return Result.failure(
                    Exception("HTTP Error: ${response.code()}")
                )
            }

            val body = response.body()
                ?: return Result.failure(
                    Exception("Empty server response")
                )

            when (body.statusCode?.uppercase()) {

                "SUCCESS" -> {

                    val serverHeaders = body.data.orEmpty()

                    // Preserve locally stored weighment types.
                    val existingTypes = dao.getAllWeighTypes()
                        .associate { it.shipmentNo to it.recevWeighType }

                    val entities = serverHeaders.map { dto ->

                        dto.toEntity(
                            existingTypes[dto.shipmentNo]
                        )
                    }

                    dao.replaceAll(entities)

                    Result.success(entities.size)
                }

                "NO_DATA", "NOT_FOUND" -> {
                    dao.clear()
                    Result.success(0)
                }

                else -> {
                    Result.failure(
                        Exception(body.message ?: "Unable to fetch PSW receipt headers")
                    )
                }
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ------------------------------------------------
// PSW RECEIPT DETAILS - OBSERVATION
// ------------------------------------------------

    fun observePswReceiptDetails(shipmentNo: String) =
        detailDao.observeByShipment(shipmentNo)

    fun observePswTotal(shipmentNo: String) =
        detailDao.observeTotal(shipmentNo)

    fun observePswReceived(shipmentNo: String) =
        detailDao.observeReceived(shipmentNo)

    fun observePswWeighed(shipmentNo: String) =
        detailDao.observeWeighed(shipmentNo)

    fun observePswPending(shipmentNo: String) =
        detailDao.observePending(shipmentNo)

    suspend fun downloadPswReceiptBales(
        shipmentNo: String
    ): Result<Int> = withContext(Dispatchers.IO) {

        try {
            val response = api.getPswReceiptBales(shipmentNo)

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP Error: ${response.code()}")
                )
            }

            val body = response.body()
                ?: return@withContext Result.failure(
                    Exception("Empty PSW receipt bales response")
                )

            when (body.statusCode?.uppercase()) {

                "SUCCESS" -> {

                    val entities = body.data.orEmpty().mapNotNull { dto ->

                        if (
                            dto.shipmentNo.isNullOrBlank() ||
                            dto.detailId.isNullOrBlank() ||
                            dto.baleNumber.isNullOrBlank()
                        ) {
                            null
                        } else {
                            dto.toPswDetailEntity()
                        }
                    }

                    detailDao.insertAll(entities)

                    Result.success(entities.size)
                }

                "NO_DATA", "NOT_FOUND" -> {
                    Result.success(0)
                }

                else -> {
                    Result.failure(
                        Exception(body.message ?: "Unable to download PSW receipt bales")
                    )
                }
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }


// =====================================================
// PSW RECEIPT DETAILS
// =====================================================

    suspend fun findBale(
        shipmentNo: String,
        baleNo: String
    ): PswReceiptDetailEntity? {
        return detailDao.findBale(shipmentNo, baleNo)
    }

    suspend fun getHeaderWeighType(shipmentNo: String): String? {
        return dao.getByShipmentNo(shipmentNo)?.recevWeighType
    }

    // Header weigh type first, then an already-received bale.
    suspend fun resolveWeighType(shipmentNo: String): String? {
        return getHeaderWeighType(shipmentNo)
            ?.takeIf { it.isNotBlank() }
            ?: detailDao.firstReceivedWeighType(shipmentNo)
    }

    suspend fun updateReceivedWeighType(
        shipmentNo: String,
        type: String
    ) {
        detailDao.updateReceivedWeighType(shipmentNo, type)
    }

    // Save one received PSW bale locally.
    suspend fun saveReceivedBale(
        shipmentNo: String,
        baleNo: String,
        receiptWeight: Double,
        weightStatus: String,
        weighType: String,
        userId: String,
        deviceId: String
    ): Boolean {

        val before = detailDao.receivedCount(shipmentNo)

        val date = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.US
        ).format(Date())

        val updated = detailDao.markReceived(
            shipmentNo = shipmentNo,
            baleNo = baleNo,
            receiptWeight = receiptWeight,
            weightStatus = weightStatus,
            weighType = weighType,
            userId = userId,
            date = date,
            deviceId = deviceId
        )

        if (updated == 0) {
            throw IllegalStateException("PSW Bale not found in this shipment")
        }

        // First received bale decides the shipment weighment type.
        if (before == 0) {
            dao.setWeighTypeIfBlank(shipmentNo, weighType)
        }

        return before == 0
    }


    private fun PswReceiptBaleDto.toPswDetailEntity(): PswReceiptDetailEntity {

        return PswReceiptDetailEntity(
            shipmentNo = shipmentNo!!,
            detailId = detailId!!,
            baleNumber = baleNumber!!,
            grade = grade,
            markedWt = markedWt ?: 0.0,
            dispatchWeight = dispatchWeight ?: 0.0,
            receiptWeight = receiptWeight,
            fromSubinventoryCode = fromSubinventoryCode,
            toSubinventoryCode = toSubinventoryCode,
            remarks = remarks,
            loadingDatetime = loadingDatetime,
            unloadingDatetime = unloadingDatetime,
            status = status ?: "INT",
            headerStatus = headerStatus ?: "Y",
            createdBy = createdBy,
            createdDate = createdDate,
            senderOrgnCode = senderOrgnCode,
            receiverOrgnCode = receiverOrgnCode,
            weightStatus = weightStatus,
            recevWeighType = recevWeighType,
            dispatchWeighmentStatus = dispatchWeighmentStatus,
            processType = processType,
            deviceId = deviceId
        )
    }

    private fun PswReceiptHeaderDto.toEntity(
        localWeighType: String?
    ): PswReceiptHeaderEntity {

        val finalWeighType =
            if (recevWeighType.isNullOrBlank()) {
                localWeighType
            } else {
                recevWeighType
            }

        return PswReceiptHeaderEntity(
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
            recevWeighType = finalWeighType
        )
    }
}
