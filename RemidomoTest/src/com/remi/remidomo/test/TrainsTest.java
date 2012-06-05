package com.remi.remidomo.test;

import org.json.JSONObject;

import com.remi.remidomo.Trains;

import android.test.AndroidTestCase;

public class TrainsTest extends AndroidTestCase {

	public void testEmpty1() {
		try {
			JSONObject json1 = new JSONObject("{\"D\":[{\"num\":\"883748\",\"heure\":\"H1\"}]}");
			JSONObject json2 = new JSONObject();
			JSONObject result = Trains.filterCommonData(json1, json2);
			assert("{\"D\":[{\"num\":\"883748\",\"heure\":\"H1\"}]}".equals(result.toString()));
		} catch (org.json.JSONException e) {
			fail();
		}
	}
	
	public void testEmpty2() {
		try {
			JSONObject json1 = new JSONObject();
			JSONObject json2 = new JSONObject("{\"D\":[{\"num\":\"883748\",\"heure\":\"H1\"}]}");
			JSONObject result = Trains.filterCommonData(json1, json2);
			assertTrue(result.length() == 0);
		} catch (org.json.JSONException e) {
			fail();
		}
	}
	
	public void testNoMatch() {
		try {
			JSONObject json1 = new JSONObject("{\"D\":[{\"num\":\"883748\",\"heure\":\"H1\"}]}");
			JSONObject json2 = new JSONObject("{\"A\":[{\"num\":\"883749\",\"heure\":\"H2\"}]}");
			JSONObject result = Trains.filterCommonData(json1, json2);
			assertEquals("{\"D\":[]}", result.toString());
		} catch (org.json.JSONException e) {
			fail();
		}		
	}
	
	public void testSimpleMatch() {
		try {
			JSONObject json1 = new JSONObject("{\"D\":[{\"num\":\"883748\",\"heure\":\"H1\"}]}");
			JSONObject json2 = new JSONObject("{\"A\":[{\"num\":\"883748\",\"heure\":\"H2\"}]}");
			JSONObject result = Trains.filterCommonData(json1, json2);
			assert("{\"D\":[{\"num\":\"883748\",\"heure\":\"H1\"}]}".equals(result.toString()));
		} catch (org.json.JSONException e) {
			fail();
		}		
	}
	
	public void testComplexMatch() {
		try {
			JSONObject json1 = new JSONObject("{\"D\":[{\"num\":\"883748\",\"heure\":\"H1\"},{\"num\":\"883749\",\"heure\":\"H2\"},{\"num\":\"883750\",\"heure\":\"H3\"},{\"num\":\"883751\",\"heure\":\"H4\"}]}");
			JSONObject json2 = new JSONObject("{\"A\":[{\"num\":\"883748\",\"heure\":\"H1b\"},{\"num\":\"783748\",\"heure\":\"H2b\"},{\"num\":\"883750\",\"heure\":\"H3b\"},{\"num\":\"783751\",\"heure\":\"H4b\"}]}");
			JSONObject result = Trains.filterCommonData(json1, json2);
			assert("{\"D\":[{\"num\":\"883748\",\"heure\":\"H1\"},{\"num\":\"883750\",\"heure\":\"H3\"},]}".equals(result.toString()));
		} catch (org.json.JSONException e) {
			fail();
		}		
	}
}