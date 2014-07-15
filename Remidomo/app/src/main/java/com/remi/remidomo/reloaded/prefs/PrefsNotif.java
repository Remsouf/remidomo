package com.remi.remidomo.reloaded.prefs;

import com.remi.remidomo.common.BaseService;
import com.remi.remidomo.reloaded.R;
import com.remi.remidomo.common.prefs.Defaults;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class PrefsNotif extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	private Preference sound_garage;
	private Preference sound_alert;

	private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_notif);

        prefs = getPreferenceManager().getDefaultSharedPreferences(this.getActivity());

        sound_garage = (Preference) findPreference("sound_garage");
        sound_alert = (Preference) findPreference("sound_alert");

        sound_garage.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference pref) {
            	String actualUri = prefs.getString("sound_garage", Defaults.DEFAULT_SOUND_GARAGE);
            	Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            	intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            	intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pref_sound_garage_title));
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
            	intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pref_sound_alert_title));
            	intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(actualUri));
            	startActivityForResult(intent, BaseService.NotifType.ALERT.ordinal());
            	return true;
            }
        });

        Defaults.DEFAULT_SOUND_GARAGE = "android.resource://" + getActivity().getPackageName() + "/" + R.raw.garage_ok;
        Defaults.DEFAULT_SOUND_ALERT = "android.resource://" + getActivity().getPackageName() + "/" + R.raw.garage_alert;
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

    	// If pref is a hidden key, don't update texts
    	// (i.e. those committed directly by the service)
    	if (getPreferenceScreen().findPreference(key) != null) {
    		updateTexts();
    	}
    }

    private void updateTexts() {
    	String value = prefs.getString("sound_garage", Defaults.DEFAULT_SOUND_GARAGE);
    	if (Defaults.DEFAULT_SOUND_GARAGE.equals(value)) {
    		sound_garage.setSummary(R.string.pref_sound_default);
    	} else {
    		Uri uri = Uri.parse(value);
    		Ringtone ringtone = RingtoneManager.getRingtone(getActivity(), uri);
    		sound_garage.setSummary(ringtone.getTitle(getActivity()));
    	}

    	value = prefs.getString("sound_alert", Defaults.DEFAULT_SOUND_ALERT);
    	if (Defaults.DEFAULT_SOUND_ALERT.equals(value)) {
    		sound_alert.setSummary(R.string.pref_sound_default);
    	} else {
    		Uri uri = Uri.parse(value);
    		Ringtone ringtone = RingtoneManager.getRingtone(getActivity(), uri);
    		sound_alert.setSummary(ringtone.getTitle(getActivity()));
    	}
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
