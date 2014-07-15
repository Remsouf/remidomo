package com.remi.remidomo.server;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import com.remi.remidomo.common.BaseService;
import com.remi.remidomo.common.data.Doors;
import com.remi.remidomo.common.prefs.Defaults;
import com.remi.remidomo.server.R;
import com.remi.remidomo.common.BaseActivity;
import com.remi.remidomo.common.IUpdateListener;
import com.remi.remidomo.common.data.SensorData;
import com.remi.remidomo.common.data.Sensors;
import com.remi.remidomo.common.views.TrainsView;
import com.remi.remidomo.server.prefs.PreferencesActivity;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

public class RDActivity extends BaseActivity {

    private final static String TAG = "Remidomo-" + RDActivity.class.getSimpleName();

    private SharedPreferences prefs;

    public RDService service;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Remove title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Request landscape orientation (90 or 270)
        if (Build.VERSION.SDK_INT > 8) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        setContentView(R.layout.main);

        new Thread(new Runnable() {
            public void run() {
                prefs = PreferenceManager.getDefaultSharedPreferences(RDActivity.this);
            }
        }).start();

        setupClickListeners();

        // Show splash screen only if we're really starting service
        boolean serviceRunning = false;
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ("com.remi.remidomo.server.RDService".equals(service.service.getClassName())) {
                serviceRunning = true;
                break;
            }
        }

        if(!serviceRunning) {
            showSplash();
        }

        // Start service *before* binding,
        // for it to survive the activity
        Intent intent = new Intent(this, RDService.class);
        startService(intent);
        if (!bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to bind to service");
        }

        // Timer for updating "Il y a ..." texts
        Timer timerIlya = new Timer("Il y a");
        timerIlya.scheduleAtFixedRate(new IlyaTask(), 1, 60000);  // 1min
    }

    private void setupClickListeners() {
        ImageButton trainNote = (ImageButton) findViewById(R.id.train_note);
        trainNote.setOnClickListener(new TrainsView.OnClickListener() {
            public void onClick(View v) {
                //	String gare = prefs.getString("gare", Preferences.DEFAULT_GARE);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.gares-en-mouvement.com/fr/frhvx/horaires-temps-reel/dep/"));
                startActivity(browserIntent);
            }
        });

        // Hooks for switches buttons
        ImageButton switchButton = (ImageButton) findViewById(R.id.switch1_cmd);
        switchButton.setOnClickListener(new TrainsView.OnClickListener() {
            public void onClick(View v) {
                service.toggleSwitch(0);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateMeteoView();
        updateTrainView();
        updateDashboardThermo();
        updateDoorsView();
        updateSwitchesView();
        updateEnergyView();

        updateTrainLastUpdate();
        updateMeteoLastUpdate();
        updateThermoLastUpdate();
    }

    @Override
    protected void onDestroy() {

        if (!prefs.getBoolean("keepservice", Defaults.DEFAULT_KEEPSERVICE)) {
            if (service != null) {
                service.stopAtActivityRequest();
            }
        }
        unbindService(serviceConnection);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save_sdcard:
                if (service != null) {
                    service.saveToSdcard();
                }
                return true;
            case R.id.clear_data:
                if (service != null) {
                    final Intent intent = new Intent(this, RDService.class);
                    intent.putExtra("RESET_DATA", true);
                    startService(intent);
                }
                return true;
            case R.id.show_log:
                startActivity(new Intent(this, LogActivity.class));
                return true;
            case R.id.settings:
                startActivity(new Intent(RDActivity.this, PreferencesActivity.class));
                return true;
        }

        return super.onMenuItemSelected(featureId, item);
    }

    private class IlyaTask extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                public void run() {
                    updateTrainLastUpdate();
                    updateMeteoLastUpdate();
                    updateThermoLastUpdate();
                }
            });
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder serviceBind) {
            RDService.LocalBinder binder = (RDService.LocalBinder) serviceBind;
            service = (RDService) binder.getService();

            binder.registerCallbacks(updateListener);

            // Once connected to service, update views
            updateTrainView();
            updateDashboardThermo();
            updateSwitchesView();
            updateDoorsView();
            updateEnergyView();
        }

        public void onServiceDisconnected(ComponentName className) {
            service = null;
        }
    };

    private void updateDashboardThermo() {

        final Animation anim = AnimationUtils.loadAnimation(this, R.anim.zoomin);
        final LinearLayout layout = (LinearLayout) findViewById(R.id.temps_layout);
        layout.startAnimation(anim);

        // Pool
        SensorData series = null;
        if (service != null) {
            series = service.getSensors().getData(Sensors.ID_POOL_T);
        }
        TextView textView = (TextView) findViewById(R.id.pool_temp);
        if ((series != null) && (series.size() > 0)) {
            DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("#0.0#");
            float lastValue = series.getLast().value;
            textView.setText(decimalFormat.format(lastValue)+getString(R.string.degC));
        } else {
            textView.setText("?");
        }

        // Ext
        series = null;
        if (service != null) {
            series = service.getSensors().getData(Sensors.ID_EXT_T);
        }
        textView = (TextView) findViewById(R.id.ext_temp);
        if ((series != null) && (series.size() > 0)) {
            DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("#0.0#");
            float lastValue = series.getLast().value;
            textView.setText(decimalFormat.format(lastValue)+getString(R.string.degC));
        } else {
            textView.setText("?");
        }
        if (service != null) {
            series = service.getSensors().getData(Sensors.ID_EXT_H);
        }
        textView = (TextView) findViewById(R.id.ext_humi);
        if ((series != null) && (series.size() > 0)) {
            DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("##");
            float lastValue = series.getLast().value;
            textView.setText(decimalFormat.format(lastValue)+"%");
        } else {
            textView.setText("?");
        }

        // Veranda
        series = null;
        if (service != null) {
            series = service.getSensors().getData(Sensors.ID_VERANDA_T);
        }
        textView = (TextView) findViewById(R.id.veranda_temp);
        if ((series != null) && (series.size() > 0)) {
            DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("#0.0#");
            float lastValue = series.getLast().value;
            textView.setText(decimalFormat.format(lastValue)+getString(R.string.degC));
        } else {
            textView.setText("?");
        }
        if (service != null) {
            series = service.getSensors().getData(Sensors.ID_VERANDA_H);
        }
        textView = (TextView) findViewById(R.id.veranda_humi);
        if ((series != null) && (series.size() > 0)) {
            DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("##");
            float lastValue = series.getLast().value;
            textView.setText(decimalFormat.format(lastValue)+"%");
        } else {
            textView.setText("?");
        }

        updateThermoLastUpdate();
    }

    private void updateTrainView() {
        if (service != null) {
            TrainsView trainsView = (TrainsView) findViewById(R.id.trains);
            trainsView.updateView(this, service.getTrains().getData());

            ImageView unknown = (ImageView) findViewById(R.id.train_unknown);
            if (service.getTrains().getData().isEmpty()) {
                unknown.setVisibility(View.VISIBLE);
            } else {
                unknown.setVisibility(View.GONE);
            }
        }
        updateTrainLastUpdate();
    }

    private void updateMeteoView() {
        if (service != null) {
            service.getMeteo().updateView(this);
        }

        updateMeteoLastUpdate();
    }

    private void updateDoorsView() {
        if (service != null) {
            // Main icon
            ImageButton portail = (ImageButton) findViewById(R.id.garage_status);
            Doors.State state = service.getDoors().getState(Doors.GARAGE);
            portail.setImageResource(Doors.getResourceForState(state));

            // Last event
            Date lastEvent = service.getDoors().getLastUpdate(Doors.GARAGE);
            if (lastEvent != null) {
                TextView textDate = (TextView) findViewById(R.id.garage_last_date);
                DateFormat df = DateFormat.getDateInstance();
                textDate.setText(df.format(lastEvent));

                TextView textTime = (TextView) findViewById(R.id.garage_last_time);
                df = DateFormat.getTimeInstance();
                textTime.setText(df.format(lastEvent));
            }
        }
    }

    private void updateSwitchesView() {
        if (service != null) {
            ImageButton button = (ImageButton) findViewById(R.id.switch1_cmd);
            if (service.getSwitches().getState(0)) {
                button.setBackgroundResource(R.drawable.switched_on);
            } else {
                button.setBackgroundResource(R.drawable.switched_off);
            }
        }
    }

    private void updateEnergyView() {
        SensorData series = null;

        // Power
        if (service != null) {
            series = service.getEnergy().getPowerData();
        }

        TextView power = (TextView) findViewById(R.id.power);
        TextView units = (TextView) findViewById(R.id.power_units);
        if ((series != null) && (series.size() > 0)) {
            DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("#0.000");
            float lastValue = series.getLast().value;
            power.setText(decimalFormat.format(lastValue));
        } else {
            power.setText("?");
        }

        Calendar hcHour = Calendar.getInstance();
        hcHour.set(Calendar.HOUR_OF_DAY, prefs.getInt("hc_hour.hour", Defaults.DEFAULT_HCHOUR));
        hcHour.set(Calendar.MINUTE, prefs.getInt("hc_hour.minute", 0));

        Calendar hpHour = Calendar.getInstance();
        hpHour.set(Calendar.HOUR_OF_DAY, prefs.getInt("hp_hour.hour", Defaults.DEFAULT_HPHOUR));
        hpHour.set(Calendar.MINUTE, prefs.getInt("hp_hour.minute", 0));

        Date now = new Date();
        if ((now.getTime() >= hpHour.getTimeInMillis()) &&
                (now.getTime() < hcHour.getTimeInMillis())) {
            power.setTextColor(Color.parseColor("#FF5555"));
            units.setTextColor(Color.parseColor("#FF5555"));
        } else {
            power.setTextColor(Color.parseColor("#5555FF"));
            units.setTextColor(Color.parseColor("#5555FF"));
        }
    }

    public void updateMeteoLastUpdate() {
        TextView lastUpdate = (TextView) findViewById(R.id.meteo_last_update);
        if ((service != null) && (service.getMeteo().getLastUpdate() != null)) {
            long delta = new Date().getTime() - service.getMeteo().getLastUpdate().getTime();
            lastUpdate.setText(String.format(getString(R.string.ilya), deltaToTimeString(delta)));
        } else {
            lastUpdate.setText("");
        }
        lastUpdate.invalidate();
    }

    public void updateTrainLastUpdate() {
        TextView lastUpdate = (TextView) findViewById(R.id.train_last_update);
        if ((service != null) && (service.getTrains().getLastUpdate() != null)) {
            long delta = new Date().getTime() - service.getTrains().getLastUpdate().getTime();
            lastUpdate.setText(String.format(getString(R.string.ilya), deltaToTimeString(delta)));
        } else {
            lastUpdate.setText("");
        }
        lastUpdate.invalidate();
    }

    public void updateThermoLastUpdate() {
        TextView lastUpdate = (TextView) findViewById(R.id.thermo_last_update);
        if ((service != null) && (service.getSensors().getLastUpdate() != null)) {
            long delta = new Date().getTime() - service.getSensors().getLastUpdate().getTime();
            lastUpdate.setText(String.format(getString(R.string.ilya), deltaToTimeString(delta)));
        } else {
            lastUpdate.setText("");
        }
        lastUpdate.invalidate();
    }

    // Callback for Service
    private IUpdateListener updateListener = new IUpdateListener() {

        public void updateLog() {}
        public void resetLeds() {}
        public void errorLeds() {}
        public void blinkLeds() {}
        public void flashLeds() {}
        public void startRefreshAnim() {}
        public void stopRefreshAnim() {}

        public void updateEnergy() {
            runOnUiThread(new Runnable() {
                public void run() {
                    updateEnergyView();
                }
            });
        }

        public void updateSwitches() {
            runOnUiThread(new Runnable() {
                public void run() {
                    updateSwitchesView();
                }
            });
        }

        public void updateDoors() {
            runOnUiThread(new Runnable() {
                public void run() {
                    updateDoorsView();
                }
            });
        }

        public void updateTrains() {
            runOnUiThread(new Runnable() {
                public void run() {
                    updateTrainView();
                }
            });
        }

        public void updateMeteo() {
            runOnUiThread(new Runnable() {
                public void run() {
                    updateMeteoView();
                }
            });
        }

        public void updateThermo() {
            runOnUiThread(new Runnable() {
                public void run() {
                    updateDashboardThermo();
                }
            });
        }

        public void postToast(final String text) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(RDActivity.this, text, Toast.LENGTH_LONG).show();
                }
            });
        }
    };
}
