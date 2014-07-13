package com.remi.remidomo.common.prefs;


public class Defaults {

    // Service
    public static final boolean DEFAULT_BOOTKICK = true;
    public static final boolean DEFAULT_KEEPSERVICE = true;
    public static final int DEFAULT_PORT = 2012;
    public static final String DEFAULT_IP = "1.2.3.4";
    public static final int DEFAULT_CLIENT_POLL = 30;
    public static final int DEFAULT_RFX_PORT = 3865;

    // General
    public static final int DEFAULT_LOGLIMIT = 365;

    // Trains
    public static final int DEFAULT_SNCF_POLL = 15;
    public static final String DEFAULT_GARE = "GOC";

    // Meteo
    public static final int DEFAULT_METEO_POLL = 4;

    // Notifs (initialized in PrefsNotif.java)
    public static String DEFAULT_SOUND_GARAGE;
    public static String DEFAULT_SOUND_ALERT;

    // Plots
    public static final boolean DEFAULT_NIGHT_HIGHLIGHT = true;
    public static final boolean DEFAULT_DOTS_HIGHLIGHT = true;
    public static final boolean DEFAULT_DAY_LABELS = true;
    public static final int DEFAULT_PLOTLIMIT = 10;

    // Energy
    public static final int DEFAULT_HCHOUR = 23;
    public static final int DEFAULT_HPHOUR = 7;
    public static final int DEFAULT_ENERGYLIMIT = 2;
    public static final boolean DEFAULT_TARIF_HIGHLIGHT = true;
}
