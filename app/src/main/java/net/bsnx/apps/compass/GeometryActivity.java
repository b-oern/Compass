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

package net.bsnx.apps.compass;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import net.bsnx.android.GeometryView;
import net.bsnx.apps.compass.db.CompassDB;
import net.bsnx.apps.compass.db.PathItem;
import net.bsnx.apps.compass.db.PathList;
import net.bsnx.apps.compass.preference.PreferenceStore;

import java.util.Locale;

public class GeometryActivity extends AppCompatActivity {

    /** Farbton (HSV) des Kompass-Tracks: tiefster Punkt orange … höchster Punkt rot. */
    private static final float HUE_LOW  = 35f;
    private static final float HUE_HIGH = 0f;
    /** Farbe für Segmente ohne Höhenwert (z. B. Gerät ohne Barometer): Mitte des Verlaufs. */
    private static final int COLOR_NO_ALTITUDE =
            Color.HSVToColor(new float[]{ (HUE_LOW + HUE_HIGH) / 2f, 0.85f, 0.9f });
    /** Farbe für alles, was mit GPS zu tun hat; grün hebt sich vom rot/orangen Track ab. */
    private static final int COLOR_GPS = Color.rgb(0x2E, 0x7D, 0x32);

    /** Markierung (Ring) des aktiven Punkts. */
    private static final int COLOR_ACTIVE = Color.BLACK;

    private GeometryView drawingView;
    /** Angezeigter Tag als Unix-Timestamp (Sekunden). */
    private long ts;
    private PathList items;
    private FrameLayout root;
    private View legend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        drawingView = new GeometryView(this);

        ts = CompassDB.getTodayDateTS();
        if (getIntent().hasExtra("date")) {
            ts = getIntent().getLongExtra("date", CompassDB.getTodayDateTS());
        }

        root = new FrameLayout(this);
        root.addView(drawingView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setFitsSystemWindows(true);
        setContentView(root);

        reload();
        drawingView.post(() -> drawingView.zoomToAll());
    }

    /** Beim Zurückkehren (z. B. aus der Liste nach Löschen) Daten neu laden; Zoom bleibt. */
    @Override
    protected void onRestart() {
        super.onRestart();
        reload();
    }

