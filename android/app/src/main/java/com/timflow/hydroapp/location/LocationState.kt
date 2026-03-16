package com.timflow.hydroapp.location

/**
 * Sealed class representing the current GPS location state.
 *
 * Used to communicate location status from [LocationManager] to the UI
 * layer via a [kotlinx.coroutines.flow.StateFlow].
 */
sealed class LocationState {

    /** The device has a valid GPS fix. */
    data class Available(
        /** Latitude in decimal degrees (WGS-84). */
        val latitude: Double,
        /** Longitude in decimal degrees (WGS-84). */
        val longitude: Double,
        /**
         * Estimated horizontal accuracy radius in metres (68 % confidence).
         * A smaller value indicates a more accurate fix.
         */
        val accuracyMetres: Float,
    ) : LocationState()

    /** Waiting for the first GPS fix or permission has just been granted. */
    data object Loading : LocationState()

    /** Location permission has not been granted by the user. */
    data object PermissionDenied : LocationState()

    /** GPS / network provider is disabled in device settings. */
    data object ProviderDisabled : LocationState()
}
