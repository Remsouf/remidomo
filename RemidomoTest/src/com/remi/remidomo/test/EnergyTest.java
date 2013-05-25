package com.remi.remidomo.test;

import java.io.File;

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
import com.remi.remidomo.reloaded.data.xPLMessage;

public class EnergyTest extends ServiceTestCase<RDService> {
	
	public EnergyTest() {
		super(RDService.class);
	}

	public void start() {
		Intent startIntent = new Intent();
        startIntent.setClass(getContext(), RDService.class);
        startService(startIntent);

        // Prevent from running on a real target
     	// (would remove preferences and files)
        assertEquals("Run on emulator !", "sdk", Build.PRODUCT);

        // Clear sensor files
        final String APP_PATH = "/data/data/com.remi.remidomo/files/";       
        String[] files = { Energy.ID_ENERGY, Energy.ID_POWER };
        for (String file: files) {
        	File f = new File(APP_PATH+file+".dat");
        	f.delete();
        }

        // Clear preferences
		RDService service = getService();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(service);
		SharedPreferences.Editor editor = prefs.edit();
		editor.clear();
		editor.commit();
	}

	public void testNominal() throws xPLMessage.xPLParseException, InterruptedException, JSONException {
		xPLMessage msg;

		start();
		RDService service = getService();
		Energy energy = new Energy(service);
		while (!energy.isReadyForUpdates()) {
			Thread.sleep(500); // Time to read files, even if none
		}
		assertNotNull(energy.getPowerData());

		msg = new xPLMessage("xpl-trig\n{\nhop=1\nsource=test\ntarget=*\n}\nsensor.basic\n{\ndevice=elec2 0xffff\ntype=energy\ncurrent=123.456\nunits=kwh\n}\n");
		energy.updateData(service, msg);
		assertEquals(0.0f, energy.getEnergyValue());

		msg = new xPLMessage("xpl-trig\n{\nhop=1\nsource=test\ntarget=*\n}\nsensor.basic\n{\ndevice=elec2 0xffff\ntype=power\ncurrent=0.123\nunits=kw\n}\n");
		energy.updateData(service, msg);
		Thread.sleep(500);
		assertEquals(1, energy.getPowerData().size());
		assertEquals(0.123f, energy.getPowerData().get(0).value, 1.0e-6);

		JSONObject json = energy.getJSON(0);
		assertTrue(json.has("initial_tstamp"));
		assertEquals(-1.0, json.getDouble("initial_energy"), 1.0e-6);
		JSONArray array = json.getJSONArray("power");
		assertEquals(0.123, array.getJSONArray(0).getDouble(1), 1.0e-6);
		array = json.getJSONArray("energy");
		assertEquals(123.456, array.getJSONArray(0).getDouble(1), 1.0e-3);
		assertTrue(json.getBoolean("status"));
	}
	
	public void testEnergy() throws xPLMessage.xPLParseException, JSONException, InterruptedException {
		xPLMessage msg;

		start();
		RDService service = getService();
		Energy energy = new Energy(service);
		while (!energy.isReadyForUpdates()) {
			Thread.sleep(500); // Time to read files, even if none
		}

		// Prevent from running on a real target
		// (would touch persisted preferences)
		assertEquals("Run on emulator !", "sdk", Build.PRODUCT);

		msg = new xPLMessage("xpl-trig\n{\nhop=1\nsource=test\ntarget=*\n}\nsensor.basic\n{\ndevice=elec2 0xffff\ntype=energy\ncurrent=123.456\nunits=kwh\n}\n");
		energy.updateData(service, msg);
		assertEquals(0.0f, energy.getEnergyValue());
		JSONObject json = energy.getJSON(0);
		assertEquals(-1.0, json.getDouble("initial_energy"), 1.0e-6);

		energy.resetEnergyCounter();
		assertEquals(0.0f, energy.getEnergyValue());
		json = energy.getJSON(0);
		assertEquals(123.456, json.getDouble("initial_energy"), 1.0e-3);

		msg = new xPLMessage("xpl-trig\n{\nhop=1\nsource=test\ntarget=*\n}\nsensor.basic\n{\ndevice=elec2 0xffff\ntype=energy\ncurrent=456.789\nunits=kwh\n}\n");
		energy.updateData(service, msg);
		assertEquals(333.333f, energy.getEnergyValue(), 1.0e-6);

		json = energy.getJSON(0);
		assertEquals(123.456, json.getDouble("initial_energy"), 1.0e-3);
	}
}