package com.remi.remidomo.test;

import java.io.File;
import java.util.Date;

import com.remi.remidomo.Energy;
import com.remi.remidomo.RDService;
import com.remi.remidomo.SensorData;
import com.remi.remidomo.Sensors;
import com.remi.remidomo.xPLMessage;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.test.ServiceTestCase;
import android.util.Log;

public class ThreadingTest extends ServiceTestCase<RDService> {
	
	private RDService service;

	public ThreadingTest() {
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

    private class ReaderThread implements Runnable {
    	public void run() {
    		assertNotNull(service);
    		long initialTime = new Date().getTime() - 1000*60;

    		while (true) {
    			Sensors sensors = service.getSensors();
    			SensorData data = sensors.getData(Sensors.ID_POOL_T);
    			assertTrue(data.size() > 0);
    			for (int i=0; i<data.size(); i++) {
    				float val = sensors.getData(Sensors.ID_POOL_T).get(i).time;
    				assertTrue(val > initialTime);
    			}

    			sensors.checkSensorsConsistency();
    		}
    	}
    }

    private class WriterThread implements Runnable {
    	
    	public void run() {
    		assertNotNull(service);
    		
    		while (true) {
    			Sensors sensors = service.getSensors();

    			xPLMessage msg = null;
    			try {
    				msg = new xPLMessage("xpl-trig\n{\nhop=1\nsource=test\ntarget=*\n}\nsensor.basic\n{\ndevice=temp3 0xdead\ntype=temp\ncurrent=20.0\nunits=c\n}\n");
    			} catch (xPLMessage.xPLParseException e) {
    				fail();
    			}

    			// No data compression
    			sensors.updateData(service, msg, false);
    			assertEquals(20.0f, sensors.getData(Sensors.ID_POOL_T).getLast().value);
    		}
    	}
    }

    public void testConcurrent() throws InterruptedException {
    	start();

    	Thread writer = new Thread(new WriterThread(), "writer");
    	writer.start();
    	Thread.sleep(1000); // write something before we read

    	Thread reader = new Thread(new ReaderThread(), "reader");
    	reader.start();

    	Thread.sleep(15000);

    	reader.stop();
    	writer.stop();
    }
}