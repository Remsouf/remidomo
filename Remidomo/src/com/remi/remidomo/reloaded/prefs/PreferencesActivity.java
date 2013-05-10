package com.remi.remidomo.reloaded.prefs;

import java.util.List;

import com.remi.remidomo.reloaded.R;

import android.app.backup.BackupManager;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

public class PreferencesActivity extends PreferenceActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Enable the "back" arrow in action bar
		getActionBar().setDisplayHomeAsUpEnabled(true);
	}
	@Override
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.pref_headers, target);
	}

	@Override
	public void onPause() {
		// Trigger a backup of preferences to the cloud
		new BackupManager(this).dataChanged();

		super.onPause();
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