package com.united.digitaldispatch.utils

import android.app.Activity
import android.content.Intent
import com.united.digitaldispatch.Dispatch.Dispatch

/**
 * Back to the Dispatch (ref-id list) screen and close everything above it
 * (Details, Summary). The list reloads itself in onResume.
 */
fun Activity.goBackToDispatchList() {
    startActivity(
        Intent(this, Dispatch::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )
    finish()
}