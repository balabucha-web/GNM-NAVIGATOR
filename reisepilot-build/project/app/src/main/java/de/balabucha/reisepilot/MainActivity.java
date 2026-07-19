package de.balabucha.reisepilot;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 100;
    private String pendingTrip = null;
    private TextView status;
    private TextView metrics;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String text = intent.getStringExtra("text");
            if (text != null) metrics.setText(text);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        IntentFilter f = new IntentFilter(TripTrackingService.ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 30, 30, 50);
        root.setBackgroundColor(Color.rgb(245,247,250));
        scroll.addView(root);

        TextView title = text("ReisePilot", 30, true);
        root.addView(title);
        root.addView(text("Schwerin → Montbéliard → Canet", 16, false));
        root.addView(space(18));

        status = text("Noch nicht gestartet.", 18, true);
        status.setPadding(20,20,20,20);
        status.setBackgroundColor(Color.rgb(255,244,219));
        root.addView(status);

        metrics = text("Abfahrt Samstag: 07:30–08:00 Uhr\nStandard: 07:45 Uhr", 18, false);
        metrics.setPadding(0,20,0,20);
        root.addView(metrics);

        root.addView(button("Samstag starten", v -> startTrip("sat")));
        root.addView(button("Sonntag starten", v -> startTrip("sun")));
        root.addView(button("Pause / Weiter", v -> sendService(TripTrackingService.PAUSE)));
        root.addView(button("Tracking stoppen", v -> sendService(TripTrackingService.STOP)));
        root.addView(space(18));

        root.addView(text("Navigation", 22, true));
        root.addView(button("Google Maps: Schwerin → Hotel", v -> openMaps("sat")));
        root.addView(button("Google Maps: Hotel → Canet", v -> openMaps("sun")));
        root.addView(button("Coyote öffnen / installieren", v -> launchPackage("com.coyotesystems.android")));
        root.addView(space(18));

        root.addView(text("Pausen und Tanken", 22, true));
        root.addView(text("Samstag 10:15 kurze Pause\n13:00 Pause + günstiger Tankstopp direkt an der Route\n16:30 zweite Pause\n20:00–22:00 greet Hôtel\n\nSonntag 07:00 Frühstück\n07:35–07:45 Abfahrt\n12:45 Pause + Tanken Intermarché Orange\n17:00–19:00 Malibu Village", 17, false));
        root.addView(space(18));

        root.addView(text("Mautpunkte", 22, true));
        root.addView(text("A36 Fontaine-Larivière\nA36 Saint-Maurice / Écot\nA7 Vienne\nA9 Perpignan Nord\n\nDie App warnt ab ungefähr 20 km Entfernung.", 17, false));
        root.addView(space(18));

        root.addView(text("Kontakte", 22, true));
        root.addView(button("greet Hôtel anrufen", v -> dial("+33381901069")));
        root.addView(button("Malibu Village anrufen", v -> dial("+33468732779")));
        root.addView(text("Malibu Check-in 16:00–19:00 Uhr; nach vorherigem Anruf bis 23:00 Uhr.", 16, false));
        return scroll;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(23,32,51));
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        return v;
    }

    private View space(int dp) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp));
        return s;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 8, 0, 8);
        b.setLayoutParams(p);
        return b;
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startTrip(String trip) {
        if (!hasLocation()) {
            pendingTrip = trip;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        status.setText("Tracking wird gestartet …");
        Intent i = new Intent(this, TripTrackingService.class);
        i.setAction("sun".equals(trip) ? TripTrackingService.START_SUN : TripTrackingService.START_SAT);
        startForegroundService(i);
    }

    private void sendService(String action) {
        Intent i = new Intent(this, TripTrackingService.class);
        i.setAction(action);
        startService(i);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && hasLocation() && pendingTrip != null) {
            String trip = pendingTrip;
            pendingTrip = null;
            startTrip(trip);
        }
    }

    private void openMaps(String trip) {
        String url = "sun".equals(trip)
            ? "https://www.google.com/maps/dir/?api=1&origin=greet+H%C3%B4tel+Montb%C3%A9liard&destination=Malibu+Village+Canet-en-Roussillon&travelmode=driving"
            : "https://www.google.com/maps/dir/?api=1&origin=19057+Schwerin&destination=greet+H%C3%B4tel+Montb%C3%A9liard&travelmode=driving";
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void launchPackage(String pkg) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) startActivity(launch);
        else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
    }

    private void dial(String number) {
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
    }

    @Override protected void onDestroy() {
        unregisterReceiver(receiver);
        super.onDestroy();
    }
}
