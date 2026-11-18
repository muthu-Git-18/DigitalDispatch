package com.united.digitaldispatch.Apiservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.united.digitaldispatch.Apiservice.repository.ReceiptRepository

class ReceiptViewModelFactory(
    private val repository: ReceiptRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ReceiptViewModel::class.java))
        return ReceiptViewModel(repository) as T
    }
}