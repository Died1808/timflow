package com.timflow.hydroapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Hydrogeological parameters for a single spatial record.
 *
 * Each record describes the aquifer properties at a geographic location and
 * is used as input to the TIMFLOW groundwater model.
 *
 * Room persists these records in the local [HydroDatabase] so they are
 * available offline after the GeoParquet file has been loaded once.
 */
@Entity(tableName = "hydro_records")
@Serializable
data class HydroParameters(

    /** Unique record identifier (from the source GeoParquet file). */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Latitude of the record centre point (decimal degrees, WGS-84). */
    @ColumnInfo(name = "lat")
    val lat: Double,

    /** Longitude of the record centre point (decimal degrees, WGS-84). */
    @ColumnInfo(name = "lon")
    val lon: Double,

    /**
     * Aquifer hydraulic conductivity [m/d].
     *
     * Governs the lateral groundwater flow speed.
     */
    @ColumnInfo(name = "kaq")
    val kaq: Double,

    /**
     * Saturated aquifer thickness [m].
     *
     * Used to compute transmissivity (T = kaq × H).
     */
    @ColumnInfo(name = "H")
    val H: Double,

    /**
     * Hydraulic resistance of the overlying aquitard [d].
     *
     * Set to 0 for a single unconfined or confined aquifer without
     * an overlying leaky layer.
     */
    @ColumnInfo(name = "c")
    val c: Double = 0.0,

    /**
     * Pumping rate [m³/d].
     *
     * Positive values represent extraction; negative values indicate
     * injection (recharge).
     */
    @ColumnInfo(name = "Qw")
    val Qw: Double,

    /**
     * Well radius [m].
     *
     * Used as the inner boundary of the analytic element solution.
     */
    @ColumnInfo(name = "rw")
    val rw: Double = 0.1,

    /**
     * Simulation time [d] for transient computation.
     *
     * Set to 0 (default) to compute steady-state head lowering.
     */
    @ColumnInfo(name = "t")
    val t: Double = 0.0,

    /**
     * Half-width of the computation grid [m].
     *
     * The model is evaluated on a square grid extending ±[gridM] metres
     * from the well in both the x and y directions.
     */
    @ColumnInfo(name = "grid_m")
    val gridM: Double = 2000.0,

    /**
     * Number of grid points per axis for contour evaluation.
     *
     * A value of 50 gives a 50 × 50 grid; higher values improve contour
     * resolution at the cost of computation time.
     */
    @ColumnInfo(name = "ngr")
    val ngr: Int = 50,

    /**
     * Comma-separated head-lowering levels to plot [m].
     *
     * Example: `"0.1,0.5,1,2,5"` produces five contour lines.
     */
    @ColumnInfo(name = "contour_levels")
    val contourLevels: String = "0.1,0.5,1,2,5",
)
