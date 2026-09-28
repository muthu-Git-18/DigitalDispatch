package com.united.digitaldispatch.Apiservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.united.digitaldispatch.Apiservice.repository.MasterDataRepository

class DispatchViewModelFactory(
    private val repository: MasterDataRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(DispatchViewModel::class.java)) {

            @Suppress("UNCHECKED_CAST")
            return DispatchViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}