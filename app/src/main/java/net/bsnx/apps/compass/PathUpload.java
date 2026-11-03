package net.bsnx.apps.compass;

import android.app.Activity;
import android.net.Uri;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import net.bsnx.apps.compass.db.CompassDB;
import net.bsnx.apps.compass.db.PathList;
import net.bsnx.nxdroid.NxDroid;
import net.bsnx.nxdroidlib.NWebAccount;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Laedt den Pfad eines Tages als Dokument der Art "compass" in das NWeb-Konto hoch
 * (NxDroid.userCreateDocument, POST /user/{userId}/doc), wie ScanUpload im arscanner.
 *
 * <p>Inhalt ist JSON: {"version":1, "day":"2026-11-03", "step_length":0.9, "items":[...]},
 * items wie {@link PathList#toJson()}. Angezeigt wird das Dokument in nweb vom Python-Modul
 * {@code compass} (compass/render.py).</p>
 */
final class PathUpload {

    private static final String TAG = "PathUpload";

    static final String KIND = "compass";
    static final String CONTENT_TYPE = "application/json";
    static final int FORMAT_VERSION = 1;

    private PathUpload() {
    }

    /** Fragt nach dem Titel und laedt nach Bestaetigung hoch. Aus dem UI-Thread aufrufen. */
    static void start(final Activity activity, final Date day) {
        if (!NWebAccount.isConfigured(activity)) {
            new AlertDialog.Builder(activity)
                    .setTitle("NWeb-Konto fehlt")
                    .setMessage("Zum Hochladen bitte zuerst Benutzer und Passwort/Token des NWeb-Kontos eintragen.")
                    .setPositiveButton("Einrichten", (dialog, which) ->
                            activity.startActivity(NWebAccount.openSettings(activity)))
                    .setNegativeButton("Abbrechen", null)
                    .show();
            return;
        }

        CompassDB db = new CompassDB(activity);
        final PathList items = db.getByDay(day);
        db.close();
        if (items.isEmpty()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Pfad hochladen")
                    .setMessage("An diesem Tag gibt es keine Einträge.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        final String dayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(day);
        final EditText title = new EditText(activity);
        title.setSingleLine(true);
        title.setText("Kompass " + new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(day));
        LinearLayout box = new LinearLayout(activity);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        String host = Uri.parse(NWebAccount.getUrl(activity)).getHost();
        new AlertDialog.Builder(activity)
                .setTitle("Pfad hochladen")
                .setMessage(items.size() + " Einträge · " + items.getTotalSteps() + " Schritte\n"
                        + "nach " + NWebAccount.getUser(activity) + " @ " + host + "\n\nTitel:")
                .setView(box)
                .setPositiveButton("Hochladen", (dialog, which) -> {
                    String t = title.getText().toString().trim();
                    upload(activity, items, dayKey, t.isEmpty() ? "Kompass " + dayKey : t);
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    /** JSON-Inhalt des Dokuments */
    static String toDocument(PathList items, String dayKey) {
        try {
            JSONObject doc = new JSONObject();
            doc.put("version", FORMAT_VERSION);
            doc.put("day", dayKey);
            doc.put("step_length", (double) items.getStepLength());
            doc.put("items", items.toJson());
            return doc.toString();
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void upload(final Activity activity, final PathList items, final String dayKey,
                               final String title) {
        final AlertDialog progress = new AlertDialog.Builder(activity)
                .setTitle("Pfad hochladen")
                .setMessage("Lade " + items.size() + " Einträge hoch …")
                .setCancelable(false)
                .show();

        new Thread(() -> {
            String result;
            boolean ok;
            File file = new File(activity.getCacheDir(), "compass_" + dayKey + ".json");
            try {
                writeFile(file, toDocument(items, dayKey));

                net.bsnx.json.JSONObject meta = new net.bsnx.json.JSONObject();
                net.bsnx.json.JSONObject ns = new net.bsnx.json.JSONObject();
                ns.put("day", dayKey);
                ns.put("items", items.size());
                ns.put("steps", items.getTotalSteps());
                ns.put("step_length", (double) items.getStepLength());
                ns.put("app_version", BuildConfig.VERSION_NAME);
                meta.put(KIND, ns);

                net.bsnx.json.JSONObject r = NxDroid.userCreateDocument(activity, null, KIND, title,
                        meta.toString(), file, CONTENT_TYPE);
                result = "Hochgeladen als Dokument " + r.optInt("document_id")
                        + " (" + Formatter.formatShortFileSize(activity, r.optInt("data_bytes")) + ")";
                ok = true;
            } catch (Exception e) {
                Log.e(TAG, "Upload fehlgeschlagen", e);
                result = e.getMessage() != null ? e.getMessage() : e.toString();
                ok = false;
            } finally {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            final String msg = result;
            final boolean success = ok;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                progress.dismiss();
                new AlertDialog.Builder(activity)
                        .setTitle(success ? "Hochgeladen" : "Upload fehlgeschlagen")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show();
            });
        }, "PathUpload").start();
    }

    private static void writeFile(File file, String content) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
