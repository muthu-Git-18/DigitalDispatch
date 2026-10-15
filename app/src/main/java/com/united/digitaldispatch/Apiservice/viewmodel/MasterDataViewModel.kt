package com.united.digitaldispatch.Apiservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository
import com.united.digitaldispatch.data.local.entity.StockEntity
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.ViewModelProvider
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

    // Stock screen state
    private val _stockCrops = MutableStateFlow<List<String>>(emptyList())
    val stockCrops: StateFlow<List<String>> = _stockCrops.asStateFlow()

    private val _stockVarieties = MutableStateFlow<List<String>>(emptyList())
    val stockVarieties: StateFlow<List<String>> = _stockVarieties.asStateFlow()

    private val _stockList = MutableStateFlow<List<StockEntity>>(emptyList())
    val stockList: StateFlow<List<StockEntity>> = _stockList.asStateFlow()

    private val _stockLoading = MutableStateFlow(false)
    val stockLoading: StateFlow<Boolean> = _stockLoading.asStateFlow()

    private val _stockError = MutableStateFlow<String?>(null)
    val stockError: StateFlow<String?> = _stockError.asStateFlow()


    // Load crops, varieties, and all locally stored stock
    fun loadStockScreen() {
        viewModelScope.launch {
            _stockLoading.value = true
            _stockError.value = null

            try {
                _stockCrops.value = repository.getLocalStockCrops()
                _stockVarieties.value = repository.getLocalStockVarieties(null)
                _stockList.value = repository.getLocalFilteredStock(null, null)
            } catch (e: Exception) {
                _stockError.value = e.message ?: "Failed to load stock"
            } finally {
                _stockLoading.value = false
            }
        }
    }

    // Update variety options when crop changes
    fun loadStockVarieties(crop: String?) {
        viewModelScope.launch {
            try {
                _stockVarieties.value =
                    repository.getLocalStockVarieties(crop)
            } catch (e: Exception) {
                _stockError.value = e.message ?: "Failed to load varieties"
            }
        }
    }

    // Apply the selected filters
    fun filterStock(crop: String?, variety: String?) {
        viewModelScope.launch {
            _stockLoading.value = true
            _stockError.value = null

            try {
                _stockList.value =
                    repository.getLocalFilteredStock(crop, variety)
            } catch (e: Exception) {
                _stockError.value = e.message ?: "Failed to filter stock"
            } finally {
                _stockLoading.value = false
            }
        }
    }

    // Clear filters and restore all stock
    fun clearStockFilters() {
        filterStock(crop = null, variety = null)
    }
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


class MasterDataViewModelFactory(
    private val repository: MasterDataRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(MasterDataViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MasterDataViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
