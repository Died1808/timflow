package com.timflow.hydroapp.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager as AndroidLocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages GPS location updates using the Fused Location Provider API.
 *
 * Emits a [Flow] of [LocationState] values that the UI layer can collect.
 * The flow is cold: location updates start when the flow is collected and
 * stop automatically when the collection is cancelled (e.g. when the
 * composable leaves the composition).
 *
 * Usage::
 *
 *     locationManager.locationFlow().collect { state ->
 *         when (state) {
 *             is LocationState.Available -> { /* use state.latitude / longitude */ }
 *             is LocationState.Loading -> { /* show spinner */ }
 *             is LocationState.PermissionDenied -> { /* request permission */ }
 *             is LocationState.ProviderDisabled -> { /* prompt settings */ }
 *         }
 *     }
 */
@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /** Interval between location updates (milliseconds). */
    private val updateIntervalMs: Long = 5_000L

    /** Fastest allowable update interval (milliseconds). */
    private val fastestIntervalMs: Long = 2_000L

    /**
     * Returns a cold [Flow] of [LocationState].
     *
     * - If permissions are missing, emits [LocationState.PermissionDenied].
     * - If the provider is disabled, emits [LocationState.ProviderDisabled].
     * - Otherwise, emits [LocationState.Loading] immediately and then
     *   [LocationState.Available] whenever a new fix is received.
     */
    fun locationFlow(): Flow<LocationState> = callbackFlow {
        if (!hasLocationPermission()) {
            trySend(LocationState.PermissionDenied)
            close()
            return@callbackFlow
        }

        if (!isProviderEnabled()) {
            trySend(LocationState.ProviderDisabled)
            close()
            return@callbackFlow
        }

        trySend(LocationState.Loading)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            updateIntervalMs,
        )
            .setMinUpdateIntervalMillis(fastestIntervalMs)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(
                    LocationState.Available(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMetres = location.accuracy,
                    ),
                )
            }
        }

        try {
            fusedClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            trySend(LocationState.PermissionDenied)
            close(e)
            return@callbackFlow
        }

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns true when ACCESS_FINE_LOCATION permission has been granted. */
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** Returns true when the GPS or network location provider is enabled. */
    private fun isProviderEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE)
            as AndroidLocationManager
        return lm.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
    }
}
