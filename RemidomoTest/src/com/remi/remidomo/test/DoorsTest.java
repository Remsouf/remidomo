package com.remi.remidomo.test;

import java.util.ArrayList;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.test.ServiceTestCase;
import android.util.Log;

import com.remi.remidomo.Doors;
import com.remi.remidomo.RDService;
import com.remi.remidomo.xPLMessage;
import com.remi.remidomo.Doors.Event;
import com.remi.remidomo.xPLMessage.xPLParseException;

public class DoorsTest extends ServiceTestCase<RDService> {

	private final String CLOSED = "0x757a0f";
	private final String OPENED = "0x8f8039";
	private final String XPL_PATTERN = "xpl-trig\n{\nhop=1\nsource=*\ntarget=*\n}\nx10.security\n{\ndevice=%1$s\ncommand=%2$s\n}\n";

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
		xPLMessage msg;

		start();
		RDService service = getService();

		msg = new xPLMessage(String.format(XPL_PATTERN, CLOSED, "normal"));
		service.getDoors().syncWithHardware(msg);
		msg = new xPLMessage(String.format(XPL_PATTERN, OPENED, "normal"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.MOVING, service.getDoors().getState(Doors.GARAGE));
		service.getDoors().clearHistory(Doors.GARAGE);
		assertEquals("{\"history\":[[]],\"current\":[\"MOVING\"]}", service.getDoors().getJSON().toString());

		msg = new xPLMessage(String.format(XPL_PATTERN, CLOSED, "alert"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.CLOSED, service.getDoors().getState(Doors.GARAGE));
		service.getDoors().clearHistory(Doors.GARAGE);
		assertEquals("{\"history\":[[]],\"current\":[\"CLOSED\"]}", service.getDoors().getJSON().toString());

		msg = new xPLMessage(String.format(XPL_PATTERN, CLOSED, "normal"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.MOVING, service.getDoors().getState(Doors.GARAGE));
		service.getDoors().clearHistory(Doors.GARAGE);
		assertEquals("{\"history\":[[]],\"current\":[\"MOVING\"]}", service.getDoors().getJSON().toString());

		msg = new xPLMessage(String.format(XPL_PATTERN, OPENED, "alert"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.OPENED, service.getDoors().getState(Doors.GARAGE));
		service.getDoors().clearHistory(Doors.GARAGE);
		assertEquals("{\"history\":[[]],\"current\":[\"OPENED\"]}", service.getDoors().getJSON().toString());

		msg = new xPLMessage(String.format(XPL_PATTERN, OPENED, "normal"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.MOVING, service.getDoors().getState(Doors.GARAGE));
		service.getDoors().clearHistory(Doors.GARAGE);
		assertEquals("{\"history\":[[]],\"current\":[\"MOVING\"]}", service.getDoors().getJSON().toString());

		msg = new xPLMessage(String.format(XPL_PATTERN, OPENED, "alert"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.OPENED, service.getDoors().getState(Doors.GARAGE));
		service.getDoors().clearHistory(Doors.GARAGE);
		assertEquals("{\"history\":[[]],\"current\":[\"OPENED\"]}", service.getDoors().getJSON().toString());

		msg = new xPLMessage(String.format(XPL_PATTERN, CLOSED, "alert"));
		service.getDoors().syncWithHardware(msg);
		assertEquals(Doors.State.ERROR, service.getDoors().getState(Doors.GARAGE));
		service.getDoors().clearHistory(Doors.GARAGE);
		assertEquals("{\"history\":[[]],\"current\":[\"ERROR\"]}", service.getDoors().getJSON().toString());
	}

	public void testHistory() throws xPLParseException, JSONException {
		xPLMessage msg;

		start();
		RDService service = getService();

		long now = new Date().getTime();

		// OPENED
		msg = new xPLMessage(String.format(XPL_PATTERN, CLOSED, "normal"));
		service.getDoors().syncWithHardware(msg);

		// MOVING
		msg = new xPLMessage(String.format(XPL_PATTERN, OPENED, "normal"));
		service.getDoors().syncWithHardware(msg);

		// CLOSED
		msg = new xPLMessage(String.format(XPL_PATTERN, CLOSED, "alert"));
		service.getDoors().syncWithHardware(msg);

		// ERROR
		msg = new xPLMessage(String.format(XPL_PATTERN, OPENED, "alert"));
		service.getDoors().syncWithHardware(msg);

		ArrayList<Event> eventHistory = service.getDoors().getHistory(Doors.GARAGE);
		assertEquals(Doors.State.OPENED, eventHistory.get(0).state);
		assertEquals(Doors.State.MOVING, eventHistory.get(1).state);
		assertEquals(Doors.State.CLOSED, eventHistory.get(2).state);
		assertEquals(Doors.State.ERROR, eventHistory.get(3).state);

		JSONObject dict = service.getDoors().getJSON();
		assertTrue(dict.has("current"));
		assertEquals("[\"ERROR\"]", dict.getJSONArray("current").toString());

		assertTrue(dict.has("history"));
		JSONArray hist = dict.getJSONArray("history");
		JSONArray garage = hist.getJSONArray(Doors.GARAGE);

		JSONArray ev = garage.getJSONArray(0);
		assertEquals("OPENED", ev.get(0).toString());
		assertTrue((ev.getLong(1) - now) < 1000);

		ev = garage.getJSONArray(1);
		assertEquals("MOVING", ev.get(0).toString());
		assertTrue((ev.getLong(1) - now) < 1000);

		ev = garage.getJSONArray(2);
		assertEquals("CLOSED", ev.get(0).toString());
		assertTrue((ev.getLong(1) - now) < 1000);

		ev = garage.getJSONArray(3);
		assertEquals("ERROR", ev.get(0).toString());
		assertTrue((ev.getLong(1) - now) < 1000);
	}

}