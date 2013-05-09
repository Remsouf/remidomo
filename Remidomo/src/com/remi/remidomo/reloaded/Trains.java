package com.remi.remidomo.reloaded;

import java.net.URI;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import com.remi.remidomo.reloaded.prefs.PrefsTrain;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class Trains {
	
	private final static String TAG = RDService.class.getSimpleName();

	private final static int NB_TRAINS = 3;
	private final static String SNCF_URL = "http://sncf.mobi/infotrafic/iphoneapp/ddge/?gare=";
	
    private ArrayList<TrainData> trainsData = new ArrayList<TrainData>();
	private Date lastUpdate = null;
	
	public static class TrainData {
		public String heure;
		public boolean status;
		public String info;
	}
	
	private JSONObject getStationData(RDService service, String gare) {
        try {
        	HttpClient client = new DefaultHttpClient();
        	HttpGet request = new HttpGet();

        	request.setURI(new URI(SNCF_URL + gare));
        	String content = client.execute(request, new BasicResponseHandler());
        	JSONObject entries = new JSONObject(content);
        	return entries;
        } catch (java.net.URISyntaxException e) {
        	service.addLog("Erreur URI SNCF: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
        	Log.e(TAG, "Bad URI: " + SNCF_URL+gare);
        } catch (org.apache.http.client.ClientProtocolException e) {
        	service.addLog("Erreur protocole SNCF: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
        	Log.e(TAG, "ClientProtocolException with URI: " + SNCF_URL + gare + ", " + e);
        } catch (java.io.IOException e) {
        	service.addLog("Erreur I/O SNCF: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
        	Log.e(TAG, "IOException with URI: " + SNCF_URL + gare + ", " + e);
        } catch (org.json.JSONException e) {
        	service.addLog("Erreur JSON SNCF", RDService.LogLevel.HIGH);
        	Log.e(TAG, "JSON error with URI: " + SNCF_URL + gare + ", " + e);
        }

        return new JSONObject();
	}
	
	public static JSONObject filterCommonData(JSONObject departs, JSONObject arrivals) {
		// Return only D from initial object, keeping only entries
		// with a train ID that exists in arrivals.
		JSONObject filtered = new JSONObject();
		try {
			
			JSONArray depArray = departs.getJSONArray("D");
			JSONArray arrArray = arrivals.getJSONArray("A");

			JSONArray newArray = new JSONArray();
			for (int i=0; i<depArray.length(); i++) {
				JSONObject depEntry = depArray.getJSONObject(i);
				String trainId = depEntry.getString("num");
				
				for (int j=0; j<arrArray.length(); j++) {
					JSONObject arrEntry = arrArray.getJSONObject(j);
					if (trainId.equals(arrEntry.getString("num"))) {
						// Ok, Id match: add to result array
						newArray.put(depEntry);
						break;
					}
				}
			}
			filtered.put("D", newArray);
		} catch (org.json.JSONException e) {
			Log.e(TAG, "JSON error filtering data: " + e);
			filtered = departs;
		}

		return filtered;
	}
	
	public void updateData(RDService service) {
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(service);
		String gare = prefs.getString("gare", PrefsTrain.DEFAULT_GARE);

		JSONObject gareGOC = getStationData(service, "GOC");
		JSONObject gareGRE = getStationData(service, "GRE");
		
		JSONObject filtered;
		if ("GOC".equals(gare)) {
			filtered = filterCommonData(gareGOC, gareGRE);
		} else if ("GRE".equals(gare)){
			filtered = filterCommonData(gareGRE, gareGOC);
		} else {
			Log.e(TAG, "No common trains !");
			service.addLog("Pas de trains en commun entre les 2 gares", RDService.LogLevel.MEDIUM);
			return;
		}

		try {
			JSONArray departs = filtered.getJSONArray("D");
			if (departs.length() == 0) {
				service.addLog("Infos SNCF vides", RDService.LogLevel.MEDIUM);
				Log.d(TAG, "Empty JSON data from SNCF");
				return;
			} else {
				ArrayList<TrainData> newTrains = new ArrayList<TrainData>();
				int maxNbTrains = Math.min(NB_TRAINS, departs.length());
				for (int i=0; i<maxNbTrains; i++) {
					JSONObject obj = departs.getJSONObject(i);
					String tstamp = obj.getString("heure");
					String etat = obj.getString("etat");
					String retard = obj.getString("retard");

					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.FRENCH);
					ParsePosition pos = new ParsePosition(0);
					Date date = sdf.parse(tstamp, pos);        			        			
					SimpleDateFormat hr = new SimpleDateFormat("HH:mm");
					String heure = hr.format(date);

					TrainData new_data = new TrainData();
					new_data.heure = heure;
					new_data.status = ((etat.length() == 0) && (retard.length() == 0));
					if ("SUP".equals(etat)) {
						new_data.info = service.getString(R.string.supprime);
					} else if (retard.length() == 4) {
						new_data.info = "+" + retard.charAt(1) + ":" + retard.charAt(2) + retard.charAt(3);
					} else {
						new_data.info = null;
					}

					newTrains.add(new_data);
				}

				if (!newTrains.isEmpty()) {
					trainsData = newTrains;
				}

				service.addLog("Mise à jour des données SNCF (" + newTrains.size() + " trains)", RDService.LogLevel.UPDATE);

				if (!trainsData.isEmpty()) {
					lastUpdate = new Date();
				}
			}
		} catch (org.json.JSONException e) {
        	service.addLog("Erreur JSON SNCF", RDService.LogLevel.HIGH);
        	Log.e(TAG, "JSON error with URI: " + SNCF_URL + gare + ", " + e);
        }
	}
	
	public Date getLastUpdate() {
		return lastUpdate;
	}
	
	public final ArrayList<TrainData> getData() {
		return trainsData;
	}
}