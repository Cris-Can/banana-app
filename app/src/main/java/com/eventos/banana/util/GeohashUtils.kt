package com.eventos.banana.util

import kotlin.math.abs

/**
 * Utility class for Geohashing.
 * Based on standard Geohash algorithm.
 * Precision:
 * 5 chars ~ 2.4km error
 * 6 chars ~ 0.6km error
 * 4 chars ~ 20km error (Good for "Nearby in City")
 */
object GeohashUtils {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(lat: Double, lon: Double, precision: Int = 6): String {
        var minLat = -90.0
        var maxLat = 90.0
        var minLon = -180.0
        var maxLon = 180.0
        
        var isEven = true
        var bit = 0
        var ch = 0
        val geohash = StringBuilder()

        while (geohash.length < precision) {
            if (isEven) {
                val mid = (minLon + maxLon) / 2
                if (lon > mid) {
                    ch = ch or (1 shl (4 - bit))
                    minLon = mid
                } else {
                    maxLon = mid
                }
            } else {
                val mid = (minLat + maxLat) / 2
                if (lat > mid) {
                    ch = ch or (1 shl (4 - bit))
                    minLat = mid
                } else {
                    maxLat = mid
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                geohash.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return geohash.toString()
    }

    fun getPrecisionForRadius(radiusKm: Int): Int {
        return when {
            radiusKm >= 100 -> 2 // ~600km
            radiusKm >= 30 -> 3  // ~150km
            radiusKm >= 10 -> 4  // ~20km
            else -> 4            // ~20km (Better more than enough and filter in client)
        }
    }
}
