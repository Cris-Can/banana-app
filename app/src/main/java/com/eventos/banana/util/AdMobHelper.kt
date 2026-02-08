package com.eventos.banana.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdMobHelper {
    private const val TAG = "AdMobHelper"
    // PRODUCTION ID for Rewarded Ads
    private const val AD_UNIT_ID = "ca-app-pub-5515224074639337/9220208729"

    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false

    fun initialize(context: Context) {
        MobileAds.initialize(context) { initializationStatus ->
            Log.d(TAG, "AdMob initialized: $initializationStatus")
        }
        loadRewardedAd(context)
    }

    fun loadRewardedAd(context: Context) {
        if (rewardedAd != null || isAdLoading) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AD_UNIT_ID, // Use constant
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, adError.toString())
                    rewardedAd = null
                    isAdLoading = false
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Ad was loaded.")
                    rewardedAd = ad
                    isAdLoading = false
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        onUserEarnedReward: () -> Unit,
        onAdDismissed: () -> Unit
    ) {
        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                // Called when a click is recorded for an ad.
                Log.d(TAG, "Ad was clicked.")
            }

            override fun onAdDismissedFullScreenContent() {
                // Called when ad is dismissed.
                // Set the ad reference to null so you don't show the ad a second time.
                Log.d(TAG, "Ad dismissed fullscreen content.")
                rewardedAd = null
                onAdDismissed()
                loadRewardedAd(activity) // Preload next one
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Called when ad fails to show.
                Log.e(TAG, "Ad failed to show fullscreen content.")
                rewardedAd = null
                onAdDismissed() // Treat as dismissed/failed
            }

            override fun onAdImpression() {
                // Called when an impression is recorded for an ad.
                Log.d(TAG, "Ad recorded an impression.")
            }

            override fun onAdShowedFullScreenContent() {
                // Called when ad is shown.
                Log.d(TAG, "Ad showed fullscreen content.")
            }
        }

        if (rewardedAd != null) {
            rewardedAd?.show(activity) { rewardItem ->
                // Handle the reward.
                val rewardAmount = rewardItem.amount
                val rewardType = rewardItem.type
                Log.d(TAG, "User earned the reward: $rewardAmount $rewardType")
                onUserEarnedReward()
            }
        } else {
            Log.d(TAG, "The rewarded ad wasn't ready yet.")
            onAdDismissed() // Fallback
            loadRewardedAd(activity)
        }
    }
}
