package com.remi.remidomo.reloaded.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WidgetBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Pass the info to RDService
        Intent srvIntent = new Intent(context, com.remi.remidomo.reloaded.RDService.class);
        srvIntent.setAction(com.remi.remidomo.reloaded.RDService.ACTION_UPDATEWIDGET);
        context.startService(srvIntent);
    }
   }
