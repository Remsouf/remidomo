package com.remi.remidomo.common;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
  
public class BootReceiver extends BroadcastReceiver{
    @Override
    public void onReceive(Context context, Intent intent) {
    	Intent serviceIntent = new Intent();
    	serviceIntent.setAction(BaseService.ACTION_BOOTKICK);
    	context.startService(serviceIntent);
    }
}