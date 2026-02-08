package com.eventos.banana.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eventos.banana.data.repository.BillingRepository
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BillingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BillingRepository(application)

    val productDetails: StateFlow<Map<String, ProductDetails>> = repository.productDetails
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val billingSetupComplete: StateFlow<Boolean> = repository.billingSetupComplete
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun buyGold(activity: Activity): Boolean {
        return repository.launchBillingFlow(activity, BillingRepository.SUB_BANANA_GOLD)
    }

    fun buyEventBoost(activity: Activity, eventId: String): Boolean {
        return repository.launchBillingFlow(activity, BillingRepository.INAPP_EVENT_BOOST, eventId)
    }
    
    fun refreshSubscriptions() {
        repository.checkActiveSubscriptions()
    }
}
