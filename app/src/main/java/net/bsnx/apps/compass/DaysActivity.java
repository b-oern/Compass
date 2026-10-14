package net.bsnx.apps.compass;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.bsnx.apps.compass.db.CompassDB;

import org.jspecify.annotations.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DaysActivity extends AppCompatActivity {

    public static class Item {
        Date date;

        public Item(Date date) {
            this.date = date;
        }

        public String getTitle() {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd. MMMM yyyy", Locale.getDefault());
            return sdf.format(date);
        }

        @androidx.annotation.NonNull
        @Override
        public String toString() {
            return "Item " + getTitle();
        }
    }

    static class DayAdapter extends ArrayAdapter<Item> {
        DayAdapter(Context ctx, List<Item> items) {
            super(ctx, 0, items);
        }

        @NonNull @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                TextView tv = new TextView(getContext());
                tv.setPadding(48, 32, 48, 32);
                tv.setTextSize(16);
                convertView = tv;
            }
            Item item = getItem(position);
            if (item != null) ((TextView) convertView).setText(item.getTitle());
            return convertView;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        //root.setLayoutParams(new LinearLayout.LayoutParams(
        //        ViewGroup.LayoutParams.MATCH_PARENT,
        //        ViewGroup.LayoutParams.MATCH_PARENT));
        ListView listView = new ListView(this);
        //listView.setLayoutParams(new LinearLayout.LayoutParams(
        //        ViewGroup.LayoutParams.MATCH_PARENT,
        //        ViewGroup.LayoutParams.MATCH_PARENT));
        List<Item> items = getItems();
        DayAdapter adapter = new DayAdapter(this, items);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) ->
                onDayClick(items.get(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            showItemMenu(adapter.getItem(position));
            return true;
        });
        root.addView(listView);
        listView.setFitsSystemWindows(true);
        root.setFitsSystemWindows(true);
        setContentView(root);
    }

    private void showItemMenu(Item item) {
        String[] actions = {
                "Show List",
                "CSV Export",
                "JSON Export",
                "Send to Map"
        };
        new AlertDialog.Builder(this)
                .setTitle("Aktion auswählen")
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            actionShowList(item);
                            break;
                        case 1:
                            actionCsvExport(item);
                            break;
                        case 2:
                            actionJsonExport(item);
                            break;
                        case 3:
                            actionShowMap(item);
                            break;
                    }
                })
                .show();
    }

    private void actionShowMap(Item item) {
        Intent intent = new Intent();
        intent.setClassName("net.bsnx.nxmap", "net.bsnx.nxmap.MainActivity");
        CompassDB db = new CompassDB(this);
        intent.putExtra("kompass_path", db.getByDay(item.date).computeCoordinates().toIntentExtra());
        startActivity(intent);
    }

    private void actionShowList(Item item) {
        Intent intent = new Intent(this, ListActivity.class);
        intent.putExtra("date", item.date.getTime() / 1000L); // Unix-Timestamp (long, ms)
        startActivity(intent);
    }

    private void actionJsonExport(Item item) {
        CompassDB db = new CompassDB(this);
        String s = db.getByDay(item.date).computeCoordinates().toJson().toString();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/json");
        intent.putExtra(Intent.EXTRA_TEXT, s);
        intent.putExtra(Intent.EXTRA_SUBJECT, "kompass_export.json");
        startActivity(Intent.createChooser(intent, "JSON teilen"));
    }

    private void actionCsvExport(Item item) {
        CompassDB db = new CompassDB(this);
        shareCsvWithAndroidShareDialog(db.getByDay(item.date).toCsv());
    }

    private void shareCsvWithAndroidShareDialog(String csv) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TEXT, csv);
        intent.putExtra(Intent.EXTRA_SUBJECT, "kompass_export.csv");
        startActivity(Intent.createChooser(intent, "CSV teilen"));
    }

    public List<Item> getItems() {
        Log.i("NX", "Load");
        CompassDB db = new CompassDB(this);
        List<Item> result = new ArrayList<>();
        for (Date day : db.getDays()) {          // Day = whatever getDays() returns
            Item item = new Item(day);
            Log.i("NX", item.toString());
            result.add(item);
        }
        return result;
    }

    public void onDayClick(Item item) {
        Intent intent = new Intent(this, GeometryActivity.class);
        intent.putExtra("date", item.date.getTime() / 1000L); // Unix-Timestamp (long, ms)
        startActivity(intent);
    }
}
