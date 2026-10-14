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

package net.bsnx.apps.compass.model;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hält die aktuelle Position bereit, damit sie zusammen mit einem
 * Schritt-Eintrag gespeichert werden kann (Referenz zur Schrittmessung).
 *
 * <p>Nutzt alle verfügbaren Provider (Fused ab Android 12, GPS, Netzwerk),
 * damit auch direkt nach dem Start und in Gebäuden schnell eine Position
 * vorliegt. Läuft nur zwischen {@link #start()} und {@link #stop()}, also
 * solange die App im Vordergrund ist. Es wird nichts selbst gespeichert.</p>
 */
public class GpsTracker implements LocationListener {

    private static final String TAG = "GpsTracker";

    private static final long  MIN_INTERVAL_MS = 1000;
    /** Ältere Fixes gelten nicht mehr als aktuelle Position. */
    private static final long  MAX_FIX_AGE_MS  = 60_000;
    /** Ungenauere Fixes werden nicht gespeichert. */
    private static final float MAX_ACCURACY_M  = 50f;
    /** Innerhalb dieser Zeit ersetzt ein ungenauerer Fix keinen genaueren. */
    private static final long  PREFER_ACCURATE_MS = 10_000;

    private final Context ctx;
    private LocationManager locationManager;
    private boolean running = false;
    private Location lastFix;
    /** Grund, warum nicht gestartet werden konnte; null wenn gestartet. */
    private String startError = "GPS nicht gestartet";

    public GpsTracker(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) return;
        if (!hasPermission()) {
            startError = "keine Standortberechtigung";
            return;
        }
        locationManager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            startError = "kein LocationManager";
            return;
        }

        List<String> providers = enabledProviders();
        if (providers.isEmpty()) {
            startError = "Standort ist deaktiviert";
            Log.w(TAG, startError);
            return;
        }
        try {
            for (String provider : providers) {
                Location last = locationManager.getLastKnownLocation(provider);
                if (last != null) onLocationChanged(last);
                locationManager.requestLocationUpdates(provider,
                        MIN_INTERVAL_MS, 0f, this, Looper.getMainLooper());
            }
            running = true;
            startError = null;
            Log.i(TAG, "Location started: " + providers);
        } catch (SecurityException e) {
            startError = "keine Standortberechtigung";
            Log.w(TAG, startError, e);
            locationManager.removeUpdates(this);
        }
    }

    public void stop() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        if (running) Log.i(TAG, "Location stopped");
        running = false;
        startError = "GPS nicht gestartet";
        // lastFix bleibt erhalten; Aktualität wird über das Alter geprüft
    }

    /** Aktuelle, ausreichend genaue Position, oder {@code null} wenn keine vorliegt. */
    @Nullable
    public Location getCurrentFix() {
        return getStatus() == null ? lastFix : null;
    }

    /**
     * Grund, warum {@link #getCurrentFix()} gerade keine Position liefert,
     * oder {@code null}, wenn eine gültige Position vorliegt.
     */
    @Nullable
    public String getStatus() {
        if (!running) return startError;
        if (lastFix == null) return "warte auf Position";
        long ageMs = ageMs(lastFix);
        if (ageMs > MAX_FIX_AGE_MS) {
            return String.format(Locale.getDefault(), "Position zu alt (%d s)", ageMs / 1000);
        }
        if (lastFix.hasAccuracy() && lastFix.getAccuracy() > MAX_ACCURACY_M) {
            return String.format(Locale.getDefault(), "Position zu ungenau (±%.0f m)", lastFix.getAccuracy());
        }
        return null;
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (isBetter(location, lastFix)) {
            lastFix = location;
        }
    }

    // Ab API 29 nicht mehr aufgerufen, aber auf älteren Geräten ohne Default-Implementierung Pflicht.
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(@NonNull String provider) { }

    @Override
    public void onProviderDisabled(@NonNull String provider) { }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> enabledProviders() {
        List<String> candidates = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            candidates.add(LocationManager.FUSED_PROVIDER);
        }
        candidates.add(LocationManager.GPS_PROVIDER);
        candidates.add(LocationManager.NETWORK_PROVIDER);

        List<String> all = locationManager.getAllProviders();
        List<String> result = new ArrayList<>();
        for (String p : candidates) {
            if (all.contains(p) && locationManager.isProviderEnabled(p)) result.add(p);
        }
        return result;
    }

    /** Neuer Fix ersetzt den alten, außer der alte ist noch frisch und deutlich genauer. */
    private static boolean isBetter(Location fix, @Nullable Location current) {
        if (current == null) return true;
        long newerByMs = (fix.getElapsedRealtimeNanos() - current.getElapsedRealtimeNanos()) / 1_000_000L;
        if (newerByMs < 0) return false;
        if (newerByMs > PREFER_ACCURATE_MS) return true;
        float accNew = fix.hasAccuracy() ? fix.getAccuracy() : Float.MAX_VALUE;
        float accOld = current.hasAccuracy() ? current.getAccuracy() : Float.MAX_VALUE;
        return accNew <= accOld;
    }

    private static long ageMs(Location fix) {
        return (SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos()) / 1_000_000L;
    }
}
