package com.united.digitaldispatch.Apiservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.Login.models.MasterDataResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MasterDataViewModel(
    private val repository: MasterDataRepository
) : ViewModel() {

    private val _syncState =
        MutableStateFlow<MasterDataSyncState>(MasterDataSyncState.Idle)

    val syncState: StateFlow<MasterDataSyncState> = _syncState

    fun syncMasterData() {
        viewModelScope.launch {

            _syncState.value = MasterDataSyncState.Loading

            val result = repository.syncMasterData()

            _syncState.value = result.fold(
                onSuccess = { response ->
                    MasterDataSyncState.Success(response)
                },
                onFailure = { exception ->
                    MasterDataSyncState.Error(
                        exception.message ?: "Master data sync failed"
                    )
                }
            )
        }
    }
}

sealed class MasterDataSyncState {

    object Idle : MasterDataSyncState()

    object Loading : MasterDataSyncState()

    data class Success(
        val response: MasterDataResponse
    ) : MasterDataSyncState()

    data class Error(
        val message: String
    ) : MasterDataSyncState()
}