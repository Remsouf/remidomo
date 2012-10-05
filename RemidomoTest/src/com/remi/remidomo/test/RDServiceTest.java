package com.remi.remidomo.test;

import com.remi.remidomo.RDService;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.test.ServiceTestCase;

public class RDServiceTest extends ServiceTestCase<RDService> {
	
	public RDServiceTest() {
		super(RDService.class);
	}

	public void start() {
		Intent startIntent = new Intent();
        startIntent.setClass(getContext(), RDService.class);
        startService(startIntent);
	}
	
    public void testStartable() {
        start();
        assertNotNull(getService());
    }

    public void testBindable() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), RDService.class);
        IBinder service = bindService(startIntent);
        assertNotNull(service);
    }
	    
	public void testLog1() {
		RDService dummy = new RDService();
		dummy.clearLog();
		dummy.addLog("msg1");
		dummy.addLog("msg2");
		dummy.addLog("msg2");
		assertEquals("msg1\nmsg2 (x2)\n", dummy.getLogMessages().toString());
	}
	
	public void testLog2() {
		RDService dummy = new RDService();
		dummy.clearLog();
		dummy.addLog("msg1");
		dummy.addLog("msg2");
		dummy.addLog("msg1");
		assertEquals("msg1\nmsg2\nmsg1\n", dummy.getLogMessages().toString());
	}
	
	public void testLog3() {
		RDService dummy = new RDService();
		dummy.clearLog();
		dummy.addLog("msg1");
		for (int i=0; i<10; i++) {
			dummy.addLog("msg2");
		}
		assertEquals("msg1\nmsg2 (x10)\n", dummy.getLogMessages().toString());
	}
	
	public void testLog4() {
		RDService dummy = new RDService();
		dummy.clearLog();
		dummy.addLog("msg1");
		for (int i=0; i<10; i++) {
			dummy.addLog("Mémàçon");
		}
		assertEquals("msg1\nMémàçon (x10)\n", dummy.getLogMessages().toString());
	}
	
	public void testLog5() {
		RDService dummy = new RDService();
		dummy.clearLog();
		dummy.addLog("msg1");
		for (int i=0; i<10; i++) {
			dummy.addLog("msg2 (en +)");
		}
		assertEquals("msg1\nmsg2 (en +) (x10)\n", dummy.getLogMessages().toString());
	}

	public void testPower() throws InterruptedException {
        start();

        // Prevent from running on a real target
        // (would remove preferences and files)
        assertEquals("Run on emulator !", "sdk", Build.PRODUCT);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getService());
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.putString("mode", "Serveur");
        editor.commit();

        assertTrue(getService().getEnergy().isPoweredOn());

        Intent down = new Intent(RDService.ACTION_POWERDISCONNECT);
        startService(down);
        assertFalse(getService().getEnergy().isPoweredOn());

        Intent up = new Intent(RDService.ACTION_POWERCONNECT);
        startService(up);
        assertTrue(getService().getEnergy().isPoweredOn());
	}
}
