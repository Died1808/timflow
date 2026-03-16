package com.timflow.hydroapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timflow.hydroapp.data.GeoParquetRepository
import com.timflow.hydroapp.data.HydroParameters
import com.timflow.hydroapp.location.LocationManager
import com.timflow.hydroapp.location.LocationState
import com.timflow.hydroapp.python.TimflowBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the map screen.
 *
 * All properties are immutable snapshots; updates are delivered via a
 * [StateFlow] collected in the composable.
 */
data class MapUiState(
    /** Current GPS location state. */
    val locationState: LocationState = LocationState.Loading,
    /** Number of hydro records loaded from the GeoParquet file. */
    val recordCount: Int = 0,
    /** Nearest hydro record to the current GPS position, if available. */
    val nearestRecord: HydroParameters? = null,
    /** GeoJSON FeatureCollection string returned by TIMFLOW, if computed. */
    val contoursGeoJson: String? = null,
    /** True while TIMFLOW is computing contours. */
    val isComputing: Boolean = false,
    /** Non-null when an error has occurred that the user should see. */
    val errorMessage: String? = null,
)

/**
 * ViewModel for [MapScreen].
 *
 * Orchestrates location updates, nearest-record lookup, and TIMFLOW
 * contour computation.  All long-running work runs in [viewModelScope]
 * on appropriate dispatchers so the main thread is never blocked.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationManager: LocationManager,
    private val repository: GeoParquetRepository,
    private val timflowBridge: TimflowBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        startLocationUpdates()
        refreshRecordCount()
    }

    // -------------------------------------------------------------------------
    // Public actions
    // -------------------------------------------------------------------------

    /**
     * Load the GeoParquet file from [filePath] and cache records in Room.
     *
     * Should be called once on first launch or when the user imports a new
     * data file.
     */
    fun loadParquetFile(filePath: String) {
        viewModelScope.launch {
            runCatching { repository.loadFromParquet(filePath) }
                .onSuccess { count ->
                    _uiState.update { it.copy(recordCount = count, errorMessage = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
        }
    }

    /**
     * Trigger TIMFLOW computation for the nearest record to the current
     * GPS position.
     *
     * Does nothing if no GPS fix or no hydro records are available.
     */
    fun computeContours() {
        val locationState = _uiState.value.locationState
        if (locationState !is LocationState.Available) {
            _uiState.update { it.copy(errorMessage = "GPS fix not yet available") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isComputing = true, errorMessage = null) }

            val nearest = repository.findNearest(locationState.latitude, locationState.longitude)
            if (nearest == null) {
                _uiState.update {
                    it.copy(
                        isComputing = false,
                        errorMessage = "No hydro records loaded. " +
                            "Place the GeoParquet file at:\n${repository.parquetFilePath}",
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(nearestRecord = nearest) }

            val geoJson = timflowBridge.computeContours(nearest)
            _uiState.update {
                it.copy(
                    isComputing = false,
                    contoursGeoJson = geoJson,
                    errorMessage = if (geoJson.contains("\"error\"")) geoJson else null,
                )
            }
        }
    }

    /** Dismiss the currently displayed error message. */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun startLocationUpdates() {
        viewModelScope.launch {
            locationManager.locationFlow().collect { state ->
                _uiState.update { it.copy(locationState = state) }
                // Auto-refresh nearest record whenever the location changes.
                if (state is LocationState.Available) {
                    updateNearestRecord(state.latitude, state.longitude)
                }
            }
        }
    }

    private fun refreshRecordCount() {
        viewModelScope.launch {
            val count = repository.recordCount()
            _uiState.update { it.copy(recordCount = count) }
        }
    }

    private fun updateNearestRecord(lat: Double, lon: Double) {
        viewModelScope.launch {
            val nearest = repository.findNearest(lat, lon)
            _uiState.update { it.copy(nearestRecord = nearest) }
        }
    }
}
