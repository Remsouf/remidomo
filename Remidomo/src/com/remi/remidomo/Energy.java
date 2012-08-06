package com.remi.remidomo;

import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.Date;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

class Energy {

	private final static String TAG = RDService.class.getSimpleName();

	// Hard-coded values
	public final static String ID_POWER = "elec2_0xf082-power";
	public final static String ID_ENERGY = "elec2_0xf082-energy";

    private final RDService service;

    private boolean readyForUpdates;

    private SharedPreferences prefs;

    private SensorData power = null;

    private SensorData energy = null;
    private float initialEnergy = -1.0f;
    private Date initialTstamp = null;

	public Energy(RDService service) {
		this.service = service;

		prefs = PreferenceManager.getDefaultSharedPreferences(service);
		initialEnergy = prefs.getFloat("initial_energy", -1.0f);
		initialTstamp = new Date(prefs.getLong("initial_tstamp", new Date().getTime()));

		// Fetch data for all sensors
		// (threaded)
        this.service.addLog("Lecture des données d'énergie locales");
        new Thread(new Runnable() {
        	public synchronized void run() {
        		readyForUpdates = false;
        		// (Update view everytime a new temp file was read)
                power = new SensorData(ID_POWER, Energy.this.service, true);
                energy = new SensorData(ID_ENERGY, Energy.this.service, true);
                if (Energy.this.service.callback != null) {
                	Energy.this.service.callback.updateEnergy();
        		}
                Energy.this.service.addLog("Lecture d'énergie terminée", RDService.LogLevel.UPDATE);
                readyForUpdates = true;
        	};
        }).start();
	}

    public SensorData getPowerData() {
    	return power;
    }

    public float getEnergyValue() {
    	if ((energy != null) && (energy.size() > 0)) {
    		return energy.getLast().value - initialEnergy;
    	} else {
    		return 0.0f;
    	}
    }

    public void updateData(RDService service, xPLMessage msg) {
    	// Ignore msg if we're still reading values from files
    	if (!readyForUpdates) {
    		return;
    	}

    	String type = msg.getNamedValue("type");
    	String device = msg.getNamedValue("device");
    	device = device.replace(' ', '_');
    	device = device + "-" + type;

    	if (service != null) {
    		service.blinkLeds();
    	}

    	if ("power".equals(type)) {
    		power.addValue(msg.getFloatNamedValue("current"));
    		String unit = msg.getNamedValue("units");
    		assert (unit.equals("kW"));

    		power.writeFile(SensorData.DirType.INTERNAL,
 				   		    SensorData.FileFormat.BINARY);
    	} else if ("energy".equals(type)) {
    		energy.clearData();
    		energy.addValue(msg.getFloatNamedValue("current"));
    		String unit = msg.getNamedValue("units");
    		assert (unit.equals("kWh"));

    		energy.writeFile(SensorData.DirType.INTERNAL,
 				  		     SensorData.FileFormat.BINARY);
    	} else {
    		assert(false);
    	}
    }

    public void dumpData(OutputStreamWriter writer, long lastTstamp) throws java.io.IOException {
    	try {
    		JSONObject object = new JSONObject();

    		JSONArray arrayPower = power.getJSONArray(lastTstamp);
    		if (arrayPower != null) {
    			object.put("power", arrayPower);
    		}

    		// Always provide last known value,
    		// ignoring time stamp
    		JSONArray arrayEnergy = energy.getJSONArray(0);
    		if (arrayEnergy != null) {
    			object.put("energy", arrayEnergy);
    		}

    		object.put("initial_energy", initialEnergy);
    		object.put("initial_tstamp", initialTstamp.getTime());

    		writer.write(object.toString());
    	} catch (org.json.JSONException ignored) {}
    }

    public Date getLastUpdate() {
    	if (power != null) {
    		return power.lastUpdate;
    	} else {
    		return null;
    	}
    }

    public Date getLastEnergyResetDate() {
    	return initialTstamp;
    }

    public synchronized void syncWithServer() {
		int port = Integer.parseInt(prefs.getString("port", Preferences.DEFAULT_PORT));
		String ipAddr = prefs.getString("ip_address", Preferences.DEFAULT_IP);
		Log.d(TAG, "Client Thread connecting to " + ipAddr + ":" + port);
		service.addLog("Connexion au serveur " + ipAddr + ":" + port + " (MAJ sondes)");

		try {
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();

			String uri = ipAddr+":"+port+"/energy";
			Date lastTstamp = service.getEnergy().getLastUpdate();
			if (lastTstamp != null) {
				uri = uri + "?last="+lastTstamp.getTime();
			}
			request.setURI(new URI(uri));
			String content = client.execute(request, new BasicResponseHandler());
			JSONObject entries = new JSONObject(content);

			JSONArray tablePower = entries.getJSONArray("power");
			SensorData newPowerData = new SensorData(ID_POWER, service, false);
			newPowerData.readJSON(tablePower);
			if (power == null) {
				power = newPowerData;
			} else {
				power.addValuesChunk(newPowerData);
			}

			JSONArray tableEnergy = entries.getJSONArray("energy");
			SensorData newEnergyData = new SensorData(ID_ENERGY, service, false);
			newEnergyData.readJSON(tableEnergy);

			// Always replace, as we're not accumulating
			// values over time
			energy = newEnergyData;

			double initial = entries.getDouble("initial_energy");
			initialEnergy = (float) initial;

			initialTstamp = new Date(entries.getLong("initial_tstamp"));

			service.addLog("Mise à jour des données de '" + ID_POWER + "' depuis le serveur (" + newEnergyData.size() + " nvx points)",
					   	   RDService.LogLevel.UPDATE);

			power.writeFile(SensorData.DirType.INTERNAL,
					 	    SensorData.FileFormat.BINARY);

			energy.writeFile(SensorData.DirType.INTERNAL,
			 	    		 SensorData.FileFormat.BINARY);

			SharedPreferences.Editor editor = prefs.edit();
	    	editor.putFloat("initial_energy", initialEnergy);
	    	editor.putLong("initial_tstamp", initialTstamp.getTime());
	    	editor.commit();

			if (service.callback != null) {
				service.callback.updateEnergy();
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

    public void saveToSdcard() {
    	power.writeFile(SensorData.DirType.SDCARD, SensorData.FileFormat.ASCII);
    	energy.writeFile(SensorData.DirType.SDCARD, SensorData.FileFormat.ASCII);
    }

    public void readFromSdcard() {
        power.readFile(SensorData.DirType.SDCARD);
        energy.readFile(SensorData.DirType.SDCARD);
    }

    public void resetEnergyCounter() {
    	initialEnergy = energy.getLast().value;
    	initialTstamp = new Date();
    	SharedPreferences.Editor editor = prefs.edit();
    	editor.putFloat("initial_energy", initialEnergy);
    	editor.putLong("initial_tstamp", initialTstamp.getTime());
        editor.commit();

        energy.clearData();
        energy.addValue(initialEnergy);
    }
}