package com.remi.remidomo.reloaded.prefs;

import com.remi.remidomo.reloaded.*;
import com.remi.remidomo.reloaded.views.CustomSpinnerPreference;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class PrefsMeteo extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	public static final int DEFAULT_METEO_POLL = 4;

	private CustomSpinnerPreference meteo_poll;

	private SharedPreferences prefs;
	
	private boolean prefChanged = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_meteo);

        prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        meteo_poll = (CustomSpinnerPreference) findPreference("meteo_poll");
        updateTexts();
    }
    
    @Override
    public void onResume() {
        super.onResume();

        // Setup the initial values
        updateTexts();

        prefChanged = false;
        
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

    	prefChanged = true;
    }

    private void updateTexts() {

    	if (meteo_poll != null) {
    		int minutes = prefs.getInt("meteo_poll", DEFAULT_METEO_POLL);
    		String msg = String.format(getString(R.string.pref_meteo_summary), minutes);
    		meteo_poll.setSummary(msg);
    	}
    }
}
