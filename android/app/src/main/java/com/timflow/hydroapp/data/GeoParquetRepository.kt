package com.timflow.hydroapp.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Repository for GeoParquet-based hydro-parameter records.
 *
 * Responsibilities:
 * 1. Load a GeoParquet file from internal storage and persist its records
 *    into [HydroDatabase] via [HydroRecordDao].
 * 2. Perform fast nearest-neighbour lookup using the Haversine formula.
 *
 * The GeoParquet file is expected to reside at
 * `<filesDir>/hydro_data/hydro_params.parquet` on the device. In
 * production the file can be bundled as an asset or downloaded OTA.
 *
 * **GeoParquet parsing** is delegated to the embedded Python runtime
 * (Chaquopy) to avoid shipping a full Parquet implementation in Kotlin.
 * The Python wrapper `timflow_mobile.nearest_record` handles the lookup
 * when all records have been loaded into memory; alternatively this class
 * performs the search entirely in Kotlin once records are cached in Room.
 */
@Singleton
class GeoParquetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: HydroRecordDao,
) {

    companion object {
        /** Sub-directory inside [Context.getFilesDir] for parquet data. */
        const val DATA_DIR = "hydro_data"
        /** Expected filename of the GeoParquet dataset. */
        const val PARQUET_FILENAME = "hydro_params.parquet"
        /** Earth radius in metres for Haversine computation. */
        private const val EARTH_RADIUS_M = 6_371_000.0
    }

    /**
     * Returns the canonical path where the GeoParquet file should be placed.
     *
     * Callers can show this path in the UI so that users know where to copy
     * the file, or use a file-picker to let the user select it.
     */
    val parquetFilePath: String
        get() = File(context.filesDir, "$DATA_DIR/$PARQUET_FILENAME").absolutePath

    /**
     * Load records from the GeoParquet file and store them in Room.
     *
     * This function must be called before [findNearest]. It is safe to
     * call repeatedly; records are replaced if they already exist.
     *
     * The heavy I/O runs on [Dispatchers.IO].
     *
     * @param filePath Absolute path to the GeoParquet file. Defaults to
     *   [parquetFilePath].
     * @return The number of records that were loaded and persisted.
     * @throws IllegalStateException if the file does not exist.
     */
    suspend fun loadFromParquet(filePath: String = parquetFilePath): Int =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            check(file.exists()) {
                "GeoParquet file not found: $filePath\n" +
                    "Place the file at: $parquetFilePath"
            }
            // Parsing is delegated to the Python runtime (Chaquopy).
            val records = parsePythonParquet(filePath)
            dao.deleteAll()
            dao.insertAll(records)
            records.size
        }

    /**
     * Find the [HydroParameters] record whose (lat, lon) is closest to the
     * given coordinates.
     *
     * Uses the Haversine formula for great-circle distance; accurate to
     * within ±0.5 % for distances up to several hundred kilometres.
     *
     * @param lat Observer latitude (decimal degrees, WGS-84).
     * @param lon Observer longitude (decimal degrees, WGS-84).
     * @return The nearest record, or `null` if the database is empty.
     */
    suspend fun findNearest(lat: Double, lon: Double): HydroParameters? =
        withContext(Dispatchers.IO) {
            val records = dao.getAllRecords()
            if (records.isEmpty()) return@withContext null
            records.minByOrNull { record ->
                haversine(lat, lon, record.lat, record.lon)
            }
        }

    /**
     * Return the total number of cached hydro records.
     *
     * A return value of 0 indicates that [loadFromParquet] has not yet been
     * called or succeeded.
     */
    suspend fun recordCount(): Int = withContext(Dispatchers.IO) { dao.count() }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Parse a GeoParquet file via the embedded Python runtime.
     *
     * The Python function `timflow_mobile._parse_parquet(path)` returns a
     * JSON array of record objects that is deserialized here.
     *
     * This helper is intentionally kept simple: it converts the Python return
     * value to a list of [HydroParameters] using kotlinx-serialization.
     */
    private fun parsePythonParquet(filePath: String): List<HydroParameters> {
        return try {
            val py = com.chaquo.python.Python.getInstance()
            val module = py.getModule("timflow_mobile")
            val jsonStr = module.callAttr("_parse_parquet_to_json", filePath).toString()
            kotlinx.serialization.json.Json.decodeFromString(jsonStr)
        } catch (e: com.chaquo.python.PyException) {
            // Python-level error (file not found, missing pyarrow, bad data).
            android.util.Log.e("GeoParquetRepository", "Python error reading Parquet: $filePath", e)
            throw RuntimeException("Failed to parse GeoParquet file: ${e.message}", e)
        } catch (e: kotlinx.serialization.SerializationException) {
            // JSON returned by Python could not be deserialized into HydroParameters.
            android.util.Log.e("GeoParquetRepository", "Deserialization failed for Parquet data", e)
            throw RuntimeException("Failed to deserialize hydro records: ${e.message}", e)
        } catch (e: Exception) {
            // Chaquopy not yet initialized (e.g., called before Application.onCreate).
            android.util.Log.w("GeoParquetRepository", "Chaquopy not ready, returning empty list", e)
            emptyList()
        }
    }

    /**
     * Haversine great-circle distance between two WGS-84 points.
     *
     * @return Distance in metres.
     */
    internal fun haversine(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) +
            cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }
}
