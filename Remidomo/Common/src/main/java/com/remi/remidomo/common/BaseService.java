package com.remi.remidomo.common;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

import com.remi.remidomo.common.data.Doors;
import com.remi.remidomo.common.data.Energy;
import com.remi.remidomo.common.data.Sensors;
import com.remi.remidomo.common.data.Switches;
import com.remi.remidomo.common.data.Trains;
import com.remi.remidomo.common.meteo.Meteo;
import com.remi.remidomo.common.meteo.MeteoVilles;
import com.remi.remidomo.common.prefs.Defaults;

import java.util.Date;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public abstract class BaseService extends Service {

    private final static String TAG = "Remidomo-Common";

    public final static String ACTION_RESTORE_DATA = "com.remi.remidomo.reloaded.RESTORE_DATA";
    public final static String ACTION_BOOTKICK = "com.remi.remidomo.reloaded.BOOTKICK";

    public final static String SERVICE_CLASS_EXTRA = "Service.class";

    private NotificationManager notificationMgr = null;
    protected SharedPreferences prefs;

    private final IBinder mBinder = new LocalBinder();
    public IUpdateListener callback;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private final int NOTIFICATION_START = 1;
    private int NOTIFICATION_ALERT = 3; // Not final !

    protected Trains trains = new Trains();
    protected Meteo meteo = new MeteoVilles();
    protected Sensors sensors = null;
    protected Switches switches = null;
    protected Doors doors = null;
    protected Energy energy = null;

    public enum LogLevel { HIGH,
        MEDIUM,
        LOW,
        UPDATE }

    private static final int MAX_LOG_LINES = 200;
    private static class LogEntry {
        public String msg;
        public LogLevel level;
        public int repeat;
    }
    private LinkedList<LogEntry> log = new LinkedList<LogEntry>();

    // Constants for invoking ringtone selection
    public enum NotifType {
        GARAGE,
        ALERT
    }

    @Override
    public void onCreate() {
        // Initialize "dynamic defaults"
        Defaults.DEFAULT_SOUND_GARAGE = "android.resource://" + getPackageName() + "/" + com.remi.remidomo.common.R.raw.garage_ok;
        Defaults.DEFAULT_SOUND_ALERT = "android.resource://" + getPackageName() + "/" + com.remi.remidomo.common.R.raw.garage_alert;

        notificationMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (notificationMgr == null) {
            Log.e(TAG, "Failed to get Notification Manager");
        }

        new Thread(new Runnable() {
            public synchronized void run() {
                prefs = PreferenceManager.getDefaultSharedPreferences(BaseService.this);
                upgradeData();

                sensors = new Sensors(BaseService.this);
                switches = new Switches(BaseService.this);
                doors = new Doors(BaseService.this);
                energy = new Energy(BaseService.this);
            }
        }).start();

        resetLeds();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        try {
            while (prefs == null) {
                Thread.sleep(100);
            }
        } catch (java.lang.InterruptedException ignored) {}

        if ((intent != null) && ACTION_RESTORE_DATA.equals(intent.getAction())) {
            addLog("Lecture des données depuis la carte SD");
            new Thread(new Runnable() {
                public void run() {
                    sensors.readFromSdcard();
                    energy.readFromSdcard();
                    if (callback != null) {
                        callback.postToast(getString(R.string.load_complete));
                    }
                }
            }, "sdcard read").start();
        } else if ((intent != null) && ACTION_BOOTKICK.equals(intent.getAction())) {
            boolean kickboot = prefs.getBoolean("bootkick", Defaults.DEFAULT_BOOTKICK);
            if (!kickboot) {
                Log.i(TAG, "Exit service to ignore boot event");
                cleanObjects();
                stopSelf();
            }
        } else {
            Log.i(TAG, "Start service");
            addLog("Service (re)démarré");

            if ((intent != null) && intent.getBooleanExtra("RESET_DATA", false)) {
                // Clear sensor data
                this.sensors.clearData();
                if (callback != null) {
                    callback.postToast(getString(R.string.data_cleared));
                    callback.updateThermo();
                }
                addLog("Données capteurs effacées");
            }

            // Stop all threads and clean everything
            cleanObjects();

            // Timer for train updates
            Timer timerTrains = new Timer("Trains");
            int period = prefs.getInt("sncf_poll", Defaults.DEFAULT_SNCF_POLL);
            timerTrains.scheduleAtFixedRate(new TrainsTask(), 1, 1000L*60*period);

            // Timer for weather updates
            Timer timerMeteo = new Timer("Meteo");
            period = prefs.getInt("meteo_poll", Defaults.DEFAULT_METEO_POLL);
            timerMeteo.scheduleAtFixedRate(new MeteoTask(), 1, 1000L*60*60*period);
        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    protected void cleanObjects() {
        resetLeds();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Stop service");

        // Cancel the persistent notification.
        notificationMgr.cancel(NOTIFICATION_START);
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        public BaseService getService() {
            return BaseService.this;
        }

        public void registerCallbacks(IUpdateListener callback) {
            BaseService.this.callback = callback;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    public void stopAtActivityRequest() {
        Log.i(TAG, "Stop service at activity request");
        cleanObjects();
        stopSelf();
    }

    /**
     * Show a notification while this service is running.
     */
    public void showServiceNotification(Class destinationClass,
                                        int destinationView) {
        // The PendingIntent to launch our activity if the user selects this notification
        Intent intent = new Intent(this, destinationClass);
        intent.putExtra("view", destinationView);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.service_icon);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentText(getText(R.string.service_started))
                .setTicker(getText(R.string.service_label))
                .setWhen(System.currentTimeMillis())
                .setLargeIcon(largeIcon)
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(contentIntent)
                .setContentTitle(getString(R.string.app_name))
                .setOngoing(true);

        // Send the notification.
        notificationMgr.notify(NOTIFICATION_START, builder.build());
    }

    /**
     * Alert notifications
     */
    public void showAlertNotification(String text,
                                      NotifType soundId,
                                      int iconResId,
                                      int destinationView,
                                      Class destinationClass,
                                      Date tstamp) {

        NotificationManager notificationMgr = (NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE);
        if (notificationMgr == null) {
            Log.e(TAG, "Failed to get Notification Manager");
        }

        // The PendingIntent to launch our activity if the user selects this notification
        Intent intent = new Intent(this, destinationClass);
        intent.putExtra("view", destinationView);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(contentIntent)
                .setTicker(text)
                .setContentText(text)
                .setContentTitle(getText(R.string.service_alert))
                .setWhen(tstamp.getTime())
                .setAutoCancel(true);

        if (iconResId != 0) {
            Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), iconResId);
            builder.setLargeIcon(largeIcon);
        }

        // Set sound if any (and if prefs allow)
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("notif_sound", true)) {
            String pref;
            if (soundId == NotifType.GARAGE) {
                pref = prefs.getString("sound_garage", Defaults.DEFAULT_SOUND_GARAGE);
            } else {
                pref = prefs.getString("sound_alert", Defaults.DEFAULT_SOUND_ALERT);
            }
            builder.setSound(Uri.parse(pref));
        }

        // Send the notification.
        notificationMgr.notify(NOTIFICATION_ALERT++, builder.build());
    }


    public void saveToSdcard() {
        new Thread(new Runnable() {
            public void run() {
                sensors.saveToSdcard();
                energy.saveToSdcard();
                if (callback != null) {
                    callback.postToast(getString(R.string.save_complete));
                }
            }
        }).start();
    }

    /****************************** TRAINS ******************************/
    public synchronized Trains getTrains() {
        return trains;
    }

    private class TrainsTask extends TimerTask {
        @Override
        public void run() {
            trains.updateData(BaseService.this);

            if (callback != null) {
                callback.updateTrains();
            }
        }
    }

    /****************************** METEO ******************************/
    public synchronized Meteo getMeteo() {
        return meteo;
    }

    private class MeteoTask extends TimerTask {
        @Override
        public void run() {
            meteo.updateData(BaseService.this);

            if (callback != null) {
                callback.updateMeteo();
            }
        }
    }

    /****************************** SENSORS ******************************/
    public Sensors getSensors() {
        return sensors;
    }

    /****************************** SWITCHES ******************************/
    public Switches getSwitches() {
        return switches;
    }


    /****************************** DOORs ******************************/
    public Doors getDoors() {
        return doors;
    }


    /****************************** ENERGY ******************************/
    public Energy getEnergy() {
        return energy;
    }

    /****************************** PUSH ******************************/

    public void pushToClients(String target, int index, String data) {
        // Do nothing
    }

    /****************************** LOG ******************************/
    public void addLog(String msg) {
        addLog(msg, LogLevel.LOW);
    }

    public synchronized void addLog(String msg, LogLevel level) {
        String prev_msg = null;
        if (!log.isEmpty()) {
            prev_msg = log.getLast().msg;
        }
        if (msg.equals(prev_msg)) {
            // Exact match -> +1
            LogEntry entry = log.getLast();
            entry.repeat = entry.repeat + 1;
            log.removeLast();
            log.addLast(entry);
        } else {
            LogEntry newEntry = new LogEntry();
            newEntry.msg = msg;
            newEntry.level = level;
            newEntry.repeat = 1;
            log.addLast(newEntry);
        }
        // Since we're adding one msg at a time,
        // we should be ok removing only the oldest one
        if (log.size() > MAX_LOG_LINES) {
            log.removeFirst();
        }

        if (callback != null) {
            callback.updateLog();
        }
    }

    public synchronized void clearLog() {
        log.clear();
        if (callback != null) {
            callback.updateLog();
        }
    }

    public synchronized SpannableStringBuilder getLogMessages() {
        SpannableStringBuilder text = new SpannableStringBuilder();

        for (LogEntry entry: log) {
            int startPos = text.length();
            text.append(entry.msg);
            if (entry.repeat > 1) {
                text.append(" (x").append(Integer.toString(entry.repeat)).append(")");
            }
            text.append("\n");
            int endPos = text.length();

            int color;
            switch (entry.level) {
                case HIGH:
                    color = Color.rgb(255, 160, 160);
                    break;
                case MEDIUM:
                    color = Color.rgb(255, 200, 100);
                    break;
                case LOW:
                    color = Color.WHITE;
                    break;
                case UPDATE:
                    color = Color.rgb(200, 255, 200);
                    break;
                default:
                    color = Color.GRAY;
                    break;
            }
            text.setSpan(new ForegroundColorSpan(color), startPos, endPos, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }

        return text;
    }

    /****************************** LEDs ******************************/
    public void resetLeds() {
        // Do nothing
    }

    public void errorLeds() {
        // Do nothing
    }

    public void blinkLeds() {
        // Do nothing
    }

    public void flashLeds() {
        // Do nothing
    }

    /****************************** UPGRADE ******************************/
    /*
     * This method upgrades data such as preferences, to deal with format
     * changes between versions.
     */
    protected abstract void upgradeData();
}
