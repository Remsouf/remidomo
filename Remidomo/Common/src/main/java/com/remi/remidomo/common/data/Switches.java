package com.remi.remidomo.common.data;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.remi.remidomo.common.BaseService;
import com.remi.remidomo.common.PushSender;

public class Switches {
	
	private final static String TAG = "Remidomo-Common";

	// Hardcoded values
	private final static int MAX_SWITCHES = 1;
	
	public final static ArrayList<String> SWITCH_ADDR =
			new ArrayList<String>(Arrays.asList("0x30f0f01"));

	public final static ArrayList<String> SWITCH_UNIT =
			new ArrayList<String>(Arrays.asList("1"));

	private boolean states[] = new boolean[MAX_SWITCHES];

	private SharedPreferences prefs;
	private BaseService service = null;

	public Switches(BaseService service) {
		this.service = service;
		
		new Thread(new Runnable() {
        	public synchronized void run() {
        		prefs = PreferenceManager.getDefaultSharedPreferences(Switches.this.service);
        		assert (SWITCH_ADDR.size() == MAX_SWITCHES);
        		assert (SWITCH_UNIT.size() == MAX_SWITCHES);

        		for (int i=0; i<MAX_SWITCHES; i++) {
        			boolean state = prefs.getBoolean("switch_" + i, false);
        			states[i] = state;
        		}
        	}
		}).start();
	}

	public boolean toggle(int index, int serverPort, String serverIpAddr) {
		// Called when a button is pressed in activity
		if ((index >= 0) && (index < MAX_SWITCHES)) {
			states[index] = ! states[index];
		
        	sendServerMessage(index, states[index], serverPort, serverIpAddr);

	        SharedPreferences.Editor editor = prefs.edit();
	        editor.putBoolean("switch_" + index, states[index]);
	        editor.commit();

			return true;
		} else {
			return false;
		}
	}

    public boolean toggle(int index, int rfxPort, DatagramSocket rfxSocket) {
        // Called when a button is pressed in activity (server)
        if ((index >= 0) && (index < MAX_SWITCHES)) {
            states[index] = ! states[index];

            sendRfxMessage(index, states[index], rfxPort, rfxSocket);
            service.pushToClients(PushSender.SWITCH, index, Boolean.toString(states[index]));

            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("switch_" + index, states[index]);
            editor.commit();

            return true;
        } else {
            return false;
        }
    }

	public boolean setState(int index, boolean state, int port, DatagramSocket rfxSocket) {
		// Called when an http request is received
		if ((index >= 0) && (index < MAX_SWITCHES)) {
			states[index] = state;
		} else {
			return false;
		}
		
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("switch_" + index, states[index]);
        editor.commit();

        if (rfxSocket != null) {
        	sendRfxMessage(index, states[index], port, rfxSocket);
        }

        // Retour d'etat ?

		if (service.callback != null) {
			service.callback.updateSwitches();
		}

		// Also update clients
		// (in addition to the one sending the request)
		service.pushToClients(PushSender.SWITCH, index, Boolean.toString(state));

		return true;
	}

	public void setFromPushedIntent(Intent intent) {
		String index = intent.getStringExtra(PushSender.ID);
		String state = intent.getStringExtra(PushSender.STATE);
		setState(Integer.parseInt(index), Boolean.parseBoolean(state), 0, null);
	}
	
	public boolean getState(int index) {
		return states[index];
	}

	public JSONArray getJSONArray() {
		try {
			JSONArray array = new JSONArray();
			for (int i=0; i<MAX_SWITCHES; i++) {
				array.put(i, states[i]);
			}
			return array;
		} catch (org.json.JSONException e) {
			Log.e(TAG, "Failed creating JSON data for switches");
			return null;
		}
	}

