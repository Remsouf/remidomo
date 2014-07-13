package com.remi.remidomo.server.prefs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.support.v4.preference.PreferenceFragment;

import com.remi.remidomo.common.BaseService;
import com.remi.remidomo.common.prefs.Defaults;
import com.remi.remidomo.common.views.CustomSpinnerPreference;
import com.remi.remidomo.server.R;
import com.remi.remidomo.server.RDService;

public class PrefsService extends PreferenceFragment implements OnSharedPreferenceChangeListener {

    private CustomSpinnerPreference rfx_port;
    private CustomSpinnerPreference port;
    private CheckBoxPreference bootkick;
    private CheckBoxPreference keepservice;

    private SharedPreferences prefs;

    private boolean prefChanged = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_service);

        prefs = getPreferenceManager().getDefaultSharedPreferences(this.getActivity());

        bootkick = (CheckBoxPreference) findPreference("bootkick");
        keepservice = (CheckBoxPreference) findPreference("keepservice");
        port = (CustomSpinnerPreference) findPreference("port");
        rfx_port = (CustomSpinnerPreference) findPreference("rfx_port");
        updateTexts();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Setup the initial values
        updateTexts();

        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

        // Start the service (again, now that prefs maybe changed)
        if (prefChanged) {
            final Intent intent = new Intent(this.getActivity(), RDService.class);
            intent.putExtra("FORCE_RESTART", true);
            getActivity().startService(intent);
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Let's do something when a preference value changes


        // Account for time prefs (with .hour / .minute suffix)
        String effectiveKey = key;
        int dotPos = key.indexOf('.');
        if (dotPos >= 0) {
            effectiveKey = key.substring(0, key.indexOf('.'));
        }

        // If pref is a hidden key, don't update texts
        // (i.e. those committed directly by the service)
        if (getPreferenceScreen().findPreference(effectiveKey) != null) {
            updateTexts();
        }

        // Some prefs don't need a service restart
        if ((!"bootkick".equals(key)) &&
                (!"keepservice".equals(key))) {
            prefChanged = true;
        }
    }

    protected void updateTexts() {
        boolean boot = prefs.getBoolean("bootkick", Defaults.DEFAULT_BOOTKICK);
        if (boot) {
            bootkick.setSummary(com.remi.remidomo.common.R.string.pref_boot_summary_on);
        } else {
            bootkick.setSummary(com.remi.remidomo.common.R.string.pref_boot_summary_off);
        }

        boolean keep = prefs.getBoolean("keepservice", Defaults.DEFAULT_KEEPSERVICE);
        if (keep) {
            keepservice.setSummary(com.remi.remidomo.common.R.string.pref_keepservice_summary_on);
        } else {
            keepservice.setSummary(com.remi.remidomo.common.R.string.pref_keepservice_summary_off);
        }

        port.setSummary(String.valueOf(prefs.getInt("port", Defaults.DEFAULT_PORT)));
        rfx_port.setSummary(String.valueOf(prefs.getInt("rfx_port", Defaults.DEFAULT_RFX_PORT)));
    }
}
