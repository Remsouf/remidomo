package com.remi.remidomo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.webkit.URLUtil;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	//public static final String DEFAULT_RFX_PORT = "3865";
	public static final int DEFAULT_RFX_PORT = 1234;
	public static final int DEFAULT_PORT = 1234;
	public static final String DEFAULT_IP = "1.2.3.4";
	public static final String DEFAULT_MODE = "Serveur";
	public static final int DEFAULT_LOGLIMIT = 365;
	public static final int DEFAULT_SNCF_POLL = 15;
	public static final String DEFAULT_GARE = "GOC";
	public static final int DEFAULT_METEO_POLL = 4;
	public static final int DEFAULT_CLIENT_POLL = 30;
	public static final boolean DEFAULT_NIGHT_HIGHLIGHT = true;
	public static final boolean DEFAULT_DOTS_HIGHLIGHT = true;
	public static final boolean DEFAULT_DAY_LABELS = true;
	public static final int DEFAULT_PLOTLIMIT = 10;
	public static final boolean DEFAULT_SOUND = true;
	public static final boolean DEFAULT_BOOTKICK = true;
	public static final boolean DEFAULT_KEEPSERVICE = true;

	private ListPreference mode;
	private PreferenceScreen mode_screen;
	private CustomEditTextPreference ip_address;
	private CustomEditTextPreference rfx_port;
	private CustomEditTextPreference port;
	private CustomEditTextPreference loglimit;
	private CustomEditTextPreference sncf_poll;
	private ListPreference gare;
	private CustomEditTextPreference meteo_poll;
	private CustomEditTextPreference client_poll;
	private CheckBoxPreference bootkick;
	private CheckBoxPreference keepservice;
	private CustomEditTextPreference plotlimit;

	private SharedPreferences prefs;
	
	private boolean prefChanged = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);        
        addPreferencesFromResource(R.xml.preferences);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        bootkick = (CheckBoxPreference) getPreferenceScreen().findPreference("bootkick");
        keepservice = (CheckBoxPreference) getPreferenceScreen().findPreference("keepservice");
        mode = (ListPreference) getPreferenceScreen().findPreference("mode");
        mode_screen = (PreferenceScreen) getPreferenceScreen().findPreference("mode_screen");
        ip_address = (CustomEditTextPreference) getPreferenceScreen().findPreference("ip_address");
        port = (CustomEditTextPreference) getPreferenceScreen().findPreference("port");
        rfx_port = (CustomEditTextPreference) getPreferenceScreen().findPreference("rfx_port");
        client_poll = (CustomEditTextPreference) getPreferenceScreen().findPreference("client_poll");
        loglimit = (CustomEditTextPreference) getPreferenceScreen().findPreference("loglimit");
        sncf_poll = (CustomEditTextPreference) getPreferenceScreen().findPreference("sncf_poll");
        gare = (ListPreference) getPreferenceScreen().findPreference("gare");
        meteo_poll = (CustomEditTextPreference) getPreferenceScreen().findPreference("meteo_poll");
        plotlimit = (CustomEditTextPreference) getPreferenceScreen().findPreference("plot_limit");

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
        mode_screen.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes            
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);    

        // Start the service (again, now that prefs maybe changed)
        if (prefChanged) {
        	final Intent intent = new Intent(this, RDService.class);
        	intent.putExtra("FORCE_RESTART", true);
        	startService(intent);
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    	// Let's do something when a preference value changes

    	// If pref is a hidden key, don't update texts
    	// (i.e. those committed directly by the service)
    	if (getPreferenceScreen().findPreference(key) != null) {
    		updateTexts();
    	}

    	// Some prefs don't need a service restart
    	if ((!"night_highlight".equals(key)) &&
    		(!"dots_highlight".equals(key)) &&
    		(!"day_labels".equals(key)) &&
    		(!"plot_limit".equals(key)) &&
    		(!"sound".equals(key)) &&
    		(!"bootkick".equals(key)) &&
    		(!"keepservice".equals(key))) {
    		prefChanged = true;
    	}
    }

    private void updateTexts() {
    	String mode_sel = prefs.getString("mode", DEFAULT_MODE);
    	mode.setSummary(mode_sel);
    	mode_screen.setSummary(mode_sel);
    	this.onContentChanged();
    	if ("Serveur".equals(mode_sel)) {
    		ip_address.setEnabled(false);
    		rfx_port.setEnabled(true);
    		client_poll.setEnabled(false);
    	} else {
    		ip_address.setEnabled(true);
    		rfx_port.setEnabled(false);
    		client_poll.setEnabled(true);
    	}

        ip_address.setSummary(prefs.getString("ip_address", DEFAULT_IP)); 
        port.setSummary(String.valueOf(prefs.getInt("port.int", DEFAULT_PORT)));
        rfx_port.setSummary(String.valueOf(prefs.getInt("rfx_port.int", DEFAULT_RFX_PORT)));
        
    	int minutes = prefs.getInt("client_poll.int", DEFAULT_CLIENT_POLL);
    	String msg = String.format(getString(R.string.pref_poll_summary), minutes);
    	client_poll.setSummary(msg);
  
    	int days = prefs.getInt("loglimit.int", DEFAULT_LOGLIMIT);
    	msg = String.format(getString(R.string.pref_storelimit_summary), days);
    	loglimit.setSummary(msg);
    	
    	minutes = prefs.getInt("sncf_poll.int", DEFAULT_SNCF_POLL);
    	msg = String.format(getString(R.string.pref_poll_summary), minutes);
    	sncf_poll.setSummary(msg);
    	
    	String gare_sel = prefs.getString("gare", DEFAULT_GARE);
    	gare.setSummary(gare_sel);

    	minutes = prefs.getInt("meteo_poll.int", DEFAULT_METEO_POLL);
    	msg = String.format(getString(R.string.pref_meteo_summary), minutes);
    	meteo_poll.setSummary(msg);
    	
    	days = prefs.getInt("plot_limit.int", DEFAULT_PLOTLIMIT);
    	msg = String.format(getString(R.string.pref_plotlimit_summary), days);
    	plotlimit.setSummary(msg);

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
