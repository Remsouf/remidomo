package com.remi.remidomo.reloaded;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Writer;
import java.lang.Math;
import java.util.Date;
import java.util.LinkedList;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;

import com.androidplot.xy.SimpleXYSeries;
import com.remi.remidomo.reloaded.prefs.PrefsGeneral;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

public class SensorData {

	private final static String TAG = RDService.class.getSimpleName();

	public static final long HOURS_24 = 24*60*60*1000;
	public static final long MINS_10 = 10*60*1000;
	public static final long SAMPLING_PERIOD = 3*MINS_10;
	public static final long SAMPLING_PERIOD_MEAN = MINS_10;

	private RDService service;
	
	public Date lastUpdate = null;
	
	private String name = null;
	
	private boolean meanCompressionToggle = false;

	public enum DirType {INTERNAL, SDCARD};
	public enum FileFormat {ASCII, BINARY};
	public enum CompressionType {NONE, TIME_BASED, MEAN};
	
	public static class Pair {
		public long time;
		public float value;
		
		public Pair(long time, float value) {
			this.time = time;
			this.value = value;
		}
	}
	
	private LinkedList<Pair> data = new LinkedList<Pair> ();
	
	public SensorData(String name, RDService service, boolean initFromFile) {
		this.name = name;
		this.service = service;
		
		if (initFromFile) {
			readFile(DirType.INTERNAL);
		}
	}

	public synchronized void readFile(DirType dir) {
		// Read file
		FileInputStream fis = null;
		Scanner scanner = null;
		try {
			if (dir == DirType.INTERNAL) {
				fis = service.openFileInput(name+".dat");
			} else if (dir == DirType.SDCARD) {
				String state = Environment.getExternalStorageState();
				if (Environment.MEDIA_MOUNTED.equals(state)) {
					File path = new File(Environment.getExternalStorageDirectory(), name + ".dat");
					fis = new FileInputStream(path);
				} else {
					Log.e(TAG, "Impossible to read from external storage");
					service.addLog("Impossible de lire depuis la carte SD", RDService.LogLevel.HIGH);
				}
			} else {
				assert(false);
			}

			DataInputStream dis = new DataInputStream(fis);
			String magic = dis.readLine();

			if (FileFormat.BINARY.toString().equals(magic)) {
				while(true) {
					try {
						long timestamp = dis.readLong();
						float value = dis.readFloat();
						data.addLast(new Pair(timestamp, value));
					} catch (java.io.EOFException eof) {
						break;
					} catch (java.io.IOException e) {
						Log.e(TAG, "Error reading " + name + ": " + e);
					}
				}
			} else if (FileFormat.ASCII.toString().equals(magic)) {
				scanner = new Scanner(dis);
				while (scanner.hasNextLine()) {
					if (!scanner.hasNext()) {
						// Blank line at EOF
						break;
					}
					long timestamp = Long.parseLong(scanner.next());
					float value = Float.parseFloat(scanner.next());
					data.addLast(new Pair(timestamp, value));

					// Filter with sampling period
					//addValue(new Date(timestamp), value);
				}
			} else {
				assert(false);
			}
		} catch (java.io.FileNotFoundException e) {
			Log.e(TAG, "Sensor file not found. " + e);
			service.addLog("Pas de données locales pour " + name, RDService.LogLevel.HIGH);
		} catch (java.lang.IllegalArgumentException e) {
			Log.e(TAG, "Error parsing sensor file for sensor " + name + " (IllegalArgument)");
			service.addLog("Erreur de lecture pour " + name, RDService.LogLevel.HIGH);
		} catch (java.util.InputMismatchException e) {
			Log.e(TAG, "Error parsing sensor file for sensor " + name + " (InputMismatch)");
			service.addLog("Erreur de lecture pour " + name, RDService.LogLevel.HIGH);
		} catch (java.util.NoSuchElementException e) {
			Log.e(TAG, "Error parsing sensor file for sensor " + name + " (NoSuchElement)");
			service.addLog("Erreur de lecture pour " + name, RDService.LogLevel.HIGH);
		} catch (java.io.IOException e) {
			Log.e(TAG, "Error reading file for sensor " + name + e);
			service.addLog("Erreur de lecture pour " + name, RDService.LogLevel.HIGH);
		} finally {
			if (scanner != null) {
				scanner.close();
			}
			if (fis != null) {
				try {
					fis.close();
				} catch (java.io.IOException ignored) {}
			}
		}
	
		if (data.size() > 0) {
			lastUpdate = new Date(data.getLast().time);
		}
	}
	
