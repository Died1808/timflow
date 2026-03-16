package com.timflow.hydroapp.di

import android.content.Context
import androidx.room.Room
import com.timflow.hydroapp.data.HydroDatabase
import com.timflow.hydroapp.data.HydroRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides application-scoped dependencies.
 *
 * Bindings:
 * - [HydroDatabase] – Room database (singleton)
 * - [HydroRecordDao] – DAO for hydro-parameter records (singleton)
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHydroDatabase(@ApplicationContext context: Context): HydroDatabase =
        Room.databaseBuilder(
            context,
            HydroDatabase::class.java,
            HydroDatabase.DATABASE_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideHydroRecordDao(database: HydroDatabase): HydroRecordDao =
        database.hydroRecordDao()
}
