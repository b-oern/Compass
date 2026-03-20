package net.bsnx.apps.compass;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import net.bsnx.apps.compass.db.CompassDB;
import net.bsnx.apps.compass.db.PathItem;
import net.bsnx.apps.compass.db.PathList;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Zeigt alle {@link PathItem}s eines Tages in einer {@link ListView} an.
 *
 * <p>Übergabe des Tages über Intent-Extra {@link #EXTRA_DAY_UNIX}.
 * Fehlt der Extra, wird der heutige Tag geladen.</p>
 *
 * <pre>
 * Intent intent = new Intent(context, PathListActivity.class);
 * intent.putExtra(PathListActivity.EXTRA_DAY_UNIX, CompassDB.dateToUnix(date));
 * startActivity(intent);
 * </pre>
 *
 * <p>LongPress auf ein Listenelement öffnet ein Kontextmenü mit der Option
 * das Item aus der Datenbank zu löschen.</p>
 */
public class ListActivity extends Activity {

    public static final String EXTRA_DAY_UNIX = "day_unix";

    private CompassDB       db;
    private PathItemAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = new CompassDB(this);

        PathList items;
        if (getIntent().hasExtra(EXTRA_DAY_UNIX)) {
            int dayUnix = getIntent().getIntExtra(
                    EXTRA_DAY_UNIX,
                    CompassDB.dateToUnix(CompassDB.getTodayDate()));

            items = db.getByDay(CompassDB.unixToDate(dayUnix));
        } else {
            items = db.getAll();
        }

        adapter = new PathItemAdapter(this, new ArrayList<>(items));

        ListView listView = new ListView(this);
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            showDeleteDialog(adapter.getItem(position));
            return true;
        });

        setContentView(listView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        db.close();
    }

    private void showDeleteDialog(PathItem item) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(CompassDB.unixToDate(item.getTimestamp()));

        new AlertDialog.Builder(this)
                .setTitle("Eintrag löschen")
                .setMessage(item.getSteps() + " Schritte · " +
                        item.getHeading() + "° (" + item.getCardinalDirection() + ")" +
                        " · " + time  + "X: " + item.getX() + " Y: " + item.getY())
                .setPositiveButton("Löschen", (dialog, which) -> deleteItem(item))
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void deleteItem(PathItem item) {
        boolean ok = db.delete(item);
        if (ok) {
            adapter.remove(item);
            Toast.makeText(this, "Eintrag gelöscht", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Löschen fehlgeschlagen", Toast.LENGTH_SHORT).show();
        }
    }

    private final class PathItemAdapter extends ArrayAdapter<PathItem> {

        private final SimpleDateFormat timeFmt =
                new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        PathItemAdapter(Context ctx, List<PathItem> items) {
            super(ctx, 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = buildRow();
                holder = buildHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            PathItem item = getItem(position);
            if (item == null) return convertView;

            holder.tvSteps.setText(item.getSteps() + " Schritte");
            holder.tvHeading.setText(item.getHeading() + "°  " + item.getCardinalDirection());
            holder.tvTime.setText(timeFmt.format(CompassDB.unixToDate(item.getTimestamp())));

            return convertView;
        }

        private LinearLayout buildRow() {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            row.setGravity(Gravity.CENTER_VERTICAL);
            return row;
        }

        private ViewHolder buildHolder(View row) {
            LinearLayout r = (LinearLayout) row;
            ViewHolder h = new ViewHolder();

            h.tvSteps = new TextView(getContext());
            h.tvSteps.setTextSize(15f);
            h.tvSteps.setTypeface(null, Typeface.BOLD);
            h.tvSteps.setTextColor(Color.parseColor("#212121"));
            r.addView(h.tvSteps,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            h.tvHeading = new TextView(getContext());
            h.tvHeading.setTextSize(14f);
            h.tvHeading.setTextColor(Color.parseColor("#1565C0"));
            h.tvHeading.setGravity(Gravity.CENTER);
            r.addView(h.tvHeading,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            h.tvTime = new TextView(getContext());
            h.tvTime.setTextSize(13f);
            h.tvTime.setTextColor(Color.parseColor("#757575"));
            h.tvTime.setGravity(Gravity.END);
            r.addView(h.tvTime,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            return h;
        }

        private int dp(int value) {
            return Math.round(value * getContext().getResources().getDisplayMetrics().density);
        }
    }

    private static final class ViewHolder {
        TextView tvSteps;
        TextView tvHeading;
        TextView tvTime;
    }
}
