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
    // Ad Unit ID is injected via BuildConfig (dev=test / prod=real)
    private val AD_UNIT_ID = com.eventos.banana.BuildConfig.ADMOB_REWARDED_ID

    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false
    private var pendingActivity: Activity? = null
    private var pendingOnReward: (() -> Unit)? = null
    private var pendingOnDismiss: (() -> Unit)? = null

    fun initialize(context: Context) {
        MobileAds.initialize(context) { initializationStatus ->
            Log.d(TAG, "AdMob initialized: $initializationStatus")
            if (pendingActivity != null) {
                val act = pendingActivity!!
                val reward = pendingOnReward!!
                val dismiss = pendingOnDismiss!!
                pendingActivity = null
                pendingOnReward = null
                pendingOnDismiss = null
                showInternal(act, reward, dismiss)
            }
            loadRewardedAd(context)
        }
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
                    Log.d(TAG, "onAdFailedToLoad: ${adError.message}")
                    rewardedAd = null
                    isAdLoading = false
                    if (pendingActivity != null) {
                        pendingOnDismiss?.invoke()
                        pendingActivity = null
                        pendingOnReward = null
                        pendingOnDismiss = null
                    }
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Ad was loaded.")
                    rewardedAd = ad
                    isAdLoading = false

                    if (pendingActivity != null) {
                        val act = pendingActivity!!
                        val reward = pendingOnReward!!
                        val dismiss = pendingOnDismiss!!
                        pendingActivity = null
                        pendingOnReward = null
                        pendingOnDismiss = null
                        showInternal(act, reward, dismiss)
                    }
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        onUserEarnedReward: () -> Unit,
        onAdDismissed: () -> Unit
    ) {
        if (rewardedAd != null) {
            pendingActivity = null
            pendingOnReward = null
            pendingOnDismiss = null
            showInternal(activity, onUserEarnedReward, onAdDismissed)
        } else {
            pendingActivity = activity
            pendingOnReward = onUserEarnedReward
            pendingOnDismiss = onAdDismissed
            loadRewardedAd(activity)
        }
    }

    private fun showInternal(
        activity: Activity,
        onUserEarnedReward: () -> Unit,
        onAdDismissed: () -> Unit
    ) {
        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                Log.d(TAG, "Ad was clicked.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed fullscreen content.")
                rewardedAd = null
                onAdDismissed()
                loadRewardedAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Ad failed to show fullscreen content.")
                rewardedAd = null
                onAdDismissed()
            }

            override fun onAdImpression() {
                Log.d(TAG, "Ad recorded an impression.")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed fullscreen content.")
            }
        }
        rewardedAd?.show(activity) { rewardItem ->
            val rewardAmount = rewardItem.amount
            val rewardType = rewardItem.type
            Log.d(TAG, "User earned the reward: $rewardAmount $rewardType")
            onUserEarnedReward()
        }
    }
}
