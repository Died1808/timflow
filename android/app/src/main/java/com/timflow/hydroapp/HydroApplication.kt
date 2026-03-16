package com.timflow.hydroapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for the TimFlow HydroApp.
 *
 * Annotated with [@HiltAndroidApp] to enable Hilt dependency injection
 * across the entire application. This class is referenced in
 * AndroidManifest.xml via `android:name=".HydroApplication"`.
 */
@HiltAndroidApp
class HydroApplication : Application()
