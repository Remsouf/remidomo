package com.remi.remidomo.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.test.ServiceTestCase;

import com.remi.remidomo.reloaded.data.Energy;
import com.remi.remidomo.reloaded.RDService;
import com.remi.remidomo.reloaded.data.SensorData;
import com.remi.remidomo.reloaded.data.Sensors;

public class SensorsTest extends ServiceTestCase<RDService> {
	
	private RDService service;

	public SensorsTest() {
		super(RDService.class);
	}

	public void start() {
        // Prevent from running on a real target
     	// (would remove preferences and files)
        assertEquals("Run on emulator !", "sdk", Build.PRODUCT);

        // Clear sensor files
        final String APP_PATH = "/data/data/com.remi.remidomo/files/";       
        String[] files = {Energy.ID_ENERGY, Energy.ID_POWER,
        				  Sensors.ID_EXT_T, Sensors.ID_EXT_H,
        				  Sensors.ID_VERANDA_T, Sensors.ID_VERANDA_T,
        				  Sensors.ID_POOL_T};
        for (String file: files) {
        	File f = new File(APP_PATH+file+".dat");
        	f.delete();
        }

		Intent startIntent = new Intent();
        startIntent.setClass(getContext(), RDService.class);
        startService(startIntent);

        // Clear preferences
		service = getService();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(service);
		SharedPreferences.Editor editor = prefs.edit();
		editor.clear();
		editor.commit();
	}
	
	public void testCheckConsistency() {
		start();

		Sensors sensors = service.getSensors();
		SensorData dataPool = sensors.getData(Sensors.ID_POOL_T);
		SensorData dataVeranda = sensors.getData(Sensors.ID_VERANDA_T);
		SensorData dataExt = sensors.getData(Sensors.ID_EXT_T);

		dataPool.addValue(new Date(0), 0.0f);
		assertTrue(sensors.checkSensorsConsistency());

		dataVeranda.addValue(new Date(1000), 0.0f);
		dataExt.addValue(new Date(2000), 0.0f);
		assertTrue(sensors.checkSensorsConsistency());

		dataPool.addValue(new Date(0+Sensors.WARN_MISSING_MINS*60*1000+5000), 1.0f);
		assertFalse(sensors.checkSensorsConsistency());

		dataVeranda.addValue(new Date(1000+Sensors.WARN_MISSING_MINS*60*1000+5000), 1.0f);
		assertFalse(sensors.checkSensorsConsistency());

		dataExt.addValue(new Date(2000+Sensors.WARN_MISSING_MINS*60*1000+5000), 1.0f);
		assertTrue(sensors.checkSensorsConsistency());
	}
	
	public void testSdcard() {
		start();

		Sensors sensors = service.getSensors();
		SensorData dataPool = sensors.getData(Sensors.ID_POOL_T);
		SensorData dataVeranda = sensors.getData(Sensors.ID_VERANDA_T);
		SensorData dataExt = sensors.getData(Sensors.ID_EXT_T);

		dataPool.addValue(new Date(0), 0.1f);
		dataVeranda.addValue(new Date(0), 0.2f);
		dataExt.addValue(new Date(0), 0.3f);

		dataPool.addValue(new Date(1000), 1.0f);
		dataVeranda.addValue(new Date(1000), 2.0f);
		dataExt.addValue(new Date(1000), 3.0f);

		sensors.saveToSdcard();

		dataPool.clearData();
		dataVeranda.clearData();
		dataExt.clearData();

		sensors.readFromSdcard();
		assertEquals(0.1f, dataPool.get(0).value);
		assertEquals(0.2f, dataVeranda.get(0).value);
		assertEquals(0.3f, dataExt.get(0).value);

		assertEquals(1.0f, dataPool.get(1).value);
		assertEquals(2.0f, dataVeranda.get(1).value);
		assertEquals(3.0f, dataExt.get(1).value);
	}
	
	public void testOutput() throws IOException {
		start();

		Sensors sensors = service.getSensors();
		SensorData dataPool = sensors.getData(Sensors.ID_POOL_T);
		SensorData dataVeranda = sensors.getData(Sensors.ID_VERANDA_T);
		SensorData dataExt = sensors.getData(Sensors.ID_EXT_T);

		dataPool.addValue(new Date(0), 0.1f);
		dataVeranda.addValue(new Date(0), 0.2f);
		dataExt.addValue(new Date(0), 0.3f);

		dataPool.addValue(new Date(1000), 1.0f);
		dataVeranda.addValue(new Date(1002), 2.0f);
		dataExt.addValue(new Date(1003), 3.0f);

		assertEquals(new Date(1003), sensors.getLastUpdate());

		// CSV
		ByteArrayOutputStream testOutput = new ByteArrayOutputStream();
		OutputStreamWriter osw = new OutputStreamWriter(testOutput);
		sensors.dumpCSV(osw);
		osw.flush();
		assertEquals(Sensors.ID_POOL_T+"<br/>1 Jan 1970 00:00:00 GMT;0.1<br/>1 Jan 1970 00:00:01 GMT;1.0<br/><br/>"+Sensors.ID_EXT_T+"<br/>1 Jan 1970 00:00:00 GMT;0.3<br/>1 Jan 1970 00:00:01 GMT;3.0<br/><br/>"+Sensors.ID_EXT_H+"<br/><br/>"+Sensors.ID_VERANDA_T+"<br/>1 Jan 1970 00:00:00 GMT;0.2<br/>1 Jan 1970 00:00:01 GMT;2.0<br/><br/>"+Sensors.ID_VERANDA_H+"<br/><br/>", testOutput.toString());
		osw.close();

		// JSON
		testOutput.reset();
		osw = new OutputStreamWriter(testOutput);
		sensors.dumpData(osw, 0);
		osw.flush();
		assertEquals("{\""+Sensors.ID_EXT_T+"\":[[0,0.30000001192092896],[1003,3]],\""+Sensors.ID_POOL_T+"\":[[0,0.10000000149011612],[1000,1]],\""+Sensors.ID_VERANDA_H+"\":[],\""+Sensors.ID_VERANDA_T+"\":[[0,0.20000000298023224],[1002,2]],\""+Sensors.ID_EXT_H+"\":[]}", testOutput.toString());
		osw.close();
	}

