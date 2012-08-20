package com.remi.remidomo;

import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

class Sensors {
	
	private final static String TAG = RDService.class.getSimpleName();

	// Hard-coded values
	public final static String ID_EXT_T = "th2_0x9b05-temp";
	public final static String ID_EXT_H = "th2_0x9b05-humidity";
	public final static String ID_POOL_T = "temp3-temp";
	public final static String ID_VERANDA_T = "th1_0xe902-temp";
	public final static String ID_VERANDA_H = "th1_0xe902-humidity";

	public final static ArrayList<String> IGNORED =
			new ArrayList<String>(Arrays.asList("temp4_0xc01-temp"));

    private ArrayList<SensorData> sensors = new ArrayList<SensorData>();

    private final RDService service;
    
    private boolean readyForUpdates;
    private boolean warnedAboutMissingSensor = false;

    private SharedPreferences prefs;
    private NotificationManager notificationMgr;

    // Unique Identification Number for the Notification.
    private final int NOTIFICATION_RESTORE = 2;
    
    // Time in minutes before warning that a sensor
    // stopped being updated.
    private final static long WARN_MISSING_MINS = 60;

	public Sensors(RDService service) {
		this.service = service;
		
		prefs = PreferenceManager.getDefaultSharedPreferences(service);
		
        notificationMgr = (NotificationManager)service.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationMgr == null) {
            Log.e(TAG, "Failed to get Notification Manager");
        }

