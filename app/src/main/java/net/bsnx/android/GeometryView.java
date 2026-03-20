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

package net.bsnx.android;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class GeometryView extends View {

    public interface OnTapListener {
        void onTap();
    }

    private static String colorToSvg(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    public static abstract class DrawingObject {
        public abstract String toSvg();
    }

    public static class DrawingLine extends DrawingObject {
        public final float x1, y1, x2, y2;
        public final int   color;
        public final float strokeWidth;

        public DrawingLine(float x1, float y1, float x2, float y2, int color, float strokeWidth) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.color = color; this.strokeWidth = strokeWidth;
        }
        public String toSvg() {
            return String.format(
                    "<line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" stroke=\"%s\" stroke-width=\"%f\" />",
                    x1, y1, x2, y2, colorToSvg(color), strokeWidth
            );
        }
    }

    public static class DrawingArrow extends DrawingObject {
        public final float x1, y1, x2, y2;
        public final int   color;
        public final float strokeWidth, arrowHeadSize;

        public DrawingArrow(float x1, float y1, float x2, float y2,
                            int color, float strokeWidth, float arrowHeadSize) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.color = color; this.strokeWidth = strokeWidth;
            this.arrowHeadSize = arrowHeadSize;
        }

        public String toSvg() {
            double angle = Math.atan2(y2 - y1, x2 - x1);
            float x3 = (float)(x2 - arrowHeadSize * Math.cos(angle - Math.PI/6));
            float y3 = (float)(y2 - arrowHeadSize * Math.sin(angle - Math.PI/6));
            float x4 = (float)(x2 - arrowHeadSize * Math.cos(angle + Math.PI/6));
            float y4 = (float)(y2 - arrowHeadSize * Math.sin(angle + Math.PI/6));
            String colorHex = colorToSvg(color);
            String line = String.format(
                    "<line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" stroke=\"%s\" stroke-width=\"%f\" />",
                    x1, y1, x2, y2, colorHex, strokeWidth
            );
            String head = String.format(
                    "<polygon points=\"%f,%f %f,%f %f,%f\" fill=\"%s\" />",
                    x2, y2, x3, y3, x4, y4, colorHex
            );
            return line + "\n" + head;
        }
    }

    public static class DrawingPoint extends DrawingObject {
        public final float  x, y, radius;
        public final int    color;
        public final String tooltip;
        public final OnTapListener onTap;

        public DrawingPoint(float x, float y, int color, float radius,
                            String tooltip, OnTapListener onTap) {
            this.x = x; this.y = y; this.color = color; this.radius = radius;
            this.tooltip = tooltip != null ? tooltip : "";
            this.onTap = onTap;
        }
        public String toSvg() {
            String circle = String.format(
                    "<circle cx=\"%f\" cy=\"%f\" r=\"%f\" fill=\"%s\">",
                    x, y, radius, colorToSvg(color)
            );

            if (!tooltip.isEmpty()) {
                circle += "<title>" + tooltip + "</title>";
            }

            circle += "</circle>";
            return circle;
        }
    }

    private final List<DrawingLine>   lines   = new ArrayList<>();
    private final List<DrawingArrow>  arrows  = new ArrayList<>();
    private final List<DrawingPoint>  points  = new ArrayList<>();

    private final Matrix matrix       = new Matrix();
    private final Matrix invertMatrix = new Matrix();
    private float scaleFactor  = 1f;
    private float translateX   = 0f;
    private float translateY   = 0f;
    private final float minScale = 0.1f;
    private final float maxScale = 20f;

    private DrawingPoint activeTooltip = null;
    private final float  tapTolerance  = 40f;

    private final Paint linePaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipBgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint= new Paint(Paint.ANTI_ALIAS_FLAG);

    private ScaleGestureDetector scaleDetector;
    private GestureDetector      gestureDetector;

    public GeometryView(Context context) {
        super(context);
        init(context);
    }

    public GeometryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GeometryView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        tooltipBgPaint.setStyle(Paint.Style.FILL);
        tooltipBgPaint.setColor(Color.argb(220, 30, 30, 30));

        tooltipTextPaint.setColor(Color.WHITE);
        tooltipTextPaint.setTextSize(36f);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        float factor   = d.getScaleFactor();
                        float newScale = Math.max(minScale, Math.min(scaleFactor * factor, maxScale));
                        float actual   = newScale / scaleFactor;
                        scaleFactor    = newScale;
                        translateX     = d.getFocusX() - actual * (d.getFocusX() - translateX);
                        translateY     = d.getFocusY() - actual * (d.getFocusY() - translateY);
                        rebuildMatrix();
                        invalidate();
                        return true;
                    }
                });

        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                            float dx, float dy) {
                        if (scaleDetector.isInProgress()) return false;
                        translateX -= dx;
                        translateY -= dy;
                        rebuildMatrix();
                        invalidate();
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        handleTap(e.getX(), e.getY());
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        resetView();
                        return true;
                    }
                });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Linie von (x1,y1) nach (x2,y2) mit Standardfarbe Schwarz. */
    public void addLine(float x1, float y1, float x2, float y2) {
        addLine(x1, y1, x2, y2, Color.BLACK, 3f);
    }

    public void addLine(float x1, float y1, float x2, float y2, int color, float strokeWidth) {
        lines.add(new DrawingLine(x1, y1, x2, y2, color, strokeWidth));
        invalidate();
    }

    /** Punkt mit Farbe, Tooltip-Text und optionalem Tap-Callback. */
    public void addPoint(float x, float y, int color, String tooltip) {
        addPoint(x, y, color, tooltip, null);
    }

    public void addPoint(float x, float y, int color, String tooltip, OnTapListener onTap) {
        addPoint(x, y, color, 12f, tooltip, onTap);
    }

    public void addPoint(float x, float y, int color, float radius,
                         String tooltip, OnTapListener onTap) {
        points.add(new DrawingPoint(x, y, color, radius, tooltip, onTap));
        invalidate();
    }

    /** Pfeil von (x1,y1) nach (x2,y2) mit ausgefülltem Pfeilkopf. */
    public void addArrow(float x1, float y1, float x2, float y2, int color) {
        addArrow(x1, y1, x2, y2, color, 3f, 20f);
    }

    public void addArrow(float x1, float y1, float x2, float y2,
                         int color, float strokeWidth, float arrowHeadSize) {
        arrows.add(new DrawingArrow(x1, y1, x2, y2, color, strokeWidth, arrowHeadSize));
        invalidate();
    }

    public void clearAll() {
        lines.clear(); arrows.clear(); points.clear();
        activeTooltip = null;
        invalidate();
    }

    public void clearLines()  { lines.clear();  invalidate(); }
    public void clearArrows() { arrows.clear(); invalidate(); }
    public void clearPoints() { points.clear(); activeTooltip = null; invalidate(); }

    /** Zoom und Pan zurücksetzen (auch per Doppeltap). */
    public void resetView() {
        scaleFactor = 1f; translateX = 0f; translateY = 0f;
        rebuildMatrix();
        invalidate();
    }

    /** Aktuellen View-Inhalt als Bitmap exportieren (für PNG-Speichern / Share). */
    public Bitmap exportBitmap() {
        Bitmap bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        draw(canvas);
        return bmp;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.concat(matrix);

        drawLines(canvas);
        drawArrows(canvas);
        drawPoints(canvas);

        canvas.restore();

        if (activeTooltip != null) drawTooltip(canvas, activeTooltip);
    }

    private void drawLines(Canvas canvas) {
        for (DrawingLine l : lines) {
            linePaint.setColor(l.color);
            linePaint.setStrokeWidth(l.strokeWidth);
            canvas.drawLine(l.x1, l.y1, l.x2, l.y2, linePaint);
        }
    }

    private void drawArrows(Canvas canvas) {
        for (DrawingArrow a : arrows) {
            linePaint.setColor(a.color);
            linePaint.setStrokeWidth(a.strokeWidth);
            canvas.drawLine(a.x1, a.y1, a.x2, a.y2, linePaint);

            double angle  = Math.atan2(a.y2 - a.y1, a.x2 - a.x1);
            double spread = Math.toRadians(25);
            float  s      = a.arrowHeadSize;

            Path path = new Path();
            path.moveTo(a.x2, a.y2);
            path.lineTo(
                    (float)(a.x2 - s * Math.cos(angle - spread)),
                    (float)(a.y2 - s * Math.sin(angle - spread))
            );
            path.lineTo(
                    (float)(a.x2 - s * Math.cos(angle + spread)),
                    (float)(a.y2 - s * Math.sin(angle + spread))
            );
            path.close();

            fillPaint.setColor(a.color);
            canvas.drawPath(path, fillPaint);
        }
    }

    private void drawPoints(Canvas canvas) {
        for (DrawingPoint p : points) {
            fillPaint.setColor(Color.WHITE);
            canvas.drawCircle(p.x, p.y, p.radius + 3f, fillPaint);
            fillPaint.setColor(p.color);
            canvas.drawCircle(p.x, p.y, p.radius, fillPaint);
        }
    }

    private void drawTooltip(Canvas canvas, DrawingPoint point) {
        if (point.tooltip.isEmpty()) return;

        float[] screenPt = {point.x, point.y};
        matrix.mapPoints(screenPt);

        float padding    = 16f;
        float textWidth  = tooltipTextPaint.measureText(point.tooltip);
        float textHeight = tooltipTextPaint.getTextSize();
        float boxW = textWidth  + padding * 2;
        float boxH = textHeight + padding * 2;

        float bx = screenPt[0] + 24f;
        float by = screenPt[1] - boxH - 24f;
        if (bx + boxW > getWidth())  bx = screenPt[0] - boxW - 24f;
        if (by < 0)                  by = screenPt[1] + 24f;

        RectF rect = new RectF(bx, by, bx + boxW, by + boxH);
        canvas.drawRoundRect(rect, 12f, 12f, tooltipBgPaint);
        canvas.drawText(point.tooltip, bx + padding, by + padding + textHeight * 0.85f, tooltipTextPaint);
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    private void handleTap(float sx, float sy) {
        float[] modelPt = {sx, sy};
        invertMatrix.mapPoints(modelPt);
        float mx = modelPt[0], my = modelPt[1];

        float toleranceModel = tapTolerance / scaleFactor;
        DrawingPoint hit = null;

        for (DrawingPoint p : points) {
            float dx = p.x - mx, dy = p.y - my;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist <= Math.max(p.radius + toleranceModel, toleranceModel)) {
                hit = p;
                break;
            }
        }
        if (hit != null) {
            activeTooltip = (activeTooltip == hit) ? null : hit;
            if (hit.onTap != null) hit.onTap.onTap();
        } else {
            activeTooltip = null;
        }
        invalidate();
    }

    // ── Matrix ────────────────────────────────────────────────────────────────

    private void rebuildMatrix() {
        matrix.reset();
        matrix.postScale(scaleFactor, scaleFactor);
        matrix.postTranslate(translateX, translateY);
        matrix.invert(invertMatrix);
    }

    public void zoomToAll() {
        if (lines.isEmpty() && arrows.isEmpty() && points.isEmpty()) return;

        // Bounding Box über alle Objekte berechnen
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

        for (DrawingLine l : lines) {
            minX = Math.min(minX, Math.min(l.x1, l.x2));
            minY = Math.min(minY, Math.min(l.y1, l.y2));
            maxX = Math.max(maxX, Math.max(l.x1, l.x2));
            maxY = Math.max(maxY, Math.max(l.y1, l.y2));
        }
        for (DrawingArrow a : arrows) {
            minX = Math.min(minX, Math.min(a.x1, a.x2));
            minY = Math.min(minY, Math.min(a.y1, a.y2));
            maxX = Math.max(maxX, Math.max(a.x1, a.x2));
            maxY = Math.max(maxY, Math.max(a.y1, a.y2));
        }
        for (DrawingPoint p : points) {
            minX = Math.min(minX, p.x - p.radius);
            minY = Math.min(minY, p.y - p.radius);
            maxX = Math.max(maxX, p.x + p.radius);
            maxY = Math.max(maxY, p.y + p.radius);
        }

        float padding   = 40f; // px Abstand zum Rand
        float contentW  = maxX - minX;
        float contentH  = maxY - minY;
        if (contentW <= 0) contentW = 1;
        if (contentH <= 0) contentH = 1;

        float scaleX = (getWidth()  - padding * 2) / contentW;
        float scaleY = (getHeight() - padding * 2) / contentH;
        scaleFactor  = Math.min(scaleX, scaleY);
        scaleFactor  = Math.max(minScale, Math.min(scaleFactor, maxScale));

        // Zentrieren
        float centerX = (minX + maxX) / 2f;
        float centerY = (minY + maxY) / 2f;
        translateX = getWidth()  / 2f - scaleFactor * centerX;
        translateY = getHeight() / 2f - scaleFactor * centerY;

        rebuildMatrix();
        invalidate();
    }
}
