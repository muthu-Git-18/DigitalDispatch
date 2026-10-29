package com.united.digitaldispatch.Apiservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Dispatch.models.BaleDetail
import com.united.digitaldispatch.Dispatch.models.BaleInfo
import com.united.digitaldispatch.sync.NetworkMonitor
import com.united.digitaldispatch.Dispatch.models.CreateDispatchHeaderRequest
import com.united.digitaldispatch.Dispatch.models.CreateDispatchHeaderResponse
import com.united.digitaldispatch.data.local.entity.DispatchHeaderEntity
import com.united.digitaldispatch.data.local.entity.TruckMasterEntity
import com.united.digitaldispatch.sync.SyncEvents
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DispatchViewModel(
    private val repository: MasterDataRepository
) : ViewModel() {

    // -----------------------------
    // CREATE DISPATCH
    // -----------------------------

    private val _createDispatchState =
        MutableStateFlow<CreateDispatchState>(
            CreateDispatchState.Idle
        )

    val createDispatchState: StateFlow<CreateDispatchState> =
        _createDispatchState

    fun createDispatchHeader(
        request: CreateDispatchHeaderRequest
    ) {

        viewModelScope.launch {

            _createDispatchState.value =
                CreateDispatchState.Loading

            val result =
                repository.createDispatchHeader(request)

            // keep the local ref-id list in step with the server
            if (result.isSuccess) {
                runCatching { repository.saveCreatedHeaderLocally(request) }
            }

            _createDispatchState.value = result.fold(

                onSuccess = { response ->
                    CreateDispatchState.Success(response)
                },

                onFailure = { exception ->
                    CreateDispatchState.Error(
                        exception.message
                            ?: "Create dispatch failed"
                    )
                }
            )
        }
    }

    fun resetCreateDispatchState() {
        _createDispatchState.value = CreateDispatchState.Idle
    }


    // -----------------------------
    // DISPATCH HEADERS (ref id list)
    // -----------------------------

    private val _syncHeadersState =
        MutableStateFlow<SyncHeadersState>(SyncHeadersState.Idle)

    val syncHeadersState: StateFlow<SyncHeadersState> = _syncHeadersState

    fun syncDispatchHeaders(orgnCode: String) {

        viewModelScope.launch {

            _syncHeadersState.value = SyncHeadersState.Loading

            _syncHeadersState.value =
                repository.syncDispatchHeaders(orgnCode).fold(
                    onSuccess = { count -> SyncHeadersState.Success(count) },
                    onFailure = { e ->
                        SyncHeadersState.Error(
                            e.message ?: "Failed to load dispatch headers"
                        )
                    }
                )
        }
    }

    fun resetSyncHeadersState() {
        _syncHeadersState.value = SyncHeadersState.Idle
    }

    private val _localHeaders =
        MutableStateFlow<List<DispatchHeaderEntity>>(emptyList())

    val localHeaders: StateFlow<List<DispatchHeaderEntity>> = _localHeaders

    fun loadLocalHeaders() {
        viewModelScope.launch {
            _localHeaders.value = repository.getLocalDispatchHeaders()
        }
    }


    // -----------------------------
    // DELETE DISPATCH HEADER
    // -----------------------------

    private val _deleteHeaderState =
        MutableStateFlow<DeleteHeaderState>(DeleteHeaderState.Idle)

    val deleteHeaderState: StateFlow<DeleteHeaderState> = _deleteHeaderState
/*

    fun deleteDispatchHeader(shipmentNo: String) {

        viewModelScope.launch {

            _deleteHeaderState.value = DeleteHeaderState.Loading

            _deleteHeaderState.value =
                repository.deleteDispatchHeader(shipmentNo).fold(
                    onSuccess = { message -> DeleteHeaderState.Success(message) },
                    onFailure = { e ->
                        DeleteHeaderState.Error(
                            e.message ?: "Failed to delete shipment"
                        )
                    }
                )
        }
    }
*/

    fun resetDeleteHeaderState() {
        _deleteHeaderState.value = DeleteHeaderState.Idle
    }


    // -----------------------------
    // DISPATCH DETAILS SCREEN
    // -----------------------------

    // header of the open shipment (weighment type, sender, receiver ...)
    private val _header = MutableStateFlow<HeaderState>(HeaderState.Loading)
    val header: StateFlow<HeaderState> = _header

    fun loadHeader(shipmentNo: String) {
        viewModelScope.launch {
            val h = repository.getLocalDispatchHeader(shipmentNo)
            _header.value = if (h == null) HeaderState.Missing else HeaderState.Loaded(h)
        }
    }

    // bale lookup: this shipment -> other shipment -> local stock table
    private val _baleLookup = MutableStateFlow<BaleLookupState>(BaleLookupState.Idle)
    val baleLookup: StateFlow<BaleLookupState> = _baleLookup

    fun lookupBale(baleNo: String, shipmentNo: String) {
        viewModelScope.launch {

            val saved = repository.findDetailByBale(baleNo)

            _baleLookup.value = when {

                saved != null && saved.shipmentNo == shipmentNo ->
                    BaleLookupState.Existing(saved)

                saved != null ->
                    BaleLookupState.InOtherShipment(saved)

                else -> {
                    val info = repository.findBaleInStock(baleNo)

                    if (info == null) BaleLookupState.NotFound(baleNo)
                    else BaleLookupState.Found(info)
                }
            }
        }
    }

    fun resetBaleLookup() {
        _baleLookup.value = BaleLookupState.Idle
    }

    // scanned bales of the open shipment (local table dispatch_detail)
    private val _details = MutableStateFlow<List<BaleDetail>>(emptyList())
    val details: StateFlow<List<BaleDetail>> = _details

    private var detailsJob: Job? = null

    fun loadDetails(shipmentNo: String) {
        detailsJob?.cancel()

        detailsJob = viewModelScope.launch {
            repository.getLocalDetails(shipmentNo).collect { list ->
                _details.value = list
            }
        }
    }

    fun findDetail(baleNo: String): BaleDetail? =
        _details.value.firstOrNull {
            it.gpilBaleNumber == baleNo
        }

    fun saveDetail(detail: BaleDetail, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveDispatchDetail(detail)
            onSaved()
        }
    }

    fun deleteDetail(detail: BaleDetail, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteDispatchDetail(detail.detailId)
            onDeleted()
        }
    }

    fun pullChanges(shipmentNo: String) {
        viewModelScope.launch {
            if (NetworkMonitor.isHighQuality.value != true) return@launch

            val applied = repository.pullDispatchChanges(shipmentNo).getOrDefault(0)

            // the Details screen listens to this and reloads its list
            if (applied > 0) SyncEvents.detailsChanged.tryEmit(shipmentNo)
        }
    }
    // -----------------------------
    // TRUCK MASTER
    // -----------------------------

    private val _truckMasterState =
        MutableStateFlow<TruckMasterState>(
            TruckMasterState.Idle
        )

    val truckMasterState: StateFlow<TruckMasterState> =
        _truckMasterState


    fun loadTruckTypes(
        fromOrgn: String,
        toOrgn: String?
    ) {

        viewModelScope.launch {

            _truckMasterState.value =
                TruckMasterState.Loading

            try {

                val trucks =
                    repository.getTruckTypes(
                        fromOrgn = fromOrgn,
                        toOrgn = toOrgn
                    )

                _truckMasterState.value =
                    TruckMasterState.Success(trucks)

            } catch (e: Exception) {

                _truckMasterState.value =
                    TruckMasterState.Error(
                        e.message ?: "Failed to load truck types"
                    )
            }
        }
    }

    // -----------------------------
    // RECEIVER ORGANIZATIONS
    // -----------------------------

    private val _receiverOrganizationsState =
        MutableStateFlow<ReceiverOrganizationsState>(
            ReceiverOrganizationsState.Idle
        )

    val receiverOrganizationsState: StateFlow<ReceiverOrganizationsState> =
        _receiverOrganizationsState

    fun loadReceiverOrganizations() {
        viewModelScope.launch {
            _receiverOrganizationsState.value =
                ReceiverOrganizationsState.Loading

            try {
                val organizations =
                    repository.getTruckMasterOrganizations()

                _receiverOrganizationsState.value =
                    ReceiverOrganizationsState.Success(organizations)

            } catch (e: Exception) {
                _receiverOrganizationsState.value =
                    ReceiverOrganizationsState.Error(
                        e.message ?: "Failed to load receiver organizations"
                    )
            }
        }
    }
}


