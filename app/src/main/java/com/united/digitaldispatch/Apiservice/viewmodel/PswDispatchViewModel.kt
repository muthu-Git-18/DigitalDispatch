package com.united.digitaldispatch.Apiservice.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Dispatch.models.BaleInfo
import com.united.digitaldispatch.PSWDispatch.models.CreatePSWDispatchHeaderRequest
import com.united.digitaldispatch.PSWDispatch.models.CreatePSWDispatchHeaderResponse
import com.united.digitaldispatch.data.local.entity.PSWDispatchDetailEntity
import com.united.digitaldispatch.data.local.entity.PswDispatchHeaderEntity
import com.united.digitaldispatch.utils.TimeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class PswDispatchViewModel(
    private val repository: MasterDataRepository
) : ViewModel() {

    private companion object {
        const val BALE_LENGTH = 31
        const val MAX_WEIGHT = 300.0

        // TODO: confirm with the backend (same open point as DISPATCH_PROCESS_TYPE)
        const val PSW_PROCESS_TYPE = "PSW_DISPATCH"
    }


    // ---------------------------------
    // CREATE PSW DISPATCH
    // ---------------------------------

    private val _createPswDispatchState =
        MutableStateFlow<CreatePSWDispatchState>(
            CreatePSWDispatchState.Idle
        )

    val createPswDispatchState: StateFlow<CreatePSWDispatchState> =
        _createPswDispatchState


    fun createPswDispatchHeader(
        request: CreatePSWDispatchHeaderRequest
    ) {

        viewModelScope.launch {

            _createPswDispatchState.value =
                CreatePSWDispatchState.Loading

            val result =
                repository.createPswDispatchHeader(request)

            // Save locally only when API creation succeeds
            if (result.isSuccess) {
                runCatching {
                    repository.saveCreatedPswHeaderLocally(request)
                }
            }

            _createPswDispatchState.value =
                result.fold(

                    onSuccess = { response ->
                        CreatePSWDispatchState.Success(response)
                    },

                    onFailure = { exception ->
                        CreatePSWDispatchState.Error(
                            exception.message
                                ?: "Create PSW dispatch failed"
                        )
                    }
                )
        }
    }


    fun resetCreatePswDispatchState() {

        _createPswDispatchState.value =
            CreatePSWDispatchState.Idle
    }


    // ---------------------------------
    // DELETE PSW DISPATCH HEADER
    // ---------------------------------

//    private val _deletePswHeaderState =
//        MutableStateFlow<DeletePswHeaderState>(
//            DeletePswHeaderState.Idle
//        )
//
//    val deletePswHeaderState: StateFlow<DeletePswHeaderState> =
//        _deletePswHeaderState
//
//
//    fun deletePswDispatchHeader(
//        shipmentNo: String
//    ) {
//
//        viewModelScope.launch {
//
//            _deletePswHeaderState.value =
//                DeletePswHeaderState.Loading
//
//            _deletePswHeaderState.value =
//                repository.deletePswDispatchHeader(shipmentNo)
//                    .fold(
//
//                        onSuccess = { message ->
//                            DeletePswHeaderState.Success(message)
//                        },
//
//                        onFailure = { exception ->
//                            DeletePswHeaderState.Error(
//                                exception.message
//                                    ?: "Failed to delete shipment"
//                            )
//                        }
//                    )
//        }
//    }
//
//
//    fun resetDeletePswHeaderState() {
//
//        _deletePswHeaderState.value =
//            DeletePswHeaderState.Idle
//    }


    // ---------------------------------
    // LOCAL PSW DISPATCH HEADERS
    // ---------------------------------

    private val _localPswHeaders =
        MutableStateFlow<List<PswDispatchHeaderEntity>>(
            emptyList()
        )

    val localPswHeaders: StateFlow<List<PswDispatchHeaderEntity>> =
        _localPswHeaders


    fun loadLocalPswHeaders() {

        viewModelScope.launch {

            _localPswHeaders.value =
                repository.getLocalPswDispatchHeaders()
        }
    }


    // ---------------------------------
    // PSW DISPATCH HEADERS SYNC
    // ---------------------------------

    private val _syncPswHeadersState =
        MutableStateFlow<SyncPswHeadersState>(
            SyncPswHeadersState.Idle
        )

    val syncPswHeadersState: StateFlow<SyncPswHeadersState> =
        _syncPswHeadersState


    fun syncPswDispatchHeaders(orgnCode: String) {
        viewModelScope.launch {

            _syncPswHeadersState.value =
                SyncPswHeadersState.Loading

            _syncPswHeadersState.value =
                repository
                    .syncPswDispatchHeaders(orgnCode)
                    .fold(
                        onSuccess = { count ->
                            SyncPswHeadersState.Success(count)
                        },
                        onFailure = { exception ->
                            SyncPswHeadersState.Error(
                                exception.message
                                    ?: "Failed to load PSW dispatch headers"
                            )
                        }
                    )
        }
    }


    fun resetSyncPswHeadersState() {

        _syncPswHeadersState.value =
            SyncPswHeadersState.Idle
    }


    // =================================================================
    // PSW DISPATCH DETAILS SCREEN
    // =================================================================

    // header of the open shipment (weighment type, sender, receiver ...)
    private val _header = MutableStateFlow<PswHeaderState>(PswHeaderState.Loading)
    val header: StateFlow<PswHeaderState> = _header

    fun loadPswHeader(shipmentNo: String) {
        viewModelScope.launch {
            val h = repository.getLocalPswDispatchHeader(shipmentNo)
            _header.value = if (h == null) PswHeaderState.Missing else PswHeaderState.Loaded(h)
        }
    }

    // bale lookup: this shipment -> other shipment -> local stock table
    private val _baleLookup = MutableStateFlow<PswBaleLookupState>(PswBaleLookupState.Idle)
    val baleLookup: StateFlow<PswBaleLookupState> = _baleLookup

    fun lookupPswBale(baleNo: String, shipmentNo: String) {
        viewModelScope.launch {

            val saved = repository.findPswDetailByBale(baleNo)

            _baleLookup.value = when {

                saved != null && saved.shipmentNo == shipmentNo ->
                    PswBaleLookupState.Existing(saved)

                saved != null ->
                    PswBaleLookupState.InOtherShipment(saved)

                else -> {
                    val info = repository.findBaleInStock(baleNo)

                    if (info == null) PswBaleLookupState.NotFound(baleNo)
                    else PswBaleLookupState.Found(info)
                }
            }
        }
    }

    fun resetBaleLookup() {
        _baleLookup.value = PswBaleLookupState.Idle
    }

    // scanned bales of the open shipment (local table psw_dispatch_detail)
    private val _details = MutableStateFlow<List<PSWDispatchDetailEntity>>(emptyList())
    val details: StateFlow<List<PSWDispatchDetailEntity>> = _details

    private var detailsJob: Job? = null

    fun loadPswDetails(shipmentNo: String) {
        detailsJob?.cancel()

        detailsJob = viewModelScope.launch {
            repository.observePswDetails(shipmentNo).collect { list ->
                _details.value = list
            }
        }
    }

    fun findPswDetail(baleNo: String): PSWDispatchDetailEntity? =
        _details.value.firstOrNull { it.baleNumber == baleNo }

    /** onSaved(true) when it was an update, false for a new bale */
    fun savePswDetail(detail: PSWDispatchDetailEntity, onSaved: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val wasUpdate = repository.savePswDispatchDetail(detail)
            onSaved(wasUpdate)
        }
    }

    fun deletePswDetail(detail: PSWDispatchDetailEntity, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deletePswDetailByBale(detail.shipmentNo, detail.baleNumber)
            onDeleted()
        }
    }
}




