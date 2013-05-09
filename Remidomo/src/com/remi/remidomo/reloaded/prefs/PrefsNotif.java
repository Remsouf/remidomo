package com.remi.remidomo.reloaded.prefs;

import com.remi.remidomo.reloaded.R;

import android.os.Bundle;
import android.preference.PreferenceFragment;;

public class PrefsNotif extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_notif);
    }

}
