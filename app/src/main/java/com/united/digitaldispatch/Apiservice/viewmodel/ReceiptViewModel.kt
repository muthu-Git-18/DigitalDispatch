package com.united.digitaldispatch.Apiservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.united.digitaldispatch.Apiservice.repository.ReceiptRepository
import com.united.digitaldispatch.Receipt.models.ReceiptCompleteData
import com.united.digitaldispatch.data.local.entity.ReceiptDetailEntity
import com.united.digitaldispatch.data.local.entity.ReceiptHeaderEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------- header list (Receipt screen) ----------------

sealed class ReceiptSyncState {
    object Idle : ReceiptSyncState()
    object Loading : ReceiptSyncState()
    data class Success(val count: Int) : ReceiptSyncState()
    data class Error(val message: String) : ReceiptSyncState()
}

// ---------------- one-time events (ReceiptDetails screen) ----------------

sealed class ReceiptDetailEvent {
    data class BaleFound(val bale: ReceiptDetailEntity) : ReceiptDetailEvent()
    object BaleNotFound : ReceiptDetailEvent()
    data class Saved(val isFirstBale: Boolean) : ReceiptDetailEvent()
    /** type already known when the screen opens (header or an earlier received bale) */
    data class InitialWeighType(val type: String) : ReceiptDetailEvent()
    /** result of the header re-check after the first bale */
    data class WeighTypeResolved(val type: String, val changed: Boolean) : ReceiptDetailEvent()
    data class Error(val message: String) : ReceiptDetailEvent()
}

sealed class ReceiptCompleteState {
    object Idle : ReceiptCompleteState()
    object Loading : ReceiptCompleteState()
    data class Success(val data: ReceiptCompleteData) : ReceiptCompleteState()
    data class Error(val message: String) : ReceiptCompleteState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptViewModel(
    private val repository: ReceiptRepository
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)

    // =====================================================
    // RECEIPT HEADER SELECTION
    // =====================================================

    // Room Flow -> list updates by itself after every sync
    val headers: StateFlow<List<ReceiptHeaderEntity>> =
        repository.observeHeaders()
            .stateIn(viewModelScope, started, emptyList())

    private val _syncState = MutableStateFlow<ReceiptSyncState>(ReceiptSyncState.Idle)
    val syncState: StateFlow<ReceiptSyncState> = _syncState.asStateFlow()

    fun syncReceiptHeaders(orgCode: String) {
        if (_syncState.value is ReceiptSyncState.Loading) return

        _syncState.value = ReceiptSyncState.Loading

        viewModelScope.launch {
            repository.syncReceiptHeaders(orgCode)
                .onSuccess { _syncState.value = ReceiptSyncState.Success(it) }
                .onFailure { _syncState.value = ReceiptSyncState.Error(it.message ?: "Sync failed") }
        }
    }

    fun resetSyncState() {
        _syncState.value = ReceiptSyncState.Idle
    }

    // =====================================================
    // RECEIPT DETAILS (bale scan + save)
    // =====================================================

    // the details screen calls setShipment() once in onCreate
    private val currentShipment = MutableStateFlow("")

    fun setShipment(shipmentNo: String) {
        currentShipment.value = shipmentNo
    }

    private fun perShipment(source: (String) -> Flow<Int>): StateFlow<Int> =
        currentShipment
            .flatMapLatest { if (it.isBlank()) flowOf(0) else source(it) }
            .stateIn(viewModelScope, started, 0)

    val totalBales: StateFlow<Int> = perShipment { repository.observeTotalBales(it) }
    val receivedBales: StateFlow<Int> = perShipment { repository.observeReceivedBales(it) }
    val weighedBales: StateFlow<Int> = perShipment { repository.observeWeighedBales(it) }

    val pendingBales: StateFlow<Int> = perShipment { repository.observePendingBales(it) }

    // Channel = events are queued, never lost while the screen is in background
    private val _events = Channel<ReceiptDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun resolveSavedWeighType() {
        val shipmentNo = currentShipment.value
        if (shipmentNo.isBlank()) return

        viewModelScope.launch {
            val type = repository.resolveWeighType(shipmentNo).orEmpty()
            if (type.isNotBlank()) _events.send(ReceiptDetailEvent.InitialWeighType(type))
        }
    }

