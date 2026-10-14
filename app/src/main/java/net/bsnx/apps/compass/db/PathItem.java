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

import java.util.Date;
import org.json.JSONException;
import org.json.JSONObject;

public class PathItem {

    /** parentId-Wert für „setzt das zeitlich vorherige Item fort“ (IDs beginnen bei 1). */
    public static final int NO_PARENT = 0;

    private final CompassDB owner;
    private final int       id;
    /** Himmelsrichtung in Grad (0 = Nord, 90 = Ost, …). */
    private final int       heading;
    private final int       steps;
    /** Unix-Timestamp in Sekunden seit Epoch. */
    private final int       timestamp;
    /** Luftdruck in hPa, {@link Float#NaN} wenn unbekannt. */
    private final float     pressure;
    /** Höhe in Metern über NN, {@link Float#NaN} wenn unbekannt. */
    private final float     altitude;
    /** GPS-Position beim Speichern, {@link Double#NaN} ohne GPS. */
    private final double    latitude;
    private final double    longitude;
    /** GPS-Genauigkeit in Metern, {@link Float#NaN} wenn unbekannt. */
    private final float     gpsAccuracy;
    /** ID des Items, von dessen Endpunkt aus fortgesetzt wird, oder {@link #NO_PARENT}. */
    private final int       parentId;

    /**
     * Item, an dessen Endpunkt dieses Item beginnt; {@code null} = Startpunkt (0, 0).
     * Transient: wird von {@link PathList#computeCoordinates()} befüllt.
     */
    private PathItem previous;

    /**
     * Berechnete X-Koordinate (Ost+/West−) relativ zum Startpunkt.
     * Transient: wird nicht in der DB gespeichert, sondern von
     * {@link PathList#computeCoordinates()} befüllt.
     */
    private float x;

    /**
     * Berechnete Y-Koordinate (Nord+/Süd−) relativ zum Startpunkt.
     * Transient: wird nicht in der DB gespeichert, sondern von
     * {@link PathList#computeCoordinates()} befüllt.
     */
    private float y;

    // ── Konstruktor (package-private: nur CompassDB darf Instanzen erzeugen) ──

    PathItem(CompassDB owner, int id, int heading, int steps, int timestamp,
             float pressure, float altitude,
             double latitude, double longitude, float gpsAccuracy, int parentId) {
        this.parentId    = parentId;
        this.owner       = owner;
        this.id          = id;
        this.heading     = heading;
        this.steps       = steps;
        this.timestamp   = timestamp;
        this.pressure    = pressure;
        this.altitude    = altitude;
        this.latitude    = latitude;
        this.longitude   = longitude;
        this.gpsAccuracy = gpsAccuracy;
    }

    // ── Getter ────────────────────────────────────────────────────────────────

    public int       getParentId()  { return parentId;  }
    /** Vorgänger im Pfad (Startpunkt dieses Segments), {@code null} = Ursprung. */
    public PathItem  getPrevious()  { return previous;  }
    public float     getStartX()    { return previous == null ? 0f : previous.x; }
    public float     getStartY()    { return previous == null ? 0f : previous.y; }

    public float     getPressure()  { return pressure;  }
    public float     getAltitude()  { return altitude;  }
    public boolean   hasAltitude()  { return !Float.isNaN(altitude); }

    public double    getLatitude()    { return latitude;    }
    public double    getLongitude()   { return longitude;   }
    public float     getGpsAccuracy() { return gpsAccuracy; }
    public boolean   hasGps()         { return !Double.isNaN(latitude) && !Double.isNaN(longitude); }

    /**
     * GPS-Position dieses Items relativ zur GPS-Position von {@code origin} in Metern,
     * als {x (Ost+), y (Nord+)} — gleiches Koordinatensystem wie {@link #getX()}/{@link #getY()}.
     * Lokale Näherung (equirektangulär), für Strecken bis einige km ausreichend genau.
     * Beide Items müssen {@link #hasGps()} erfüllen.
     */
    public float[] gpsToLocalMeters(PathItem origin) {
        final double earthRadiusM = 6371000.0;
        double lat0 = Math.toRadians(origin.latitude);
        double dLat = Math.toRadians(latitude  - origin.latitude);
        double dLon = Math.toRadians(longitude - origin.longitude);
        return new float[]{
                (float) (earthRadiusM * dLon * Math.cos(lat0)),
                (float) (earthRadiusM * dLat)
        };
    }

    public CompassDB getOwner()     { return owner;     }
    public int       getId()        { return id;        }
    public int       getHeading()   { return heading;   }
    public int       getSteps()     { return steps;     }
    public int       getTimestamp() { return timestamp; }
    public float     getX()         { return x;         }
    public float     getY()         { return y;         }

    // ── Package-private Setter (nur PathList darf x/y setzen) ─────────────────

    void setX(float x) { this.x = x; }
    void setY(float y) { this.y = y; }
    void setPrevious(PathItem previous) { this.previous = previous; }

    /** ID des tatsächlichen Vorgängers (Startpunkt), 0 = Ursprung. */
    private int previousId() {
        return previous == null ? 0 : previous.id;
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    /** Gibt den Timestamp als {@link Date} zurück. */
    public Date getDate() {
        return new Date((long) timestamp * 1000L);
    }

    /**
     * Gibt die Himmelsrichtung als lesbaren String zurück.
     * Teilt den Kreis in 8 Sektoren à 45°.
     */
    public String getCardinalDirection() {
        String[] directions = { "N", "NO", "O", "SO", "S", "SW", "W", "NW" };
        int index = (int) Math.round(((heading % 360) / 45.0)) % 8;
        return directions[index];
    }

    @Override
    public String toString() {
        return "PathItem{id=" + id +
                ", heading=" + heading + "° (" + getCardinalDirection() + ")" +
                ", steps=" + steps +
                ", x=" + x + ", y=" + y +
                ", timestamp=" + timestamp +
                ", pressure=" + pressure +
                ", altitude=" + altitude +
                "}";
    }

    /**
     * Spalten: heading,steps,x,y,timestamp,id,pressure,altitude,latitude,longitude,gps_accuracy,previous_id
     * (leer, wenn unbekannt; previous_id = Item, an dessen Endpunkt dieses beginnt, 0 = Ursprung)
     * @return
     */
    public String toCsvLine() {
        return "" + heading + ";" +
                steps + ";" +
                x + ";" + y + ";" +
                timestamp + ";" +
                id + ";" +
                (Float.isNaN(pressure) ? "" : pressure) + ";" +
                (Float.isNaN(altitude) ? "" : altitude) + ";" +
                (Double.isNaN(latitude)  ? "" : latitude)  + ";" +
                (Double.isNaN(longitude) ? "" : longitude) + ";" +
                (Float.isNaN(gpsAccuracy) ? "" : gpsAccuracy) + ";" +
                previousId() + ";";
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("heading", heading);
            json.put("steps", steps);
            json.put("x", x);
            json.put("y", y);
            json.put("timestamp", timestamp);
            json.put("id", id);
            json.put("previous_id", previousId());
            // JSON kennt kein NaN → unbekannte Werte weglassen
            if (!Float.isNaN(pressure)) json.put("pressure", pressure);
            if (!Float.isNaN(altitude)) json.put("altitude", altitude);
            if (hasGps()) {
                json.put("latitude", latitude);
                json.put("longitude", longitude);
                if (!Float.isNaN(gpsAccuracy)) json.put("gps_accuracy", gpsAccuracy);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return json;
    }

    public String toJsonString() {
        return toJson().toString();
    }

}
