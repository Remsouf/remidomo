package com.remi.remidomo.reloaded.prefs;

import com.remi.remidomo.reloaded.*;
import com.remi.remidomo.reloaded.views.CustomSpinnerPreference;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class PrefsPlots extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	public static final boolean DEFAULT_NIGHT_HIGHLIGHT = true;
	public static final boolean DEFAULT_DOTS_HIGHLIGHT = true;
	public static final boolean DEFAULT_DAY_LABELS = true;
	public static final int DEFAULT_PLOTLIMIT = 10;

	private CustomSpinnerPreference plotlimit;

	private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_plots);

        prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        plotlimit = (CustomSpinnerPreference) findPreference("plot_limit");
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

    	int days = prefs.getInt("plot_limit", DEFAULT_PLOTLIMIT);
    	String msg = String.format(getString(R.string.pref_plotlimit_summary), days);
    	plotlimit.setSummary(msg);
    	
    	
    }
}
