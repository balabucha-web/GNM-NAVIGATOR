package de.balabucha.reisepilot;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.IBinder;
import java.util.Locale;

public class TripService extends Service implements LocationListener {
    private static final String CHANNEL = "reise_live";
    private LocationManager manager;
    private String trip = "sat";
    private long startedAt;

    private static final Toll[] TOLLS = {
        new Toll("A36 Fontaine-Larivière", 47.716, 7.005),
        new Toll("A36 Saint-Maurice/Écot", 47.436, 6.652),
        new Toll("A7 Vienne", 45.513, 4.874),
        new Toll("A9 Perpignan Nord", 42.785, 2.894)
    };

    @Override public void onCreate() {
        super.onCreate();
        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        NotificationManager notifications = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Live-Reise", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Standort, Fahrzeit, Pausen und Mautpunkte");
        notifications.createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        trip = intent != null ? intent.getStringExtra("trip") : "sat";
        if (trip == null) trip = "sat";
        startedAt = System.currentTimeMillis();
        startForeground(42, notification("Standort wird gestartet"));
        requestLocation();
        return START_STICKY;
    }

    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, this);
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 25, this);
        } catch (Exception ignored) { }
    }

    @Override public void onLocationChanged(Location location) {
        long minutes = Math.max(0, (System.currentTimeMillis() - startedAt) / 60000);
        String text = minutes >= 150 ? "Pause fällig · " : minutes >= 135 ? "Pause vorbereiten · " : "";
        text += minutes + " Min. Fahrt";
        String toll = nearestToll(location);
        if (!toll.isEmpty()) text += " · " + toll;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.notify(42, notification(text));
    }

    private String nearestToll(Location location) {
        double best = Double.MAX_VALUE;
        String name = "";
        for (Toll toll : TOLLS) {
            float[] distance = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(), toll.lat, toll.lon, distance);
            double km = distance[0] / 1000.0;
            if (km < best) { best = km; name = toll.name; }
        }
        return best <= 20 ? name + " in ca. " + Math.round(best) + " km" : "";
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String title = "ReisePilot · " + ("sun".equals(trip) ? "Montbéliard → Canet" : "Schwerin → Montbéliard");
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build();
    }

    @Override public void onDestroy() {
        try { manager.removeUpdates(this); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static class Toll {
        final String name;
        final double lat;
        final double lon;
        Toll(String name, double lat, double lon) { this.name = name; this.lat = lat; this.lon = lon; }
    }
}
