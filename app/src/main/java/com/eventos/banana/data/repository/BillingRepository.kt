package com.eventos.banana.data.repository

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Subscription state management
 */
enum class SubscriptionState {
    ACTIVE,         // Currently valid subscription
    EXPIRED,        // Subscription has expired
    CANCELED,       // User canceled, valid until expiryTimeMillis
    PENDING,        // Payment pending
    ON_HOLD,        // Account hold (billing issue)
    PAUSED,         // Paused subscription
    UNKNOWN         // Unable to determine state
}

class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    private val authRepository: com.eventos.banana.data.repository.AuthRepository,
    private val eventRepository: com.eventos.banana.data.repository.EventRepository,
    @com.eventos.banana.di.ApplicationScope private val appScope: CoroutineScope
) : PurchasesUpdatedListener {

    private val _billingSetupComplete = MutableStateFlow(false)
    val billingSetupComplete: StateFlow<Boolean> = _billingSetupComplete

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails
    
    private val _subscriptionState = MutableStateFlow<SubscriptionState>(SubscriptionState.UNKNOWN)
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState
    
    private val _subscriptionExpiryMillis = MutableStateFlow<Long?>(null)
    val subscriptionExpiryMillis: StateFlow<Long?> = _subscriptionExpiryMillis
    
    // Store event ID for boost purchase using SharedPreferences to survive process death
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
        const val SUB_BANANA_GOLD = "banana_plus_subscription"
        const val INAPP_EVENT_BOOST = "event_boost_24h"
        const val INAPP_CREDITS_3PACK = "rating_credits_3pack"
        
        // Periodic verification interval (1 hour)
        private const val PERIODIC_VERIFICATION_INTERVAL_MS = 60 * 60 * 1000L
    }

    init {
        startConnection()
        schedulePeriodicVerification()
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
                    Timber.e("BillingRepository", "Setup failed: ${billingResult.debugMessage}")
                    // Retry after delay
                    appScope.launch {
                        kotlinx.coroutines.delay(5000)
                        if (!_billingSetupComplete.value) {
                            startConnection()
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingSetupComplete.value = false
                Timber.d("BillingRepository", "Billing service disconnected, will retry")
            }
        })
    }
    
    /**
     * Schedule periodic subscription verification
     * Runs every hour to check subscription status
     */
    private fun schedulePeriodicVerification() {
        appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(PERIODIC_VERIFICATION_INTERVAL_MS)
                if (_billingSetupComplete.value) {
                    verifySubscriptionStatus()
                }
            }
        }
    }
    
    /**
     * Verify subscription status by querying Google Play
     */
    private fun verifySubscriptionStatus() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                val goldPurchase = purchases.find { it.products.contains(SUB_BANANA_GOLD) }
                
                if (goldPurchase != null) {
                    val state = determineSubscriptionState(goldPurchase)
                    _subscriptionState.value = state
                    _subscriptionExpiryMillis.value = goldPurchase.purchaseTime
                    
                    Timber.d("BillingRepository", "Subscription verification: state=$state")
                    
                    // Update user profile based on state
                    appScope.launch {
                        val uid = authRepository.currentUid() ?: return@launch
                        when (state) {
                            SubscriptionState.ACTIVE, SubscriptionState.CANCELED -> {
                                // Subscription is valid (either active or canceled but not expired)
                                userRepository.setGoldStatus(uid, true)
                            }
                            SubscriptionState.EXPIRED, SubscriptionState.ON_HOLD, SubscriptionState.PAUSED -> {
                                // Subscription is no longer valid
                                userRepository.setGoldStatus(uid, false)
                            }
                            else -> { /* Keep current status */ }
                        }
                    }
                } else {
                    // No subscription found
                    _subscriptionState.value = SubscriptionState.UNKNOWN
                    _subscriptionExpiryMillis.value = null
                }
            }
        }
    }
    
    /**
     * Determine subscription state from purchase data
     */
    private fun determineSubscriptionState(purchase: Purchase): SubscriptionState {
        return when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                // Check if auto-renewing is enabled
                val purchaseData = purchase.originalJson
                if (purchaseData != null) {
                    try {
                        val json = org.json.JSONObject(purchaseData)
                        val autoRenewing = json.optBoolean("autoRenewing", true)
                        val expiryTimeMillis = json.optLong("expiryTimeMillis", 0)
                        
                        if (System.currentTimeMillis() > expiryTimeMillis) {
                            return SubscriptionState.EXPIRED
                        }
                        
                        return if (autoRenewing) SubscriptionState.ACTIVE else SubscriptionState.CANCELED
                    } catch (e: org.json.JSONException) {
                        Timber.e(e, "Failed to parse purchase data")
                    }
                }
                SubscriptionState.ACTIVE
            }
            Purchase.PurchaseState.PENDING -> SubscriptionState.PENDING
            else -> SubscriptionState.UNKNOWN
        }
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
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(INAPP_CREDITS_3PACK)
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
                         appScope.launch {
                            grantEntitlement(purchase)
                        }
                    }
                }
            } else {
                 appScope.launch {
                    grantEntitlement(purchase)
                }
            }
        }
    }
    
    // 🔥 Grant Logic — Delegated to Server-Side Cloud Function (C2 Audit Fix)
    private suspend fun grantEntitlement(purchase: Purchase) {
        val uid = authRepository.currentUid() ?: return
        
        purchase.products.forEach { productId ->
            try {
                val data = hashMapOf(
                    "purchaseToken" to purchase.purchaseToken,
                    "productId" to productId,
                    "packageName" to context.packageName,
                    "eventId" to (if (productId == INAPP_EVENT_BOOST) pendingBoostEventId else null)
                )
                
                val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
                functions
                    .getHttpsCallable("validateAndGrantPurchase")
                    .call(data)
                    .await()
                
                Timber.d("Purchase validated server-side for $productId")
                if (productId == INAPP_EVENT_BOOST) {
                    pendingBoostEventId = null
                    consumePurchase(purchase.purchaseToken)
                } else if (productId == INAPP_CREDITS_3PACK) {
                    consumePurchase(purchase.purchaseToken)
                }
            } catch (e: Exception) {
                Timber.e(e, "Server validation failed for $productId.")
            }
        }
    }

    private fun consumePurchase(purchaseToken: String) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        billingClient.consumeAsync(consumeParams) { _, _ -> }
    }
    
    fun checkActiveSubscriptions() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases -> 
             if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                 val goldPurchase = purchases.find { it.products.contains(SUB_BANANA_GOLD) }
                 
                 if (goldPurchase != null) {
                     val state = determineSubscriptionState(goldPurchase)
                     _subscriptionState.value = state
                     
                     appScope.launch {
                         val uid = authRepository.currentUid()
                         if (uid != null) {
                             // 🛡️ PROTECTION: Don't downgrade Founders (force server read)
                             val profile = userRepository.getUserProfile(uid, forceRefresh = true)
                             val isFounder = profile?.isFounder == true
                             
                             if (isFounder) {
                                 Timber.d("BillingRepository", "User is Founder. Skipping Google Play sync. Ensuring FOUNDER type.")
                                 userRepository.setGoldStatus(uid, true) // Will auto-repair to FOUNDER
                             } else {
                                 // Handle subscription state
                                 when (state) {
                                     SubscriptionState.ACTIVE, SubscriptionState.CANCELED -> {
                                         // Subscription is valid
                                         userRepository.setGoldStatus(uid, true)
                                     }
                                     SubscriptionState.EXPIRED, SubscriptionState.ON_HOLD, SubscriptionState.PAUSED -> {
                                         // Subscription is no longer valid
                                         userRepository.setGoldStatus(uid, false)
                                     }
                                     else -> {
                                         // Keep current status
                                     }
                                 }
                             }
                         }
                     }
                 } else {
                     // No active subscription
                     _subscriptionState.value = SubscriptionState.UNKNOWN
                 }
             }
        }
    }
    
    /**
     * Get current subscription status for UI display
     */
    fun getSubscriptionInfo(): SubscriptionInfo {
        return SubscriptionInfo(
            state = _subscriptionState.value,
            expiryMillis = _subscriptionExpiryMillis.value,
            productId = if (_subscriptionState.value == SubscriptionState.ACTIVE) SUB_BANANA_GOLD else null
        )
    }
    
    /**
     * Check if user currently has active premium
     */
    suspend fun hasActivePremium(): Boolean {
        val uid = authRepository.currentUid() ?: return false
        val profile = userRepository.getUserProfile(uid)
        
        // Check local profile first
        if (profile?.isGold == true) {
            return true
        }
        
        // Check subscription state
        return _subscriptionState.value == SubscriptionState.ACTIVE || _subscriptionState.value == SubscriptionState.CANCELED
    }
    
    /**
     * Data class for subscription info
     */
    data class SubscriptionInfo(
        val state: SubscriptionState,
        val expiryMillis: Long?,
        val productId: String?
    ) {
        val isPremium: Boolean
            get() = state == SubscriptionState.ACTIVE || state == SubscriptionState.CANCELED
            
        val timeUntilExpiry: Long?
            get() = expiryMillis?.let { it - System.currentTimeMillis() }
    }
}
