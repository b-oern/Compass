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

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.bsnx.android.GeometryView;
import net.bsnx.apps.compass.db.CompassDB;
import net.bsnx.apps.compass.db.PathItem;
import net.bsnx.apps.compass.db.PathList;

import java.util.List;

public class GeometryActivity extends AppCompatActivity {

    private GeometryView drawingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        drawingView = new GeometryView(this);

        int ts = CompassDB.getTodayDateTS();
        if (getIntent().hasExtra("date")) {
            getIntent().getIntExtra("date", CompassDB.getTodayDateTS());
        }
        CompassDB db = new CompassDB(this);
        PathList items = db.getByDay(CompassDB.unixToDate(ts));
        float x = 0, y = 0;
        for (PathItem item : items) {
            drawingView.addArrow(-x,y, -item.getX(), item.getY(), Color.RED);
            x = item.getX();
            y = item.getY();
        }
        /*drawingView.addLine(200f, 50f, 200f, 500f, Color.BLUE, 5f);
        drawingView.addArrow(300f, 100f, 300f, 400f, Color.MAGENTA, 3f, 30f);
        drawingView.addPoint(100f, 100f, Color.RED, "Startpunkt", () -> Toast.makeText(this, "Startpunkt getappt!", Toast.LENGTH_SHORT).show());*/

        setContentView(drawingView);
        drawingView.post(() -> drawingView.zoomToAll());

    }
}
