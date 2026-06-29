package app.pwhs.universalinstaller.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import timber.log.Timber
import app.pwhs.universalinstaller.billing.BillingManager

object AdManager {
    private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-4989086156410627/1105830543"
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-4989086156410627/4094955965"

    private var appOpenAd: AppOpenAd? = null
    private var isShowingAppOpenAd = false
    private var isLoadingAppOpenAd = false

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitialAd = false
    private var lastInterstitialTime: Long = 0
    private const val INTERSTITIAL_COOLDOWN_MS = 3 * 60 * 1000L // 3 minutes

    fun loadAppOpenAd(context: Context) {
        if (isLoadingAppOpenAd || isAppOpenAdAvailable()) {
            return
        }
        
        if (BillingManager.getInstance(context).hasPurchasedRemoveAds.value) {
            return // Ads are disabled
        }

        isLoadingAppOpenAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            APP_OPEN_AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAppOpenAd = false
                    Timber.d("AppOpenAd loaded.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAppOpenAd = false
                    Timber.e("AppOpenAd failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    private fun isAppOpenAdAvailable(): Boolean {
        return appOpenAd != null
    }

    fun showAppOpenAdIfAvailable(activity: Activity) {
        if (BillingManager.getInstance(activity).hasPurchasedRemoveAds.value) {
            return
        }

        if (!isShowingAppOpenAd && isAppOpenAdAvailable()) {
            appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isShowingAppOpenAd = false
                    loadAppOpenAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    appOpenAd = null
                    isShowingAppOpenAd = false
                    loadAppOpenAd(activity)
                }

                override fun onAdShowedFullScreenContent() {
                    isShowingAppOpenAd = true
                }
            }
            appOpenAd?.show(activity)
        } else {
            loadAppOpenAd(activity)
        }
    }

    fun loadInterstitialAd(context: Context) {
        if (isLoadingInterstitialAd || interstitialAd != null) {
            return
        }
        
        if (BillingManager.getInstance(context).hasPurchasedRemoveAds.value) {
            return
        }

        isLoadingInterstitialAd = true
        val request = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    isLoadingInterstitialAd = false
                    Timber.e("InterstitialAd failed to load: ${adError.message}")
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitialAd = false
                    Timber.d("InterstitialAd loaded.")
                }
            }
        )
    }

    fun showInterstitialAd(activity: Activity, onAdClosed: () -> Unit = {}) {
        if (BillingManager.getInstance(activity).hasPurchasedRemoveAds.value) {
            onAdClosed()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastInterstitialTime < INTERSTITIAL_COOLDOWN_MS) {
            Timber.d("Interstitial ad is on cooldown.")
            onAdClosed()
            return
        }

        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    lastInterstitialTime = System.currentTimeMillis()
                    loadInterstitialAd(activity)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    onAdClosed()
                }
            }
            interstitialAd?.show(activity)
        } else {
            loadInterstitialAd(activity)
            onAdClosed()
        }
    }
}
