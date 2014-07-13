package com.remi.remidomo.reloaded.prefs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.remi.remidomo.common.BaseService;
import com.remi.remidomo.common.prefs.Defaults;
import com.remi.remidomo.common.views.CustomEditTextPreference;
import com.remi.remidomo.common.views.CustomSpinnerPreference;
import com.remi.remidomo.reloaded.*;

public class PrefsService extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	private CustomEditTextPreference ip_address;
	private CustomSpinnerPreference port;
	private Preference reset_data;
    private CustomSpinnerPreference client_poll;
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
        ip_address = (CustomEditTextPreference) findPreference("ip_address");
        port = (CustomSpinnerPreference) findPreference("port");
        reset_data = (Preference) findPreference("reset_data");
        client_poll = (CustomSpinnerPreference) findPreference("client_poll");
        updateTexts();

        reset_data.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference pref) {
            	final Intent intent = new Intent(PrefsService.this.getActivity(), RDService.class);
            	intent.putExtra("RESET_DATA", true);
            	getActivity().startService(intent);
            	return true;
            }
        });
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

    	ip_address.setSummary(prefs.getString("ip_address", Defaults.DEFAULT_IP));
    	port.setSummary(String.valueOf(prefs.getInt("port", Defaults.DEFAULT_PORT)));

        int minutes = prefs.getInt("client_poll", Defaults.DEFAULT_CLIENT_POLL);
        String msg = String.format(getString(R.string.pref_poll_summary), minutes);
        client_poll.setSummary(msg);
    }
}
