package com.remi.remidomo.reloaded.prefs;

import java.text.DecimalFormat;

import com.remi.remidomo.reloaded.views.CustomSpinnerPreference;
import com.remi.remidomo.reloaded.views.CustomTimePickerPreference;
import com.remi.remidomo.reloaded.R;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class PrefsEnergy extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	public static final int DEFAULT_HCHOUR = 23;
	public static final int DEFAULT_HPHOUR = 7;
	public static final int DEFAULT_ENERGYLIMIT = 2;
	public static final boolean DEFAULT_ENERGY_GRAPH = true;
	public static final boolean DEFAULT_TARIF_HIGHLIGHT = true;
	
	private CustomTimePickerPreference hchour;
	private CustomTimePickerPreference hphour;
	private CustomSpinnerPreference energylimit;

	private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_energy);

        prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        hchour = (CustomTimePickerPreference) findPreference("hc_hour");
        hphour = (CustomTimePickerPreference) findPreference("hp_hour");
        energylimit = (CustomSpinnerPreference) findPreference("energy_limit");
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

    	String msg;
    	int days, hours, minutes;

    	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
        decimalFormat.applyPattern("#00");

        hours = prefs.getInt("hc_hour.hour", DEFAULT_HCHOUR);
        minutes = prefs.getInt("hc_hour.minute", 0);
        msg = String.format(getString(R.string.pref_hchour_summary), hours, decimalFormat.format(minutes));
        hchour.setSummary(msg);

        hours = prefs.getInt("hp_hour.hour", DEFAULT_HPHOUR);
        minutes = prefs.getInt("hp_hour.minute", 0);
        msg = String.format(getString(R.string.pref_hphour_summary), hours, decimalFormat.format(minutes));
        hphour.setSummary(msg);
        
        days = prefs.getInt("energy_limit", DEFAULT_ENERGYLIMIT);
        msg = String.format(getString(R.string.pref_energylimit_summary), days);
        energylimit.setSummary(msg);
    }

}