package com.united.digitaldispatch.Apiservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.united.digitaldispatch.Apiservice.repository.PswReceiptRepository

class PswReceiptViewModelFactory(
    private val repository: PswReceiptRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PswReceiptViewModel::class.java)) {
            return PswReceiptViewModel(repository) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}