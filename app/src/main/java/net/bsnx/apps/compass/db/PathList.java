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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONArray;

/**
 * Eine geordnete Liste von {@link PathItem}s, die einen zusammenhängenden Pfad
 * repräsentiert (z. B. alle Einträge eines Tages).
 *
 * <p>Nach dem Befüllen kann {@link #computeCoordinates()} aufgerufen werden, um
 * für jedes Item die kartesischen Koordinaten (x/y) relativ zum Startpunkt zu
 * berechnen. Der Startpunkt ist dabei immer (0, 0).</p>
 *
 * <p>Koordinatensystem:</p>
 * <pre>
 *        +y (Nord)
 *         |
 *  −x ────┼──── +x (Ost)
 *         |
 *        −y (Süd)
 * </pre>
 */
public class PathList extends ArrayList<PathItem> {

    /** Schrittweite in Metern, mit der Schritte in Koordinaten umgerechnet werden. */
    private float stepLength = 1f;

    public PathList() {
        super();
    }

    public PathList(List<PathItem> items) {
        super(items);
    }

    // ── Koordinatenberechnung ─────────────────────────────────────────────────

    /**
     * Berechnet für jedes {@link PathItem} die kartesischen Koordinaten (x, y)
     * relativ zum Startpunkt (0, 0) und schreibt sie mit
     * {@link PathItem#setX(float)} / {@link PathItem#setY(float)} zurück.
     *
     * <p>Jedes Item beschreibt eine Bewegung vom Endpunkt seines Vorgängers aus.
     * Vorgänger ist das Item mit {@link PathItem#getParentId()}, sofern es in
     * dieser Liste vor ihm liegt, sonst das zeitlich vorherige Item (erstes Item:
     * Ursprung). So kann der Pfad an einem beliebigen Punkt fortgesetzt werden.
     * Die Position eines Items ist der Endpunkt seiner Bewegung:</p>
     * <pre>
     *   x_i = x_prev + steps_i * stepLength * sin(heading_i)
     *   y_i = y_prev + steps_i * stepLength * cos(heading_i)
     * </pre>
     *
     * <p>Ein Item mit heading=0° und steps=100 landet bei stepLength=0,9 bei
     * (0, 90) — also 90 m nach Norden. Die Einheit von x/y ist Meter.</p>
     */
    public PathList computeCoordinates() {
        Map<Integer, PathItem> byId = new HashMap<>();
        PathItem last = null;

        for (PathItem item : this) {
            PathItem previous = byId.get(item.getParentId());
            if (previous == null) previous = last;
            item.setPrevious(previous);

            double rad = Math.toRadians(item.getHeading());
            float distance = item.getSteps() * stepLength;
            item.setX(item.getStartX() + (float) (distance * Math.sin(rad)));
            item.setY(item.getStartY() + (float) (distance * Math.cos(rad)));

            byId.put(item.getId(), item);
            last = item;
        }
        return this;
    }

    /** Wie {@link #computeCoordinates()}, mit neuer Schrittweite in Metern. */
    public PathList computeCoordinates(float stepLength) {
        this.stepLength = stepLength;
        return computeCoordinates();
    }

    public float getStepLength() {
        return stepLength;
    }

    /**
     * Gibt die Gesamtdistanz (Summe aller Steps) zurück.
     */
    public int getTotalSteps() {
        int total = 0;
        for (PathItem item : this) {
            total += item.getSteps();
        }
        return total;
    }

    /** Gesamte gelaufene Strecke in Metern. */
    public float getTotalDistance() {
        return getTotalSteps() * stepLength;
    }

    /**
     * Gibt die Luftlinien-Distanz vom Start- zum Endpunkt zurück.
     * Setzt voraus, dass {@link #computeCoordinates()} bereits aufgerufen wurde.
     */
    public float getDisplacement() {
        if (isEmpty()) return 0f;
        PathItem last = get(size() - 1);
        return (float) Math.sqrt(last.getX() * last.getX() + last.getY() * last.getY());
    }

    /** Kleinste bekannte Höhe in Metern, {@link Float#NaN} wenn keine bekannt ist. */
    public float getMinAltitude() {
        float min = Float.NaN;
        for (PathItem item : this) {
            if (item.hasAltitude() && !(item.getAltitude() >= min)) min = item.getAltitude();
        }
        return min;
    }

    /** Größte bekannte Höhe in Metern, {@link Float#NaN} wenn keine bekannt ist. */
    public float getMaxAltitude() {
        float max = Float.NaN;
        for (PathItem item : this) {
            if (item.hasAltitude() && !(item.getAltitude() <= max)) max = item.getAltitude();
        }
        return max;
    }

    /**
     * Spalten: heading,steps,x,y,timestamp,id,pressure,altitude
     * @return
     */
    public String toCsv() {
        StringBuilder sb = new StringBuilder();
        for (PathItem item : this) {
            sb.append(item.toCsvLine());
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "PathList{size=" + size() +
                ", totalSteps=" + getTotalSteps() +
                "}";
    }

    /**
     * Pfad für NXMap als JSON-Array von Tupeln [heading, meter, steps].
     * NXMap interpretiert das zweite Element als Meter; Meter = steps × Schrittweite.
     */
    public String toIntentExtra() {
        JSONArray array = new JSONArray();
        try {
            for (PathItem item : this) {
                JSONArray tuple = new JSONArray();
                tuple.put(item.getHeading());
                tuple.put((double) (item.getSteps() * stepLength));
                tuple.put(item.getSteps());
                array.put(tuple);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return array.toString();
    }

    public JSONArray toJson() {
        JSONArray array = new JSONArray();
        for (PathItem item : this) {
            array.put(item.toJson());
        }
        return array;
    }
}
