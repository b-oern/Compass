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
import java.util.List;

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
     * <p>Jedes Item beschreibt einen Schritt vom <em>Ende des vorherigen Items</em>
     * aus. Die Position eines Items ist also der Endpunkt seiner Bewegung:</p>
     * <pre>
     *   x_i = x_{i-1} + steps_i * sin(heading_i)
     *   y_i = y_{i-1} + steps_i * cos(heading_i)
     * </pre>
     *
     * <p>Ein Item mit heading=0° und steps=100 landet bei (0, 100) — also genau
     * 100 Schritte nach Norden.</p>
     */
    public void computeCoordinates() {
        float curX = 0f;
        float curY = 0f;

        for (PathItem item : this) {
            double rad = Math.toRadians(item.getHeading());
            curX += (float) (item.getSteps() * Math.sin(rad));
            curY += (float) (item.getSteps() * Math.cos(rad));

            item.setX(curX);
            item.setY(curY);
        }
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

    /**
     * Gibt die Luftlinien-Distanz vom Start- zum Endpunkt zurück.
     * Setzt voraus, dass {@link #computeCoordinates()} bereits aufgerufen wurde.
     */
    public float getDisplacement() {
        if (isEmpty()) return 0f;
        PathItem last = get(size() - 1);
        return (float) Math.sqrt(last.getX() * last.getX() + last.getY() * last.getY());
    }

    @Override
    public String toString() {
        return "PathList{size=" + size() +
                ", totalSteps=" + getTotalSteps() +
                "}";
    }
}
