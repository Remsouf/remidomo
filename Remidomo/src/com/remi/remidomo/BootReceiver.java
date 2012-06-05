package com.remi.remidomo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
  
public class BootReceiver extends BroadcastReceiver{
    @Override
    public void onReceive(Context context, Intent intent) {
    	Intent serviceIntent = new Intent();
    	serviceIntent.setAction("com.remo.remidomo.RDService");
    	context.startService(serviceIntent);
    }
}