	public void testGetJSONChart() throws JSONException {
		start();

		// Nb of days in past that cover 1/1/1970 (=Date(0))
		final int DAYS_PAST = (int)(new Date().getTime()/SensorData.HOURS_24) + 1;

		Sensors sensors = service.getSensors();
		SensorData dataPool = sensors.getData(Sensors.ID_POOL_T);
		SensorData dataVeranda = sensors.getData(Sensors.ID_VERANDA_T);
		SensorData dataExt = sensors.getData(Sensors.ID_EXT_T);

		dataPool.addValue(new Date(0), 0.125f);
		dataVeranda.addValue(new Date(0), 0.25f);
		dataExt.addValue(new Date(0), 0.3f);

		dataPool.addValue(new Date(1000), 1.0f);
		dataVeranda.addValue(new Date(1000), 2.0f);
		dataExt.addValue(new Date(1000), 3.0f);

		// Empty rows
		String[] names0 = {};
		JSONObject out = sensors.getJSONChart(DAYS_PAST, names0);

		assertTrue(out.has("cols"));
		JSONArray cols = out.getJSONArray("cols");
		assertEquals("[{\"type\":\"datetime\",\"label\":\"dates\"}]", cols.toString());

		assertTrue(out.has("rows"));
		JSONArray rows = out.getJSONArray("rows");
		assertEquals(0, rows.length());

		// Only one sensor, sync'ed times
		String[] names1 = {Sensors.ID_POOL_T};
		out = sensors.getJSONChart(DAYS_PAST, names1);
		assertTrue(out.has("cols"));
		cols = out.getJSONArray("cols");
		assertEquals(2, cols.length());
		rows = out.getJSONArray("rows");
		assertEquals("[{\"c\":[{\"v\":\"Date(1970,0,1,1,0,0)\"},{\"v\":0.125}]},"
				    + "{\"c\":[{\"v\":\"Date(1970,0,1,1,0,1)\"},{\"v\":1}]}]",
				   	rows.toString());

		// Two sensors, sync'ed times
		String[] names2 = {Sensors.ID_POOL_T, Sensors.ID_VERANDA_T};
		out = sensors.getJSONChart(DAYS_PAST, names2);
		assertTrue(out.has("cols"));
		cols = out.getJSONArray("cols");
		assertEquals(3, cols.length());

		assertTrue(out.has("rows"));
		rows = out.getJSONArray("rows");
		assertEquals("[{\"c\":[{\"v\":\"Date(1970,0,1,1,0,0)\"},{\"v\":0.125},{\"v\":0.25}]},"
				    + "{\"c\":[{\"v\":\"Date(1970,0,1,1,0,1)\"},{\"v\":1},{\"v\":2}]}]",
				    rows.toString());

		// Two sensors, unsync'ed end
		dataPool.addValue(new Date(2000), 10.0f);
		String[] names3 = {Sensors.ID_POOL_T, Sensors.ID_VERANDA_T};
		out = sensors.getJSONChart(DAYS_PAST, names3);
		rows = out.getJSONArray("rows");
		assertEquals("[{\"c\":[{\"v\":\"Date(1970,0,1,1,0,0)\"},{\"v\":0.125},{\"v\":0.25}]},"
			    + "{\"c\":[{\"v\":\"Date(1970,0,1,1,0,1)\"},{\"v\":1},{\"v\":2}]},"
			    + "{\"c\":[{\"v\":\"Date(1970,0,1,1,0,2)\"},{\"v\":10},{\"v\":2}]}]",
			    rows.toString());

		// Two sensors, unsync'ed begin
		dataPool.clearData();
		dataPool.addValue(new Date(500), 0.125f);
		dataPool.addValue(new Date(1500), 1.0f);
		String[] names4 = {Sensors.ID_POOL_T, Sensors.ID_VERANDA_T};
		out = sensors.getJSONChart(DAYS_PAST, names4);
		assertTrue(out.has("cols"));
		cols = out.getJSONArray("cols");
		assertEquals(3, cols.length());

		assertTrue(out.has("rows"));
		rows = out.getJSONArray("rows");
		assertEquals("[{\"c\":[{\"v\":\"Date(1970,0,1,1,0,0)\"},{\"v\":0.125},{\"v\":0.25}]},"
				    + "{\"c\":[{\"v\":\"Date(1970,0,1,1,0,0)\"},{\"v\":0.125},{\"v\":2}]}]",
				    rows.toString());
	}
}