package com.united.digitaldispatch.sync

import org.json.JSONObject

/** Parses the sync-receipt-changes response (payload is a JSON string inside the JSON). */
object ReceiptChangeParser {

    data class Change(
        val changeId: Long,
        val processName: String,
        val payload: JSONObject?
    )

    data class Parsed(
        val isSuccess: Boolean,
        val message: String,
        val changes: List<Change>
    )

    fun parse(raw: String): Parsed {
        return try {
            val root = JSONObject(raw)
            val ok = root.optString("statusCode").equals("SUCCESS", ignoreCase = true)
            val message = root.optString("message")

            val list = mutableListOf<Change>()
            val arr = root.optJSONArray("data")

            if (ok && arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue

                    val p = o.opt("payload")
                    val payload = when (p) {
                        is JSONObject -> p
                        is String -> if (p.isBlank()) null else try { JSONObject(p) } catch (_: Exception) { null }
                        else -> null
                    }

                    list.add(Change(o.optLong("changeId"), o.optString("processName"), payload))
                }
            }

            Parsed(ok, message, list)
        } catch (e: Exception) {
            Parsed(false, "Unreadable response: ${e.message}", emptyList())
        }
    }

    // payload keys are matched ignoring case (ShipmentNo / shipmentNo ...)
    private fun JSONObject.find(name: String): Any? {
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.equals(name, ignoreCase = true)) return opt(k)
        }
        return null
    }

    fun str(p: JSONObject, name: String): String? =
        p.find(name)?.takeIf { it != JSONObject.NULL }?.toString()

    fun dbl(p: JSONObject, name: String): Double? {
        val v = p.find(name)
        return (v as? Number)?.toDouble() ?: str(p, name)?.toDoubleOrNull()
    }
}