    /** Lädt die Einträge des Tages neu und zeichnet Karte und Legende. */
    private void reload() {
        CompassDB db = new CompassDB(this);
        items = db.getByDay((int) ts);
        db.close();

        String gpsSummary = render();

        if (legend != null) root.removeView(legend);
        legend = buildLegend(items, items.getMinAltitude(), items.getMaxAltitude(), gpsSummary);
        root.addView(legend, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));
    }

    /**
     * Zeichnet Pfad, GPS-Spur und aktiven Punkt neu (Zoom bleibt erhalten).
     *
     * @return GPS-Text für die Legende, oder {@code null} ohne GPS-Daten
     */
    private String render() {
        drawingView.clearAll();

        float minAlt = items.getMinAltitude();
        float maxAlt = items.getMaxAltitude();
        int activeId = effectiveActiveId();

        String gpsSummary = drawGps(items);

        for (PathItem item : items) {
            // Segmentfarbe aus mittlerer Höhe von Start- und Endpunkt
            float alt = item.getAltitude();
            float prevAlt = item.getPrevious() == null ? Float.NaN : item.getPrevious().getAltitude();
            float segAlt = Float.isNaN(prevAlt) ? alt
                    : Float.isNaN(alt) ? prevAlt
                    : (prevAlt + alt) / 2f;
            int color = altitudeColor(segAlt, minAlt, maxAlt);

            // Bildschirm-y wächst nach unten → y negieren, damit Norden oben liegt
            drawingView.addArrow(item.getStartX(), -item.getStartY(), item.getX(), -item.getY(), color);
            if (item.getId() == activeId) {
                // Ring unter dem Punkt markiert den aktiven Punkt
                drawingView.addPoint(item.getX(), -item.getY(), COLOR_ACTIVE, 14f, null, null);
            }
            drawingView.addPoint(item.getX(), -item.getY(),
                    item.hasAltitude() ? altitudeColor(alt, minAlt, maxAlt) : COLOR_NO_ALTITUDE,
                    8f, tooltip(item, items.getStepLength(), item.getId() == activeId), null,
                    () -> setActive(item));
        }
        return gpsSummary;
    }

    /**
     * Aktiver Punkt dieses Tages: der per Long-Press gewählte, sonst (nur heute)
     * der zuletzt hinzugefügte. 0 = keiner.
     */
    private int effectiveActiveId() {
        if (items.isEmpty() || !CompassDB.isToday((int) ts)) return 0;
        int stored = PreferenceStore.readActiveItemId(this);
        for (PathItem item : items) {
            if (item.getId() == stored) return stored;
        }
        return items.get(items.size() - 1).getId();
    }

    private void setActive(PathItem item) {
        if (!CompassDB.isToday((int) ts)) {
            Toast.makeText(this, "Fortsetzen ist nur für heutige Einträge möglich",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        PreferenceStore.writeActiveItemId(this, item.getId());
        Toast.makeText(this, "Aktiver Punkt: nächstes Add setzt hier fort",
                Toast.LENGTH_SHORT).show();
        render();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_geometry, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_show_list) {
            Intent intent = new Intent(this, ListActivity.class);
            intent.putExtra(ListActivity.EXTRA_DAY_UNIX, ts);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Zeichnet die beim Speichern erfassten GPS-Positionen in {@link #COLOR_GPS}.
     *
     * <p>Ausrichtung: Das erste Item mit GPS dient als Anker; seine GPS-Position
     * wird auf seine Schritt-Position gelegt. Alle weiteren GPS-Positionen werden
     * relativ dazu eingezeichnet, die Abweichung zur jeweiligen Schritt-Position
     * zeigt die Genauigkeit der Schrittmessung.</p>
     *
     * @return Text für die Legende, oder {@code null} ohne GPS-Daten
     */
    private String drawGps(PathList items) {
        PathItem anchor = null;
        for (PathItem item : items) {
            if (item.hasGps()) { anchor = item; break; }
        }
        if (anchor == null) return null;

        float lastDeviation = Float.NaN;
        PathItem lastGps = anchor;
        for (PathItem item : items) {
            if (!item.hasGps()) continue;

            float[] g = gpsPosition(item, anchor);
            // Linie zum nächsten Vorgänger mit GPS (folgt Verzweigungen)
            PathItem prev = item.getPrevious();
            while (prev != null && !prev.hasGps()) prev = prev.getPrevious();
            if (prev != null) {
                float[] p = gpsPosition(prev, anchor);
                drawingView.addLine(p[0], -p[1], g[0], -g[1], COLOR_GPS, 2f);
            }

            float deviation = (float) Math.hypot(g[0] - item.getX(), g[1] - item.getY());
            String tip = String.format(Locale.getDefault(), "GPS · Abweichung %.0f m", deviation);
            if (!Float.isNaN(item.getGpsAccuracy())) {
                tip += String.format(Locale.getDefault(), " (±%.0f m)", item.getGpsAccuracy());
            }
            drawingView.addPoint(g[0], -g[1], COLOR_GPS, 5f, tip, null);
            lastDeviation = deviation;
            lastGps = item;
        }

        // Weglänge entlang des Pfads zwischen Anker und letztem GPS-Eintrag
        int steps = Math.abs(stepsFromOrigin(lastGps) - stepsFromOrigin(anchor));
        if (steps == 0) return "GPS (grün): nur ein Eintrag mit GPS";
        float distance = steps * items.getStepLength();
        return String.format(Locale.getDefault(),
                "GPS (grün): Abweichung am Ende %.0f m (%.0f %% von %.0f m)",
                lastDeviation, 100f * lastDeviation / distance, distance);
    }

    /** GPS-Position von {@code item} in Pfad-Koordinaten (Anker liegt auf seiner Schritt-Position). */
    private static float[] gpsPosition(PathItem item, PathItem anchor) {
        float[] m = item.gpsToLocalMeters(anchor);
        return new float[]{ m[0] + anchor.getX(), m[1] + anchor.getY() };
    }

    /** Summe der Schritte vom Ursprung bis zum Endpunkt von {@code item} entlang seiner Vorgänger. */
    private static int stepsFromOrigin(PathItem item) {
        int steps = 0;
        for (PathItem p = item; p != null; p = p.getPrevious()) steps += p.getSteps();
        return steps;
    }

    /**
     * Bildet eine Höhe auf einen Farbverlauf orange (tief) → rot (hoch) ab,
     * relativ zu min/max des Tages.
     */
    static int altitudeColor(float altitude, float min, float max) {
        if (Float.isNaN(altitude)) return COLOR_NO_ALTITUDE;
        float range = max - min;
        // Kaum Höhenunterschied (Messrauschen) → einheitlich Mitte des Verlaufs
        float t = range < 1f ? 0.5f : (altitude - min) / range;
        t = Math.max(0f, Math.min(1f, t));
        return Color.HSVToColor(new float[]{ HUE_LOW + (HUE_HIGH - HUE_LOW) * t, 0.85f, 0.9f });
    }

    private static String tooltip(PathItem item, float stepLength, boolean active) {
        String s = String.format(Locale.getDefault(), "%d Schritte (%.0f m) · %d°",
                item.getSteps(), item.getSteps() * stepLength, item.getHeading());
        if (item.hasAltitude()) {
            s += String.format(Locale.getDefault(), " · %.1f m", item.getAltitude());
        }
        if (active) s += " · aktiv";
        return s;
    }

    private LinearLayout buildLegend(PathList items, float minAlt, float maxAlt, String gpsSummary) {
        int pad = dp(12);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, pad / 2, pad, pad / 2);
        panel.setBackgroundColor(Color.argb(200, 255, 255, 255));

        TextView distance = new TextView(this);
        distance.setText(String.format(Locale.getDefault(),
                "%d Schritte · %.0f m · Luftlinie %.0f m  (Schrittweite %.2f m)",
                items.getTotalSteps(), items.getTotalDistance(),
                items.getDisplacement(), items.getStepLength()));
        distance.setTextColor(Color.DKGRAY);
        panel.addView(distance);

        if (gpsSummary != null) {
            TextView gpsLabel = new TextView(this);
            gpsLabel.setText(gpsSummary);
            gpsLabel.setTextColor(COLOR_GPS);
            panel.addView(gpsLabel);
        }

        if (Float.isNaN(minAlt)) return panel;

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.setPadding(0, pad / 2, 0, 0);
        panel.addView(legend);

        TextView minLabel = new TextView(this);
        minLabel.setText(String.format(Locale.getDefault(), "%.0f m", minAlt));
        minLabel.setTextColor(Color.DKGRAY);
        legend.addView(minLabel);

        int[] stops = new int[5];
        for (int i = 0; i < stops.length; i++) {
            float t = i / (float) (stops.length - 1);
            stops[i] = altitudeColor(t, 0f, 1f);
        }
        View bar = new View(this);
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, stops));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, dp(12), 1f);
        barLp.setMargins(pad, 0, pad, 0);
        legend.addView(bar, barLp);

        TextView maxLabel = new TextView(this);
        maxLabel.setText(String.format(Locale.getDefault(), "%.0f m  (Δ %.0f m)",
                maxAlt, maxAlt - minAlt));
        maxLabel.setTextColor(Color.DKGRAY);
        legend.addView(maxLabel);

        return panel;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
