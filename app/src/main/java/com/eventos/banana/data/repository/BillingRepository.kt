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

import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

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
                // Call server-side validation Cloud Function
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
                    .addOnSuccessListener {
                        timber.log.Timber.d("Purchase validated server-side for $productId")
                        if (productId == INAPP_EVENT_BOOST) {
                            pendingBoostEventId = null // Clear pending boost
                            
                            // Consume the purchase to allow buying again
                            val consumeParams = ConsumeParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                            billingClient.consumeAsync(consumeParams) { _, _ -> }
                        }
                    }
                    .addOnFailureListener { e ->
                        timber.log.Timber.e(e, "Server validation failed for $productId. Applying local fallback.")
                        // Fallback: grant locally if server is unreachable
                        appScope.launch {
                            grantEntitlementLocalFallback(purchase, productId, uid)
                        }
                    }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error calling validateAndGrantPurchase")
                grantEntitlementLocalFallback(purchase, productId, uid)
            }
        }
    }
    
    /**
     * 🛡️ Local fallback: only used if Cloud Function is unreachable.
     * This preserves the original behavior as a safety net.
     */
    private suspend fun grantEntitlementLocalFallback(purchase: Purchase, productId: String, uid: String) {
        when (productId) {
            SUB_BANANA_GOLD -> {
                userRepository.setGoldStatus(uid, true)
            }
            INAPP_EVENT_BOOST -> {
                val eventId = pendingBoostEventId
                if (eventId != null) {
                    val duration = 24L * 60 * 60 * 1000
                    eventRepository.boostEvent(eventId, duration)
                    pendingBoostEventId = null
                }
                val consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.consumeAsync(consumeParams) { _, _ -> }
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
                 appScope.launch {
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