	public synchronized void writeFile(DirType dir, FileFormat format) {
		FileOutputStream fos = null;
		DataOutputStream dos = null;
		try {
			if (dir == DirType.INTERNAL) {
				fos = service.openFileOutput(name+".dat", Context.MODE_WORLD_READABLE);
			} else if (dir == DirType.SDCARD) {
				String state = Environment.getExternalStorageState();
				if (Environment.MEDIA_MOUNTED.equals(state)) {
					File path = new File(Environment.getExternalStorageDirectory(), name + ".dat");
					//path.mkdirs();
					fos = new FileOutputStream(path);
				} else {
				    Log.e(TAG, "Impossible to write to external storage");
				    service.addLog("Impossible d'écrire sur la carte SD", RDService.LogLevel.HIGH);
				}
			} else {
				assert(false);
			}
			if (fos != null) {
				if (format == FileFormat.BINARY) {
					// Binary
					dos = new DataOutputStream(fos);
					dos.writeBytes(FileFormat.BINARY+"\n");
					for (Pair pair:data) {
						dos.writeLong(pair.time);
						dos.writeFloat(pair.value);
					}
				} else if (format == FileFormat.ASCII) {
					// ASCII
					fos.write((FileFormat.ASCII+"\n").getBytes());
					for (Pair pair:data) {
						fos.write(String.valueOf(pair.time).getBytes());
						fos.write(" ".getBytes());
						fos.write(String.valueOf(pair.value).getBytes());
						fos.write("\n".getBytes());
					}
				} else {
					assert(false);
				}
			}
		} catch (java.io.IOException e) {
			Log.e(TAG, "Failed to write file for sensor " + this.name + ": " + e);
			service.addLog("Impossible d'écrire le fichier de données pour " + this.name, RDService.LogLevel.HIGH);
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (java.io.IOException ignored) {}
			}
			if (dos != null) {
				try {
					dos.close();
				} catch (java.io.IOException ignored) {}
			}
		}
	}

	public synchronized JSONArray getJSONArray(long lastTstamp) {
		try {
			JSONArray array = new JSONArray();
			for (Pair pair:data) {
				if (pair.time >= lastTstamp) {
					JSONArray couple = new JSONArray();
					couple.put(0, pair.time);
					couple.put(1, pair.value);
					array.put(couple);
				}
			}
			return array;
		} catch (org.json.JSONException e) {
			Log.e(TAG, "Failed creating JSON data for sensor " + this.name);
			return null;
		}
	}

	public synchronized JSONArray getJSONChart(long lastTstamp) {
		try {
			JSONArray rows = new JSONArray();
			for (Pair pair:data) {
				if (pair.time >= lastTstamp) {
					JSONArray entry = new JSONArray();

					JSONObject time = new JSONObject();
					Date date = new Date(pair.time);
					String dateJSON = "Date("+(date.getYear()+1900)+","+date.getMonth()+","+date.getDate()+
							          ","+date.getHours()+","+date.getMinutes()+","+date.getSeconds()+")";
					time.put("v", dateJSON);
					entry.put(time);

					JSONObject value = new JSONObject();
					value.put("v", pair.value);
					entry.put(value);

					JSONObject row = new JSONObject();
					row.put("c", entry);
					rows.put(row);
				}
			}
			return rows;
		} catch (org.json.JSONException e) {
			Log.e(TAG, "Failed creating JSON chart data for sensor " + this.name);
			return null;
		}
	}

	public synchronized void readJSON(JSONArray input) {
		data.clear();
		
		try {
			for (int i=0; i<input.length(); i++) {
				JSONArray couple = input.getJSONArray(i);
				long timestamp = couple.optLong(0);
				float value = (float)couple.optDouble(1);
				data.addLast(new Pair(timestamp, value));
			}
		} catch (org.json.JSONException e) {
			Log.e(TAG, "Failed reading JSON data");
		}
		
		if (data.size() > 0) {
			lastUpdate = new Date(data.getLast().time);
		}
	}
	
	public void writeCSV(Writer writer) {
		try {
			writer.write(name + "<br/>");
			for (Pair pair:data) {
				writer.write(new Date(pair.time).toGMTString());
				writer.write(";");
				writer.write(String.valueOf(pair.value));
				writer.write("<br/>");
			}
			writer.write("<br/>");
		} catch (java.io.IOException ignored) {
			// Ignored
		}
	}
	
	public synchronized void addValuesChunk(SensorData newData) {
		if (newData.data.size() == 0) {
			return;
		}

		if ((data.size() > 0) &&
			(newData.data.getFirst().time < data.getLast().time)) {
			return;
		}

		// concatenate
		data.addAll(newData.data);

		lastUpdate = new Date(data.getLast().time);
	}

	public void addValue(Date tstamp, float value) {
		addValue(tstamp, value, CompressionType.NONE);
	}

	public synchronized void addValue(Date tstamp, float value, CompressionType compress) {
		long tstampLong = tstamp.getTime();
		
		if (compress == CompressionType.TIME_BASED) {
			// If not later than 30min, and below 0.5 delta,
			// just replace last value
			if (data.size() >= 2) {
				long lastLong1 = data.getLast().time;
				float lastVal1 = data.getLast().value;
				long lastLong2 = data.get(data.size()-2).time;
				float lastVal2 = data.get(data.size()-2).value;
				if ((tstampLong-lastLong1 < SAMPLING_PERIOD) &&
						(Math.abs(lastVal1 - value) < 0.5f) &&
						(lastLong1-lastLong2 < SAMPLING_PERIOD) &&
						(Math.abs(lastVal2 - lastVal1) < 0.5f)) {
					data.removeLast();
				}

				data.addLast(new Pair(tstampLong, value));
			} else {
				data.addLast(new Pair(tstampLong, value));
			}
		} else if (compress == CompressionType.MEAN) {
			float newValue = value;

			if ((data.size() > 0) && meanCompressionToggle) {
				newValue = (value + data.getLast().value) / 2;
				data.removeLast();
			}
			data.addLast(new Pair(tstampLong, newValue));

			meanCompressionToggle = !meanCompressionToggle;

			// Now compress the usual way
			// (here with 10min and below 50 delta)
			if (data.size() >= 3) {
				long lastLong1 = data.get(data.size()-2).time;
				float lastVal1 = data.get(data.size()-2).value;
				long lastLong2 = data.get(data.size()-3).time;
				float lastVal2 = data.get(data.size()-3).value;
				if ((tstampLong-lastLong1 < SAMPLING_PERIOD_MEAN) &&
						(Math.abs(lastVal1 - newValue) < 50.0f) &&
						(lastLong1-lastLong2 < SAMPLING_PERIOD_MEAN) &&
						(Math.abs(lastVal2 - lastVal1) < 50.0f)) {
					data.removeLast();
					data.removeLast();
					data.addLast(new Pair(tstampLong, newValue));
				}
			}
		} else if (compress == CompressionType.NONE){
			data.addLast(new Pair(tstampLong, value));
		} else {
			assert(false);
		}
		lastUpdate = tstamp;

		if (service != null) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.service);
			int maxHistory = prefs.getInt("loglimit", PrefsGeneral.DEFAULT_LOGLIMIT);
			long tstampLimit = tstampLong - maxHistory*HOURS_24;
			while ((data.size() > 0) && (data.getFirst().time < tstampLimit)) {
				data.removeFirst();
			}
		}
	}
	
	public synchronized SimpleXYSeries filter(long limit) {
		SimpleXYSeries series = new SimpleXYSeries(name);
		
		// Ignore points before days limit
		for (Pair pair:data) {
			if (pair.time < limit) {
				continue;
			} else {
				series.addLast(pair.time, pair.value);
			}
		}
		return series;
	}
	
	/* Wrappers to this.data */
	public Pair getLast() {
		return data.getLast();
	}
	
	public Pair getFirst() {
		return data.getFirst();
	}
	
	public int size() {
		return data.size();
	}
	
	public String getName() {
		return name;
	}
	
	public synchronized Pair get(int i) {
		return data.get(i);
	}
	
	public synchronized void set(int i, Pair pair) {
		data.set(i, pair);
	}

	public synchronized void clearData() {
		data.clear();
		lastUpdate = null;
	}
}
