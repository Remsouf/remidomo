package com.remi.remidomo.reloaded.prefs;

import com.remi.remidomo.reloaded.CustomSpinnerPreference;
import com.remi.remidomo.reloaded.R;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class PrefsTrain extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	public static final int DEFAULT_SNCF_POLL = 15;
	public static final String DEFAULT_GARE = "GOC";

	private CustomSpinnerPreference sncf_poll;
	private ListPreference gare;


	private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_train);

        prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        sncf_poll = (CustomSpinnerPreference) findPreference("sncf_poll");
        gare = (ListPreference) findPreference("gare");
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
    	int minutes = prefs.getInt("sncf_poll", DEFAULT_SNCF_POLL);
    	String msg = String.format(getString(R.string.pref_poll_summary), minutes);
    	sncf_poll.setSummary(msg);
    	
    	String gare_sel = prefs.getString("gare", DEFAULT_GARE);
    	gare.setSummary(gare_sel);
    }
}
