package com.remi.remidomo.reloaded.prefs;

import com.remi.remidomo.reloaded.*;
import com.remi.remidomo.reloaded.views.CustomSpinnerPreference;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class PrefsGeneral extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	public static final int DEFAULT_LOGLIMIT = 365;
	public static final int DEFAULT_CLIENT_POLL = 30;

	private CustomSpinnerPreference loglimit;
	private CustomSpinnerPreference client_poll;

	private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_general);

        prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        loglimit = (CustomSpinnerPreference) findPreference("loglimit");
        client_poll = (CustomSpinnerPreference) findPreference("client_poll");
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
    }

    private void updateTexts() {

    	String mode_sel = prefs.getString("mode", PrefsService.DEFAULT_MODE);
    	if ("Serveur".equals(mode_sel)) {
    		client_poll.setEnabled(false);
    	} else {
    		client_poll.setEnabled(true);
    	}

    	int minutes = prefs.getInt("client_poll", DEFAULT_CLIENT_POLL);
    	String msg = String.format(getString(R.string.pref_poll_summary), minutes);
    	client_poll.setSummary(msg);

    	int days = prefs.getInt("loglimit", DEFAULT_LOGLIMIT);
    	msg = String.format(getString(R.string.pref_storelimit_summary), days);
    	loglimit.setSummary(msg);
    }
}
