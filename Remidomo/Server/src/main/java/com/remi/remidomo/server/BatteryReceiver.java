package com.remi.remidomo.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public class BatteryReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
        // Just bounce the same action to the service
        Intent serviceIntent = new Intent();
        if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction())) {
            serviceIntent.setAction(RDService.ACTION_BATTERYLOW);
        } else if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
            serviceIntent.setAction(RDService.ACTION_POWERCONNECT);
        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
            if (intent.hasExtra(BatteryManager.EXTRA_PLUGGED)) {
                // Something was disconnected. Check if we still get power.
                int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                if ((plugged == BatteryManager.BATTERY_PLUGGED_USB) ||
                        (plugged == BatteryManager.BATTERY_PLUGGED_AC) ||
                        (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS)) {
                    // Powered by something: ignore disconnections
                    return;
                }
            }
            serviceIntent.setAction(RDService.ACTION_POWERDISCONNECT);
        } else {
            assert(false);
        }
        context.startService(serviceIntent);
    }
}