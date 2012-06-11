package com.remi.remidomo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;

import com.remi.remidomo.RDService.LogLevel;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class Doors {
	
	private final static String TAG = RDService.class.getSimpleName();

	// Hardcoded values
	private final static int MAX_DOORS = 1;

	private SharedPreferences prefs;
	private RDService service = null;

	public enum State { OPENED,
						CLOSED,
						MOVING,
						UNKNOWN };

	private State states[];

	// Specific to portail !
	public final static int GARAGE = 0;
	public final static int GARAGE_DOOR_DELAY = 23000; // ms
	
	private boolean garageStates[] = {false, false};

	// 1st=closed, 2nd=opened
	public final static ArrayList<String> GARAGE_ADDR =
			new ArrayList<String>(Arrays.asList("0xb5baf7", "0x0f00d9"));
	
	public Doors(RDService service) {
		this.service = service;
		prefs = PreferenceManager.getDefaultSharedPreferences(service);

		states = new State[MAX_DOORS];
		for (int i=0; i<MAX_DOORS; i++) {
			String state = prefs.getString("door_" + i, State.UNKNOWN.toString());
			states[i] = State.valueOf(state);
		}

		if (states[GARAGE] == State.MOVING) {
			garageStates[0] = true;
			garageStates[1] = true;
		} else if (states[GARAGE] == State.OPENED) {
			garageStates[0] = true;
			garageStates[1] = false;
		} else if (states[GARAGE] == State.CLOSED) {
			garageStates[0] = false;
			garageStates[1] = true;
		} else {
			garageStates[0] = false;
			garageStates[1] = false;
		}

		if (service.callback != null) {
			service.callback.updateDoors();
		}
	}
	
	public State getState(int i) {
		return states[i];
	}

	public void setState(int index, State state, boolean enable_alert) {
		setState(index, state, enable_alert, new Date());
	}
	
	public void setState(int index, State state, boolean enable_alert, Date tstamp) {
		states[index] = state;

		SharedPreferences.Editor editor = prefs.edit();
        editor.putString("door_" + index, states[index].toString());
        editor.commit();

        if (service.callback != null) {
			service.callback.updateDoors();
		}
        
		// Play sound if transitioning to opened/closed,
        // and if not server
        String mode = prefs.getString("mode", Preferences.DEFAULT_MODE);
		if ("Client".equals(mode) && enable_alert) {
			if (index == GARAGE) {
				if (states[GARAGE] == State.CLOSED) {
					service.showAlertNotification(service.getString(R.string.garage_closed),
												  R.raw.garage_ok, tstamp);
				} else if (states[GARAGE] == State.OPENED) {
					service.showAlertNotification(service.getString(R.string.garage_opened),
												  R.raw.garage_ok, tstamp);
				}
			}
		}
	}

	public void setFromPushedIntent(Intent intent) {
		String index = intent.getStringExtra(PushSender.ID);
		State state = State.valueOf(intent.getStringExtra(PushSender.STATE));
		Date tstamp = new Date(Long.parseLong(intent.getStringExtra(PushSender.TSTAMP)));
		setState(Integer.parseInt(index), state, true, tstamp);

		if (state == State.MOVING) {
			// Client is notified of moving state only
			// if it's not temporary. In that case, it's an
			// anomaly !
			service.showAlertNotification(service.getString(R.string.garage_moving),
									      R.raw.garage_alert, tstamp);
		}
	}
	
	public synchronized void syncWithServer() {
		int port = Integer.parseInt(prefs.getString("port", Preferences.DEFAULT_PORT));
		String ipAddr = prefs.getString("ip_address", Preferences.DEFAULT_IP);
		Log.d(TAG, "Client switches connecting to " + ipAddr + ":" + port);
		service.addLog("Connexion au serveur " + ipAddr + ":" + port + " (MAJ portes)");

		try {
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();

			request.setURI(new URI("http://"+ipAddr+":"+port+"/doors"));
			String content = client.execute(request, new BasicResponseHandler());
			JSONArray array = new JSONArray(content);
			for (int i=0; i<MAX_DOORS; i++) {
				if (i<array.length()) {
					String stateStr = array.getString(i);
					setState(i, State.valueOf(stateStr), false);
				}
			}
			service.addLog("Mise à jour des états portes depuis le serveur", RDService.LogLevel.UPDATE);
			if (service.callback != null) {
				service.callback.updateDoors();
			}
		} catch (java.net.URISyntaxException e) {
			service.addLog("Erreur URI serveur: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
			Log.e(TAG, "Bad server URI");
		} catch (org.apache.http.conn.HttpHostConnectException e) {
			service.addLog("Impossible de se connecter au serveur: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
			Log.e(TAG, "HostConnectException with server: " + e);
		} catch (org.apache.http.client.ClientProtocolException e) {
			service.addLog("Erreur protocole serveur", RDService.LogLevel.HIGH);
			Log.e(TAG, "ClientProtocolException with server: " + e);
		} catch (java.net.SocketException e) {
			service.addLog("Serveur non joignable: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
			Log.e(TAG, "SocketException with client: " + e);
		} catch (java.io.IOException e) {
			service.addLog("Erreur I/O client: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
			Log.e(TAG, "IOException with client: " + e);
		} catch (org.json.JSONException e) {
			service.addLog("Erreur JSON serveur", RDService.LogLevel.HIGH);
			Log.e(TAG, "JSON error with server: " + e);
		}
	}
	
	public JSONArray getJSONArray() {
		try {
			JSONArray array = new JSONArray();
			for (int i=0; i<MAX_DOORS; i++) {
				array.put(i, states[i].toString());
			}
			return array;
		} catch (org.json.JSONException e) {
			Log.e(TAG, "Failed creating JSON data for doors");
			return null;
		}
	}
	
	public void syncWithHardware(xPLMessage msg) {
		String address = msg.getNamedValue("device");
		// unused String unit = msg.getNamedValue("unit");
		String command = msg.getNamedValue("command");
		
		// Trim parens (if any)
		if (address.startsWith("(") && address.endsWith(")")) {
			address = address.substring(1, address.length()-1);
		}

		int i = GARAGE_ADDR.indexOf(address);
		if (i == -1) {
			Log.e(TAG, "Door address unknown: " + address);
			service.addLog("Message portes provenant d'une adresse inconnue: " + address, RDService.LogLevel.HIGH);
			return;
		}

		if ("alert".equals(command)) {
			garageStates[i] = true;
		} else if ("normal".equals(command)) {
			garageStates[i] = false;
		} else {
			Log.e(TAG, "Doors command unknown: " + command);
			service.addLog("Message portes avec commande inconnue :" + command, RDService.LogLevel.HIGH);
		}
		
		// Handle garage door states -> one state
		State newState = State.UNKNOWN;
		if (garageStates[0] && garageStates[1]) {
			newState = State.MOVING;
		} else if (garageStates[0] && !garageStates[1]) {
			newState = State.OPENED;
		} else if (!garageStates[0] && garageStates[1]) {
			newState = State.CLOSED;
		} else {
			Log.e(TAG, "Something wrong with garage gate: " + garageStates[0] + "/" + garageStates[1]);
			service.addLog("Anomalie porte de garage: " + garageStates[0] + "/" + garageStates[1], RDService.LogLevel.HIGH);
		}
	
		Log.i(TAG, "Doors state updated from hardware: " + newState);
		service.addLog("Portes synchronisées avec le matériel", RDService.LogLevel.UPDATE);

		if (newState != states[GARAGE]) {
			setState(GARAGE, newState, true);
			if ((newState == State.CLOSED) || (newState == State.OPENED)) {
				service.pushToClients("door", GARAGE, newState.toString());
			} else if (newState == State.MOVING) {
				// TODO: NOT RELIABLE
				//Timer doorTimer = new Timer();
				//Date later = new Date(new Date().getTime() + GARAGE_DOOR_DELAY);
				//doorTimer.schedule(new DoorTask(), later);
			}
		}
	}
	
	class DoorTask extends TimerTask {
		@Override
		public void run() {
			if (states[GARAGE] == State.MOVING) {
				// If still not opened/closed after a delay
				// there's something wrong.
				service.pushToClients("door", GARAGE, states[GARAGE].toString());
			}
		}
	}
}