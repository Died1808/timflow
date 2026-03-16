package com.timflow.hydroapp.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timflow.hydroapp.R
import com.timflow.hydroapp.location.LocationState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Main map screen composable.
 *
 * Displays:
 * - An OpenStreetMap base layer (OSMDroid).
 * - A marker at the user's current GPS location.
 * - TIMFLOW groundwater-lowering contour lines as polylines.
 * - A floating "Compute" FAB that triggers model execution.
 * - Status cards showing GPS accuracy and record count.
 *
 * Permission rationale dialogs are shown inline when location permission
 * is missing.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Configure OSMDroid user-agent once (required by tile servers' ToS).
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // Location permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* Permission result is observed via the LocationManager flow. */ }

    // Request permissions on first composition if not yet granted.
    LaunchedEffect(uiState.locationState) {
        if (uiState.locationState is LocationState.PermissionDenied) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── OSMDroid map ──────────────────────────────────────────────────────
        OsmMapView(
            modifier = Modifier.fillMaxSize(),
            uiState = uiState,
        )

        // ── Status card (top) ─────────────────────────────────────────────────
        StatusCard(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(8.dp),
        )

        // ── Compute button (bottom centre) ────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.isComputing) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.computeContours() },
                    enabled = uiState.locationState is LocationState.Available,
                ) {
                    Text(stringResource(R.string.btn_compute_contours))
                }
            }
        }

        // ── Error snackbar ────────────────────────────────────────────────────
        uiState.errorMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
                action = {
                    TextButton(onClick = viewModel::dismissError) {
                        Text(stringResource(R.string.btn_dismiss))
                    }
                },
            ) {
                Text(message)
            }
        }

        // ── Provider-disabled dialog ──────────────────────────────────────────
        if (uiState.locationState is LocationState.ProviderDisabled) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.dialog_gps_disabled_title)) },
                text = { Text(stringResource(R.string.dialog_gps_disabled_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                            ),
                        )
                    }) {
                        Text(stringResource(R.string.btn_open_settings))
                    }
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(
    uiState: MapUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            val locationText = when (val s = uiState.locationState) {
                is LocationState.Available ->
                    "GPS: %.5f, %.5f  ±%.0fm".format(s.latitude, s.longitude, s.accuracyMetres)
                LocationState.Loading -> "GPS: acquiring fix…"
                LocationState.PermissionDenied -> "GPS: permission denied"
                LocationState.ProviderDisabled -> "GPS: provider disabled"
            }
            Text(text = locationText, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "Hydro records: ${uiState.recordCount}",
                style = MaterialTheme.typography.bodySmall,
            )
            uiState.nearestRecord?.let { rec ->
                Text(
                    text = "Nearest: id=${rec.id}  kaq=${rec.kaq} m/d  H=${rec.H} m  Qw=${rec.Qw} m³/d",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OSMDroid map view (AndroidView wrapper)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OsmMapView(
    uiState: MapUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Keep a single MapView instance across recompositions.
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { mv ->
            // Clear previous overlays before re-drawing.
            mv.overlays.clear()

            // GPS marker
            if (uiState.locationState is LocationState.Available) {
                val loc = uiState.locationState
                val geoPoint = GeoPoint(loc.latitude, loc.longitude)
                // Centre map on user location only when a new fix arrives.
                mv.controller.animateTo(geoPoint)

                val marker = Marker(mv).apply {
                    position = geoPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Your location"
                    snippet = "±%.0f m".format(loc.accuracyMetres)
                }
                mv.overlays.add(marker)
            }

            // TIMFLOW contour polylines
            uiState.contoursGeoJson?.let { geojson ->
                val polylines = parseContoursFromGeoJson(geojson)
                mv.overlays.addAll(polylines)
            }

            mv.invalidate()
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// GeoJSON → OSMDroid Polyline parser
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parse a GeoJSON FeatureCollection string into a list of OSMDroid
 * [Polyline] overlays, one per contour line segment.
 *
 * Only `MultiLineString` features with a `lowering` property are rendered.
 * The colour is scaled from cyan (low lowering) to red (high lowering).
 */
private fun parseContoursFromGeoJson(geojson: String): List<Polyline> {
    val polylines = mutableListOf<Polyline>()

    try {
        val root = org.json.JSONObject(geojson)
        if (root.has("error")) return emptyList()

        val features = root.getJSONArray("features")

        // Determine max lowering for colour scaling.
        var maxLowering = 1.0
        for (i in 0 until features.length()) {
            val feat = features.getJSONObject(i)
            val props = feat.optJSONObject("properties") ?: continue
            val lv = props.optDouble("lowering", 0.0)
            if (lv > maxLowering) maxLowering = lv
        }

        for (i in 0 until features.length()) {
            val feat = features.getJSONObject(i)
            val geom = feat.optJSONObject("geometry") ?: continue
            val props = feat.optJSONObject("properties") ?: continue

            if (geom.getString("type") != "MultiLineString") continue
            val lowering = props.optDouble("lowering", 0.0)
            val color = loweringColor(lowering, maxLowering)

            val mls = geom.getJSONArray("coordinates")
            for (lineIdx in 0 until mls.length()) {
                val coords = mls.getJSONArray(lineIdx)
                val points = (0 until coords.length()).map { j ->
                    val coord = coords.getJSONArray(j)
                    GeoPoint(coord.getDouble(1), coord.getDouble(0))
                }
                if (points.size >= 2) {
                    val polyline = Polyline().apply {
                        setPoints(points)
                        outlinePaint.color = color
                        outlinePaint.strokeWidth = 3f
                        title = "Lowering: %.2f m".format(lowering)
                    }
                    polylines.add(polyline)
                }
            }
        }
    } catch (e: Exception) {
        // Malformed GeoJSON or unexpected error during parsing.
        // The error snackbar is already shown by the ViewModel, but we log
        // the exception here so that developers can diagnose non-GeoJSON
        // failures (e.g., out-of-bounds array access) in Logcat.
        android.util.Log.w("MapScreen", "Failed to parse contours GeoJSON", e)
    }

    return polylines
}

/**
 * Map a lowering value to an ARGB colour.
 *
 * Low values → cyan/green; high values → orange/red.
 */
private fun loweringColor(lowering: Double, maxLowering: Double): Int {
    val t = (lowering / maxLowering).coerceIn(0.0, 1.0).toFloat()
    val r = (t * 255).toInt()
    val g = ((1 - t) * 200).toInt()
    val b = ((1 - t) * 255).toInt()
    return android.graphics.Color.argb(220, r, g, b)
}
