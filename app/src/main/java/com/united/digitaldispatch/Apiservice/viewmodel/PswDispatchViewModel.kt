package com.united.digitaldispatch.Apiservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.PSWDispatch.models.CreatePSWDispatchHeaderRequest
import com.united.digitaldispatch.PSWDispatch.models.CreatePSWDispatchHeaderResponse
import com.united.digitaldispatch.PSWDispatch.models.PSWDispatchHeaderItem
import com.united.digitaldispatch.data.local.entity.PswDispatchHeaderEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class PswDispatchViewModel(
    private val repository: MasterDataRepository
) : ViewModel() {

    // ---------------------------------
    // CREATE PSW DISPATCH
    // ---------------------------------

    private val _createPswDispatchState =
        MutableStateFlow<CreatePSWDispatchState>(
            CreatePSWDispatchState.Idle
        )

    val createPswDispatchState: StateFlow<CreatePSWDispatchState> = _createPswDispatchState

    fun createPswDispatchHeader(
        request: CreatePSWDispatchHeaderRequest
    ) {

        viewModelScope.launch {

            _createPswDispatchState.value =
                CreatePSWDispatchState.Loading

            val result =
                repository.createPswDispatchHeader(request)

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

    private val _localPswHeaders =
        MutableStateFlow<List<PswDispatchHeaderEntity>>(emptyList())

    val localPswHeaders: StateFlow<List<PswDispatchHeaderEntity>> =
        _localPswHeaders

    fun loadLocalPswHeaders() {

        viewModelScope.launch {

            _localPswHeaders.value =
                repository.getLocalPswDispatchHeaders()
        }
    }

    fun resetCreatePswDispatchState() {

        _createPswDispatchState.value = CreatePSWDispatchState.Idle
    }


    // ---------------------------------
    // PSW DISPATCH HEADERS
    // ---------------------------------

    private val _syncPswHeadersState =
        MutableStateFlow<SyncPswHeadersState>(
            SyncPswHeadersState.Idle
        )

    val syncPswHeadersState: StateFlow<SyncPswHeadersState> = _syncPswHeadersState


    fun syncPswDispatchHeaders(
        orgnCode: String
    ) {

        viewModelScope.launch {

            _syncPswHeadersState.value = SyncPswHeadersState.Loading

            _syncPswHeadersState.value = repository.syncPswDispatchHeaders(orgnCode)
                    .fold(
                        onSuccess = { count ->
                            SyncPswHeadersState.Success(
                                count
                            )
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

        _syncPswHeadersState.value = SyncPswHeadersState.Idle
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