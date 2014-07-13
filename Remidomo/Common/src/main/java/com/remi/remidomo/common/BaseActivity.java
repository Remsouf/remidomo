package com.remi.remidomo.common;

import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

public class BaseActivity extends Activity {

    // Not an enum, because it also represents
    // index into the flipper view
    public static final int DASHBOARD_VIEW_ID = 0;
    public static final int TEMP_VIEW_ID = 1;
    public static final int POOL_VIEW_ID = 2;
    public static final int SWITCHES_VIEW_ID = 3;
    public static final int ENERGY_VIEW_ID = 4;
    public static final int LOG_VIEW_ID = 5;

    public void showSplash() {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.splash,
                (ViewGroup) findViewById(R.id.splash));
        Toast toast = new Toast(this);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    public static String deltaToTimeString(long delta) {
        int hours = (int) delta / 3600000;
        int minutes = ((int)delta - (hours * 3600000)) / 60000;
        String delai = "" + hours + ":" + String.format("%02d", minutes);
        return delai;
    }

    public String deltaToDateString(long delta) {
        int days = (int) (delta / 86400000);
        return String.format(getString(R.string.days), days);
    }
}
