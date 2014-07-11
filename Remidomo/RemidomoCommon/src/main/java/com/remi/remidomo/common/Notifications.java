package com.remi.remidomo.common;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.remi.remidomo.common.prefs.PrefsNotif;

import java.util.Date;

public class Notifications {

    private final static String TAG = "Remidomo-Common";

    private static int NOTIFICATION_ALERT = 3; // Not final !

    // Constants for invoking ringtone selection
    public enum NotifType {
        GARAGE,
        ALERT
    }

    /**
     * Alert notifications
     */
    public static void showAlertNotification(Context context,
                                             String text,
                                             NotifType soundId,
                                             int iconResId,
                                             int destinationView,
                                             Class destinationClass,
                                             Date tstamp) {

        NotificationManager notificationMgr = (NotificationManager) context.getSystemService(Service.NOTIFICATION_SERVICE);
        if (notificationMgr == null) {
            Log.e(TAG, "Failed to get Notification Manager");
        }

        // The PendingIntent to launch our activity if the user selects this notification
        Intent intent = new Intent(context, destinationClass);
        intent.putExtra("view", destinationView);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(contentIntent)
                .setTicker(text)
                .setContentText(text)
                .setContentTitle(context.getText(R.string.service_alert))
                .setWhen(tstamp.getTime())
                .setAutoCancel(true);

        if (iconResId != 0) {
            Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), iconResId);
            builder.setLargeIcon(largeIcon);
        }

        // Set sound if any (and if prefs allow)
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean("notif_sound", true)) {
            String pref;
            if (soundId == NotifType.GARAGE) {
                pref = prefs.getString("sound_garage", PrefsNotif.DEFAULT_SOUND_GARAGE);
            } else {
                pref = prefs.getString("sound_alert", PrefsNotif.DEFAULT_SOUND_ALERT);
            }
            builder.setSound(Uri.parse(pref));
        }

        // Send the notification.
        notificationMgr.notify(NOTIFICATION_ALERT++, builder.build());
    }
}
