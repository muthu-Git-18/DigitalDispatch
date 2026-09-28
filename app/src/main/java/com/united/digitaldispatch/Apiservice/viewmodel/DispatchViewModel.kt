package com.united.digitaldispatch.Apiservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Dispatch.models.CreateDispatchHeaderRequest
import com.united.digitaldispatch.Dispatch.models.CreateDispatchHeaderResponse
import com.united.digitaldispatch.data.local.entity.TruckMasterEntity
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
        toOrgn: String
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