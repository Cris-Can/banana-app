package com.eventos.banana.data.repository

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.eventos.banana.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillingRepository(
    private val context: Context,
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: com.eventos.banana.data.repository.AuthRepository = com.eventos.banana.data.repository.AuthRepository(),
    private val eventRepository: com.eventos.banana.data.repository.EventRepository = com.eventos.banana.data.repository.EventRepository()
) : PurchasesUpdatedListener {

    private val _billingSetupComplete = MutableStateFlow(false)
    val billingSetupComplete: StateFlow<Boolean> = _billingSetupComplete

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails
    
    // TEMPORARY: Store event ID for boost purchase (Risk: Process death clears this)
    // FIXED: Now using SharedPreferences to survive process death
    private val prefs = context.getSharedPreferences("billing_prefs", Context.MODE_PRIVATE)

    private var pendingBoostEventId: String?
        get() = prefs.getString("pending_boost_event_id", null)
        set(value) {
            prefs.edit().putString("pending_boost_event_id", value).apply()
        }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    // 💰 PRODUCT IDs (Must match Google Play Console)
    companion object {
        const val SUB_BANANA_GOLD = "banana_plus_monthly"
        const val INAPP_EVENT_BOOST = "event_boost_24h"
    }

    init {
        startConnection()
    }

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingSetupComplete.value = true
                    queryProductDetails()
                    checkActiveSubscriptions()
                    // Check for pending purchases that might have finished while app was dead
                    billingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder()
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    ) { result, purchases ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            purchases.forEach { handlePurchase(it) }
                        }
                    }
                } else {
                    android.util.Log.e("BillingRepository", "Setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingSetupComplete.value = false
                // Retry logic could go here
            }
        })
    }

    private fun queryProductDetails() {
        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SUB_BANANA_GOLD)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()

        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(INAPP_EVENT_BOOST)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()

        billingClient.queryProductDetailsAsync(subParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                updateProductMap(productDetailsList)
            }
        }
        
        billingClient.queryProductDetailsAsync(inAppParams) { billingResult, productDetailsList ->
             if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                updateProductMap(productDetailsList)
            }
        }
    }

    private fun updateProductMap(newList: List<ProductDetails>) {
        val current = _productDetails.value.toMutableMap()
        newList.forEach { current[it.productId] = it }
        _productDetails.value = current
    }

    fun launchBillingFlow(activity: Activity, productId: String, eventId: String? = null): Boolean {
        val productDetails = _productDetails.value[productId]
        if (productDetails == null) {
            android.util.Log.e("BillingRepository", "Product not found: $productId. Check Google Play Console.")
            return false
        }
        
        // Store Pending Event ID Persistently
        if (eventId != null) {
            pendingBoostEventId = eventId
        }

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
        
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .apply {
                if (offerToken.isNotEmpty()) setOfferToken(offerToken)
            }
            .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
        return true
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // User canceled
        } else {
            // Handle other errors
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        // Grant entitlement
                         CoroutineScope(Dispatchers.IO).launch {
                            grantEntitlement(purchase)
                        }
                    }
                }
            } else {
                 CoroutineScope(Dispatchers.IO).launch {
                    grantEntitlement(purchase)
                }
            }
        }
    }
    
    // 🔥 Grant Logic (Sync with Firestore)
    private suspend fun grantEntitlement(purchase: Purchase) {
        val uid = authRepository.currentUid() ?: return
        
        purchase.products.forEach { productId ->
            when (productId) {
                SUB_BANANA_GOLD -> {
                    // Activate Gold Logic
                    userRepository.setGoldStatus(uid, true)
                }
                INAPP_EVENT_BOOST -> {
                    // 1. Apply Boost Logic (Server preferred, client fallback)
                    val eventId = pendingBoostEventId
                    if (eventId != null) {
                         // 24h Boost
                         val duration = 24L * 60 * 60 * 1000
                         eventRepository.boostEvent(eventId, duration)
                         pendingBoostEventId = null // Clear from prefs
                    } else {
                        android.util.Log.e("BillingRepository", "Boost purchased but no EventID pending!")
                        // TODO: Handle "lost" purchase (maybe refund or generic credit?)
                    }
                    
                    // 2. Consume Purchase to allow buying again
                    val consumeParams = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                        
                     billingClient.consumeAsync(consumeParams) { result, token ->
                         if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                             android.util.Log.d("BillingRepository", "Boost Consumed Successfully")
                         }
                     }
                }
            }
        }
    }
    
    fun checkActiveSubscriptions() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases -> 
             if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                 val hasGold = purchases.any { it.products.contains(SUB_BANANA_GOLD) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                 CoroutineScope(Dispatchers.IO).launch {
                     val uid = authRepository.currentUid()
                     if (uid != null) {
                        // 🛡️ PROTECTION: Don't downgrade Founders (force server read)
                         val profile = userRepository.getUserProfile(uid, forceRefresh = true)
                         val isFounder = profile?.isFounder == true
                         
                         if (isFounder) {
                             android.util.Log.d("BillingRepository", "User is Founder. Skipping Google Play sync. Ensuring FOUNDER type.")
                             userRepository.setGoldStatus(uid, true) // Will auto-repair to FOUNDER
                         } else {
                             userRepository.setGoldStatus(uid, hasGold)
                         }
                     }
                 }
             }
        }
    }
}
