package com.remi.remidomo.reloaded.prefs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.remi.remidomo.reloaded.CustomEditTextPreference;
import com.remi.remidomo.reloaded.CustomSpinnerPreference;
import com.remi.remidomo.reloaded.R;
import com.remi.remidomo.reloaded.RDService;

public class PrefsService extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	public static final int DEFAULT_RFX_PORT = 3865;
	public static final int DEFAULT_PORT = 2012;
	public static final String DEFAULT_IP = "1.2.3.4";
	public static final String DEFAULT_MODE = "Serveur";
	public static final boolean DEFAULT_BOOTKICK = true;
	public static final boolean DEFAULT_KEEPSERVICE = true;

	private CustomEditTextPreference ip_address;
	private CustomSpinnerPreference rfx_port;
	private CustomSpinnerPreference port;
	private CheckBoxPreference bootkick;
	private CheckBoxPreference keepservice;
	private ListPreference mode;

	private SharedPreferences prefs;
	
	private boolean prefChanged = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_service);

        prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        bootkick = (CheckBoxPreference) findPreference("bootkick");
        keepservice = (CheckBoxPreference) findPreference("keepservice");
        mode = (ListPreference) findPreference("mode");
        ip_address = (CustomEditTextPreference) findPreference("ip_address");
        port = (CustomSpinnerPreference) findPreference("port");
        rfx_port = (CustomSpinnerPreference) findPreference("rfx_port");
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

    	// Some prefs don't need a service restart
    	if ((!"bootkick".equals(key)) &&
    		(!"keepservice".equals(key))) {
    		prefChanged = true;
    	}
    }

    private void updateTexts() {

    	String mode_sel = prefs.getString("mode", DEFAULT_MODE);
    	if ("Serveur".equals(mode_sel)) {
    		ip_address.setEnabled(false);
    		rfx_port.setEnabled(true);
    	} else {
    		ip_address.setEnabled(true);
    		rfx_port.setEnabled(false);
    	}

    	String msg = String.format(getString(R.string.pref_mode_summary), mode_sel);
    	mode.setSummary(msg);

    	ip_address.setSummary(prefs.getString("ip_address", DEFAULT_IP));
    	port.setSummary(String.valueOf(prefs.getInt("port", DEFAULT_PORT)));
    	rfx_port.setSummary(String.valueOf(prefs.getInt("rfx_port", DEFAULT_RFX_PORT)));

    	boolean boot = prefs.getBoolean("bootkick", DEFAULT_BOOTKICK);
    	if (boot) {
    		bootkick.setSummary(R.string.pref_boot_summary_on);
    	} else {
    		bootkick.setSummary(R.string.pref_boot_summary_off);
    	}

    	boolean keep = prefs.getBoolean("keepservice", DEFAULT_KEEPSERVICE);
    	if (keep) {
    		keepservice.setSummary(R.string.pref_keepservice_summary_on);
    	} else {
    		keepservice.setSummary(R.string.pref_keepservice_summary_off);
    	}
    }
}
