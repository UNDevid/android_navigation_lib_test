package com.example.navigationchangeroutetest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.model.LatLng

val Any.M_TAG: String
    get() {
        return if (!javaClass.isAnonymousClass) {
            val name = javaClass.simpleName
            // first 23 chars
            if (name.length <= 23) name else name.substring(0, 23)
        } else {
            val name = javaClass.name
            // last 23 chars
            if (name.length <= 23) name else name.substring(name.length - 23, name.length)
        }
    }

fun Any.log(
    vararg messages: Any?
) {
    if (BuildConfig.DEBUG) {
        val messageComplete = messages.joinToString("\t")
        Log.d(M_TAG, messageComplete)
    }
}

fun Any.loge(
    vararg messages: Any?,
    e: Exception? = null,
) {
    if (BuildConfig.DEBUG) {
// 		val tag = if (this.javaClass.simpleName.isEmpty()) "App" else this.javaClass.simpleName
        Log.e(M_TAG, messages.joinToString("\t"), e)
    }
}

fun Context.checkLocationPermission(): Boolean = ActivityCompat.checkSelfPermission(
    this,
    Manifest.permission.ACCESS_FINE_LOCATION
) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
    this,
    Manifest.permission.ACCESS_COARSE_LOCATION
) == PackageManager.PERMISSION_GRANTED

fun Location.toLatLng() = LatLng(this.latitude, this.longitude)

fun LatLng.toLocation(): Location = Location(LocationManager.GPS_PROVIDER).apply {
    latitude = this@toLocation.latitude
    longitude = this@toLocation.longitude
}