package com.remi.remidomo.test;

import com.remi.remidomo.RDService;

import android.content.Intent;
import android.os.IBinder;
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
		assertEquals("msg1\nmsg2 (x2)", dummy.getLogMessages());
	}
	
	public void testLog2() {
		RDService dummy = new RDService();
		dummy.clearLog();
		dummy.addLog("msg1");
		dummy.addLog("msg2");
		dummy.addLog("msg1");
		assertEquals("msg1\nmsg2\nmsg1", dummy.getLogMessages());
	}
	
	public void testLog3() {
		RDService dummy = new RDService();
		dummy.clearLog();
		dummy.addLog("msg1");
		for (int i=0; i<10; i++) {
			dummy.addLog("msg2");
		}
		assertEquals("msg1\nmsg2 (x10)", dummy.getLogMessages());
	}
	
	public void testLog4() {
		RDService dummy = new RDService();
		dummy.clearLog();
		dummy.addLog("msg1");
		for (int i=0; i<10; i++) {
			dummy.addLog("Mémàçon");
		}
		assertEquals("msg1\nMémàçon (x10)", dummy.getLogMessages());
	}
	
	public void testLog5() {
		RDService dummy = new RDService();
		dummy.clearLog();
		dummy.addLog("msg1");
		for (int i=0; i<10; i++) {
			dummy.addLog("msg2 (en +)");
		}
		assertEquals("msg1\nmsg2 (en +) (x10)", dummy.getLogMessages());
	}
}
