package com.remi.remidomo.test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ServiceTestCase;
import android.util.Log;

import com.remi.remidomo.reloaded.Doors;
import com.remi.remidomo.reloaded.Energy;
import com.remi.remidomo.reloaded.RDService;
import com.remi.remidomo.reloaded.SensorData;
import com.remi.remidomo.reloaded.Sensors;
import com.remi.remidomo.reloaded.ServerThread;
import com.remi.remidomo.reloaded.prefs.PrefsService;

public class ServerTest extends ServiceTestCase<RDService> {
	
	private final static String URL = "http://localhost:" + PrefsService.DEFAULT_PORT;

	public ServerTest() {
		super(RDService.class);
	}

	public void start() {
        // Clear sensor files
        final String APP_PATH = "/data/data/com.remi.remidomo/files/";       
        String[] files = { Energy.ID_ENERGY, Energy.ID_POWER,
				  		   Sensors.ID_EXT_T, Sensors.ID_EXT_H,
				  		   Sensors.ID_VERANDA_T, Sensors.ID_VERANDA_T,
				  		   Sensors.ID_POOL_T };
        for (String file: files) {
        	File f = new File(APP_PATH+file+".dat");
        	f.delete();
        }

        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), RDService.class);
        startService(startIntent);

        // Clear preferences
		RDService service = getService();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(service);
		SharedPreferences.Editor editor = prefs.edit();
		editor.clear();
		editor.commit();

		// Restart service, now that prefs were cleared
        startService(startIntent);
	}

	public void testServedLog() throws ClientProtocolException, URISyntaxException, IOException, ParserConfigurationException, SAXException, InterruptedException {
		start();
		RDService service = getService();
		assertNotNull(service);

		ServerThread server = new ServerThread(service);
		server.start();

		// This returns HTML
		String content = getFromServer(URL+"/log");
		assertTrue(content.contains("Service (re)d&#233;marr&#233;"));
		assertTrue(content.contains("Requete HTTP re&#231;ue (log)"));
	}
	
	public void testServedImages() throws ClientProtocolException, URISyntaxException, IOException {
		start();
		RDService service = getService();
		assertNotNull(service);

		ServerThread server = new ServerThread(service);
		server.start();
		
		String content = getFromServer(URL+"/img/unknown");
		assertTrue(content.contains("PNG")); // serves a black img

		content = getFromServer(URL+"/img/poolplot");
		assertTrue(content.contains("PNG"));

		content = getFromServer(URL+"/img/thermoplot");
		assertTrue(content.contains("PNG"));
		
		content = getFromServer(URL+"/img/powerplot");
		assertTrue(content.contains("PNG"));
	}

	public void testServedDashboard() throws ClientProtocolException, URISyntaxException, IOException, JSONException, InterruptedException {
		start();
		RDService service = getService();
		assertNotNull(service);

		ServerThread server = new ServerThread(service);
		server.start();

		// Unknown values everywhere
		JSONObject json = new JSONObject(getFromServer(URL+"/dashboard"));
		assertNotNull(json);
		JSONObject energy = json.getJSONObject("energy");
		assertNotNull(energy);
		assertEquals("?", energy.get("power"));
		assertEquals("0,0", energy.get("energy"));
		assertTrue(energy.has("tarif"));
		JSONObject thermo = json.getJSONObject("thermo");
		assertNotNull(thermo);
		assertTrue(thermo.has("veranda"));
		assertEquals("?", thermo.getJSONObject("veranda").get("temperature"));
		assertEquals("?", thermo.getJSONObject("veranda").get("humidity"));
		assertTrue(thermo.has("ext"));
		assertEquals("?", thermo.getJSONObject("ext").get("temperature"));
		assertEquals("?", thermo.getJSONObject("ext").get("humidity"));
		assertTrue(thermo.has("pool"));
		assertEquals("?", thermo.getJSONObject("pool").get("temperature"));
		assertFalse(thermo.getJSONObject("pool").has("humidity"));

		// Add data
		Sensors sensors = service.getSensors();
		SensorData dataPool = sensors.getData(Sensors.ID_POOL_T);
		SensorData dataVeranda = sensors.getData(Sensors.ID_VERANDA_T);
		SensorData dataExt = sensors.getData(Sensors.ID_EXT_T);
		SensorData dataPower = service.getEnergy().getPowerData();

		dataPool.addValue(new Date(0), 12.34f);
		dataVeranda.addValue(new Date(0), 34.56f);
		dataExt.addValue(new Date(0), 56.78f);
		dataPower.addValue(new Date(0), 123.456f);
		Thread.sleep(5);

		json = new JSONObject(getFromServer(URL+"/dashboard"));
		assertNotNull(json);
		energy = json.getJSONObject("energy");
		assertNotNull(energy);
		assertEquals("123,456", energy.get("power"));
		assertEquals("0,0", energy.get("energy"));
		assertTrue(energy.has("tarif"));
		thermo = json.getJSONObject("thermo");
		assertNotNull(thermo);
		assertTrue(thermo.has("veranda"));
		assertEquals("34,56", thermo.getJSONObject("veranda").get("temperature"));
		assertEquals("?", thermo.getJSONObject("veranda").get("humidity"));
		assertTrue(thermo.has("ext"));
		assertEquals("56,78", thermo.getJSONObject("ext").get("temperature"));
		assertEquals("?", thermo.getJSONObject("ext").get("humidity"));
		assertTrue(thermo.has("pool"));
		assertEquals("12,34", thermo.getJSONObject("pool").get("temperature"));
		assertFalse(thermo.getJSONObject("pool").has("humidity"));
		
	}

	public void testServedSwitches() throws ClientProtocolException, URISyntaxException, IOException, JSONException, InterruptedException {
		start();
		RDService service = getService();
		assertNotNull(service);

		ServerThread server = new ServerThread(service);
		server.start();

		String content = getFromServer(URL+"/switches");
		assertEquals("ERROR: missing parameters", content);

		//service.getSwitches().setState(0, false);
		//Thread.sleep(4);
		JSONArray json = new JSONArray(getFromServer(URL+"/switches/query"));
		assertNotNull(json);
		assertEquals(1, json.length());
		assertFalse(json.getBoolean(0));
	}

	public void testServedDoors() throws ClientProtocolException, URISyntaxException, IOException, JSONException {
		start();
		RDService service = getService();
		assertNotNull(service);

		ServerThread server = new ServerThread(service);
		server.start();

		JSONObject json = new JSONObject(getFromServer(URL+"/doors"));
		assertNotNull(json);
		assertTrue(json.has("history"));

		JSONArray current = json.getJSONArray("current");
		assertEquals(Doors.MAX_DOORS, current.length());
		assertEquals(Doors.State.UNKNOWN.toString(), current.get(0));
	}

	public void testServedEnergy() throws ClientProtocolException, URISyntaxException, IOException, JSONException, InterruptedException {
		start();
		RDService service = getService();
		assertNotNull(service);

		ServerThread server = new ServerThread(service);
		server.start();

		// Unknown values everywhere
		JSONObject json = new JSONObject(getFromServer(URL+"/energy"));
		assertNotNull(json);
		assertTrue(json.has("initial_tstamp"));
		assertEquals(-1.0, json.getDouble("initial_energy"));
		assertTrue(json.getBoolean("status"));
		assertEquals(0, json.getJSONArray("power").length());
		assertEquals(0, json.getJSONArray("energy").length());

		// Add data
		SensorData dataPower = service.getEnergy().getPowerData();
		SensorData dataEnergy = service.getEnergy().getEnergyData();
		dataPower.addValue(new Date(0), 123.456f);
		dataEnergy.addValue(new Date(0), 456.789f);
		Thread.sleep(2);

		json = new JSONObject(getFromServer(URL+"/energy"));
		assertNotNull(json);
		assertTrue(json.has("initial_tstamp"));
		assertEquals(-1.0, json.getDouble("initial_energy"));
		assertTrue(json.getBoolean("status"));
		JSONArray power = json.getJSONArray("power");
		assertEquals(1, power.length());
		assertTrue(power.getJSONArray(0).getLong(0) < 10);
		assertTrue(Math.abs(123.456f - power.getJSONArray(0).getDouble(1)) < 1.0e-6);
		JSONArray energy = json.getJSONArray("energy");
		assertEquals(1, energy.length());
		assertTrue(energy.getJSONArray(0).getLong(0) < 10);
		assertTrue(Math.abs(456.789f - energy.getJSONArray(0).getDouble(1)) < 1.0e-6);
		
		// Reset energy counter
		service.getEnergy().resetEnergyCounter();
		json = new JSONObject(getFromServer(URL+"/energy"));
		assertNotNull(json);
		assertTrue(json.getLong("initial_tstamp") > new Date().getTime()-10000);
		assertTrue(Math.abs(456.789f - json.getDouble("initial_energy")) < 1.0e-6);
	}

	public void testServedConfig() throws ClientProtocolException, URISyntaxException, IOException, JSONException {
		start();
		RDService service = getService();
		assertNotNull(service);

		ServerThread server = new ServerThread(service);
		server.start();

		String content = getFromServer(URL+"/config");
		assertEquals("ERROR: missing parameters", content);

		content = getFromServer(URL+"/config/test");
		assertEquals("ERROR: missing parameters", content);

		content = getFromServer(URL+"/config?test");
		assertEquals("ERROR: missing parameters", content);

		content = getFromServer(URL+"/config?days");
		assertEquals("ERROR: missing parameters", content);

		content = getFromServer(URL+"/config?days=");
		assertEquals("ERROR: missing parameters", content);

		content = getFromServer(URL+"/config?days=13");
		assertTrue(content.contains("<html>"));
	}

	public void testServedPushReg() throws ClientProtocolException, URISyntaxException, IOException, JSONException {
		start();
		RDService service = getService();
		assertNotNull(service);

		ServerThread server = new ServerThread(service);
		server.start();

		String content = getFromServer(URL+"/pushreg");
		assertEquals("ERROR: missing parameters", content);

		content = getFromServer(URL+"/pushreg/test");
		assertEquals("ERROR: missing parameters", content);

		content = getFromServer(URL+"/pushreg?test");
		assertEquals("ERROR: missing parameters", content);

		content = getFromServer(URL+"/pushreg?key");
		assertEquals("ERROR: missing parameters", content);

		content = getFromServer(URL+"/pushreg?key=");
		assertEquals("ERROR: missing parameters", content);

		content = getFromServer(URL+"/pushreg?key=1234");
		assertEquals("OK", content);
	}

	private String getFromServer(String url) throws URISyntaxException, ClientProtocolException, IOException {
		HttpClient client = new DefaultHttpClient();
		HttpGet request = new HttpGet();

		request.setURI(new URI(url));
		return client.execute(request, new BasicResponseHandler());
	}
}