		// Fetch data for all sensors
		// (threaded)
        this.service.addLog("Lecture des données capteurs locales");
        new Thread(new Runnable() {
        	public synchronized void run() {
        		readyForUpdates = false;
        		// (Update view everytime a new temp file was read)
                sensors.add(new SensorData(ID_POOL_T, Sensors.this.service, true));
                if (Sensors.this.service.callback != null) {
                	Sensors.this.service.callback.updateThermo();
        		}
                sensors.add(new SensorData(ID_EXT_T, Sensors.this.service, true));
                sensors.add(new SensorData(ID_EXT_H, Sensors.this.service, true));
                if (Sensors.this.service.callback != null) {
                	Sensors.this.service.callback.updateThermo();
        		}
                sensors.add(new SensorData(ID_VERANDA_T, Sensors.this.service, true));
                sensors.add(new SensorData(ID_VERANDA_H, Sensors.this.service, true));
                if (Sensors.this.service.callback != null) {
                	Sensors.this.service.callback.updateThermo();
        		}
                Sensors.this.service.addLog("Lecture terminée", RDService.LogLevel.UPDATE);
                readyForUpdates = true;
                
                /* Check if all data empty */
                boolean allEmpty = true;
                for (SensorData i: sensors) {
                	if (i.size() > 0) {
                		allEmpty =false;
                	}
                }
                if (allEmpty) {
                	
                	CharSequence text = Sensors.this.service.getText(R.string.sensors_restore);

                    // Set the icon, scrolling text and timestamp
                    Notification notification = new Notification(R.drawable.app_icon, text,
                            System.currentTimeMillis());

                    // The PendingIntent to launch our activity if the user selects this notification
                    Intent intent = new Intent(Sensors.this.service, RDService.class);
                    intent.setAction(RDService.ACTION_RESTORE_DATA);
                    PendingIntent contentIntent = PendingIntent.getService(Sensors.this.service, 0,
                            												intent, 0);

                    // Set the info for the views that show in the notification panel.
                    notification.setLatestEventInfo(Sensors.this.service,
                    								Sensors.this.service.getText(R.string.sensors_empty),
                    								text, contentIntent);

                    // Auto-cancel the notification when clicked
                    notification.flags |= Notification.FLAG_AUTO_CANCEL;
                    
                    // Send the notification.
                    notificationMgr.notify(NOTIFICATION_RESTORE, notification);
                }
        	};
        }).start();
	}
	
    public SensorData getData(String name) {
    	synchronized(sensors) {
    		for (SensorData i: sensors) {
    			if (i.getName().equals(name)) {
    				return i;
    			}
    		}
    	}
    	return null;
    }
    
    public void updateDataChunk(String name, SensorData newData) {
    	synchronized(sensors) {
    		for (SensorData sensor:sensors) {
    			if (sensor.getName().equals(name)) {
    				sensor.addValuesChunk(newData);
    				return;
    			}
    		}

    		// New sensor
    		sensors.add(newData);
    	}
    }

    public void updateData(RDService service, xPLMessage msg) {
    	// Ignore msg if we're still reading values from files
    	if (!readyForUpdates) {
    		return;
    	}
    	
    	String device = msg.getNamedValue("device");

    	// Special case for temp3 (pool), which changes
    	// address after each reboot (much too often)
    	if (device.startsWith("temp3")) {
    		device = device.split(" ")[0];
    	}
    	device = device.replace(' ', '_');
    	device = device + "-" + msg.getNamedValue("type");
    	
    	if (IGNORED.contains(device)) {
    		// Device ignored
    		return;
    	}

    	if (service != null) {
    		service.blinkLeds();
    	}

    	synchronized(sensors) {
    		SensorData data = null;
    		for (SensorData i: sensors) {
    			if (i.getName().equals(device)) {
    				data = i;
    			}
    		}
    		if (data == null) {
    			data = new SensorData(device, service, true);
    			sensors.add(data);
    		}

    		data.addValue(msg.getFloatNamedValue("current"));

    		String unit = msg.getNamedValue("units");
    		assert (unit.equals("c"));
    		String type = msg.getNamedValue("type");
    		assert (type.equals("temp"));

    		data.writeFile(SensorData.DirType.INTERNAL,
    				SensorData.FileFormat.BINARY);

    		checkSensorsConsistency();
    	}
    }

    public void dumpData(OutputStreamWriter writer, long lastTstamp) throws java.io.IOException {
    	try {
    		JSONObject object = new JSONObject();
    		for (SensorData sensor: sensors) {
    			JSONArray array = sensor.getJSONArray(lastTstamp);
    			if (array != null) {
    				object.put(sensor.getName(), array);
    			}
    		}
    		writer.write(object.toString());
    	} catch (org.json.JSONException ignored) {}
    }

    public void dumpCSV(OutputStreamWriter writer) {
    	for (SensorData sensor: sensors) {
			sensor.writeCSV(writer);
		}
    }
  
    public Date getLastUpdate() {
    	Date tstamp = null;
    
    	for (SensorData i: sensors) {
    		if (i.lastUpdate != null) {
    			if (tstamp == null || (i.lastUpdate.getTime() > tstamp.getTime())) {
    				tstamp = i.lastUpdate;
    			}
    		}
    	}

    	return tstamp;
    }
    
    public synchronized void syncWithServer() {
		int port = Integer.parseInt(prefs.getString("port", Preferences.DEFAULT_PORT));
		String ipAddr = prefs.getString("ip_address", Preferences.DEFAULT_IP);
		Log.d(TAG, "Client Thread connecting to " + ipAddr + ":" + port);
		service.addLog("Connexion au serveur " + ipAddr + ":" + port + " (MAJ sondes)");

		try {
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();

			String uri = ipAddr+":"+port+"/sensors";
			Date lastTstamp = service.getSensors().getLastUpdate();
			if (lastTstamp != null) {
				uri = uri + "?last="+lastTstamp.getTime();
			}
			request.setURI(new URI(uri));
			String content = client.execute(request, new BasicResponseHandler());
			JSONObject entries = new JSONObject(content);
			Iterator<?> iter = entries.keys();
			while (iter.hasNext()) {
				String name = (String) iter.next();
				JSONArray table = entries.getJSONArray(name);
				SensorData newData = new SensorData(name, service, false);
				newData.readJSON(table);

				updateDataChunk(name, newData);
				service.addLog("Mise à jour des données de '" + name + "' depuis le serveur (" + newData.size() + " nvx points)",
							   RDService.LogLevel.UPDATE);
			}
			synchronized(sensors) {
				for (SensorData sensor: sensors) {
					sensor.writeFile(SensorData.DirType.INTERNAL,
							SensorData.FileFormat.BINARY);
				}
			}
			if (service.callback != null) {
				service.callback.updateThermo();
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
    	synchronized(sensors) {
    		for (SensorData sensor: sensors) {
    			sensor.writeFile(SensorData.DirType.SDCARD, SensorData.FileFormat.ASCII);
    		}
    	}
    }

    public void readFromSdcard() {
    	synchronized(sensors) {
    		for (SensorData sensor: sensors) {
    			sensor.readFile(SensorData.DirType.SDCARD);
    		}
    	}
    }

    private synchronized void checkSensorsConsistency() {
    	// 1st, find the most recent sensor timestamp
    	long maxTime = 0;
    	for (SensorData sensor: sensors) {
    		long tstamp = sensor.getLast().time;
    		if (tstamp > maxTime) {
    			maxTime = tstamp;
    		}
    	}

    	// Now, see if a sensor has not been updated for 15 min
    	boolean foundMissingSensor = false;
    	for (SensorData sensor: sensors) {
    		long tstamp = sensor.getLast().time;

    		if (maxTime - tstamp > WARN_MISSING_MINS*60*1000) {
    			// Note: we're necessarily in server mode,
    			// because the only caller is updateData() from xPL message
    			if ((service != null) && (!warnedAboutMissingSensor)) {
        			service.pushToClients("missing_sensor", 0, sensor.getName());
        			String msg = String.format(service.getString(R.string.missing_sensor), sensor.getName());
        			service.showAlertNotification(msg, R.raw.garage_alert, new Date());
        			service.addLog(msg);
        			warnedAboutMissingSensor = true;
    			}

    			foundMissingSensor = true;
        		Log.i(TAG, "Sensor " + sensor.getName() + " not updated any more !");
        		// Warn for 1st sensor found missing
        		return;
    		}
    	}

    	// Reset flag when everything's fine
    	if (!foundMissingSensor) {
    		warnedAboutMissingSensor = false;
    	}
    }
}