// ---------------------------------
// CREATE PSW DISPATCH STATE
// ---------------------------------

sealed class CreatePSWDispatchState {

    object Idle : CreatePSWDispatchState()

    object Loading : CreatePSWDispatchState()

    data class Success(
        val response: CreatePSWDispatchHeaderResponse
    ) : CreatePSWDispatchState()

    data class Error(
        val message: String
    ) : CreatePSWDispatchState()
}


// ---------------------------------
// DELETE PSW HEADER STATE
// ---------------------------------

sealed class DeletePswHeaderState {

    object Idle : DeletePswHeaderState()

    object Loading : DeletePswHeaderState()

    data class Success(
        val message: String
    ) : DeletePswHeaderState()

    data class Error(
        val message: String
    ) : DeletePswHeaderState()
}


// ---------------------------------
// SYNC PSW HEADERS STATE
// ---------------------------------

sealed class SyncPswHeadersState {

    object Idle : SyncPswHeadersState()

    object Loading : SyncPswHeadersState()

    data class Success(
        val count: Int
    ) : SyncPswHeadersState()

    data class Error(
        val message: String
    ) : SyncPswHeadersState()
}


// ---------------------------------
// PSW DETAILS STATES
// ---------------------------------

// ---------------------------------
// PSW DETAILS STATES
// ---------------------------------

sealed class PswHeaderState {
    object Loading : PswHeaderState()
    data class Loaded(val header: PswDispatchHeaderEntity) : PswHeaderState()
    object Missing : PswHeaderState()
}

sealed class PswBaleLookupState {
    object Idle : PswBaleLookupState()

    /** bale found in the local stock table */
    data class Found(val info: BaleInfo) : PswBaleLookupState()

    data class NotFound(val baleNo: String) : PswBaleLookupState()

    /** already scanned in THIS shipment */
    data class Existing(val detail: PSWDispatchDetailEntity) : PswBaleLookupState()

    /** already scanned in ANOTHER shipment */
    data class InOtherShipment(val detail: PSWDispatchDetailEntity) : PswBaleLookupState()
}