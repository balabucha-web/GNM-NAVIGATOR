package de.balabucha.reisepilot;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 10;
    private TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    private TextView label(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(23, 32, 51));
        view.setPadding(18, 12, 18, 12);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private Button action(String title, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(listener);
        return button;
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 40);
        root.setBackgroundColor(Color.rgb(245, 247, 250));
        scroll.addView(root);

        root.addView(label("ReisePilot", 28, true));
        root.addView(label("Schwerin → Montbéliard → Canet", 15, false));
        root.addView(label("Abfahrt Samstag: 07:30–08:00 Uhr · Standard 07:45", 18, true));
        status = label("Tracking nicht gestartet", 16, true);
        root.addView(status);

        root.addView(action("Samstag starten", v -> startTrip("sat")));
        root.addView(action("Sonntag starten", v -> startTrip("sun")));
        root.addView(action("Tracking stoppen", v -> stopService(new Intent(this, TripService.class))));
        root.addView(action("Google Maps Samstag", v -> open("https://www.google.com/maps/dir/?api=1&origin=19057+Schwerin&destination=greet+H%C3%B4tel+Montb%C3%A9liard&travelmode=driving")));
        root.addView(action("Google Maps Sonntag", v -> open("https://www.google.com/maps/dir/?api=1&origin=greet+H%C3%B4tel+Montb%C3%A9liard&destination=Malibu+Village+Canet-en-Roussillon&travelmode=driving")));

        root.addView(label("Plan", 22, true));
        root.addView(label("Samstag 07:45 Abfahrt · Pause ca. 10:15 · Tanken/Pause ca. 13:00 · zweite Pause ca. 16:30 · Hotel ca. 20:00–22:00. Bei später Ankunft Hotel anrufen.", 15, false));
        root.addView(label("Sonntag 07:00 Frühstück · 07:35–07:45 Abfahrt · Pause um Lyon · Tanken/Pause Orange · Canet ca. 17:00–19:00. Nach Anruf Check-in bis 23:00.", 15, false));

        root.addView(label("Tankstopps", 22, true));
        root.addView(label("Freitag: Hoyer Schwerin volltanken. Samstag: günstige Station direkt an der Live-Route; BayWa Schwabach nur bei passender Route. Sonntag: Intermarché Orange. Keine Autobahntankstelle erzwingen.", 15, false));

        root.addView(label("Mautpunkte", 22, true));
        root.addView(label("A36 Fontaine-Larivière · A36 Saint-Maurice/Écot · A7 Vienne · A9 Perpignan Nord. Hintergrundwarnung ab etwa 20 km.", 15, false));

        WebView map = new WebView(this);
        map.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 700));
        WebSettings settings = map.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        map.loadDataWithBaseURL("https://reisepilot.local/", mapHtml(), "text/html", "UTF-8", null);
        root.addView(map);

        root.addView(label("Kontakte", 22, true));
        root.addView(action("greet Hôtel anrufen", v -> dial("+33381901069")));
        root.addView(action("Malibu Village anrufen", v -> dial("+33468732779")));
        root.addView(label("Google Maps bleibt für Live-Verkehr zuständig. Fremde Warn-Apps werden nicht automatisch gesteuert.", 14, false));
        return scroll;
    }

    private void startTrip(String trip) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        Intent service = new Intent(this, TripService.class);
        service.putExtra("trip", trip);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        status.setText("Tracking aktiv · " + (trip.equals("sun") ? "Montbéliard → Canet" : "Schwerin → Montbéliard"));
    }

    private void open(String url) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
    private void dial(String number) { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))); }

    private String mapHtml() {
        return "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body,#m{height:100%;margin:0}</style><link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'><div id='m'></div><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>var m=L.map('m').setView([48.8,6.8],5);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'OpenStreetMap'}).addTo(m);[[53.64,11.40,'Schwerin'],[47.499678,6.817207,'greet Hotel'],[42.7069,3.0182,'Malibu Village'],[47.716,7.005,'Maut A36'],[47.436,6.652,'Maut A36'],[45.513,4.874,'Maut A7'],[42.785,2.894,'Maut A9']].forEach(function(x){L.marker([x[0],x[1]]).addTo(m).bindPopup(x[2]);});</script>";
    }
}
