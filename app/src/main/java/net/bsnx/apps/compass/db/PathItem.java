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

public class PathItem {

    private final CompassDB owner;
    private final int       id;
    /** Himmelsrichtung in Grad (0 = Nord, 90 = Ost, …). */
    private final int       heading;
    private final int       steps;
    /** Unix-Timestamp in Sekunden seit Epoch. */
    private final int       timestamp;

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

    PathItem(CompassDB owner, int id, int heading, int steps, int timestamp) {
        this.owner     = owner;
        this.id        = id;
        this.heading   = heading;
        this.steps     = steps;
        this.timestamp = timestamp;
    }

    // ── Getter ────────────────────────────────────────────────────────────────

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
                "}";
    }

}
