package com.remi.remidomo.reloaded.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class WidgetProvider extends AppWidgetProvider {

	@Override
	public void onEnabled(Context context) {
	    super.onEnabled(context);

	    AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
	    Intent intent = new Intent(context, WidgetBroadcastReceiver.class);
	    PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, 0);
	    // After after 20 seconds
	    am.setRepeating(AlarmManager.RTC, System.currentTimeMillis()+ 1000 * 20, 60000 , pi);
	}

	@Override
    public void onDisabled(Context context) {
	    Intent intent = new Intent(context, WidgetBroadcastReceiver.class);
	    PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
	    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
	    alarmManager.cancel(sender);

	    super.onDisabled(context);
    }

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		// Pass the info to RDService
		Intent intent = new Intent(context, com.remi.remidomo.reloaded.RDService.class);
		intent.setAction(com.remi.remidomo.reloaded.RDService.ACTION_UPDATEWIDGET);
        context.startService(intent);
	}
}