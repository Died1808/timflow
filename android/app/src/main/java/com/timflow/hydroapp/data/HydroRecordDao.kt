package com.timflow.hydroapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for [HydroParameters] records.
 *
 * Provides insert and query operations for the `hydro_records` table.
 */
@Dao
interface HydroRecordDao {

    /**
     * Insert a collection of hydro records.
     *
     * Existing records with the same primary key are replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<HydroParameters>)

    /** Return all records ordered by their ID. */
    @Query("SELECT * FROM hydro_records ORDER BY id")
    suspend fun getAllRecords(): List<HydroParameters>

    /** Return the total number of records in the table. */
    @Query("SELECT COUNT(*) FROM hydro_records")
    suspend fun count(): Int

    /** Delete all records (used when reloading from a new GeoParquet file). */
    @Query("DELETE FROM hydro_records")
    suspend fun deleteAll()
}
