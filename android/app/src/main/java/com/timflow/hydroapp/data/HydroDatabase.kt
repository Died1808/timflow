package com.timflow.hydroapp.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database holding locally cached hydrogeological parameter records.
 *
 * Records are loaded once from the GeoParquet file (via
 * [GeoParquetRepository]) and then stored here for fast, offline nearest-
 * neighbour queries.
 *
 * Migration strategy is set to destructive migration; the database is
 * re-populated from the GeoParquet file whenever the schema changes.
 */
@Database(
    entities = [HydroParameters::class],
    version = 1,
    exportSchema = false,
)
abstract class HydroDatabase : RoomDatabase() {

    abstract fun hydroRecordDao(): HydroRecordDao

    companion object {
        const val DATABASE_NAME = "hydro_database"
    }
}
