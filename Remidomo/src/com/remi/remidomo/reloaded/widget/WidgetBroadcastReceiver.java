package com.remi.remidomo.reloaded.widget;

import com.remi.remidomo.reloaded.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WidgetBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Pass the info to RDService
        Intent srvIntent = new Intent(context, com.remi.remidomo.reloaded.RDService.class);
        srvIntent.setAction(RDService.ACTION_UPDATEWIDGET);
        context.startService(srvIntent);
    }
   }
