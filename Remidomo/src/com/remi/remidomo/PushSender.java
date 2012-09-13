package com.remi.remidomo;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;


import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.MulticastResult;
import com.google.android.gcm.server.Sender;

import android.util.Log;

public class PushSender {
	
	private final static String TAG = RDService.class.getSimpleName();
	
	public final static String TARGET = "target";
	public final static String ID = "id";
	public final static String STATE = "state";
	public final static String KEY = "key";
	public final static String TSTAMP = "tstamp";

	private final static String GCM_API_KEY = "AIzaSyBBCNs05kxAwiQntfBsga-PujphIsy79AY";

	// Constants for target
	public final static String SWITCH = "switch";
	public final static String DOOR = "door";
	public final static String LOWBAT = "lowbat";
	public final static String POWER_DROP = "power_drop";
	public final static String POWER_RESTORE = "power_restore";
	public final static String MISSING_SENSOR = "missing_sensor";

	private RDService service = null;

	private SecureRandom random = new SecureRandom();

	private Sender sender;

	public PushSender(RDService service) {
		this.service = service;
		sender = new Sender(GCM_API_KEY);
	}
	
	public void pushMsg(List<String> regIds, String target, int index, String state) {

		final String UTF8 = "UTF-8";

		if (regIds.isEmpty()) {
			return;
		}

		try {
			String uniqueKey = new BigInteger(130, random).toString(32);

			Message message = new Message.Builder()
			.collapseKey(STATE)
			.delayWhileIdle(false)
			.addData(TARGET, target)
			.addData(ID, Integer.toString(index))
			.addData(STATE, URLEncoder.encode(state, UTF8))
			.addData(KEY, URLEncoder.encode(uniqueKey, UTF8))
			.addData(TSTAMP, Long.toString(new Date().getTime()))
			.build();

			MulticastResult result = sender.send(message, regIds, 1);
			if (result.getFailure() > 0) {
				service.addLog("Erreur envoi push: " + result.toString(), RDService.LogLevel.HIGH);
			}
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, "Encoding error: " + e);
		} catch (java.io.IOException e) {
			Log.e(TAG, "IO: " + e);
		}
	}
}