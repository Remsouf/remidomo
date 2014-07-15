package com.remi.remidomo.reloaded.prefs;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;


import com.remi.remidomo.reloaded.R;
import com.remi.remidomo.common.prefs.Defaults;
import com.remi.remidomo.common.views.CustomSpinnerPreference;

public class PrefsGeneral extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	private CustomSpinnerPreference loglimit;

	private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_general);

        prefs = getPreferenceManager().getDefaultSharedPreferences(this.getActivity());

        loglimit = (CustomSpinnerPreference) findPreference("loglimit");
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
    	int days = prefs.getInt("loglimit", Defaults.DEFAULT_LOGLIMIT);
    	String msg = String.format(getString(R.string.pref_storelimit_summary), days);
    	loglimit.setSummary(msg);
    }
}