// ---------------------------------
// CREATE DISPATCH STATE
// ---------------------------------

sealed class CreateDispatchState {

    object Idle : CreateDispatchState()

    object Loading : CreateDispatchState()

    data class Success(
        val response: CreateDispatchHeaderResponse
    ) : CreateDispatchState()

    data class Error(
        val message: String
    ) : CreateDispatchState()
}


// ---------------------------------
// SYNC HEADERS STATE
// ---------------------------------

sealed class SyncHeadersState {

    object Idle : SyncHeadersState()

    object Loading : SyncHeadersState()

    data class Success(
        val count: Int
    ) : SyncHeadersState()

    data class Error(
        val message: String
    ) : SyncHeadersState()
}


// ---------------------------------
// TRUCK MASTER STATE
// ---------------------------------

sealed class TruckMasterState {

    object Idle : TruckMasterState()

    object Loading : TruckMasterState()

    data class Success(
        val trucks: List<TruckMasterEntity>
    ) : TruckMasterState()

    data class Error(
        val message: String
    ) : TruckMasterState()
}

sealed class ReceiverOrganizationsState {
    object Idle : ReceiverOrganizationsState()
    object Loading : ReceiverOrganizationsState()

    data class Success(
        val organizations: List<String>
    ) : ReceiverOrganizationsState()

    data class Error(
        val message: String
    ) : ReceiverOrganizationsState()
}


// ---------------------------------
// DELETE HEADER STATE
// ---------------------------------

sealed class DeleteHeaderState {

    object Idle : DeleteHeaderState()

    object Loading : DeleteHeaderState()

    data class Success(
        val message: String
    ) : DeleteHeaderState()

    data class Error(
        val message: String
    ) : DeleteHeaderState()
}


// ---------------------------------
// DISPATCH DETAILS STATES
// ---------------------------------

sealed class HeaderState {
    object Loading : HeaderState()
    data class Loaded(val header: DispatchHeaderEntity) : HeaderState()
    object Missing : HeaderState()
}

sealed class BaleLookupState {
    object Idle : BaleLookupState()

    /** bale found in the local stock table */
    data class Found(val info: BaleInfo) : BaleLookupState()

    data class NotFound(val baleNo: String) : BaleLookupState()

    /** already scanned in THIS shipment */
    data class Existing(val detail: BaleDetail) : BaleLookupState()

    /** already scanned in ANOTHER shipment */
    data class InOtherShipment(val detail: BaleDetail) : BaleLookupState()
}