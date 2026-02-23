package com.eventos.banana.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eventos.banana.util.LocationHelper
import com.eventos.banana.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationWorker(
    appContext: Context,
    workerParams: WorkerParameters
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
                val userRepository = UserRepository(
                com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                com.eventos.banana.data.repository.NotificationRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance())
            )
                val geohash = com.eventos.banana.util.GeohashUtils.encode(location.latitude, location.longitude, 9)
                
                userRepository.updateLocation(
                    uid, 
                    location.region, 
                    location.commune,
                    location.latitude,
                    location.longitude,
                    geohash
                )
                
                android.util.Log.d("LocationWorker", "Location updated in background: ${location.commune} ($geohash)")
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
