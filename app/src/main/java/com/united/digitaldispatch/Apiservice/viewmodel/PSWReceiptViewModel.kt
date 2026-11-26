package com.united.digitaldispatch.Apiservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.united.digitaldispatch.Apiservice.repository.PswReceiptRepository
import com.united.digitaldispatch.data.local.entity.PswReceiptDetailEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class PswReceiptSyncState {
    data object Idle : PswReceiptSyncState()
    data object Loading : PswReceiptSyncState()
    data class Success(val count: Int) : PswReceiptSyncState()
    data class Error(val message: String) : PswReceiptSyncState()
}

// One-time events for PSW Receipt Details
sealed class PswReceiptDetailEvent {
    data class BaleFound(val bale: PswReceiptDetailEntity) : PswReceiptDetailEvent()
    data object BaleNotFound : PswReceiptDetailEvent()
    data class Saved(val isFirstBale: Boolean) : PswReceiptDetailEvent()
    data class InitialWeighType(val type: String) : PswReceiptDetailEvent()
    data class WeighTypeResolved(val type: String, val changed: Boolean) : PswReceiptDetailEvent()
    data class Error(val message: String) : PswReceiptDetailEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
class PswReceiptViewModel(
    private val repository: PswReceiptRepository
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)

    // =====================================================
    // PSW RECEIPT HEADERS
    // =====================================================

    val headers = repository.observeHeaders()
        .stateIn(viewModelScope, started, emptyList())

    private val _syncState =
        MutableStateFlow<PswReceiptSyncState>(PswReceiptSyncState.Idle)

    val syncState: StateFlow<PswReceiptSyncState> =
        _syncState.asStateFlow()

    fun syncPswReceiptHeaders(orgCode: String) {
        if (_syncState.value is PswReceiptSyncState.Loading) return

        viewModelScope.launch {
            _syncState.value = PswReceiptSyncState.Loading

            val result = repository.syncPswReceiptHeaders(orgCode)

            _syncState.value = result.fold(
                onSuccess = { PswReceiptSyncState.Success(it) },
                onFailure = {
                    PswReceiptSyncState.Error(
                        it.message ?: "PSW Header sync failed"
                    )
                }
            )
        }
    }

    fun resetSyncState() {
        _syncState.value = PswReceiptSyncState.Idle
    }

    // =====================================================
    // PSW RECEIPT DETAILS
    // =====================================================

    private val currentShipment = MutableStateFlow("")

    fun setShipment(shipmentNo: String) {
        currentShipment.value = shipmentNo
    }

    private fun perShipment(
        source: (String) -> kotlinx.coroutines.flow.Flow<Int>
    ): StateFlow<Int> =
        currentShipment
            .flatMapLatest {
                if (it.isBlank()) flowOf(0) else source(it)
            }
            .stateIn(viewModelScope, started, 0)

    val totalBales = perShipment {
        repository.observePswTotal(it)
    }

    val receivedBales = perShipment {
        repository.observePswReceived(it)
    }

    val weighedBales = perShipment {
        repository.observePswWeighed(it)
    }

    val pendingBales = perShipment {
        repository.observePswPending(it)
    }

    private val _events =
        Channel<PswReceiptDetailEvent>(Channel.BUFFERED)

    val events = _events.receiveAsFlow()

    fun resolveSavedWeighType() {
        val shipmentNo = currentShipment.value
        if (shipmentNo.isBlank()) return

        viewModelScope.launch {
            try {
                val type = repository.resolveWeighType(shipmentNo).orEmpty()

                if (type.isNotBlank()) {
                    _events.send(
                        PswReceiptDetailEvent.InitialWeighType(type)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(
                    PswReceiptDetailEvent.Error(
                        e.message ?: "Unable to resolve PSW weighment type"
                    )
                )
            }
        }
    }

    fun lookupBale(baleNo: String) {
        val shipmentNo = currentShipment.value

        viewModelScope.launch {
            try {
                val bale = repository.findBale(shipmentNo, baleNo)

                _events.send(
                    if (bale == null) {
                        PswReceiptDetailEvent.BaleNotFound
                    } else {
                        PswReceiptDetailEvent.BaleFound(bale)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(
                    PswReceiptDetailEvent.Error(
                        e.message ?: "Unable to read PSW bale"
                    )
                )
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
                    shipmentNo = shipmentNo,
                    baleNo = baleNo,
                    receiptWeight = receiptWeight,
                    weightStatus = weightStatus,
                    weighType = weighType,
                    userId = userId,
                    deviceId = deviceId
                )

                _events.send(
                    PswReceiptDetailEvent.Saved(first)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(
                    PswReceiptDetailEvent.Error(
                        e.message ?: "Unable to save PSW bale"
                    )
                )
            }
        }
    }

    fun refreshWeighTypeFromServer(
        orgCode: String,
        chosenType: String
    ) {
        val shipmentNo = currentShipment.value
        if (shipmentNo.isBlank()) return

        viewModelScope.launch {
            try {
                if (orgCode.isNotBlank()) {
                    repository.syncPswReceiptHeaders(orgCode)
                }

                val serverType =
                    repository.getHeaderWeighType(shipmentNo).orEmpty()

                val finalType = serverType.ifBlank { chosenType }
                val changed = finalType != chosenType

                if (changed) {
                    repository.updateReceivedWeighType(
                        shipmentNo,
                        finalType
                    )
                }

                _events.send(
                    PswReceiptDetailEvent.WeighTypeResolved(
                        finalType,
                        changed
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(
                    PswReceiptDetailEvent.Error(
                        e.message ?: "Unable to refresh PSW weighment type"
                    )
                )
            }
        }
    }

    // =====================================================
    // DOWNLOAD PSW BALES
    // =====================================================

    private val _baleSyncState =
        MutableStateFlow<PswReceiptSyncState>(PswReceiptSyncState.Idle)

    val baleSyncState: StateFlow<PswReceiptSyncState> =
        _baleSyncState.asStateFlow()

    private var baleDownloadStarted = false

    fun downloadBales(shipmentNo: String) {
        if (baleDownloadStarted || shipmentNo.isBlank()) return

        baleDownloadStarted = true
        _baleSyncState.value = PswReceiptSyncState.Loading

        viewModelScope.launch {
            val result = repository.downloadPswReceiptBales(shipmentNo)

            _baleSyncState.value = result.fold(
                onSuccess = {
                    PswReceiptSyncState.Success(it)
                },
                onFailure = {
                    baleDownloadStarted = false
                    PswReceiptSyncState.Error(
                        it.message ?: "PSW Bale download failed"
                    )
                }
            )
        }
    }
}
