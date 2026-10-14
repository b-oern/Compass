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

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import static android.content.Context.SENSOR_SERVICE;

/**
 * Liest den Luftdruck (Barometer) und leitet daraus die Höhe ab.
 *
 * <p>Die Höhe basiert auf der Standardatmosphäre (1013,25 hPa auf Meereshöhe).
 * Der Absolutwert schwankt mit dem Wetter, Höhenunterschiede innerhalb
 * weniger Stunden sind aber auf wenige Meter genau.</p>
 */
public class PressureModel implements SensorEventListener {

    /** Glättungsfaktor des Tiefpassfilters (0 … 1, kleiner = glatter). */
    private static final float ALPHA = 0.15f;

    private final Context ctx;
    private SensorManager sensorManager;
    private Sensor pressureSensor;

    private float pressure;
    private boolean hasReading = false;

    public PressureModel(Context ctx) {
        this.ctx = ctx;
    }

    public void onCreate() {
        sensorManager = (SensorManager) ctx.getSystemService(SENSOR_SERVICE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (pressureSensor != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void onDestroy() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    /** true, wenn das Gerät ein Barometer hat. */
    public boolean isAvailable() {
        return pressureSensor != null;
    }

    /** true, sobald das Barometer mindestens einen Wert geliefert hat. */
    public boolean hasReading() {
        return hasReading;
    }

    /** Geglätteter Luftdruck in hPa. */
    public float getPressure() {
        return pressure;
    }

    /** Höhe in Metern über NN (Standardatmosphäre). */
    public float getAltitude() {
        return SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float value = event.values[0];
        if (!hasReading) {
            pressure = value;
            hasReading = true;
        } else {
            pressure += ALPHA * (value - pressure);
        }
    }
}
