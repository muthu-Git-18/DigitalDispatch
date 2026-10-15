package com.united.digitaldispatch.sync

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * In-app signal "the local bales of this shipment changed" (a download applied
 * something). The Details screen listens and reloads its list.
 */
object SyncEvents {

    val detailsChanged = MutableSharedFlow<String>(extraBufferCapacity = 16)
}