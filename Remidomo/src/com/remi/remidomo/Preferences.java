package com.remi.remidomo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	
	public static final String DEFAULT_RFX_PORT = "3865";
	public static final String DEFAULT_PORT = "2012";
	public static final String DEFAULT_IP = "remidomo.hd.free.fr";
	public static final String DEFAULT_MODE = "Serveur";
	public static final String DEFAULT_LOGLIMIT = "365";
	public static final String DEFAULT_SNCF_POLL = "15";
	public static final String DEFAULT_GARE = "GOC";
	public static final String DEFAULT_METEO_POLL = "60";
	public static final String DEFAULT_CLIENT_POLL = "30";
	public static final boolean DEFAULT_NIGHT_HIGHLIGHT = true;
	public static final boolean DEFAULT_DOTS_HIGHLIGHT = true;
	public static final boolean DEFAULT_DAY_LABELS = true;
	public static final String DEFAULT_PLOTLIMIT = "10";
	public static final boolean DEFAULT_SOUND = true;

	private ListPreference mode;
	private EditTextPreference ip_address;
	private EditTextPreference rfx_port;
	private EditTextPreference port;
	private EditTextPreference loglimit;
	private EditTextPreference sncf_poll;
	private ListPreference gare;
	private EditTextPreference meteo_poll;
	private EditTextPreference client_poll;
	private CheckBoxPreference reset;
	private EditTextPreference plotlimit;
	private EditTextPreference c2dm_account;
	private EditTextPreference c2dm_password;

	private SharedPreferences prefs;
	
	private boolean prefChanged = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);        
        addPreferencesFromResource(R.xml.preferences);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        reset = (CheckBoxPreference) getPreferenceScreen().findPreference("reset");
        mode = (ListPreference) getPreferenceScreen().findPreference("mode");
        ip_address = (EditTextPreference) getPreferenceScreen().findPreference("ip_address");
        port = (EditTextPreference) getPreferenceScreen().findPreference("port");
        rfx_port = (EditTextPreference) getPreferenceScreen().findPreference("rfx_port");
        client_poll = (EditTextPreference) getPreferenceScreen().findPreference("client_poll");
        loglimit = (EditTextPreference) getPreferenceScreen().findPreference("loglimit");
        sncf_poll = (EditTextPreference) getPreferenceScreen().findPreference("sncf_poll");
        gare = (ListPreference) getPreferenceScreen().findPreference("gare");
        meteo_poll = (EditTextPreference) getPreferenceScreen().findPreference("meteo_poll");
        plotlimit = (EditTextPreference) getPreferenceScreen().findPreference("plot_limit");
        c2dm_account = (EditTextPreference) getPreferenceScreen().findPreference("c2dm_account");
        c2dm_password = (EditTextPreference) getPreferenceScreen().findPreference("c2dm_password");
        
        updateTexts();
    }
    
    @Override
    protected void onResume() {
        super.onResume();

        // Setup the initial values
        updateTexts();

        prefChanged = false;
        
        // Set up a listener whenever a key changes            
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes            
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);    

        // Start the service (again, now that prefs maybe changed)
        if (prefChanged || reset.isChecked()) {
        	final Intent intent = new Intent(this, RDService.class);
        	intent.putExtra("FORCE_RESTART", true);
        	startService(intent);
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Let's do something when a preference value changes
    	updateTexts();
    	
    	// Some prefs don't need a service restart
    	if ((!"night_highlight".equals(key)) &&
    		(!"dots_highlight".equals(key)) &&
    		(!"day_labels".equals(key)) &&
    		(!"plot_limit".equals(key)) &&
    		(!"sound".equals(key))) {
    		prefChanged = true;
    	}
    }

    private void updateTexts() {
    	String mode_sel = prefs.getString("mode", DEFAULT_MODE);
    	mode.setSummary(mode_sel);
    	if ("Serveur".equals(mode_sel)) {
    		ip_address.setEnabled(false);
    		rfx_port.setEnabled(true);
    		client_poll.setEnabled(false);
    		c2dm_account.setEnabled(true);
    		c2dm_password.setEnabled(true);
    	} else {
    		ip_address.setEnabled(true);
    		rfx_port.setEnabled(false);
    		client_poll.setEnabled(true);
    		c2dm_account.setEnabled(false);
    		c2dm_password.setEnabled(false);
    	}

        ip_address.setSummary(prefs.getString("ip_address", DEFAULT_IP)); 
        port.setSummary(prefs.getString("port", DEFAULT_PORT));
        rfx_port.setSummary(prefs.getString("rfx_port", DEFAULT_RFX_PORT));
        
    	int minutes = Integer.parseInt(prefs.getString("client_poll", DEFAULT_CLIENT_POLL));
    	String msg = String.format(getString(R.string.pref_poll_summary), minutes);
    	client_poll.setSummary(msg);
  
    	int days = Integer.parseInt(prefs.getString("loglimit", DEFAULT_LOGLIMIT));
    	msg = String.format(getString(R.string.pref_storelimit_summary), days);
    	loglimit.setSummary(msg);
    	
    	minutes = Integer.parseInt(prefs.getString("sncf_poll", DEFAULT_SNCF_POLL));
    	msg = String.format(getString(R.string.pref_poll_summary), minutes);
    	sncf_poll.setSummary(msg);
    	
    	String gare_sel = prefs.getString("gare", DEFAULT_GARE);
    	gare.setSummary(gare_sel);

    	minutes = Integer.parseInt(prefs.getString("meteo_poll", DEFAULT_METEO_POLL));
    	msg = String.format(getString(R.string.pref_meteo_summary), minutes);
    	meteo_poll.setSummary(msg);
    	
    	days = Integer.parseInt(prefs.getString("plot_limit", DEFAULT_PLOTLIMIT));
    	msg = String.format(getString(R.string.pref_plotlimit_summary), days);
    	plotlimit.setSummary(msg);
    }
}
