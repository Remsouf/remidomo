package com.remi.remidomo.reloaded.prefs;

import java.util.List;

import com.remi.remidomo.reloaded.R;

import android.preference.PreferenceActivity;

public class PreferencesActivity extends PreferenceActivity {

	@Override
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.pref_headers, target);
	}
}