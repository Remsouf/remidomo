package com.remi.remidomo.test;

import org.json.JSONArray;

import android.content.Intent;
import android.test.ServiceTestCase;

import com.remi.remidomo.Doors;
import com.remi.remidomo.RDService;
import com.remi.remidomo.xPLMessage;
import com.remi.remidomo.xPLMessage.xPLParseException;

public class DoorsTest extends ServiceTestCase<RDService> {

	public DoorsTest() {
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

	public void testxPLSequence() throws xPLParseException {
		final String CLOSED = "0xb5baf7";
		final String OPENED = "0x0f00d9";
		final String XPL_PATTERN = "xpl-trig\n{\nhop=1\nsource=*\ntarget=*\n}\nx10.security\n{\ndevice=%1$s\ncommand=%2$s\n}\n";
		xPLMessage msg;

		start();
		RDService service = getService();

		msg = new xPLMessage(String.format(XPL_PATTERN, CLOSED, "alert"));
		service.getDoors().syncWithHardware(msg);
		msg = new xPLMessage(String.format(XPL_PATTERN, OPENED, "alert"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.MOVING, service.getDoors().getState(Doors.GARAGE));
		assertEquals("[\"MOVING\"]", service.getDoors().getJSONArray().toString());

		msg = new xPLMessage(String.format(XPL_PATTERN, CLOSED, "normal"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.CLOSED, service.getDoors().getState(Doors.GARAGE));
		assertEquals("[\"CLOSED\"]", service.getDoors().getJSONArray().toString());
		
		msg = new xPLMessage(String.format(XPL_PATTERN, CLOSED, "alert"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.MOVING, service.getDoors().getState(Doors.GARAGE));
		assertEquals("[\"MOVING\"]", service.getDoors().getJSONArray().toString());
		
		msg = new xPLMessage(String.format(XPL_PATTERN, OPENED, "normal"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.OPENED, service.getDoors().getState(Doors.GARAGE));
		assertEquals("[\"OPENED\"]", service.getDoors().getJSONArray().toString());
		
		msg = new xPLMessage(String.format(XPL_PATTERN, OPENED, "alert"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.MOVING, service.getDoors().getState(Doors.GARAGE));
		assertEquals("[\"MOVING\"]", service.getDoors().getJSONArray().toString());
		
		msg = new xPLMessage(String.format(XPL_PATTERN, OPENED, "normal"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.OPENED, service.getDoors().getState(Doors.GARAGE));
		assertEquals("[\"OPENED\"]", service.getDoors().getJSONArray().toString());
		
		msg = new xPLMessage(String.format(XPL_PATTERN, CLOSED, "normal"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.UNKNOWN, service.getDoors().getState(Doors.GARAGE));
		assertEquals("[\"UNKNOWN\"]", service.getDoors().getJSONArray().toString());
	}
	
}