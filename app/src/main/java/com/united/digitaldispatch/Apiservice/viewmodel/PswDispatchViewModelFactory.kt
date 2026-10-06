package com.united.digitaldispatch.Apiservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository

class PswDispatchViewModelFactory(
    private val repository: MasterDataRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(PswDispatchViewModel::class.java)) {

            @Suppress("UNCHECKED_CAST")
            return PswDispatchViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}