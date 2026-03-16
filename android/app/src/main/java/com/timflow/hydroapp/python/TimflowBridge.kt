package com.timflow.hydroapp.python

import com.chaquo.python.Python
import com.timflow.hydroapp.data.HydroParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin bridge to the Python TIMFLOW groundwater model.
 *
 * Uses Chaquopy to call `timflow_mobile.py` functions embedded in the APK.
 * All computation is offloaded to [Dispatchers.Default] to keep the main
 * thread responsive.
 *
 * The Python module exposes two relevant functions:
 * - `run_timflow_from_json(json_str)` → GeoJSON FeatureCollection string
 * - `nearest_record(lat, lon, records_json)` → JSON record string
 *
 * Example usage::
 *
 *     val geojson = timflowBridge.computeContours(hydroParams)
 *     // geojson is a GeoJSON string ready for OSMDroid rendering
 */
@Singleton
class TimflowBridge @Inject constructor() {

    companion object {
        private const val MODULE_NAME = "timflow_mobile"
        private const val FN_RUN = "run_timflow_from_json"
    }

    /**
     * Run the TIMFLOW groundwater model for the given [HydroParameters] and
     * return a GeoJSON FeatureCollection string.
     *
     * The GeoJSON contains:
     * - One `MultiLineString` feature per contour level (head lowering in m).
     * - One `Point` feature for the pumping well location.
     *
     * @param params Hydrogeological parameters for the well / site.
     * @return GeoJSON FeatureCollection string, or an error JSON string with
     *   `{ "error": "..." }` if computation fails.
     */
    suspend fun computeContours(params: HydroParameters): String =
        withContext(Dispatchers.Default) {
            runCatching {
                val py = Python.getInstance()
                val module = py.getModule(MODULE_NAME)
                val jsonInput = buildJsonInput(params)
                module.callAttr(FN_RUN, jsonInput).toString()
            }.getOrElse { e ->
                buildErrorJson(e)
            }
        }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Serialize [HydroParameters] to a JSON string suitable for passing to
     * the Python `run_timflow_from_json` function.
     *
     * We build the JSON manually to avoid requiring full kotlinx-serialization
     * support on the parameters class while keeping the bridge simple.
     */
    private fun buildJsonInput(p: HydroParameters): String = buildString {
        append('{')
        appendField("lat", p.lat)
        append(',')
        appendField("lon", p.lon)
        append(',')
        appendField("kaq", p.kaq)
        append(',')
        appendField("H", p.H)
        append(',')
        appendField("c", p.c)
        append(',')
        appendField("Qw", p.Qw)
        append(',')
        appendField("rw", p.rw)
        append(',')
        appendField("t", p.t)
        append(',')
        appendField("grid_m", p.gridM)
        append(',')
        appendField("ngr", p.ngr)
        append(',')
        appendStringField("contour_levels", p.contourLevels)
        append('}')
    }

    private fun StringBuilder.appendField(key: String, value: Number) {
        append('"').append(key).append('"').append(':').append(value)
    }

    private fun StringBuilder.appendStringField(key: String, value: String) {
        append('"').append(key).append('"').append(':')
        append('"').append(value.replace("\"", "\\\"")).append('"')
    }

    private fun buildErrorJson(e: Throwable): String {
        val msg = (e.message ?: e.javaClass.simpleName)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return """{"error":"$msg"}"""
    }
}
