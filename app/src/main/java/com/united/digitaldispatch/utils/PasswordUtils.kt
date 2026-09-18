package com.united.digitaldispatch.utils

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Same SHA-256 based hashing the WH/PPD LoginActivity used
 * (`encryptPassword`), kept as-is so credentials coming from the existing
 * sync API / user-master table stay compatible with the unified app.
 *
 * NOTE (flagging, not fixing silently): the old code truncates the hash to
 * the first 20 hex characters, which throws away most of SHA-256's
 * collision resistance (~80 bits instead of 256). That's a legacy
 * decision, not something introduced here — worth revisiting with
 * whoever owns the sync API once it's ready, rather than changing it
 * unilaterally on the client and breaking compatibility.
 */
object PasswordUtils {

    fun hash(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(password.toByteArray(StandardCharsets.UTF_8))
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return hex.substring(0, 20)
    }
}
