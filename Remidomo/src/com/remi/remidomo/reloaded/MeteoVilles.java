package com.remi.remidomo.reloaded;

import java.io.InputStream;
import java.net.URL;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;

import android.text.Html;
import android.util.Log;
import android.util.Xml;

class MeteoVilles extends Meteo {
	private final static String TAG = RDActivity.class.getSimpleName();

	private final static String LOCATION = "grenoble";
	private final static String METEO_URL = "http://data.meteo-villes.com/previsions12j.php?ville=";
	private final static String url = METEO_URL+LOCATION;

	private final static Map<Integer, Integer> resourcesMap;

	static {		
		Map<Integer, Integer> map = new HashMap<Integer, Integer> ();

		map.put(2, R.drawable.meteo_lsnow);     // averse_neige_faible
		map.put(4, R.drawable.meteo_lsnow);     // averse_neige_fondue
		map.put(6, R.drawable.meteo_hsnow);     // averse_neige_forte
		map.put(7, R.drawable.meteo_ltonerre);  // averse_orageuse
		map.put(9, R.drawable.meteo_mist);      // averse_pluie_faible
		map.put(12, R.drawable.meteo_srain);    // averse_pluie_forte
		map.put(10, R.drawable.meteo_pcloud);   // brume
		map.put(14, R.drawable.meteo_ncloud);   // couvert
		map.put(16, R.drawable.meteo_lsnow);    // neige_faible
		map.put(18, R.drawable.meteo_hfrain);   // neige_fondue
		map.put(20, R.drawable.meteo_hsnow);    // neige_forte
		map.put(22, R.drawable.meteo_cloud);    // nuageux
		map.put(23, R.drawable.meteo_ltonerre); // orage_isole
		map.put(40, R.drawable.meteo_ltonerre); // orage_isole2
		map.put(26, R.drawable.meteo_voile);    // peu_nuageux
		map.put(42, R.drawable.meteo_wind);     // vent_violent
		map.put(28, R.drawable.meteo_lrain);    // pluie_faible
		map.put(30, R.drawable.meteo_hrain);    // pluie_forte
		map.put(87, R.drawable.meteo_hfrain);   // pluie_verglas
		map.put(32, R.drawable.meteo_sunny);    // soleil
		map.put(34, R.drawable.meteo_ncloud);   // tres_nuageux
		map.put(36, R.drawable.meteo_tonerre);  // tres_orageux
	    map.put(24, R.drawable.meteo_verglas);  // verglas

		resourcesMap = Collections.unmodifiableMap(map);
	}

	@Override
	public void updateData(RDService service) {
		XmlPullFeedParser parser = new XmlPullFeedParser(service, url);
		ArrayList<MeteoData> newMeteo = parser.parse();

		if (!newMeteo.isEmpty()) {
			meteoData = newMeteo;
		}

		service.addLog("Mise à jour des données météo (" + NB_DAYS + " jours)", RDService.LogLevel.UPDATE);

		if (!meteoData.isEmpty()) {
			lastUpdate = new Date();
		}
	}

	public class XmlPullFeedParser {
	    // names of the XML tags
		static final String PREVISIONS = "previsions";
	    static final String PREVISION = "prevision";
	    static final String DATE_ISO = "dateIso";
	    static final String PICTO_MATIN = "pictoMatin";
	    static final String PICTO_APREM = "pictoApresMidi";
	    static final String TEMP_MIN_MATIN = "tempMinMatin";
	    static final String TEMP_MAX_APREM = "tempMaxApresMidi";
	    static final String COMMENTAIRE = "commentaire";
	    
	    final URL feedUrl;
	    RDService service;
	    
	    protected XmlPullFeedParser(RDService service, String feedUrl){
	        try {
	            this.feedUrl = new URL(feedUrl);
	            this.service = service;
	        } catch (java.net.MalformedURLException e) {
	            throw new RuntimeException(e);
	        }
	    }

	    protected InputStream getInputStream() {
	        try {
	            return feedUrl.openConnection().getInputStream();
	        } catch (java.io.IOException e) {
	        	Log.e(TAG, "Failed to read weather URL: " + feedUrl);
	        	service.addLog("Impossible d'obtenir les infos météo", RDService.LogLevel.HIGH);
	        	return null;
	        }
	    }
	    
	    public ArrayList<MeteoData> parse() {
	    	ArrayList<MeteoData> newMeteo = null;
	        XmlPullParser parser = Xml.newPullParser();
	        try {
	            // auto-detect the encoding from the stream
	        	InputStream is = this.getInputStream();
	        	if (is == null) {
	        		return new ArrayList<MeteoData>();
	        	}
	            parser.setInput(is, null);
	            int eventType = parser.getEventType();
	            
	            // Data being parsed
	            MeteoData currentData = null;
	            boolean done = false;
	            int pictoMatin = 0;
	            int pictoAprem = 0;
	            Date date = null;
	            float tempMinMatin = 0.0f;
	            float tempMaxAprem = 0.0f;
	            String details = null;
	            
	            while (eventType != XmlPullParser.END_DOCUMENT && !done){
	                String name = null;
	                switch (eventType){
	                    case XmlPullParser.START_DOCUMENT:
	                    	newMeteo = new ArrayList<MeteoData>();
	                        break;
	                    case XmlPullParser.START_TAG:
	                        name = parser.getName();
	                        if (PREVISION.equals(name)) {
	                        	currentData = new MeteoData();
	                        } else if (DATE_ISO.equals(name)) {
	                        	String dateIso = parser.nextText();
	                        	
	            				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
	            				ParsePosition pos = new ParsePosition(0);
	            				date = sdf.parse(dateIso, pos);

	                        } else if (PICTO_MATIN.equals(name)) {
	                        	pictoMatin = Integer.parseInt(parser.nextText());
	                        } else if (PICTO_APREM.equals(name)) {
	                        	pictoAprem = Integer.parseInt(parser.nextText());
	                        } else if (TEMP_MIN_MATIN.equals(name)) {
	                        	tempMinMatin = Float.parseFloat(parser.nextText());
	                        } else if (TEMP_MAX_APREM.equals(name)) {
	                        	tempMaxAprem = Float.parseFloat(parser.nextText());
	                        } else if (COMMENTAIRE.equals(name)) {
	                        	details = Html.fromHtml(parser.nextText()).toString();
	                        }
	                        break;
	                    case XmlPullParser.END_TAG:
	                        name = parser.getName();
	                        if (PREVISION.equals(name) && (currentData != null)) {
	                        	currentData.date = date;
	                        	currentData.resourceId = combinePictos(pictoMatin, pictoAprem);
	                        	currentData.minTemp = tempMinMatin;
	                        	currentData.maxTemp = tempMaxAprem;
	                        	currentData.details = details;
	                        	newMeteo.add(currentData);
	                        	currentData = null;
	                        } else if (PREVISIONS.equals(name)) {
	                        	done = true;
	                        }
	                        break;
	                }
	                eventType = parser.next();
	            }
	        } catch (java.net.SocketException e) {
	        	// Timeout for example
	        	return new ArrayList<MeteoData>();
	        } catch (Exception e) {
	            throw new RuntimeException(e);
	        }
	        
	        return newMeteo;
	    }
	}
	
	private int combinePictos(int pictoMatin, int pictoAprem) {
		Integer resId = resourcesMap.get(pictoAprem);
	    if (resId != null) {
	    	return resId;
	    } else {
	    	Log.d(TAG, "Unknown weather code: " + pictoAprem);
			return R.drawable.meteo_unknown;
	    }
	}
}