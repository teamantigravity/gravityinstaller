package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"

    var isAdFree = false

    private var interstitialAd: InterstitialAd? = null
    private var appOpenAd: AppOpenAd? = null
    private var isInitializing = false
    private var isInitialized = false

    fun initialize(context: Context, onComplete: () -> Unit = {}) {
        if (isAdFree) {
            onComplete()
            return
        }
        if (isInitialized || isInitializing) {
            onComplete()
            return
        }
        isInitializing = true
        Log.d(TAG, "Initializing MobileAds SDK...")
        
        try {
            MobileAds.initialize(context) { initializationStatus ->
                isInitialized = true
                isInitializing = false
                Log.d(TAG, "MobileAds SDK Initialized: ${initializationStatus.adapterStatusMap}")
                
                Handler(Looper.getMainLooper()).post {
                    // Preload interstitial and app open ads
                    loadInterstitial(context)
                    loadAppOpen(context)
                    onComplete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MobileAds SDK", e)
            isInitializing = false
            onComplete()
        }
    }

    fun loadInterstitial(context: Context) {
        if (isAdFree) return
        val adUnitId = "ca-app-pub-4989086156410627/4094955965"
        if (adUnitId.isEmpty()) {
            Log.w(TAG, "Interstitial Ad Unit ID is empty.")
            return
        }
        Log.d(TAG, "Loading Interstitial Ad with ID: $adUnitId")
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial Ad Loaded Successfully.")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Failed to load Interstitial Ad: ${error.message}")
                    interstitialAd = null
                }
            }
        )
    }

    fun showInterstitialIfAvailable(activity: Activity, onAdClosed: () -> Unit) {
        if (isAdFree) {
            onAdClosed()
            return
        }
        val ad = interstitialAd
        if (ad != null) {
            Log.d(TAG, "Showing Interstitial Ad.")
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial Ad Dismissed.")
                    interstitialAd = null
                    loadInterstitial(activity) // reload for next time
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    Log.e(TAG, "Failed to show Interstitial Ad: ${error.message}")
                    interstitialAd = null
                    loadInterstitial(activity) // reload for next time
                    onAdClosed()
                }
            }
            ad.show(activity)
        } else {
            Log.d(TAG, "Interstitial Ad not available. Loading one now...")
            loadInterstitial(activity)
            onAdClosed()
        }
    }

    fun loadAppOpen(context: Context) {
        if (isAdFree) return
        val adUnitId = "ca-app-pub-4989086156410627/1105830543"
        if (adUnitId.isEmpty()) {
            Log.w(TAG, "App Open Ad Unit ID is empty.")
            return
        }
        Log.d(TAG, "Loading App Open Ad with ID: $adUnitId")
        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            adUnitId,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    Log.d(TAG, "App Open Ad Loaded Successfully.")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Failed to load App Open Ad: ${error.message}")
                    appOpenAd = null
                }
            }
        )
    }

    fun showAppOpenIfAvailable(activity: Activity, onAdClosed: () -> Unit = {}) {
        if (isAdFree) {
            onAdClosed()
            return
        }
        val ad = appOpenAd
        if (ad != null) {
            Log.d(TAG, "Showing App Open Ad.")
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "App Open Ad Dismissed.")
                    appOpenAd = null
                    loadAppOpen(activity) // reload for next time
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    Log.e(TAG, "Failed to show App Open Ad: ${error.message}")
                    appOpenAd = null
                    loadAppOpen(activity) // reload for next time
                    onAdClosed()
                }
            }
            ad.show(activity)
        } else {
            Log.d(TAG, "App Open Ad not available. Loading one now...")
            loadAppOpen(activity)
            onAdClosed()
        }
    }
}

@Composable
fun AdmobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-4989086156410627/2211450115"
) {
    if (AdManager.isAdFree || adUnitId.isEmpty()) {
        return
    }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
                adListener = object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("AdmobBanner", "Banner Ad Failed to Load: ${error.message}")
                    }
                    override fun onAdLoaded() {
                        Log.d("AdmobBanner", "Banner Ad Loaded Successfully.")
                    }
                }
            }
        },
        update = { adView ->
            // Banner is managed automatically by the AdView lifecycle
        }
    )
}
