package com.remi.remidomo.reloaded;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Timer;

import com.google.android.gcm.GCMConstants;
import com.remi.remidomo.common.BaseActivity;
import com.remi.remidomo.common.BaseService;
import com.remi.remidomo.common.PushSender;
import com.remi.remidomo.common.data.Doors;
import com.remi.remidomo.common.data.SensorData;
import com.remi.remidomo.common.data.Sensors;
import com.remi.remidomo.common.prefs.Defaults;
import com.remi.remidomo.reloaded.prefs.PrefsService;
import com.remi.remidomo.reloaded.widget.WidgetProvider;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.util.Log;
import android.widget.RemoteViews;

public class RDService extends BaseService {

	private final static String TAG = RDService.class.getSimpleName();

    /* Receiver for network state notifications */
    private BroadcastReceiver wifiBroadcastReceiver;

    public final static String ACTION_UPDATEWIDGET = "com.remi.remidomo.reloaded.UPDATE_WIDGET";

    private Timer clientTimer = null;
    
    private final static String GCM_PROJECT_ID = "25944642123";

    private String registrationKey = null;

    @Override
    public void onCreate() {
        super.onCreate();

        // Display a notification about us starting.  We put an icon in the status bar.
        showServiceNotification(RDActivity.class, BaseActivity.DASHBOARD_VIEW_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

    	try {
    		while (prefs == null) {
    			Thread.sleep(100);
    		}
    	} catch (java.lang.InterruptedException ignored) {}

    	// In case of crash/restart, intent can be null
    	if ((intent != null) && GCMConstants.INTENT_FROM_GCM_REGISTRATION_CALLBACK.equals(intent.getAction())) {
    		registrationKey = intent.getStringExtra("registration_id");
    		Log.d(TAG, "New GCM registration key: " + registrationKey);
    	} else if ((intent != null) && GCMConstants.INTENT_FROM_GCM_MESSAGE.equals(intent.getAction())) {
    		// Don't restart, just account for received Push intent
    		handlePushedMessage(intent);
    	} else if ((intent != null) && ACTION_BOOTKICK.equals(intent.getAction())) {
            boolean kickboot = prefs.getBoolean("bootkick", Defaults.DEFAULT_BOOTKICK);
            if (!kickboot) {
                Log.i(TAG, "Exit service to ignore boot event");
                cleanObjects();
                stopSelf();
            }
        } else if ((intent != null) && ACTION_UPDATEWIDGET.equals(intent.getAction())) {
			this.updateWidgets(intent);
    	} else {
    		Log.i(TAG, "Start service");
    		addLog("Service (re)démarré");

    		if ((intent != null) && intent.getBooleanExtra("FORCE_RESTART", true)) {
    			// Deep cleaning: push registration
    			registrationKey = null;
    		}

    		// Stop all threads and clean everything
    		cleanObjects();

    		// Start threads
            clientTimer = new Timer("Client");
            int period = prefs.getInt("client_poll", Defaults.DEFAULT_CLIENT_POLL);
            // 15s graceful period, to let the service read data from FS,
            // before attempting updates from the server
            clientTimer.scheduleAtFixedRate(new ClientTask(this), 15000, 1000L*60*period);

            registerPushMessaging();
    	}
    
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return super.onStartCommand(intent, flags, startId);
    }
    
    protected void cleanObjects() {
    	if (clientTimer != null) {
    		clientTimer.cancel();
    		clientTimer = null;
    	}
    	
        // Stop listening to wifi events, if registered
        if (wifiBroadcastReceiver != null) {
            try {
                unregisterReceiver(wifiBroadcastReceiver);
            } catch (IllegalArgumentException ignored) {}
        }

        super.cleanObjects();
    }
	
	public void forceRefresh() {
        int port = prefs.getInt("port", Defaults.DEFAULT_PORT);
        String ipAddr = prefs.getString("ip_address", Defaults.DEFAULT_IP);

		switches.syncWithServer(port, ipAddr);
		sensors.syncWithServer(port, ipAddr);
		doors.syncWithServer(port, ipAddr, RDActivity.class);
		energy.syncWithServer(port, ipAddr);
	}

	/****************************** LEDs ******************************/
    public synchronized void resetLeds() {
        if (callback != null) {
    		callback.resetLeds();
    	}
    }
    
    public synchronized void errorLeds() {
        if (callback != null) {
    		callback.errorLeds();
    	}
    }
    
    public synchronized void blinkLeds() {
        if (callback != null) {
    		callback.blinkLeds();
    	}
    }

    public synchronized void flashLeds() {
        if (callback != null) {
    		callback.flashLeds();
    	}
    }
    
    
    /****************************** SWITCHESs ******************************/
    public synchronized boolean toggleSwitch(int index) {
        int port = prefs.getInt("port", Defaults.DEFAULT_PORT);
        String ipAddr = prefs.getString("ip_address", Defaults.DEFAULT_IP);
    	boolean result = switches.toggle(index, port, ipAddr);
    	if (callback != null) {
    		callback.updateSwitches();
    		// Don't do this directly from the activity,
    		// or it won't prove anything about the message
    		// being sent.
    		callback.postToast(getString(R.string.cmd_sent));
    	}

    	return result;
    }

    /****************************** PUSH ******************************/
    
    // For clients
    private void registerPushMessaging() {
    	if (registrationKey == null) {
    		Intent registrationIntent = new Intent(GCMConstants.INTENT_TO_GCM_REGISTRATION);
    		registrationIntent.putExtra(GCMConstants.EXTRA_APPLICATION_PENDING_INTENT,
    									PendingIntent.getBroadcast(this, 0, new Intent(), 0));
    		registrationIntent.putExtra(GCMConstants.EXTRA_SENDER, GCM_PROJECT_ID);
    		startService(registrationIntent);
    	}
    }

    private void handlePushedMessage(Intent intent) {
    	String target = intent.getStringExtra(PushSender.TARGET);

		if (target == null) {
			Log.e(TAG, "Pushed intent misses extras");
		} else {
			Log.d(TAG, "Push received: " + target);
			addLog("Push reçu: " + target, LogLevel.UPDATE);

			if (PushSender.SWITCH.equals(target)) {
				switches.setFromPushedIntent(intent);
			} else if (PushSender.DOOR.equals(target)) {
				doors.setFromPushedIntent(intent, RDActivity.class);
			} else if (PushSender.LOWBAT.equals(target)) {
				Date tstamp = new Date(Long.parseLong(intent.getStringExtra(PushSender.TSTAMP)));
				showAlertNotification(getString(R.string.low_bat), BaseService.NotifType.ALERT, R.drawable.battery_low, BaseActivity.TEMP_VIEW_ID, RDActivity.class, tstamp);
			} else if (PushSender.POWER_DROP.equals(target)) {
				// In case the server still has connectivity...
				Date tstamp = new Date(Long.parseLong(intent.getStringExtra(PushSender.TSTAMP)));
                showAlertNotification(getString(R.string.power_dropped), BaseService.NotifType.ALERT, R.drawable.energy, BaseActivity.DASHBOARD_VIEW_ID, RDActivity.class, tstamp);
				energy.updatePowerStatus(false);
				if (callback != null) {
					callback.updateEnergy();
				}
			} else if (PushSender.POWER_RESTORE.equals(target)) {
				Date tstamp = new Date(Long.parseLong(intent.getStringExtra(PushSender.TSTAMP)));
				String duration = intent.getStringExtra(PushSender.STATE);
                showAlertNotification(String.format(getString(R.string.power_restored), duration), BaseService.NotifType.ALERT, R.drawable.energy, BaseActivity.DASHBOARD_VIEW_ID, RDActivity.class, tstamp);
				energy.updatePowerStatus(true);
				if (callback != null) {
					callback.updateEnergy();
				}
			} else if (PushSender.MISSING_SENSOR.equals(target)) {
				Date tstamp = new Date(Long.parseLong(intent.getStringExtra(PushSender.TSTAMP)));
				String sensorName = intent.getStringExtra(PushSender.STATE);
                showAlertNotification(String.format(getString(R.string.missing_sensor), sensorName), BaseService.NotifType.ALERT, R.drawable.temperature2, BaseActivity.TEMP_VIEW_ID, RDActivity.class, tstamp);
			} else {
				Log.e(TAG, "Unknown push target: " + target);
				addLog("Cible push inconnue: " + target, LogLevel.HIGH);
			}
		}
    }

    /****************************** UPGRADE ******************************/
    /*
     * This method upgrades data such as preferences, to deal with format
     * changes between versions.
     */
    protected void upgradeData() {
    	Log.i(TAG, "Upgrading data...");

    	SharedPreferences.Editor editor = prefs.edit();

    	// 103 : prefs converted to ints
    	if (prefs.getInt("version", 0) <= 103) {
    		String keys[] = {"rfx_port", "port", "loglimit", "sncf_poll",
    				"meteo_poll", "client_poll", "plot_limit" };
    		for (String key:keys) {
    			try {
    				prefs.getInt(key, 0);
    			} catch (java.lang.ClassCastException e) {
    				String oldValue = prefs.getString(key, "");
    				editor.remove(key);
    				editor.remove(key+".int");
    				editor.putInt(key, Integer.parseInt(oldValue));
    			}
    		}
    	}

    	int versionCode;
    	try {
    		versionCode = getPackageManager().getPackageInfo("com.remi.remidomo.reloaded", 0).versionCode;
    		editor.putInt("version", versionCode);
    	} catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}

    	editor.commit();
    }

