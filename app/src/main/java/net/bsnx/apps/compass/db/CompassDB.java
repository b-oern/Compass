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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class CompassDB {

    // ── Schema ────────────────────────────────────────────────────────────────

    private static final String DB_NAME    = "compass.db";
    private static final int    DB_VERSION = 1;

    private static final String TABLE      = "path_items";
    private static final String COL_ID        = "id";
    private static final String COL_HEADING   = "heading";
    private static final String COL_STEPS     = "steps";
    private static final String COL_TIMESTAMP = "timestamp";

    private static final String SQL_CREATE =
            "CREATE TABLE " + TABLE + " (" +
                    COL_ID        + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_HEADING   + " INTEGER NOT NULL, " +
                    COL_STEPS     + " INTEGER NOT NULL, " +
                    COL_TIMESTAMP + " INTEGER NOT NULL" +
                    ")";



    // ── Inner SQLiteOpenHelper ────────────────────────────────────────────────

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
            db.execSQL("DROP TABLE IF EXISTS " + TABLE);
            onCreate(db);
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final DbHelper       helper;
    private final SQLiteDatabase db;

    // ── Constructor ───────────────────────────────────────────────────────────

    public CompassDB(Context ctx) {
        helper = new DbHelper(ctx.getApplicationContext());
        db     = helper.getWritableDatabase();
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
        int now = (int) (System.currentTimeMillis() / 1000L);

        ContentValues cv = new ContentValues();
        cv.put(COL_HEADING,   heading);
        cv.put(COL_STEPS,     steps);
        cv.put(COL_TIMESTAMP, now);

        long rowId = db.insert(TABLE, null, cv);
        if (rowId == -1) return null;

        return new PathItem(this, (int) rowId, heading, steps, now);
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
                COL_TIMESTAMP + " ASC"
        );

        List<PathItem> result = new ArrayList<>();
        try {
            int idxId   = cursor.getColumnIndexOrThrow(COL_ID);
            int idxHead = cursor.getColumnIndexOrThrow(COL_HEADING);
            int idxStep = cursor.getColumnIndexOrThrow(COL_STEPS);
            int idxTs   = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);

            while (cursor.moveToNext()) {
                result.add(new PathItem(this, cursor.getInt(idxId), cursor.getInt(idxHead),cursor.getInt(idxStep),cursor.getInt(idxTs)));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public PathList getAll() {
        Cursor cursor = db.query(
                TABLE,
                null,                               // all columns
                "",
                new String[]{},
                null, null,
                COL_TIMESTAMP + " ASC"
        );
        PathList result = new PathList();
        try {
            int idxId   = cursor.getColumnIndexOrThrow(COL_ID);
            int idxHead = cursor.getColumnIndexOrThrow(COL_HEADING);
            int idxStep = cursor.getColumnIndexOrThrow(COL_STEPS);
            int idxTs   = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);

            while (cursor.moveToNext()) {
                result.add(new PathItem(this, cursor.getInt(idxId), cursor.getInt(idxHead),cursor.getInt(idxStep),cursor.getInt(idxTs)));
            }
        } finally {
            cursor.close();
        }
        result.computeCoordinates();
        return result;
    }

    /**
     * Gibt alle Tage zurück, an denen mindestens ein PathItem existiert.
     * Die zurückgegebenen {@link Date}-Objekte sind jeweils auf Mitternacht
     * (00:00:00) des jeweiligen Tages normiert.
     */
    public List<Date> getDays() {
        // Distinct-Tage per SQL: Timestamp auf Tagesbeginn runden.
        // SQLite: ganzzahlige Division durch 86400 liefert den "Tag-Index".
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT (" + COL_TIMESTAMP + " / 86400) * 86400 AS day_start" +
                        " FROM " + TABLE +
                        " ORDER BY day_start ASC",
                null
        );

        List<Date> days = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                long epochSeconds = cursor.getLong(0);
                days.add(new Date(epochSeconds * 1000L));
            }
        } finally {
            cursor.close();
        }
        return days;
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
                COL_TIMESTAMP + " ASC"
        );

        PathList result = new PathList();
        try {
            int idxId   = cursor.getColumnIndexOrThrow(COL_ID);
            int idxHead = cursor.getColumnIndexOrThrow(COL_HEADING);
            int idxStep = cursor.getColumnIndexOrThrow(COL_STEPS);
            int idxTs   = cursor.getColumnIndexOrThrow(COL_TIMESTAMP);

            while (cursor.moveToNext()) {
                result.add(new PathItem(
                        this,
                        cursor.getInt(idxId),
                        cursor.getInt(idxHead),
                        cursor.getInt(idxStep),
                        cursor.getInt(idxTs)
                ));
            }
        } finally {
            cursor.close();
        }
        result.computeCoordinates();
        return result;
    }

    /** Closes the underlying database connection. */
    public void close() {
        helper.close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