	private void sendRfxMessage(final int index, final boolean state, final int port, final DatagramSocket rfxSocket) {
		if ((service == null) || (rfxSocket == null)) {
			Log.e(TAG, "RFX socket not opened. Impossible to send");
			if (service != null) {
				service.addLog("Socket RFX pas ouvert: impossible d'envoyer des commandes", BaseService.LogLevel.HIGH);
			}
			return;
		}

   		new Thread(new Runnable() {
			public void run() {

				//int port = prefs.getInt("rfx_port", PrefsService.DEFAULT_RFX_PORT);
				InetAddress destination;
				try {
					// Could be ip address of RFX-Lan... save pref !
					destination = InetAddress.getByName("255.255.255.255");
				} catch (java.net.UnknownHostException e) {
					Log.e(TAG, "Unknown host for sending RFX message");
					service.addLog("Hote RFX inconnu", BaseService.LogLevel.HIGH);
					return;
				}

				String data = "xpl-cmnd\n{\n";
				data = data + "hop=1\n";
				data = data + "source=RDService\n";
				data = data + "target=*\n}\n";

				// data = data + "hbeat.request\n{\ncommand=request\n}\n";
				data = data + "ac.basic\n{\naddress=" + SWITCH_ADDR.get(index) + "\n";
				data = data + "unit=" + SWITCH_UNIT.get(index) + "\n";
				data = data + "command=";
				if (state) {
					data = data + "on\n";
				} else {
					data = data + "off\n";
				}
				data = data + "}\n";

				byte [] buffer = data.getBytes();
				try {
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length, destination, port);
                    rfxSocket.send(packet);
					service.addLog("Packet RFX envoyé");
					Log.i(TAG, "RFX packet sent");
				} catch (Exception e) {
					Log.e(TAG, "Error sending: " + e.getLocalizedMessage());
					service.addLog("Erreur socket RFX (tx): " + e.getLocalizedMessage(), BaseService.LogLevel.HIGH);
					service.errorLeds();
				}
			}
		}).start();
	}

	public void syncWithHardware(xPLMessage msg) {
		String address = msg.getNamedValue("address");
		String unit = msg.getNamedValue("unit");
		String command = msg.getNamedValue("command");
		
		// Trim parens (if any)
		if (address.startsWith("(") && address.endsWith(")")) {
			address = address.substring(1, address.length()-1);
		}
		if (unit.startsWith("(") && unit.endsWith(")")) {
			unit = unit.substring(1, unit.length()-1);
		}

		int i = SWITCH_ADDR.indexOf(address);
		if (i == -1) {
			Log.e(TAG, "AC address unknown: " + address);
			service.addLog("Message AC provenant d'une adresse inconnue: " + address, BaseService.LogLevel.HIGH);
			return;
		}
		
		if (!SWITCH_UNIT.get(i).equals(unit)) {
			Log.e(TAG, "AC unit and address known but not consistent (" + address + "/" + unit + ")");
			service.addLog("Message AC provenant d'une adresse incohérente avec l'unité (" + address + "/" + unit + ")", BaseService.LogLevel.HIGH);
			return;			
		}
		
		if ("on".equals(command)) {
			states[i] = true;
		} else if ("off".equals(command)) {
			states[i] = false;
		} else {
			Log.e(TAG, "AC command unknown: " + command);
			service.addLog("Message AC avec commande inconnue :" + command, BaseService.LogLevel.HIGH);
		}
		
		Log.i(TAG, "Switches state updated from hardware");
		service.addLog("Commandes synchronisées avec le matériel", BaseService.LogLevel.UPDATE);
		if (service.callback != null) {
			service.callback.updateSwitches();
		}
	}

	public synchronized void syncWithServer(int port, String ipAddr) {
		//int port = prefs.getInt("port", PrefsService.DEFAULT_PORT);
		//String ipAddr = prefs.getString("ip_address", PrefsService.DEFAULT_IP);
		Log.d(TAG, "Client switches connecting to " + ipAddr + ":" + port);
		service.addLog("Connexion au serveur " + ipAddr + ":" + port + " (MAJ commandes)");

		try {
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();

			request.setURI(new URI(ipAddr+":"+port+"/switches/query"));
			String content = client.execute(request, new BasicResponseHandler());
			JSONArray array = new JSONArray(content);
			for (int i=0; i<MAX_SWITCHES; i++) {
				if (i<array.length()) {
					states[i] = array.getBoolean(i);
				}
			}
			service.addLog("Mise à jour des états commandes depuis le serveur", BaseService.LogLevel.UPDATE);
			if (service.callback != null) {
				service.callback.updateSwitches();
			}
		} catch (java.net.URISyntaxException e) {
			service.addLog("Erreur URI serveur: " + e.getLocalizedMessage(), BaseService.LogLevel.HIGH);
			Log.e(TAG, "Bad server URI");
		} catch (org.apache.http.conn.HttpHostConnectException e) {
			service.addLog("Impossible de se connecter au serveur: " + e.getLocalizedMessage(), BaseService.LogLevel.HIGH);
			Log.e(TAG, "HostConnectException with server: " + e);
		} catch (org.apache.http.client.ClientProtocolException e) {
			service.addLog("Erreur protocole serveur", BaseService.LogLevel.HIGH);
			Log.e(TAG, "ClientProtocolException with server: " + e);
		} catch (java.net.SocketException e) {
			service.addLog("Serveur non joignable: " + e.getLocalizedMessage(), BaseService.LogLevel.HIGH);
			Log.e(TAG, "SocketException with client: " + e);
		} catch (java.io.IOException e) {
			service.addLog("Erreur I/O client: " + e.getLocalizedMessage(), BaseService.LogLevel.HIGH);
			Log.e(TAG, "IOException with client: " + e);
		} catch (org.json.JSONException e) {
			service.addLog("Erreur JSON serveur", BaseService.LogLevel.HIGH);
			Log.e(TAG, "JSON error with server: " + e);
		}
	}

	public void sendServerMessage(final int index, final boolean state, final int port, final String ipAddr) {
		new Thread(new Runnable() {
			public void run() {

				String url = ipAddr + ":" + port + "/switches/toggle?id=" + index + "&cmd=";
				if (state) {
					url = url + "on";
				} else {
					url = url + "off";
				}

				try {
					HttpClient client = new DefaultHttpClient();
					HttpGet request = new HttpGet();

					request.setURI(new URI(url));
					String content = client.execute(request, new BasicResponseHandler());

					service.addLog("Envoi commande au serveur");
					if (!"OK".equals(content)) {
						Log.d(TAG, "Bad response to command URL: " + content);
						service.addLog("Mauvaise réponse du serveur: " + content, BaseService.LogLevel.HIGH);
					}
				} catch (java.net.URISyntaxException e) {
					Log.e(TAG, "Bad URI: " + url);
				} catch (org.apache.http.client.ClientProtocolException e) {
					Log.e(TAG, "ClientProtocolException with URI: " + url + ", " + e);
				} catch (java.io.IOException e) {
					Log.e(TAG, "IOException with URI: " + url + ", " + e);
				}
			}
		}).start();
	}
}
