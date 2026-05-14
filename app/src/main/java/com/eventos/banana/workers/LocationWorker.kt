package com.eventos.banana.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eventos.banana.util.LocationHelper
import com.eventos.banana.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker
import com.eventos.banana.util.AppConstants
import com.eventos.banana.util.GeohashUtils

@HiltWorker
class LocationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userRepository: UserRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            
            // 1. Check Permissions
            if (!LocationHelper.hasLocationPermissions(context)) {
                return@withContext Result.failure()
            }

            // 2. Get Current User
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                return@withContext Result.failure()
            }

            // 3. Detect Location
            val helper = LocationHelper(context)
            val location = helper.detectLocationFull()
            
            if (location != null) {
                // 4. Update Firestore
                val geohash = GeohashUtils.encode(location.latitude, location.longitude, GeohashUtils.getPrecisionForRadius(AppConstants.DEFAULT_SEARCH_RADIUS_KM))
                
                userRepository.updateLocation(
                    uid, 
                    location.region, 
                    location.commune,
                    location.country,
                    location.latitude,
                    location.longitude,
                    geohash
                )
                
                // Sync notification topics for the new commune (Requested "ON" by default sync)
                val profile = userRepository.getUserProfile(uid, forceRefresh = true)
                if (profile != null) {
                    userRepository.syncNotificationTopics(profile)
                }
                
                android.util.Log.d("LocationWorker", "Location and topics updated in background: ${location.commune} ($geohash)")
                return@withContext Result.success()
            } else {
                return@withContext Result.retry()
            }

        } catch (e: Exception) {
            android.util.Log.e("LocationWorker", "Error updating location", e)
            return@withContext Result.failure()
        }
    }
}
