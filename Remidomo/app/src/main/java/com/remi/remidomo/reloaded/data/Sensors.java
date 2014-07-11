package com.remi.remidomo.reloaded.data;

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

import com.remi.remidomo.common.Notifications;
import com.remi.remidomo.reloaded.*;
import com.remi.remidomo.reloaded.data.SensorData.Pair;
import com.remi.remidomo.common.prefs.PrefsNotif;
import com.remi.remidomo.reloaded.prefs.PrefsService;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class Sensors {
	
	private final static String TAG = RDService.class.getSimpleName();

	// Hard-coded values
        public final static String ID_EXT_T = "th2_0x1005-temp";
        public final static String ID_EXT_H = "th2_0x1005-humidity";
        public final static String ID_POOL_T = "temp3-temp";
        public final static String ID_VERANDA_T = "th1_0x2d02-temp";
        public final static String ID_VERANDA_H = "th1_0x2d02-humidity";

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
    public final static long WARN_MISSING_MINS = 120;

	public Sensors(RDService service) {
		this.service = service;
		
		new Thread(new Runnable() {
        	public void run() {
        		prefs = PreferenceManager.getDefaultSharedPreferences(Sensors.this.service);

        		notificationMgr = (NotificationManager)Sensors.this.service.getSystemService(Context.NOTIFICATION_SERVICE);
        		if (notificationMgr == null) {
        			Log.e(TAG, "Failed to get Notification Manager");
        		}

        		// Fetch data for all sensors
        		Sensors.this.service.addLog("Lecture des données capteurs locales");

        		readyForUpdates = false;
        		synchronized (sensors) {
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
        				// The PendingIntent to launch our activity if the user selects this notification
        				Intent intent = new Intent(Sensors.this.service, RDService.class);
                        intent.setAction(RDService.ACTION_RESTORE_DATA);
                        PendingIntent contentIntent = PendingIntent.getService(Sensors.this.service, 0, intent, 0);

        		        NotificationCompat.Builder builder = new NotificationCompat.Builder(Sensors.this.service)
        		            .setSmallIcon(R.drawable.app_icon)
        		            .setContentIntent(contentIntent)
        		            .setTicker(Sensors.this.service.getText(R.string.sensors_empty))
        		            .setContentText(Sensors.this.service.getText(R.string.sensors_restore))
        		            .setContentTitle(Sensors.this.service.getText(R.string.sensors_empty))
        		            .setWhen(System.currentTimeMillis())
        		            .setAutoCancel(true);

        		        Bitmap largeIcon = BitmapFactory.decodeResource(Sensors.this.service.getResources(), R.drawable.sdcard);
        		        builder.setLargeIcon(largeIcon);

        		        // Send the notification.
        		        notificationMgr.notify(NOTIFICATION_RESTORE, builder.build());
        			}
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
    	updateData(service, msg, SensorData.CompressionType.TIME_BASED);
    }

    public void updateData(RDService service, xPLMessage msg, SensorData.CompressionType compress) {
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

    		data.addValue(new Date(), msg.getFloatNamedValue("current"), compress);

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
    		synchronized (sensors) {
    			for (SensorData sensor: sensors) {
    				JSONArray array = sensor.getJSONArray(lastTstamp);
    				if (array != null) {
    					object.put(sensor.getName(), array);
    				}
    			}
    		}
    		writer.write(object.toString());
    	} catch (org.json.JSONException ignored) {}
    }

    public void dumpCSV(OutputStreamWriter writer) {
    	synchronized (sensors) {
    		for (SensorData sensor: sensors) {
    			sensor.writeCSV(writer);
    		}
    	}
    }
  
    public Date getLastUpdate() {
    	Date tstamp = null;
    
    	synchronized (sensors) {
    		for (SensorData i: sensors) {
    			if (i.lastUpdate != null) {
    				if (tstamp == null || (i.lastUpdate.getTime() > tstamp.getTime())) {
    					tstamp = i.lastUpdate;
    				}
    			}
    		}
    	}

    	return tstamp;
    }
    
    public void syncWithServer() {
		int port = prefs.getInt("port", PrefsService.DEFAULT_PORT);
		String ipAddr = prefs.getString("ip_address", PrefsService.DEFAULT_IP);
		Log.d(TAG, "Client Thread connecting to " + ipAddr + ":" + port);
		service.addLog("Connexion au serveur " + ipAddr + ":" + port + " (MAJ sondes)");

		try {
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();

			String uri = ipAddr+":"+port+"/sensors";
			Date lastTstamp = getLastUpdate();
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

    public void clearData() {
    	synchronized(sensors) {
    		for (SensorData sensor: sensors) {
    			sensor.clearData();
    		}
    	}
    }

    public boolean checkSensorsConsistency() {
    	synchronized (sensors) {
    		// 1st, find the most recent sensor timestamp
    		long maxTime = 0;
    		for (SensorData sensor: sensors) {
    			if (sensor.size() > 0) {
    				long tstamp = sensor.getLast().time;
    				if (tstamp > maxTime) {
    					maxTime = tstamp;
    				}
    			}
    		}

    		// Now, see if a sensor has not been updated for 15 min
    		boolean foundMissingSensor = false;
    		for (SensorData sensor: sensors) {
    			if (sensor.size() == 0) {
    				continue;
    			}

    			long tstamp = sensor.getLast().time;

    			if (maxTime - tstamp > WARN_MISSING_MINS*60*1000) {
    				// Note: we're necessarily in server mode,
    				// because the only caller is updateData() from xPL message
    				if ((service != null) && (!warnedAboutMissingSensor)) {
    					service.pushToClients(PushSender.MISSING_SENSOR, 0, sensor.getName());
    					String msg = String.format(service.getString(R.string.missing_sensor), sensor.getName());
    					Notifications.showAlertNotification(service, msg, Notifications.NotifType.ALERT, R.drawable.temperature2, RDActivity.TEMP_VIEW_ID, RDActivity.class, new Date());
    					service.addLog(msg);
    					warnedAboutMissingSensor = true;
    				}

    				foundMissingSensor = true;
    				Log.i(TAG, "Sensor " + sensor.getName() + " not updated any more !");
    				// Warn for 1st sensor found missing
    				return false;
    			}
    		}

    		// Reset flag when everything's fine
    		if (!foundMissingSensor) {
    			warnedAboutMissingSensor = false;
    		}

    		return true;
    	}
    }

	public synchronized JSONObject getJSONChart(int daysBack, String[] names) {
		JSONObject dict = new JSONObject();
		if (names == null) {
			return dict;
		}

		long pastDate = new Date(new Date().getTime() - daysBack*SensorData.HOURS_24).getTime();

		try {
			JSONArray columns = new JSONArray();

			JSONObject dates = new JSONObject();
			dates.put("label", "dates");
			dates.put("type", "datetime");
			columns.put(dates);

			for (int i=0; i<names.length; ++i) {
				JSONObject values = new JSONObject();
				if (ID_POOL_T.equals(names[i])) {
					values.put("label", service.getString(R.string.pool));
				} else if (ID_EXT_T.equals(names[i])) {
					values.put("label", service.getString(R.string.outside));
				} else if (ID_VERANDA_T.equals(names[i])) {
					values.put("label", service.getString(R.string.veranda));
				} else {
					values.put("label", service.getString(R.string.temperature));
				}
				values.put("type", "number");
				columns.put(values);
			}

			dict.put("cols", columns);

			JSONArray rows = new JSONArray();

			// Indices into each sensor data
			ArrayList<Integer> positions = new ArrayList<Integer> ();

			// Current values and times for each sensor
			ArrayList<Float> currentValues = new ArrayList<Float> ();
			ArrayList<Long> currentTimes = new ArrayList<Long> ();

			/* Initialization phase:
			 * Find 1st values at relevant dates (later than pastDate)
			 */
			int index = 0;
			for (SensorData sensor: sensors) {
				if (sensor.size() == 0) {
					continue;
				}

				boolean match = false;
				for (String name: names) {
					if (sensor.getName().equals(name)) {
						match = true;
						break;
					}
				}
				if (!match) {
					continue;
				}

				currentTimes.add(0L);
				currentValues.add(0.0f);
				for (int i=0; i<sensor.size(); i++) {
					final Pair pair = sensor.get(i);
					currentTimes.set(index, pair.time);
					currentValues.set(index, pair.value);

					if (pair.time >= pastDate) {
						positions.add(i);
						break;
					}
				}

				index++;
			}

			/* Traversal phase */
			while (true) {
				index = 0;
				boolean endReached = true;
				for (SensorData sensor: sensors) {

					// Ignore this sensor
					if (sensor.size() == 0) {
						continue;
					}

					boolean match = false;
					for (String name: names) {
						if (sensor.getName().equals(name)) {
							match = true;
							break;
						}
					}
					if (!match) {
						continue;
					}

					// Move one point forward, if too far in the past
					int position = positions.get(index);
					if (position >= sensor.size()) {
						// Exhausted data for this sensor, skip
						index++;
						continue;
					}

					final Pair pair = sensor.get(position);

					if (position < sensor.size()) {
						// See if we can increment position
						// (only if sensor is the last in time)
						long currentTime = currentTimes.get(index);
						boolean isLast = true;
						for (int i=0; i<names.length; i++) {
							if (currentTimes.get(i) < currentTime) {
								isLast = false;
								break;
							}
						}

						if (isLast) {
							positions.set(index, position+1);
						} else {
							index++;
							continue;
						}
					}

					// Remember current value for this sensor
					currentValues.set(index, pair.value);
					currentTimes.set(index, pair.time);

					// Exit clause, if all indices reached the end
					if (positions.get(index) < sensor.size()) {
						endReached = false;
					}

					// Next sensor
					index++;
				} // end sensors loop

				if (names.length > 0) {
					JSONArray entry = new JSONArray();

					JSONObject time = new JSONObject();
					Date date = new Date(currentTimes.get(0));
					String dateJSON = "Date("+(date.getYear()+1900)+","+date.getMonth()+","+date.getDate()+
							          ","+date.getHours()+","+date.getMinutes()+","+date.getSeconds()+")";
					time.put("v", dateJSON);
					entry.put(time);

					for (int i=0; i<names.length; i++) {
						JSONObject value = new JSONObject();
						value.put("v", currentValues.get(i));
						entry.put(value);
					}

					JSONObject row = new JSONObject();
					row.put("c", entry);
					rows.put(row);
				}

				if (endReached) {
					break;
				}
			} // end outer while

			dict.put("rows", rows);
		} catch (org.json.JSONException e) {
			Log.d(TAG, "Failed generating JSON for charts: ", e);
		}
		return dict;
	}
}
