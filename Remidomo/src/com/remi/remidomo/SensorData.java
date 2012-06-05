package com.remi.remidomo;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Writer;
import java.lang.Math;
import java.util.Date;
import java.util.Scanner;

import org.json.JSONArray;

import com.androidplot.xy.SimpleXYSeries;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

public class SensorData extends SimpleXYSeries {

	private final static String TAG = RDService.class.getSimpleName();

	private static final long HOURS_24 = 24*60*60*1000;
	private static final long MINS_10 = 10*60*1000;
	public static final long SAMPLING_PERIOD = 3*MINS_10;

	private RDService service;
	
	public Date lastUpdate = null;
	
	public enum DirType {INTERNAL, SDCARD};
	public enum FileFormat {ASCII, BINARY};

	public SensorData(SensorData origin) {
		super(origin.getTitle());
	
		for (int i=0; i<origin.size(); i++) {
			addLast(origin.getX(i), origin.getY(i));
		}
	}
	
	public SensorData(String name, RDService service, boolean initFromFile) {
		super(name);
		this.service = service;
		
		if (initFromFile) {
			readFile(DirType.INTERNAL);
		}
	}

	public void readFile(DirType dir) {
		// Read file
		FileInputStream fis = null;
		Scanner scanner = null;
		try {
			if (dir == DirType.INTERNAL) {
				fis = service.openFileInput(getTitle()+".dat");
			} else if (dir == DirType.SDCARD) {
				String state = Environment.getExternalStorageState();
				if (Environment.MEDIA_MOUNTED.equals(state)) {
					File path = new File(Environment.getExternalStorageDirectory(), getTitle() + ".dat");
					fis = new FileInputStream(path);
				} else {
					Log.e(TAG, "Impossible to read from external storage");
					service.addLog("Impossible de lire depuis la carte SD");
				}
			} else {
				assert(false);
			}

			DataInputStream dis = new DataInputStream(fis);
			String magic = dis.readLine();
			
			Log.d(TAG, "READ, MAGIC:" + magic);

			if (FileFormat.BINARY.toString().equals(magic)) {
				while(true) {
					try {
						long timestamp = dis.readLong();
						float value = dis.readFloat();
						addLast(timestamp, value);
					} catch (java.io.EOFException eof) {
						break;
					} catch (java.io.IOException e) {
						Log.e(TAG, "Error reading " + getTitle() + ": " + e);
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
					addLast(timestamp, value);

					// Filter with sampling period
					//addValue(new Date(timestamp), value);
				}
			} else {
				assert(false);
			}
		} catch (java.io.FileNotFoundException e) {
			Log.e(TAG, "Sensor file not found. " + e);
			service.addLog("Pas de données locales pour " + getTitle());
		} catch (java.lang.IllegalArgumentException e) {
			Log.e(TAG, "Error parsing sensor file for sensor " + getTitle() + " (IllegalArgument)");
			service.addLog("Erreur de lecture pour " + getTitle());
		} catch (java.util.InputMismatchException e) {
			Log.e(TAG, "Error parsing sensor file for sensor " + getTitle() + " (InputMismatch)");
			service.addLog("Erreur de lecture pour " + getTitle());
		} catch (java.util.NoSuchElementException e) {
			Log.e(TAG, "Error parsing sensor file for sensor " + getTitle() + " (NoSuchElement)");
			service.addLog("Erreur de lecture pour " + getTitle());
		} catch (java.io.IOException e) {
			Log.e(TAG, "Error reading file for sensor " + getTitle() + e);
			service.addLog("Erreur de lecture pour " + getTitle());
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
	
		if (this.size() > 0) {
			lastUpdate = new Date(this.getX(this.size()-1).longValue());
		}
	}
	
	public void writeFile(DirType dir, FileFormat format) {
		FileOutputStream fos = null;
		try {
			if (dir == DirType.INTERNAL) {
				fos = service.openFileOutput(getTitle()+".dat", Context.MODE_WORLD_READABLE);
			} else if (dir == DirType.SDCARD) {
				String state = Environment.getExternalStorageState();
				if (Environment.MEDIA_MOUNTED.equals(state)) {
					File path = new File(Environment.getExternalStorageDirectory(), getTitle() + ".dat");
					//path.mkdirs();
					fos = new FileOutputStream(path);
				} else {
				    Log.e(TAG, "Impossible to write to external storage");
				    service.addLog("Impossible d'écrire sur la carte SD");
				}
			} else {
				assert(false);
			}
			if (fos != null) {
				if (format == FileFormat.BINARY) {
					// Binary
					DataOutputStream dos = new DataOutputStream(fos);
					dos.writeBytes(FileFormat.BINARY+"\n");
					for (int i=0; i<this.size(); i++) {
						dos.writeLong(this.getX(i).longValue());
						dos.writeFloat(this.getY(i).floatValue());
					}
				} else if (format == FileFormat.ASCII) {
					// ASCII
					fos.write((FileFormat.ASCII+"\n").getBytes());
					for (int i=0; i<this.size(); i++) {
						fos.write(this.getX(i).toString().getBytes());
						fos.write(" ".getBytes());
						fos.write(this.getY(i).toString().getBytes());
						fos.write("\n".getBytes());
					}
				} else {
					assert(false);
				}
			}
		} catch (java.io.IOException e) {
			Log.e(TAG, "Failed to write file for sensor " + this.getTitle() + ": " + e);
			service.addLog("Impossible d'écrire le fichier de données pour " + this.getTitle());
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (java.io.IOException ignored) {}
			}
		}
	}

	public JSONArray getJSONArray(long lastTstamp) {
		try {
			JSONArray array = new JSONArray();
			for (int i=0; i<this.size(); i++) {
				if (this.getX(i).longValue() >= lastTstamp) {
					JSONArray couple = new JSONArray();
					couple.put(0, this.getX(i));
					couple.put(1, this.getY(i));
					array.put(couple);
				}
			}
			return array;
		} catch (org.json.JSONException e) {
			Log.e(TAG, "Failed creating JSON data for sensor " + this.getTitle());
			return null;
		}
	}
	
	public void readJSON(JSONArray input) {
		while (this.size() > 0) {
			this.removeLast();
		}
		
		try {
			for (int i=0; i<input.length(); i++) {
				JSONArray couple = input.getJSONArray(i);
				long timestamp = couple.optLong(0);
				float value = (float)couple.optDouble(1);
				this.addLast(timestamp, value);
			}
		} catch (org.json.JSONException e) {
			Log.e(TAG, "Failed reading JSON data");
		}
		
		if (this.size() > 0) {
			lastUpdate = new Date(this.getX(this.size()-1).longValue());
		}
	}
	
	public void writeCSV(Writer writer) {
		try {
			writer.write(getTitle() + "<br/>");
			for (int i=0; i<this.size(); i++) {
				writer.write(new Date(getX(i).longValue()).toGMTString());
				writer.write(";");
				writer.write(getY(i).toString());
				writer.write("<br/>");
			}
			writer.write("<br/>");
		} catch (java.io.IOException ignored) {
			// Ignored
		}
	}
	
	public void addValue(Number value) {
		addValue(new Date(), value);
	}

	public void addValuesChunk(SensorData newData) {
		if (newData.size() == 0) {
			return;
		}

		if ((this.size() > 0) &&
			(newData.getX(0).longValue() < this.getX(this.size() - 1).longValue())) {
			return;
		}
		
		for (int i=0; i<newData.size(); i++) {
			// Values come from the server
			// -> no need for filtering with sampling period
			this.addLast(newData.getX(i), newData.getY(i));
		}
		lastUpdate = new Date(this.getX(this.size()-1).longValue());
	}
	
	public void addValue(Date tstamp, Number value) {
		long tstampLong = tstamp.getTime();
		
		// If not later than 10min, and below 0.5 delta,
		// just replace last value
		if (this.size() >= 2) {
			long lastLong1 = getX(size()-1).longValue();
			float lastVal1 = getY(size()-1).floatValue();
			long lastLong2 = getX(size()-2).longValue();
			float lastVal2 = getY(size()-2).floatValue();
			if ((tstampLong-lastLong1 < SAMPLING_PERIOD) &&
				(Math.abs(lastVal1 - value.floatValue()) < 0.5f) &&
				(lastLong1-lastLong2 < SAMPLING_PERIOD) &&
				(Math.abs(lastVal2 - lastVal1) < 0.5f)) {
				this.removeLast();
			}
				
			this.addLast(tstampLong, value);
		} else {
			this.addLast(tstampLong, value);
		}
		lastUpdate = tstamp;

		if (service != null) {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.service);
			int maxHistory = Integer.parseInt(prefs.getString("loglimit", Preferences.DEFAULT_LOGLIMIT));
			long tstampLimit = tstampLong - maxHistory*HOURS_24;
			while ((size() > 0) && (getX(0).longValue() < tstampLimit)) {
				this.removeFirst();
			}
		}
	}
}
