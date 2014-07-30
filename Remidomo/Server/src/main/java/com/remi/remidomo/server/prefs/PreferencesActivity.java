package com.remi.remidomo.server.prefs;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import com.remi.remidomo.common.BaseService;
import com.remi.remidomo.common.prefs.Defaults;
import com.remi.remidomo.common.views.CustomSpinnerPreference;
import com.remi.remidomo.server.R;
import com.remi.remidomo.server.RDService;

public class PreferencesActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private CustomSpinnerPreference rfx_port;
    private CustomSpinnerPreference port;
    private CheckBoxPreference bootkick;
    private CheckBoxPreference keepservice;
    private CustomSpinnerPreference loglimit;
    private CustomSpinnerPreference meteo_poll;
    private Preference sound_garage;
    private Preference sound_alert;
    private CustomSpinnerPreference sncf_poll;
    private ListPreference gare;

    private boolean prefChanged = false;

    private SharedPreferences prefs;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        prefs = getPreferenceManager().getDefaultSharedPreferences(this);

        bootkick = (CheckBoxPreference) findPreference("bootkick");
        keepservice = (CheckBoxPreference) findPreference("keepservice");
        port = (CustomSpinnerPreference) findPreference("port");
        rfx_port = (CustomSpinnerPreference) findPreference("rfx_port");
        loglimit = (CustomSpinnerPreference) findPreference("loglimit");
        meteo_poll = (CustomSpinnerPreference) findPreference("meteo_poll");
        sncf_poll = (CustomSpinnerPreference) findPreference("sncf_poll");
        gare = (ListPreference) findPreference("gare");
        sound_garage = (Preference) findPreference("sound_garage");
        sound_alert = (Preference) findPreference("sound_alert");

        sound_garage.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference pref) {
                String actualUri = prefs.getString("sound_garage", Defaults.DEFAULT_SOUND_GARAGE);
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(com.remi.remidomo.common.R.string.pref_sound_garage_title));
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(actualUri));
                startActivityForResult(intent, BaseService.NotifType.GARAGE.ordinal());
                return true;
            }
        });

        sound_alert.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference pref) {
                String actualUri = prefs.getString("sound_alert", Defaults.DEFAULT_SOUND_ALERT);
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(com.remi.remidomo.common.R.string.pref_sound_alert_title));
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(actualUri));
                startActivityForResult(intent, BaseService.NotifType.ALERT.ordinal());
                return true;
            }
        });

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
            Intent intent = new Intent(this, RDService.class);
            intent.putExtra("FORCE_RESTART", true);
            startService(intent);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
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
        if (("port".equals(key)) ||
            ("rfxport".equals(key))) {
            prefChanged = true;
        }
    }

    private void updateTexts() {
        int days = prefs.getInt("loglimit", Defaults.DEFAULT_LOGLIMIT);
        String msg = String.format(getString(com.remi.remidomo.common.R.string.pref_storelimit_summary), days);
        loglimit.setSummary(msg);

        int minutes = prefs.getInt("meteo_poll", Defaults.DEFAULT_METEO_POLL);
        msg = String.format(getString(com.remi.remidomo.common.R.string.pref_meteo_summary), minutes);
        meteo_poll.setSummary(msg);

        String value = prefs.getString("sound_garage", Defaults.DEFAULT_SOUND_GARAGE);
        if (Defaults.DEFAULT_SOUND_GARAGE.equals(value)) {
            sound_garage.setSummary(com.remi.remidomo.common.R.string.pref_sound_default);
        } else {
            Uri uri = Uri.parse(value);
            Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            sound_garage.setSummary(ringtone.getTitle(this));
        }

        value = prefs.getString("sound_alert", Defaults.DEFAULT_SOUND_ALERT);
        if (Defaults.DEFAULT_SOUND_ALERT.equals(value)) {
            sound_alert.setSummary(com.remi.remidomo.common.R.string.pref_sound_default);
        } else {
            Uri uri = Uri.parse(value);
            Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            sound_alert.setSummary(ringtone.getTitle(this));
        }

        minutes = prefs.getInt("sncf_poll", Defaults.DEFAULT_SNCF_POLL);
        msg = String.format(getString(com.remi.remidomo.common.R.string.pref_poll_summary), minutes);
        sncf_poll.setSummary(msg);

        String gare_sel = prefs.getString("gare", Defaults.DEFAULT_GARE);
        gare.setSummary(gare_sel);

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

        port.setSummary(String.valueOf(prefs.getInt("port", Defaults.DEFAULT_PORT)));
        rfx_port.setSummary(String.valueOf(prefs.getInt("rfx_port", Defaults.DEFAULT_RFX_PORT)));
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        // Back from ringtone chooser, save pref
        // requestCode is the one we used to launch activity
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

            SharedPreferences.Editor editor = prefs.edit();
            if (requestCode == BaseService.NotifType.GARAGE.ordinal()) {
                if (uri != null) {
                    editor.putString("sound_garage", uri.toString());
                } else {
                    editor.putString("sound_garage", Defaults.DEFAULT_SOUND_GARAGE);
                }
            } else if (requestCode == BaseService.NotifType.ALERT.ordinal()) {
                if (uri != null) {
                    editor.putString("sound_alert", uri.toString());
                } else {
                    editor.putString("sound_alert", Defaults.DEFAULT_SOUND_ALERT);
                }
            }
            editor.commit();
        }
    }
}