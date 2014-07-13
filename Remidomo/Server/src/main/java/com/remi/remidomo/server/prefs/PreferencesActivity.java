package com.remi.remidomo.server.prefs;

import java.util.List;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import com.remi.remidomo.server.R;
import com.remi.remidomo.server.RDService;

public class PreferencesActivity extends PreferenceActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_service);
        addPreferencesFromResource(R.xml.pref_general);
        addPreferencesFromResource(R.xml.pref_train);
        addPreferencesFromResource(R.xml.pref_meteo);
        addPreferencesFromResource(R.xml.pref_notif);
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
}