    fun lookupBale(baleNo: String) {
        val shipmentNo = currentShipment.value

        viewModelScope.launch {
            try {
                val bale = repository.findBale(shipmentNo, baleNo)
                _events.send(
                    if (bale == null) ReceiptDetailEvent.BaleNotFound
                    else ReceiptDetailEvent.BaleFound(bale)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ReceiptDetailEvent.Error(e.message ?: "Unable to read bale"))
            }
        }
    }

    fun saveBale(
        baleNo: String,
        receiptWeight: Double,
        weightStatus: String,
        weighType: String,
        userId: String,
        deviceId: String
    ) {
        val shipmentNo = currentShipment.value

        viewModelScope.launch {
            try {
                val first = repository.saveReceivedBale(
                    shipmentNo, baleNo, receiptWeight, weightStatus, weighType, userId, deviceId
                )
                _events.send(ReceiptDetailEvent.Saved(first))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ReceiptDetailEvent.Error(e.message ?: "Unable to save bale"))
            }
        }
    }

    /**
     * After the FIRST bale: call the header API again, read the weigh type of this
     * shipment from the local header and report it. Offline -> keeps the chosen type.
     */
    fun refreshWeighTypeFromServer(orgCode: String, chosenType: String) {
        val shipmentNo = currentShipment.value
        if (shipmentNo.isBlank()) return

        viewModelScope.launch {
            if (orgCode.isNotBlank()) repository.syncReceiptHeaders(orgCode)   // result ignored on purpose

            val serverType = repository.getHeaderWeighType(shipmentNo).orEmpty()
            val finalType = serverType.ifBlank { chosenType }
            val changed = finalType != chosenType

            if (changed) repository.updateReceivedWeighType(shipmentNo, finalType)

            _events.send(ReceiptDetailEvent.WeighTypeResolved(finalType, changed))
        }
    }

    private val _baleSyncState = MutableStateFlow<ReceiptSyncState>(ReceiptSyncState.Idle)
    val baleSyncState: StateFlow<ReceiptSyncState> = _baleSyncState.asStateFlow()

    private var baleDownloadStarted = false   // rotation re-creates the Activity, not the ViewModel

    fun downloadBales(shipmentNo: String) {
        if (baleDownloadStarted || shipmentNo.isBlank()) return
        baleDownloadStarted = true
        _baleSyncState.value = ReceiptSyncState.Loading

        viewModelScope.launch {
            repository.downloadReceiptBales(shipmentNo)
                .onSuccess { _baleSyncState.value = ReceiptSyncState.Success(it) }
                .onFailure {
                    baleDownloadStarted = false          // allow retry next time the screen opens
                    _baleSyncState.value = ReceiptSyncState.Error(it.message ?: "Download failed")
                }
        }
    }


    // in ReceiptViewModel
    private val _details = MutableStateFlow<List<ReceiptDetailEntity>>(emptyList())
    val details: StateFlow<List<ReceiptDetailEntity>> = _details

    private var detailsJob: Job? = null
    fun loadDetails(shipmentNo: String) {
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            repository.observeReceiptDetails(shipmentNo).collect { _details.value = it }
        }
    }


    // ---------------- COMPLETE RECEIPT ----------------
    private val _completeState = MutableStateFlow<ReceiptCompleteState>(ReceiptCompleteState.Idle)
    val completeState: StateFlow<ReceiptCompleteState> = _completeState.asStateFlow()

    fun completeReceipt(shipmentNo: String) {
        if (_completeState.value is ReceiptCompleteState.Loading) return   // double-tap guard
        viewModelScope.launch {
            _completeState.value = ReceiptCompleteState.Loading
            _completeState.value = repository.completeReceipt(shipmentNo).fold(
                onSuccess = { ReceiptCompleteState.Success(it) },
                onFailure = { ReceiptCompleteState.Error(it.message ?: "Complete receipt failed") }
            )
        }
    }

    fun resetCompleteState() { _completeState.value = ReceiptCompleteState.Idle }
}