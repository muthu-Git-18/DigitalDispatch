package com.united.digitaldispatch.sync

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.united.digitaldispatch.Dispatch.models.BaleDetail

object DispatchChangeParser {

    data class ParsedChange(
        val changeId: Long,
        val processName: String,
        val payload: JsonObject?
    )

    data class ParsedChanges(
        val isSuccess: Boolean,
        val message: String,
        val changes: List<ParsedChange>
    )

    fun parse(raw: String): ParsedChanges {
        if (raw.isBlank()) {
            return ParsedChanges(false, "Empty response", emptyList())
        }

        val root = try {
            JsonParser.parseString(raw)
        } catch (e: Exception) {
            return ParsedChanges(false, "Response is not valid JSON", emptyList())
        }

        val rootObj = root.takeIf { it.isJsonObject }?.asJsonObject

        val array: JsonArray? = when {
            root.isJsonArray -> root.asJsonArray
            rootObj != null -> findArray(rootObj)
            else -> null
        }

        val message = rootObj?.pick("message").orEmpty()
        val statusCode = rootObj?.pick("statusCode")
        val successText = rootObj?.pick("isSuccess")

        val isSuccess = when {
            // "NO_DATA" = request worked, there is just nothing new
            statusCode != null ->
                statusCode.equals("SUCCESS", ignoreCase = true) ||
                        statusCode.equals("NO_DATA", ignoreCase = true)

            successText != null ->
                successText.equals("true", ignoreCase = true)

            array != null -> true
            else -> false
        }

        if (!isSuccess) {
            return ParsedChanges(
                isSuccess = false,
                message = message.ifBlank { "Dispatch changes request was unsuccessful" },
                changes = emptyList()
            )
        }

        val changes = array
            ?.mapNotNull { parseChange(it) }
            .orEmpty()

        return ParsedChanges(
            isSuccess = true,
            message = message,
            changes = changes
        )
    }

    /** Returns the bale number from the payload. */
    fun baleOf(payload: JsonObject): String? =
        payload.pick("gpilBaleNumber", "baleNumber", "baleNo")

    /**
     * Converts a server payload to a local BaleDetail.
     * Existing local values are retained when the server omits a field.
     */
    fun toBaleDetail(
        payload: JsonObject,
        shipmentNo: String,
        existing: BaleDetail?
    ): BaleDetail? {

        val bale = baleOf(payload) ?: return null

        return BaleDetail(
            // keep the local key if the bale already exists -> no duplicate rows
            detailId = existing?.detailId
                ?: payload.pick("detailId", "recordPrimaryKey")
                ?: "SRV$bale",

            shipmentNo = payload.pick("shipmentNo")
                ?: existing?.shipmentNo
                ?: shipmentNo,

            gpilBaleNumber = bale,

            markedWeight = payload.pick("markedWt", "markedWeight")
                ?: existing?.markedWeight
                ?: "0",

            dispatchWeight = payload.pick("dispatchWeight")
                ?: existing?.dispatchWeight
                ?: "0",

            fromSubinventoryCode = payload.pick("fromSubinventoryCode", "fromSubinventory")
                ?: existing?.fromSubinventoryCode.orEmpty(),

            toSubinventoryCode = payload.pick("toSubinventoryCode", "toSubinventory")
                ?: existing?.toSubinventoryCode.orEmpty(),

            status = payload.pick("status")
                ?: existing?.status
                ?: "INT",

            headerStatus = payload.pick("headerStatus")
                ?: existing?.headerStatus
                ?: "Y",

            grade = payload.pick("grade")
                ?: existing?.grade
                ?: "",

            createdBy = payload.pick("createdBy")
                ?: existing?.createdBy
                ?: "",

            createdDate = payload.pick("createdDate")
                ?: existing?.createdDate
                ?: "",

            senderOrgnCode = payload.pick("senderOrgnCode", "senderOrgn", "senderOrgCode")
                ?: existing?.senderOrgnCode.orEmpty(),

            receiverOrgnCode = payload.pick(
                "receiverOrgnCode",
                "recOrgnCode",
                "receiverOrgn",
                "receiverOrgCode"
            ) ?: existing?.receiverOrgnCode.orEmpty(),

            price = payload.pick("price", "rate")
                ?: existing?.price
                ?: "0",

            tbLotNumber = payload.pick("tbLotNo", "tbLotNumber")
                ?: existing?.tbLotNumber
                ?: "",

            purchaseDate = payload.pick("purchaseDate")
                ?: existing?.purchaseDate
                ?: "",

            dispatchWeighmentStatus = payload.pick("dispatchWeighmentStatus")
                ?: existing?.dispatchWeighmentStatus
                ?: "N",

            // fixed: was falling back to dispatchWeighmentStatus
            deviceId = payload.pick("deviceId")
                ?: existing?.deviceId
                ?: "",

            syncStatus = BaleDetail.SYNC_SUCCESS,
            serverMessage = "Synced from server",
            attemptCount = 0
        )
    }

    private fun parseChange(element: JsonElement): ParsedChange? {
        if (!element.isJsonObject) return null

        val obj = element.asJsonObject

        val changeId = obj.pick("changeId")
            ?.toLongOrNull()
            ?: obj.pick("changeId")
                ?.toDoubleOrNull()
                ?.toLong()
            ?: 0L

        val processName = obj.pick("processName", "processType").orEmpty()

        val payloadElement = obj.find("payload")

        val payload: JsonObject? = when {
            payloadElement == null || payloadElement.isJsonNull -> obj

            payloadElement.isJsonObject -> payloadElement.asJsonObject

            payloadElement.isJsonPrimitive &&
                    payloadElement.asJsonPrimitive.isString -> {
                try {
                    JsonParser.parseString(payloadElement.asString)
                        .takeIf { it.isJsonObject }
                        ?.asJsonObject
                } catch (e: Exception) {
                    null
                }
            }

            else -> null
        }

        return ParsedChange(
            changeId = changeId,
            processName = processName,
            payload = payload
        )
    }

    private fun findArray(obj: JsonObject): JsonArray? {
        for (name in listOf("changes", "data", "items", "records")) {
            val element = obj.find(name)
            if (element != null && element.isJsonArray) {
                return element.asJsonArray
            }
        }

        return obj.entrySet()
            .firstOrNull { it.value.isJsonArray }
            ?.value
            ?.asJsonArray
    }
}

/** Case-insensitive key lookup that ignores underscores. */
private fun norm(name: String): String =
    name.lowercase().replace("_", "")

private fun JsonObject.find(name: String): JsonElement? {
    val target = norm(name)

    for ((key, value) in entrySet()) {
        if (norm(key) == target) return value
    }

    return null
}

private fun JsonObject.pick(vararg names: String): String? {
    for (name in names) {
        val element = find(name) ?: continue

        if (element.isJsonNull) continue

        val text = if (element.isJsonPrimitive) {
            element.asString
        } else {
            element.toString()
        }

        if (text.isNotBlank()) return text.trim()
    }

    return null
}