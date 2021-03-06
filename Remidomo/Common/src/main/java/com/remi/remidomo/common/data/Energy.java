package com.remi.remidomo.common.data;

import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.Date;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.remi.remidomo.common.BaseService;
import com.remi.remidomo.common.PushSender;
import com.remi.remidomo.common.R;

public class Energy {

	private final static String TAG = "Remidomo-Common";

	// Hard-coded values
	public final static String ID_POWER = "elec2_0xf082-power";
	public final static String ID_ENERGY = "elec2_0xf082-energy";

	private final static int POWER_LOSS_THRESHOLD = 1000*15; // 15s

    private final BaseService service;

    private boolean readyForUpdates;

    private SharedPreferences prefs;

    private SensorData power = null;

    private SensorData energy = null;
    private float initialEnergy = -1.0f;
    private Date initialTstamp = null;

    private boolean powerStatus;
    private long powerLossTimestamp = 0;

	public Energy(BaseService service) {
		this.service = service;

		updatePowerStatus();

        new Thread(new Runnable() {
        	public synchronized void run() {

        		prefs = PreferenceManager.getDefaultSharedPreferences(Energy.this.service);
        		initialEnergy = prefs.getFloat("initial_energy", -1.0f);
        		initialTstamp = new Date(prefs.getLong("initial_tstamp", new Date().getTime()));

        		// Fetch data for all sensors
        		Energy.this.service.addLog("Lecture des données d'énergie locales");

        		readyForUpdates = false;
        		// (Update view everytime a new temp file was read)
                power = new SensorData(ID_POWER, Energy.this.service, true);
                energy = new SensorData(ID_ENERGY, Energy.this.service, true);
                if (Energy.this.service.callback != null) {
                	Energy.this.service.callback.updateEnergy();
        		}
                Energy.this.service.addLog("Lecture d'énergie terminée", BaseService.LogLevel.UPDATE);
                readyForUpdates = true;
        	};
        }).start();
	}

	public boolean isReadyForUpdates() {
		// For JUnit tests
		return readyForUpdates;
	}

    public SensorData getPowerData() {
    	return power;
    }

    public SensorData getEnergyData() {
    	return energy;
    }

    public float getEnergyValue() {
    	if ((energy != null) && (energy.size() > 0) && (initialEnergy >= 0.0f)) {
    		return energy.getLast().value - initialEnergy;
    	} else {
    		return 0.0f;
    	}
    }

    public boolean isPoweredOn() {
    	return powerStatus;
    }

    public void updatePowerStatus(boolean status) {

        // This is called only in server context
    	powerStatus = status;

        if (powerStatus) {
            service.addLog("Alimentation électrique restaurée", BaseService.LogLevel.HIGH);
            if (powerLossTimestamp != 0) {
                long delta = new Date().getTime() - powerLossTimestamp;
                if (delta >= POWER_LOSS_THRESHOLD) {
                    int hours = (int) delta / 3600000;
                    int minutes = ((int)delta - (hours * 3600000)) / 60000;
                    String duration = "" + hours + "h" + String.format("%02d", minutes);
                    service.pushToClients(PushSender.POWER_RESTORE, 0, duration);
                }
            }
        } else {
            powerLossTimestamp = new Date().getTime();
            service.addLog("Alimentation électrique perdue", BaseService.LogLevel.HIGH);
        }
    }

    public void updatePowerStatus() {
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent batteryStatus = service.registerReceiver(null, ifilter);
		int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

		if (status != -1) {
			// 0 => On battery
			powerStatus = (status != 0);
		}
    }

    public void updateData(BaseService service, xPLMessage msg) {
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
    		power.addValue(new Date(), msg.getFloatNamedValue("current"), SensorData.CompressionType.MEAN);
    		String unit = msg.getNamedValue("units");
    		assert (unit.equals("kW"));

    		power.writeFile(SensorData.DirType.INTERNAL,
 				   		    SensorData.FileFormat.BINARY);
    	} else if ("energy".equals(type)) {
    		energy.clearData();
    		energy.addValue(new Date(), msg.getFloatNamedValue("current"));
    		String unit = msg.getNamedValue("units");
    		assert (unit.equals("kWh"));

    		energy.writeFile(SensorData.DirType.INTERNAL,
 				  		     SensorData.FileFormat.BINARY);
    	} else {
    		assert(false);
    	}
    }

    public void dumpData(OutputStreamWriter writer, long lastTstamp) throws java.io.IOException {
    	writer.write(getJSON(lastTstamp).toString());
    }

    public JSONObject getJSON(long lastTstamp) {
    	JSONObject object = new JSONObject();

    	try {
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
    		object.put("status", powerStatus);

    	} catch (org.json.JSONException ignored) {}

    	return object;
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

    public synchronized void syncWithServer(int port, String ipAddr) {
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

            /* Always enable graph in client context */
			if (true) {
				JSONArray tablePower = entries.getJSONArray("power");
				SensorData newPowerData = new SensorData(ID_POWER, service, false);
				newPowerData.readJSON(tablePower);
				if (power == null) {
					power = newPowerData;
				} else {
					power.addValuesChunk(newPowerData);
				}
			} else {
				// Clear everything possibly downloaded before
				power = new SensorData(ID_POWER, service, false);
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
					   	   BaseService.LogLevel.UPDATE);

			power.writeFile(SensorData.DirType.INTERNAL,
					 	    SensorData.FileFormat.BINARY);

			energy.writeFile(SensorData.DirType.INTERNAL,
			 	    		 SensorData.FileFormat.BINARY);

			SharedPreferences.Editor editor = prefs.edit();
	    	editor.putFloat("initial_energy", initialEnergy);
	    	editor.putLong("initial_tstamp", initialTstamp.getTime());
	    	editor.commit();

	    	powerStatus = entries.getBoolean("status");

			if (service.callback != null) {
				service.callback.updateEnergy();
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
        energy.addValue(new Date(), initialEnergy);
    }

    public synchronized JSONObject getJSONChart(int daysBack) {
    	JSONObject dict = new JSONObject();

    	long pastDate = new Date(new Date().getTime() - daysBack*SensorData.HOURS_24).getTime();

    	try {
    		JSONArray columns = new JSONArray();

    		JSONObject dates = new JSONObject();
    		dates.put("label", "dates");
    		dates.put("type", "datetime");
    		columns.put(dates);

    		JSONObject values = new JSONObject();
    		values.put("label", service.getString(R.string.power));
    		values.put("type", "number");
    		columns.put(values);

    		dict.put("cols", columns);

    		JSONArray rows = power.getJSONChart(pastDate);

    		dict.put("rows", rows);
    	} catch (org.json.JSONException e) {
			Log.d(TAG, "Failed generating JSON for charts: ", e);
		}
    	return dict;
    }
}
