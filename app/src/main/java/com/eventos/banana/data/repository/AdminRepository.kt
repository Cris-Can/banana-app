package com.eventos.banana.data.repository

import android.content.Context
import android.location.Geocoder
import com.eventos.banana.util.GeohashUtils
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

class AdminRepository(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()

    // Status callback
    suspend fun migrateData(onProgress: (String) -> Unit): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val sb = StringBuilder()
                var totalUpdated = 0

                // 1. MIGRATE USERS
                onProgress("🔍 Buscando usuarios sin GPS...")
                // TODO: Implement actual pagination for large migration scripts
                val usersSnapshot = firestore.collection("users").limit(500).get().await() // Get ALL to check fields
                
                // Filter locally because "missing field" query is hard
                val usersToMigrate = usersSnapshot.documents.filter { doc ->
                    val lat = doc.getDouble("latitude")
                    val geohash = doc.getString("geohash")
                    val commune = doc.getString("commune")
                    
                    // Migrate if: Has Commune BUT (Missing Lat OR Missing Geohash)
                    !commune.isNullOrBlank() && (lat == null || geohash == null)
                }

                if (usersToMigrate.isNotEmpty()) {
                    onProgress("🚀 Migrando ${usersToMigrate.size} usuarios...")
                    val geocoder = Geocoder(context, Locale("es", "CL"))
                    val batch = firestore.batch()
                    var batchCount = 0

                    usersToMigrate.forEachIndexed { index, doc ->
                        val commune = doc.getString("commune")!!
                        val region = doc.getString("region") ?: ""
                        
                        try {
                            // Try to geocode: "Commune, Region, Chile"
                            val query = "$commune, $region, Chile"
                            val addresses = geocoder.getFromLocationName(query, 1)
                            
                            if (!addresses.isNullOrEmpty()) {
                                val address = addresses[0]
                                val lat = address.latitude
                                val lng = address.longitude
                                val geohash = com.eventos.banana.util.GeohashUtils.encode(lat, lng, 9)
                                
                                batch.update(doc.reference, mapOf(
                                    "latitude" to lat,
                                    "longitude" to lng,
                                    "geohash" to geohash
                                ))
                                batchCount++
                                totalUpdated++
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AdminRepository", "Geocode failed for $commune", e)
                        }

                        // Update progress every 5 users
                        if (index % 5 == 0) {
                            onProgress("Migrando usuarios (${index + 1}/${usersToMigrate.size})...")
                        }
                        
                        // Commit batch every 400 (Firestore limit is 500)
                        if (batchCount >= 400) {
                            batch.commit().await()
                            batchCount = 0
                        }
                    }
                    
                    if (batchCount > 0) {
                        batch.commit().await()
                    }
                    sb.append("✅ ${usersToMigrate.size} Usuarios procesados.\n")
                } else {
                    sb.append("✨ Usuarios ya están al día.\n")
                }

                // 2. MIGRATE EVENTS
                onProgress("🔍 Buscando eventos sin GPS...")
                val eventsSnapshot = firestore.collection("events").limit(500).get().await()
                
                val eventsToMigrate = eventsSnapshot.documents.filter { doc ->
                    val lat = doc.getDouble("latitude")
                    val geohash = doc.getString("geohash")
                    val commune = doc.getString("commune")
                    
                    // Migrate if: Has Commune BUT (Missing Lat OR Missing Geohash)
                    !commune.isNullOrBlank() && (lat == null || geohash == null)
                }

                if (eventsToMigrate.isNotEmpty()) {
                    onProgress("🚀 Migrando ${eventsToMigrate.size} eventos...")
                    val geocoder = Geocoder(context, Locale("es", "CL"))
                    val batch = firestore.batch()
                    var batchCount = 0

                    eventsToMigrate.forEachIndexed { index, doc ->
                        val commune = doc.getString("commune")!!
                        // Events might not have region stored in older versions, try just Commune + Chile
                        val query = "$commune, Chile"
                        
                        try {
                            val addresses = geocoder.getFromLocationName(query, 1)
                            
                            if (!addresses.isNullOrEmpty()) {
                                val address = addresses[0]
                                val lat = address.latitude
                                val lng = address.longitude
                                val geohash = com.eventos.banana.util.GeohashUtils.encode(lat, lng, 9)
                                
                                batch.update(doc.reference, mapOf(
                                    "latitude" to lat,
                                    "longitude" to lng,
                                    "geohash" to geohash
                                ))
                                batchCount++
                                totalUpdated++
                            }
                        } catch (e: Exception) {
                           // Ignore
                        }
                        
                        // Update progress
                         if (index % 5 == 0) {
                             onProgress("Migrando eventos (${index + 1}/${eventsToMigrate.size})...")
                         }
                         
                         if (batchCount >= 400) {
                            batch.commit().await()
                            batchCount = 0
                        }
                    }
                     if (batchCount > 0) {
                        batch.commit().await()
                    }
                    sb.append("✅ ${eventsToMigrate.size} Eventos procesados.\n")
                } else {
                     sb.append("✨ Eventos ya están al día.\n")
                }

                Result.success(sb.toString())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
