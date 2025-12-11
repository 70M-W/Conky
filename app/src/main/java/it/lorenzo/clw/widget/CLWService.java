package it.lorenzo.clw.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import it.lorenzo.clw.R;
import it.lorenzo.clw.core.Core;
import it.lorenzo.clw.chooser.FileSelect;

public class CLWService extends Service {

    private Handler handler = new Handler();
    private boolean isScreenOn = true;
    private static final int INTERVAL = 1000; // 1 Sekunde Takt

    // Der "Loop", der das Widget aktualisiert
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScreenOn) {
                updateAllWidgets();
            }
            handler.postDelayed(this, INTERVAL);
        }
    };

    // Erkennt, ob Bildschirm AN oder AUS ist
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                isScreenOn = false;
                // Optional: Loop anhalten um CPU komplett zu schonen
                handler.removeCallbacks(updateRunnable);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                isScreenOn = true;
                updateAllWidgets(); // Sofort-Update beim Aufwachen
                handler.removeCallbacks(updateRunnable);
                handler.postDelayed(updateRunnable, INTERVAL);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundServiceNotification();

        // Screen-Überwachung registrieren
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);

        // Motor starten
        handler.post(updateRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Service läuft weiter, auch wenn RAM knapp war
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception e) {
            // Receiver war vielleicht nicht registriert
        }
        handler.removeCallbacks(updateRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Erstellt die persistente Benachrichtigung (Pflicht für Android 8+)
    private void startForegroundServiceNotification() {
        String channelId = "clw_service_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "CLW Live Update",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("CLW ist aktiv")
                .setContentText("Aktualisiere Widgets...")
                .setSmallIcon(R.drawable.notification) // Stelle sicher, dass dieses Icon existiert!
                .build();

        startForeground(999, notification);
    }

    // Hier passiert das eigentliche Update (Kopie der Logik aus dem alten Provider)
    private void updateAllWidgets() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this, MyWidgetProvider.class));

        for (int n : appWidgetIds) {
            updateSingleWidget(this, appWidgetManager, n);
        }
    }

    // Die Logik aus deinem alten MyWidgetProvider, angepasst für den Service
    private void updateSingleWidget(Context context, AppWidgetManager appWidgetManager, int n) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference), Context.MODE_PRIVATE);
        String path = sharedPref.getString("" + n, "");

        if (path.equals("")) {
             // Keine Konfiguration -> Mache nichts oder zeige Standard
        } else {
            try {
                RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widgetlayout);
                // Das hier ist der CPU-intensive Teil: Core rendert das Bild
                remoteViews.setImageViewBitmap(R.id.widget_image,
                        new Core().getImageToSet(context.getApplicationContext(), path));

                // Klick-Intent (zum Ändern der Settings)
                Intent intent = new Intent(context, FileSelect.class); // Annahme: FileSelect öffnet Config
                intent.putExtra("appWidgetId", n);
                PendingIntent pendIntent = PendingIntent.getActivity(context, n, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                remoteViews.setOnClickPendingIntent(R.id.widget_image, pendIntent);

                appWidgetManager.updateAppWidget(n, remoteViews);
            } catch (Exception e) {
                // Fehlerbehandlung leise im Hintergrund oder Loggen
                e.printStackTrace();
            }
        }
    }
}