    /****************************** WIDGET ******************************/
    private void updateWidgets(Intent intent) {

    	AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);

    	ComponentName thisWidget = new ComponentName(this, WidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

		final int N = appWidgetIds.length;

        // Perform this loop procedure for each App Widget that belongs to this provider
        for (int i=0; i<N; i++) {
            int appWidgetId = appWidgetIds[i];

            // See if the widget is on home screen or keyguard
            int layoutId = R.layout.widget_home;
            try {
                Method method = AppWidgetManager.class.getMethod("getAppWidgetOptions", int.class);
                Bundle options = (Bundle) method.invoke(appWidgetManager, appWidgetId);
                int category = options.getInt("appWidgetCategory", -1);
                if (category == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) {
                    layoutId = R.layout.widget_keyguard;
                }
            } catch (InvocationTargetException ignored) {
            } catch (NoSuchMethodException ignored) {
            } catch (IllegalAccessException ignored) {
            }

            RemoteViews views = new RemoteViews(this.getPackageName(), layoutId);

            // Create an Intent to launch main activity
            Intent actIntent = new Intent(this, RDActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, actIntent, 0);
            views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent);

            // Pool
    		SensorData series = null;
    		if (getSensors() != null) {
    		    series = getSensors().getData(Sensors.ID_POOL_T);
    		}
    		if ((series != null) && (series.size() > 0)) {
    			DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
    			decimalFormat.applyPattern("#0.0#");
    			float lastValue = series.getLast().value;
    			views.setTextViewText(R.id.widget_pool_temp, decimalFormat.format(lastValue)+getString(R.string.degC));
    		} else {
    			views.setTextViewText(R.id.widget_pool_temp, "??.?"+getString(R.string.degC));
    		}

    		// Ext
    		if (getSensors() != null) {
    		    series = getSensors().getData(Sensors.ID_EXT_T);
    		}
    		if ((series != null) && (series.size() > 0)) {
    			DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
    			decimalFormat.applyPattern("#0.0#");
    			float lastValue = series.getLast().value;
    			views.setTextViewText(R.id.widget_ext_temp, decimalFormat.format(lastValue)+getString(R.string.degC));
    		} else {
    			views.setTextViewText(R.id.widget_ext_temp, "??.?"+getString(R.string.degC));
    		}

    		// Veranda
    		if (getSensors() != null) {
    		    series = getSensors().getData(Sensors.ID_VERANDA_T);
    		}
    		if ((series != null) && (series.size() > 0)) {
    			DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
    			decimalFormat.applyPattern("#0.0#");
    			float lastValue = series.getLast().value;
    			views.setTextViewText(R.id.widget_veranda_temp, decimalFormat.format(lastValue)+getString(R.string.degC));
    		} else {
    			views.setTextViewText(R.id.widget_veranda_temp, "??.?"+getString(R.string.degC));
    		}

    		if ((getSensors() != null) && (getSensors().getLastUpdate() != null)) {
                long delta = new Date().getTime() - getSensors().getLastUpdate().getTime();
                views.setTextViewText(R.id.widget_last_thermo_update, RDActivity.deltaToTimeString(delta));
            } else {
                views.setTextViewText(R.id.widget_last_thermo_update, "?:??");
            }

    		// Garage
    		Doors.State state;
    		if (getDoors() != null) {
    		    state = getDoors().getState(Doors.GARAGE);
    		} else {
    		    state = Doors.State.UNKNOWN;
    		}
			views.setImageViewResource(R.id.widget_garage_status, Doors.getResourceForState(state));

			if ((getDoors() != null) && (getDoors().getLastUpdate(Doors.GARAGE) != null)) {
			    Date lastEvent = getDoors().getLastUpdate(Doors.GARAGE);
			    DateFormat format = DateFormat.getTimeInstance(DateFormat.SHORT);
			    views.setTextViewText(R.id.widget_last_garage_update, format.format(lastEvent));
			} else {
			    views.setTextViewText(R.id.widget_last_garage_update, "??:??");
			}

			// Clock
			DateFormat format = DateFormat.getTimeInstance(DateFormat.SHORT);
			views.setTextViewText(R.id.widget_time, format.format(new Date()));

			format = DateFormat.getDateInstance(DateFormat.MEDIUM);
			views.setTextViewText(R.id.widget_date, format.format(new Date()));

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
