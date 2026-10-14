/*
 * This file is part of Compass.
 * Copyright (C) 2026 Philipp Bobek <philipp.bobek@mailbox.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Compass is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.bsnx.apps.compass

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.bsnx.apps.compass.databinding.ActivityCompassBinding
import net.bsnx.apps.compass.db.CompassDB
import net.bsnx.apps.compass.db.PathItem
import net.bsnx.apps.compass.model.AppNightMode
import net.bsnx.apps.compass.model.GpsTracker
import net.bsnx.apps.compass.model.PressureModel
import net.bsnx.apps.compass.model.StepModel
import net.bsnx.apps.compass.preference.PreferenceStore
import net.bsnx.apps.compass.view.CompassView

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private lateinit var stepModel: StepModel
    private lateinit var pressureModel: PressureModel
    private lateinit var gpsTracker: GpsTracker

    private val accessLocationPermissionRequest = registerAccessLocationPermissionRequest()

    private lateinit var binding: ActivityCompassBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var preferenceStore: PreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stepModel = StepModel(this)
        stepModel.requestStepPremission()
        stepModel.onCreate()
        pressureModel = PressureModel(this)
        pressureModel.onCreate()
        gpsTracker = GpsTracker(this)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_compass)
        setSupportActionBar(binding.toolbar)

        binding.btnA.setOnClickListener {
            val intent = Intent(this, GeometryActivity::class.java)
            startActivity(intent)
        }
        binding.btnStepsReset.setOnClickListener {
            stepsReset()
        }
        binding.btnB.setOnClickListener {
            addPath()
        }

        val navController = getNavController()
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        // Schritt-Buttons nur im Kompass zeigen, nicht in Einstellungen/Lizenzen
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.stepButtons.visibility =
                if (destination.id == R.id.CompassFragment) View.VISIBLE else View.GONE
        }

        initPreferenceStore()
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        updateGpsTracking()
    }

    override fun onPause() {
        isInForeground = false
        gpsTracker.stop()
        super.onPause()
    }

    /**
     * Eigenes Flag statt lifecycle.currentState: der Lifecycle steht während
     * onResume() noch auf STARTED und wechselt erst danach auf RESUMED.
     */
    private var isInForeground = false

    override fun onDestroy() {
        pressureModel.onDestroy()
        super.onDestroy()
    }

    /** Startet/stoppt die GPS-Aufzeichnung passend zur Einstellung (nur im Vordergrund). */
    private fun updateGpsTracking() {
        val enabled = preferenceStore.gpsTracking.value == true
        if (!enabled) gpsPermissionRequested = false
        if (enabled && isInForeground) {
            if (gpsTracker.hasPermission()) {
                gpsTracker.start()
            } else if (!gpsPermissionRequested) {
                // Nur einmal pro Aktivierung fragen, sonst Endlosschleife über onResume
                gpsPermissionRequested = true
                gpsPermissionRequest.launch(arrayOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION))
            }
        } else {
            gpsTracker.stop()
        }
    }

    private var gpsPermissionRequested = false

    private val gpsPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[ACCESS_FINE_LOCATION] == true) {
                updateGpsTracking()
            } else {
                Toast.makeText(this, "GPS-Aufzeichnung benötigt den genauen Standort", Toast.LENGTH_LONG).show()
            }
        }

    private fun stepsReset() {
        Log.i("NX", "stepsReset()")
        if (!stepModel.hasReading()) {
            Toast.makeText(this, "Schrittzähler noch nicht bereit", Toast.LENGTH_LONG).show()
            return
        }
        preferenceStore.setStepCount(stepModel.getSteps())
        Toast.makeText(this, "Reset, " + stepModel.getSteps(), Toast.LENGTH_LONG).show()
    }

    private fun addPath() {
        if (!stepModel.hasReading()) {
            Toast.makeText(this, "Schrittzähler noch nicht bereit", Toast.LENGTH_LONG).show()
            return
        }

        val current = stepModel.getSteps()
        val base = preferenceStore.getStepCount(-1)
        if (base < 0) {
            // Noch nie zurückgesetzt: keine Basis vorhanden → nur Basis setzen
            stepsReset()
            return
        }
        // TYPE_STEP_COUNTER beginnt nach Geräte-Neustart wieder bei 0;
        // dann sind nur die Schritte seit dem Neustart bekannt.
        val stepsDiff = if (current >= base) current - base else current

        val c = findViewById<CompassView>(R.id.compass)
        Log.i("NX", ""+c.getAzimuth());

        val hasPressure = pressureModel.hasReading()
        val pressure = if (hasPressure) pressureModel.getPressure() else Float.NaN
        val altitude = if (hasPressure) pressureModel.getAltitude() else Float.NaN

        // GPS nur, wenn die Einstellung aktiv ist (sonst läuft der Tracker nicht → null)
        val fix = gpsTracker.getCurrentFix()
        val latitude = fix?.latitude ?: Double.NaN
        val longitude = fix?.longitude ?: Double.NaN
        val gpsAccuracy = if (fix != null && fix.hasAccuracy()) fix.accuracy else Float.NaN

        val db = CompassDB(this)
        // Vom aktiven Punkt (Long-Press in der Karte) fortsetzen, sofern er von heute ist
        val activeId = PreferenceStore.readActiveItemId(this)
        val parentId = if (db.getTodays().any { it.id == activeId }) activeId else PathItem.NO_PARENT
        val item = db.create(c.getAzimuth().toInt(), stepsDiff, pressure, altitude,
            latitude, longitude, gpsAccuracy, parentId)
        // Der neue Eintrag ist der neue aktive Punkt
        if (item != null) PreferenceStore.writeActiveItemId(this, item.id)

        val altText = if (hasPressure) ", %.1f m".format(altitude) else ""
        val gpsText = when {
            fix != null -> ", GPS ±%.0f m".format(gpsAccuracy)
            preferenceStore.gpsTracking.value == true -> ", kein GPS: ${gpsTracker.getStatus()}"
            else -> ""
        }
        Toast.makeText(this, "Added, $stepsDiff$altText$gpsText", Toast.LENGTH_LONG).show()

        db.close()
        stepsReset()
    }

    private fun initPreferenceStore() {
        preferenceStore = PreferenceStore(this, lifecycle)
        preferenceStore.nightMode.observe(this) { setNightMode(it) }
        preferenceStore.screenOrientationLocked.observe(this) { setScreenRotationMode(it) }
        preferenceStore.trueNorth.observe(this) { setupTrueNorthFunctionality(it) }
        preferenceStore.gpsTracking.observe(this) { updateGpsTracking() }
    }

    private fun setNightMode(appNightMode: AppNightMode) {
        Log.d(TAG, "Setting night mode to $appNightMode")
        setDefaultNightMode(appNightMode.systemValue)
    }

    private fun setScreenRotationMode(screenOrientationLocked: Boolean) {
        if (screenOrientationLocked) {
            setScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED)
        } else {
            setScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        }
    }

    private fun setScreenOrientation(screenOrientation: Int) {
        Log.d(TAG, "Setting requested orientation to value $screenOrientation")
        requestedOrientation = screenOrientation
    }

    private fun setupTrueNorthFunctionality(trueNorth: Boolean?) {
        if (trueNorth == true) {
            handleLocationPermission()
        }
    }

    private fun handleLocationPermission() {
        if (neverRequestedAccessLocationPermission() && accessLocationPermissionDenied()) {
            startAccessLocationPermissionRequestWorkflow()
        }
    }

    private fun neverRequestedAccessLocationPermission() =
        preferenceStore.accessLocationPermissionRequested.value != true

    private fun accessLocationPermissionDenied() =
        ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) == PERMISSION_DENIED
                && ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_DENIED

    private fun startAccessLocationPermissionRequestWorkflow() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, ACCESS_COARSE_LOCATION)
            || ActivityCompat.shouldShowRequestPermissionRationale(this, ACCESS_FINE_LOCATION)
        ) {
            showRequestNotificationsPermissionRationale()
        } else {
            launchAccessLocationPermissionRequest()
        }
    }

    private fun showRequestNotificationsPermissionRationale() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.access_location_permission_rationale_title)
            .setMessage(R.string.access_location_permission_rationale_message)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                launchAccessLocationPermissionRequest()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.no_thanks) { dialog, _ ->
                Log.i(TAG, "Continuing without requesting location permission")
                preferenceStore.accessLocationPermissionRequested.value = true
                dialog.dismiss()
            }
            .show()
    }

    private fun launchAccessLocationPermissionRequest() {
        Log.i(TAG, "Requesting location permission")
        accessLocationPermissionRequest.launch(arrayOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION))
    }

    private fun registerAccessLocationPermissionRequest() =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions[ACCESS_FINE_LOCATION] == true -> {
                    Log.i(TAG, "Permission ACCESS_FINE_LOCATION granted")
                }

                permissions[ACCESS_COARSE_LOCATION] == true -> {
                    Log.i(TAG, "Permission ACCESS_COARSE_LOCATION granted")
                }

                else -> {
                    Log.i(TAG, "Location permission denied")
                    preferenceStore.accessLocationPermissionRequested.value = true
                }
            }
        }


    override fun onSupportNavigateUp(): Boolean {
        val navController = getNavController()
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private fun getNavController(): NavController {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        return navHostFragment.navController
    }
}
