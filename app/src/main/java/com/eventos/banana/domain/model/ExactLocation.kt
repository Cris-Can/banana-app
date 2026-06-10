package com.eventos.banana.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ExactLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val commune: String = "",
    val region: String = "",
    val country: String = ""
) : Parcelable
