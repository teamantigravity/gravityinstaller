package com.example.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BillingManager(private val context: Context, private val coroutineScope: CoroutineScope) : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null

    private val _isAdFree = MutableStateFlow(false)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()
    
    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    companion object {
        const val REMOVE_ADS_PRODUCT_ID = "remove_ads_lifetime" // Subscription or lifetime IAP product ID
        const val TAG = "BillingManager"
    }

    init {
        try {
            billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases()
                .build()
            startConnection()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BillingClient", e)
        }
    }

    private fun startConnection() {
        try {
            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Billing setup successful")
                        queryPurchases()
                        queryProductDetails()
                    } else {
                        Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Log.d(TAG, "Billing service disconnected, retrying...")
                    // In production, you might want to wait a bit before retrying
                    // startConnection() // Disable auto-retry to avoid infinite loops if it crashes
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to billing service (Play Store might be missing)", e)
        }
    }

    private fun queryProductDetails() {
        try {
            val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(REMOVE_ADS_PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.SUBS) // Or INAPP depending on strategy
                            .build()
                    )
                )
                .build()
            
            billingClient?.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _products.value = productDetailsList
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query product details", e)
        }
    }

    private fun queryPurchases() {
        try {
            if (billingClient?.isReady != true) {
                Log.e(TAG, "queryPurchases: BillingClient is not ready")
                return
            }
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    processPurchases(purchases)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query purchases", e)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled purchase flow.")
        } else {
            Log.e(TAG, "Error in purchase flow: ${billingResult.debugMessage}")
        }
    }

    private fun processPurchases(purchases: List<Purchase>?) {
        var isSubscribed = false
        purchases?.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                purchase.products.forEach { product ->
                    if (product == REMOVE_ADS_PRODUCT_ID) {
                        isSubscribed = true
                        // Acknowledge the purchase if not already done
                        if (!purchase.isAcknowledged) {
                            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                            billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { ackResult ->
                                if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                    Log.d(TAG, "Purchase acknowledged.")
                                }
                            }
                        }
                    }
                }
            }
        }
        _isAdFree.value = isSubscribed
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        try {
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .apply {
                        if (offerToken != null) {
                            setOfferToken(offerToken)
                        }
                    }
                    .build()
            )

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            billingClient?.launchBillingFlow(activity, billingFlowParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch billing flow", e)
        }
    }

    fun endConnection() {
        try {
            if (billingClient?.isReady == true) {
                billingClient?.endConnection()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end billing connection", e)
        }
    }
}
