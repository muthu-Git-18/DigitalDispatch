package com.united.digitaldispatch

import android.app.Application
import com.united.digitaldispatch.sync.NetworkMonitor
import com.united.digitaldispatch.sync.SyncManager

/**
 * Only needed if the project has no Application class yet.
 * Register it in AndroidManifest.xml:   <application android:name=".DigitalDispatchApp" ...>
 * If you already have one, just add the SyncManager.start(this) line to its onCreate().
 */
class DigitalDispatchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NetworkMonitor.start(this)
        SyncManager.start(this)
    }
}