package de.balabucha.reisepilot;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.IBinder;
import java.util.Locale;

public class TripTrackingService extends Service implements LocationListener {
    public static final String ACTION_UPDATE = "de.balabucha.reisepilot.UPDATE";
    public static final String START_SAT = "START_SAT";
    public static final String START_SUN = "START_SUN";
    public static final String PAUSE = "PAUSE";
    public static final String STOP = "STOP";
    private static final String CHANNEL = "reise_live";

    private LocationManager locationManager;
    private String trip = "sat";
    private boolean paused = false;
    private long startedAt;
    private long pauseStarted;
    private long pausedMillis;

    private static final TollPoint[] TOLLS = {
        new TollPoint("A36 Fontaine-Larivière", 47.716, 7.005),
        new TollPoint("A36 Saint-Maurice / Écot", 47.436, 6.652),
        new TollPoint("A7 Vienne", 45.513, 4.874),
        new TollPoint("A9 Perpignan Nord", 42.785, 2.894)
    };

    @Override public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Live-Reise", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (STOP.equals(action)) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) {}
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (PAUSE.equals(action)) {
            paused = !paused;
            if (paused) pauseStarted = System.currentTimeMillis();
            else if (pauseStarted > 0) pausedMillis += System.currentTimeMillis() - pauseStarted;
            notifyStatus(null);
            return START_STICKY;
        }

        trip = START_SUN.equals(action) ? "sun" : "sat";
        startedAt = System.currentTimeMillis();
        pausedMillis = 0;
        paused = false;
        startForeground(42, notification("Standort wird gestartet …"));

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 25, this);
            } catch (Exception ignored) {}
        }
        return START_STICKY;
    }

    @Override public void onLocationChanged(Location location) {
        notifyStatus(location);
    }

    private void notifyStatus(Location location) {
        long now = paused ? pauseStarted : System.currentTimeMillis();
        long driveMinutes = Math.max(0, (now - startedAt - pausedMillis) / 60000);
        String toll = location == null ? "" : nearestToll(location);
        String text = paused ? "Pause aktiv" : String.format(Locale.GERMANY, "%d Min. Fahrt", driveMinutes);
        if (!toll.isEmpty()) text += " · " + toll;
        if (driveMinutes >= 150 && !paused) text = "Pause fällig · " + text;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(42, notification(text));

        String speed = location != null && location.hasSpeed() ? String.valueOf(Math.round(location.getSpeed() * 3.6)) : "–";
        String remaining = location == null ? "–" : String.valueOf(Math.round(location.distanceTo(destination()) / 1000f));
        Intent update = new Intent(ACTION_UPDATE);
        update.setPackage(getPackageName());
        update.putExtra("text", "Status: " + text + "\nGeschwindigkeit: " + speed + " km/h\nRest Luftlinie: " + remaining + " km\nNächster Mautpunkt: " + (toll.isEmpty() ? "–" : toll));
        sendBroadcast(update);
    }

    private Location destination() {
        Location d = new Location("target");
        if ("sun".equals(trip)) { d.setLatitude(42.7069); d.setLongitude(3.0182); }
        else { d.setLatitude(47.499678); d.setLongitude(6.817207); }
        return d;
    }

    private String nearestToll(Location location) {
        double best = Double.MAX_VALUE;
        String label = "";
        for (TollPoint point : TOLLS) {
            float[] result = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(), point.lat, point.lon, result);
            double km = result[0] / 1000d;
            if (km < best) { best = km; label = point.name; }
        }
        return best <= 20 ? label + " in ca. " + Math.round(best) + " km" : "";
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent pauseIntent = new Intent(this, TripTrackingService.class); pauseIntent.setAction(PAUSE);
        PendingIntent pause = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stopIntent = new Intent(this, TripTrackingService.class); stopIntent.setAction(STOP);
        PendingIntent stop = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(de.balabucha.reisepilot.R.drawable.ic_trip)
            .setContentTitle("ReisePilot · " + ("sun".equals(trip) ? "Montbéliard → Canet" : "Schwerin → Montbéliard"))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, paused ? "Weiter" : "Pause", pause).build())
            .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
            .build();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    private static class TollPoint {
        final String name; final double lat; final double lon;
        TollPoint(String name, double lat, double lon) { this.name = name; this.lat = lat; this.lon = lon; }
    }
}
