package it.lorenzo.clw.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

import it.lorenzo.clw.R;
import it.lorenzo.clw.chooser.FileSelect;
import it.lorenzo.clw.core.Core;

public class MyWidgetProvider extends AppWidgetProvider {

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        startCLWService(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Erstes Update sofort machen (damit man nicht 1 Sekunde warten muss)
        for (int id : appWidgetIds) {
            updateWidgetOnce(context, appWidgetManager, id);
        }
        // Service starten für den Takt
        startCLWService(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference), Context.MODE_PRIVATE);
        for (int i : appWidgetIds)
            sharedPref.edit().remove("" + i).apply();
        super.onDeleted(context, appWidgetIds);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        // Wenn das letzte Widget gelöscht wird, Service stoppen um Akku zu sparen
        Intent serviceIntent = new Intent(context, CLWService.class);
        context.stopService(serviceIntent);
        
        context.getSharedPreferences(context.getString(R.string.preference),
                Context.MODE_PRIVATE).edit().clear().apply();
    }

    private void startCLWService(Context context) {
        Intent serviceIntent = new Intent(context, CLWService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    // Statische Hilfsmethode für das einmalige Update (beim Hinzufügen)
    private void updateWidgetOnce(Context context, AppWidgetManager appWidgetManager, int n) {
        SharedPreferences sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference), Context.MODE_PRIVATE);
        String path = sharedPref.getString("" + n, "");

        if (path.equals("")) {
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.clickme);
            Intent intent = new Intent(context, FileSelect.class);
            intent.putExtra("appWidgetId", n);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            remoteViews.setOnClickPendingIntent(R.id.button1, pendIntent);
            remoteViews.setTextViewText(R.id.button1, "No configuration file set");
            appWidgetManager.updateAppWidget(n, remoteViews);
        } else {
            try {
                RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widgetlayout);
                remoteViews.setImageViewBitmap(R.id.widget_image,
                        new Core().getImageToSet(context.getApplicationContext(), path));
                
                // Klick öffnet Konfiguration
                Intent intent = new Intent(context, FileSelect.class);
                intent.putExtra("appWidgetId", n);
                PendingIntent pendIntent = PendingIntent.getActivity(context, n, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                remoteViews.setOnClickPendingIntent(R.id.widget_image, pendIntent);

                appWidgetManager.updateAppWidget(n, remoteViews);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
