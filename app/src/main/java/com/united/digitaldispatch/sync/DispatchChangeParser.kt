package com.united.digitaldispatch.sync

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.united.digitaldispatch.Dispatch.models.BaleDetail

/**
 * Reads the answer of  Dispatch/sync-dispatch-changes.
 *
 * It follows the shape of the purchase app (PurchaseChangeResponse):
 *     { isSuccess, message, changes: [ { changeId, processName, payload } ] }
 * and is forgiving: key names are compared without upper/lower case and
 * without "_"  (gpiL_BALE_NUMBER == GPIL_BALE_NUMBER == gpilBaleNumber),
 * "payload" may be a JSON string or an object, and if "changes" has another
 * name the first array in the answer is used.
 */
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

        val root: JsonElement = try {
            JsonParser.parseString(raw)
        } catch (e: Exception) {
            return ParsedChanges(false, "Response is not valid JSON", emptyList())
        }

        val rootObj = if (root.isJsonObject) root.asJsonObject else null

        val array: JsonArray? = when {
            root.isJsonArray -> root.asJsonArray
            rootObj != null -> findArray(rootObj)
            else -> null
        }

        val message = rootObj?.pick("message") ?: ""
        val successText = rootObj?.pick("isSuccess")

        val isSuccess = when {
            successText != null -> successText.equals("true", ignoreCase = true)
            array != null -> true
            else -> false
        }

        val changes = array?.mapNotNull { parseChange(it) }.orEmpty()

        return ParsedChanges(isSuccess, message, changes)
    }

    /** bale number of a payload, or null */
    fun baleOf(payload: JsonObject): String? =
        payload.pick("gpilBaleNumber", "baleNumber", "baleNo")

    /** payload -> local record (marked as coming from the server = SUCCESS) */
    fun toBaleDetail(payload: JsonObject, shipmentNo: String, existing: BaleDetail?): BaleDetail? {

        val bale = baleOf(payload) ?: return null

        return BaleDetail(
            detailId = payload.pick("detailId") ?: existing?.detailId ?: "SRV$bale",
            shipmentNo = payload.pick("shipmentNo") ?: existing?.shipmentNo ?: shipmentNo,
            gpilBaleNumber = bale,
            markedWeight = payload.pick("markedWt", "markedWeight") ?: existing?.markedWeight ?: "0",
            dispatchWeight = payload.pick("dispatchWeight") ?: existing?.dispatchWeight ?: "0",
            fromSubinventoryCode = payload.pick("fromSubinventoryCode") ?: existing?.fromSubinventoryCode ?: "",
            toSubinventoryCode = payload.pick("toSubinventoryCode") ?: existing?.toSubinventoryCode ?: "",
            status = payload.pick("status") ?: existing?.status ?: "INT",
            headerStatus = payload.pick("headerStatus") ?: existing?.headerStatus ?: "Y",
            grade = payload.pick("grade") ?: existing?.grade ?: "",
            createdBy = payload.pick("createdBy") ?: existing?.createdBy ?: "",
            createdDate = payload.pick("createdDate") ?: existing?.createdDate ?: "",
            senderOrgnCode = payload.pick("senderOrgnCode") ?: existing?.senderOrgnCode ?: "",
            receiverOrgnCode = payload.pick("receiverOrgnCode") ?: existing?.receiverOrgnCode ?: "",
            price = payload.pick("price") ?: existing?.price ?: "0",
            tbLotNumber = payload.pick("tbLotNo", "tbLotNumber") ?: existing?.tbLotNumber ?: "",
            purchaseDate = payload.pick("purchaseDate") ?: existing?.purchaseDate ?: "",
            dispatchWeighmentStatus = payload.pick("dispatchWeighmentStatus")
                ?: existing?.dispatchWeighmentStatus ?: "N",
            syncStatus = BaleDetail.SYNC_SUCCESS,
            serverMessage = "Synced from server",
            attemptCount = 0
        )
    }

    // ------------------------------------------------------------

    private fun parseChange(element: JsonElement): ParsedChange? {

        if (!element.isJsonObject) return null

        val obj = element.asJsonObject

        val changeId = obj.pick("changeId")?.toDoubleOrNull()?.toLong() ?: 0L
        val processName = obj.pick("processName", "processType") ?: ""

        val payloadElement = obj.find("payload")

        val payload: JsonObject? = when {
            // no "payload" key -> the change itself carries the data
            payloadElement == null || payloadElement.isJsonNull -> obj
            payloadElement.isJsonObject -> payloadElement.asJsonObject
            payloadElement.isJsonPrimitive -> try {
                JsonParser.parseString(payloadElement.asString).asJsonObject
            } catch (e: Exception) {
                null
            }
            else -> null
        }

        return ParsedChange(changeId, processName, payload)
    }

    private fun findArray(obj: JsonObject): JsonArray? {

        for (name in listOf("changes", "data", "items", "records")) {
            val element = obj.find(name)
            if (element != null && element.isJsonArray) return element.asJsonArray
        }

        return obj.entrySet().firstOrNull { it.value.isJsonArray }?.value?.asJsonArray
    }
}

// ---------------- key lookup without case / underscores ----------------

private fun norm(name: String): String = name.lowercase().replace("_", "")

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
        val text = if (element.isJsonPrimitive) element.asString else element.toString()
        if (text.isNotBlank()) return text.trim()
    }
    return null
}