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

package net.bsnx.apps.compass.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import net.bsnx.apps.compass.preference.PreferenceStore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class CompassDB {

    // ── Schema ────────────────────────────────────────────────────────────────

    private static final String DB_NAME    = "compass.db";
    private static final int    DB_VERSION = 5;

    private static final String TABLE      = "path_items";
    private static final String COL_ID        = "id";
    private static final String COL_HEADING   = "heading";
    private static final String COL_STEPS     = "steps";
    private static final String COL_TIMESTAMP = "timestamp";
    /** Luftdruck in hPa, NULL ohne Barometer (seit DB-Version 2). */
    private static final String COL_PRESSURE  = "pressure";
    /** Höhe in Metern über NN, NULL ohne Barometer (seit DB-Version 2). */
    private static final String COL_ALTITUDE  = "altitude";
    /** GPS-Position beim Speichern, NULL wenn GPS aus/kein Fix (seit DB-Version 4). */
    private static final String COL_LAT          = "latitude";
    private static final String COL_LON          = "longitude";
    /** GPS-Genauigkeit in Metern (seit DB-Version 4). */
    private static final String COL_GPS_ACCURACY = "gps_accuracy";
    /**
     * ID des Items, von dessen Endpunkt dieses Item ausgeht (seit DB-Version 5).
     * NULL = setzt das zeitlich vorherige Item fort.
     */
    private static final String COL_PARENT_ID    = "parent_id";

    private static final String SQL_CREATE =
            "CREATE TABLE " + TABLE + " (" +
                    COL_ID        + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_HEADING   + " INTEGER NOT NULL, " +
                    COL_STEPS     + " INTEGER NOT NULL, " +
                    COL_TIMESTAMP + " INTEGER NOT NULL, " +
                    COL_PRESSURE  + " REAL, " +
                    COL_ALTITUDE  + " REAL, " +
                    COL_LAT       + " REAL, " +
                    COL_LON       + " REAL, " +
                    COL_GPS_ACCURACY + " REAL, " +
                    COL_PARENT_ID + " INTEGER" +
                    ")";

    private static final class DbHelper extends SQLiteOpenHelper {

        DbHelper(Context ctx) {
            super(ctx, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Bestehende Pfade erhalten: Spalten ergänzen statt Tabelle neu anlegen.
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_PRESSURE + " REAL");
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_ALTITUDE + " REAL");
            }
            if (oldVersion < 4) {
                // Version 3 (nur Entwicklungsstand) hatte eine separate GPS-Tabelle
                db.execSQL("DROP TABLE IF EXISTS gps_points");
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_LAT + " REAL");
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_LON + " REAL");
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_GPS_ACCURACY + " REAL");
            }
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_PARENT_ID + " INTEGER");
            }
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final DbHelper       helper;
    private final SQLiteDatabase db;
    /** Schrittweite in Metern aus den Einstellungen, für die Koordinatenberechnung. */
    private final float          stepLength;

    // ── Constructor ───────────────────────────────────────────────────────────

    public CompassDB(Context ctx) {
        helper     = new DbHelper(ctx.getApplicationContext());
        db         = helper.getWritableDatabase();
        stepLength = PreferenceStore.readStepLength(ctx);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Inserts a new PathItem with the current unix timestamp.
     *
     * @param heading direction in degrees (0–359)
     * @param steps   number of steps walked
     * @return the newly created PathItem, or {@code null} on failure
     */
    public PathItem create(int heading, int steps) {
        return create(heading, steps, Float.NaN, Float.NaN, Double.NaN, Double.NaN, Float.NaN,
                PathItem.NO_PARENT);
    }

    /**
     * Wie {@link #create(int, int)}, zusätzlich mit Luftdruck, Höhe, GPS-Position
     * und Ausgangspunkt. Unbekannte Werte als NaN übergeben; sie werden als NULL gespeichert.
     *
     * @param pressure    Luftdruck in hPa
     * @param altitude    Höhe in Metern über NN
     * @param latitude    GPS-Breitengrad
     * @param longitude   GPS-Längengrad
     * @param gpsAccuracy GPS-Genauigkeit in Metern
     * @param parentId    ID des Items, von dem aus fortgesetzt wird,
     *                    oder {@link PathItem#NO_PARENT} für das zeitlich vorherige
     */
    public PathItem create(int heading, int steps, float pressure, float altitude,
                           double latitude, double longitude, float gpsAccuracy,
                           int parentId) {
        int now = (int) (System.currentTimeMillis() / 1000L);

        ContentValues cv = new ContentValues();
        cv.put(COL_HEADING,   heading);
        cv.put(COL_STEPS,     steps);
        cv.put(COL_TIMESTAMP, now);
        if (Float.isNaN(pressure)) cv.putNull(COL_PRESSURE); else cv.put(COL_PRESSURE, pressure);
        if (Float.isNaN(altitude)) cv.putNull(COL_ALTITUDE); else cv.put(COL_ALTITUDE, altitude);
        if (Double.isNaN(latitude))  cv.putNull(COL_LAT); else cv.put(COL_LAT, latitude);
        if (Double.isNaN(longitude)) cv.putNull(COL_LON); else cv.put(COL_LON, longitude);
        if (Float.isNaN(gpsAccuracy)) cv.putNull(COL_GPS_ACCURACY); else cv.put(COL_GPS_ACCURACY, gpsAccuracy);
        if (parentId == PathItem.NO_PARENT) cv.putNull(COL_PARENT_ID); else cv.put(COL_PARENT_ID, parentId);

        long rowId = db.insert(TABLE, null, cv);
        if (rowId == -1) return null;

        return new PathItem(this, (int) rowId, heading, steps, now, pressure, altitude,
                latitude, longitude, gpsAccuracy, parentId);
    }

    /**
     * Returns all PathItems whose timestamp falls within today
     * (midnight … midnight), ordered by timestamp ascending.
     */
    public List<PathItem> getTodays() {
        long[] bounds = todayBounds();

        Cursor cursor = db.query(
                TABLE,
                null,                               // all columns
                COL_TIMESTAMP + " >= ? AND " + COL_TIMESTAMP + " < ?",
                new String[]{ String.valueOf(bounds[0]), String.valueOf(bounds[1]) },
                null, null,
                COL_TIMESTAMP + " ASC, " + COL_ID + " ASC"
        );

        List<PathItem> result = new ArrayList<>();
        readItems(cursor, result);
        return result;
    }

    public PathList getAll() {
        Cursor cursor = db.query(
                TABLE,
                null,                               // all columns
                "",
                new String[]{},
                null, null,
                COL_TIMESTAMP + " ASC, " + COL_ID + " ASC"
        );
        PathList result = new PathList();
        readItems(cursor, result);
        result.computeCoordinates(stepLength);
        return result;
    }

    /**
     * Gibt alle Tage zurück, an denen mindestens ein PathItem existiert.
     * Die zurückgegebenen {@link Date}-Objekte sind jeweils auf Mitternacht
     * (00:00:00) des jeweiligen Tages normiert.
     */
    public List<Date> getDays() {
        // Tagesbeginn in lokaler Zeitzone bestimmen (wie getByDay), nicht in UTC.
        Cursor cursor = db.rawQuery(
                "SELECT " + COL_TIMESTAMP +
                        " FROM " + TABLE +
                        " ORDER BY " + COL_TIMESTAMP + " ASC",
                null
        );

        List<Date> days = new ArrayList<>();
        try {
            long lastDayStart = Long.MIN_VALUE;
            while (cursor.moveToNext()) {
                long dayStart = dayBounds(unixToDate(cursor.getInt(0)))[0];
                if (dayStart != lastDayStart) {
                    days.add(new Date(dayStart * 1000L));
                    lastDayStart = dayStart;
                }
            }
        } finally {
            cursor.close();
        }
        return days;
    }

    public PathList getByDay(int ts) {
        return getByDay(new Date(1000L*ts));
    }

    /**
     * Gibt alle PathItems zurück, deren Timestamp in den angegebenen Tag fällt.
     *
     * @param day ein beliebiger {@link Date}-Zeitpunkt innerhalb des gewünschten Tages
     * @return PathItems des Tages, aufsteigend nach Timestamp sortiert
     */
    public PathList getByDay(Date day) {
        long[] bounds = dayBounds(day);

        Cursor cursor = db.query(
                TABLE,
                null,
                COL_TIMESTAMP + " >= ? AND " + COL_TIMESTAMP + " < ?",
                new String[]{ String.valueOf(bounds[0]), String.valueOf(bounds[1]) },
                null, null,
                COL_TIMESTAMP + " ASC, " + COL_ID + " ASC"
        );

        PathList result = new PathList();
        readItems(cursor, result);
        result.computeCoordinates(stepLength);
        return result;
    }

    /** Closes the underlying database connection. */
    public void close() {
        helper.close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Liest alle Zeilen des Cursors als PathItems in {@code out} und schließt ihn. */
    private void readItems(Cursor cursor, List<PathItem> out) {
        try {
            int idxId   = cursor.getColumnIndexOrThrow(COL_ID);
            int idxHead = cursor.getColumnIndexOrThrow(COL_HEADING);
            int idxStep = cursor.getColumnIndexOrThrow(COL_STEPS);
            int idxTs   = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);
            int idxPres = cursor.getColumnIndexOrThrow(COL_PRESSURE);
            int idxAlt  = cursor.getColumnIndexOrThrow(COL_ALTITUDE);
            int idxLat  = cursor.getColumnIndexOrThrow(COL_LAT);
            int idxLon  = cursor.getColumnIndexOrThrow(COL_LON);
            int idxAcc  = cursor.getColumnIndexOrThrow(COL_GPS_ACCURACY);
            int idxPar  = cursor.getColumnIndexOrThrow(COL_PARENT_ID);

            while (cursor.moveToNext()) {
                out.add(new PathItem(
                        this,
                        cursor.getInt(idxId),
                        cursor.getInt(idxHead),
                        cursor.getInt(idxStep),
                        cursor.getInt(idxTs),
                        cursor.isNull(idxPres) ? Float.NaN  : cursor.getFloat(idxPres),
                        cursor.isNull(idxAlt)  ? Float.NaN  : cursor.getFloat(idxAlt),
                        cursor.isNull(idxLat)  ? Double.NaN : cursor.getDouble(idxLat),
                        cursor.isNull(idxLon)  ? Double.NaN : cursor.getDouble(idxLon),
                        cursor.isNull(idxAcc)  ? Float.NaN  : cursor.getFloat(idxAcc),
                        cursor.isNull(idxPar)  ? PathItem.NO_PARENT : cursor.getInt(idxPar)
                ));
            }
        } finally {
            cursor.close();
        }
    }

    /** Returns [startOfDayUnix, startOfTomorrowUnix] as seconds for today. */
    private static long[] todayBounds() {
        return dayBounds(new Date());
    }

    /** Returns [startOfDayUnix, startOfTomorrowUnix] as seconds for any date. */
    private static long[] dayBounds(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE,      0);
        cal.set(Calendar.SECOND,      0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis() / 1000L;

        cal.add(Calendar.DAY_OF_MONTH, 1);
        long end = cal.getTimeInMillis() / 1000L;

        return new long[]{ start, end };
    }

    public static Date getTodayDate() {
        return new Date(dayBounds(new Date())[0] * 1000L);
    }

    public static int getTodayDateTS() {
        return CompassDB.dateToUnix(new Date(dayBounds(new Date())[0] * 1000L));
    }

    /** true, wenn der Unix-Timestamp (Sekunden) auf den heutigen Tag (lokale Zeit) fällt. */
    public static boolean isToday(int timestamp) {
        return dayBounds(unixToDate(timestamp))[0] == todayBounds()[0];
    }

    public static int dateToUnix(Date date) {
        return (int) (date.getTime() / 1000L);
    }

    public static Date unixToDate(int timestamp) {
        return new Date((long) timestamp * 1000L);
    }

    /**
     * Löscht ein PathItem anhand seiner ID aus der Datenbank.
     *
     * @return true wenn ein Datensatz gelöscht wurde, sonst false
     */
    public boolean delete(PathItem item) {
        int rows = db.delete(TABLE, COL_ID + " = ?",
                new String[]{ String.valueOf(item.getId()) });
        return rows > 0;
    }

}
