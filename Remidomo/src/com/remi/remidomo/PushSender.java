package com.remi.remidomo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Date;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class PushSender {
	
	private final static String TAG = RDService.class.getSimpleName();
	
	public final static String TARGET = "target";
	public final static String ID = "id";
	public final static String STATE = "state";
	public final static String KEY = "key";
	public final static String TSTAMP = "tstamp";

	private RDService service = null;

	private String authenticationToken = null;
	
	private SecureRandom random = new SecureRandom();

	public PushSender(RDService service) {
		this.service = service;
		getAuthenticationToken();
	}
	
	public void pushMsg(String registrationKey, String target, int index, String state) {
		final String PARAM_REGISTRATION_ID = "registration_id";
		final String PARAM_COLLAPSE_KEY = "collapse_key";
		final String PARAM_DELAY_IDLE = "delay_while_idle";
		final String UTF8 = "UTF-8";

		if (authenticationToken == null) {
			Log.e(TAG, "Missing authentication token");
			service.addLog("Push: token d'authentification manquant");
			return;
		}

		try {
			String uniqueKey = new BigInteger(130, random).toString(32);

			// Note: use state as collapse key, to avoid attenuation
			// for bursts of messages
			StringBuilder postDataBuilder = new StringBuilder();
			postDataBuilder.append(PARAM_REGISTRATION_ID).append("=").append(registrationKey);
			postDataBuilder.append("&").append(PARAM_COLLAPSE_KEY).append("=").append(STATE);
			// postDataBuilder.append("&").append(PARAM_DELAY_IDLE).append("=").append("0");
			postDataBuilder.append("&").append("data."+TARGET).append("=").append(URLEncoder.encode(target, UTF8));
			postDataBuilder.append("&").append("data."+ID).append("=").append(Integer.toString(index));
			postDataBuilder.append("&").append("data."+STATE).append("=").append(URLEncoder.encode(state, UTF8));
			postDataBuilder.append("&").append("data."+KEY).append("=").append(URLEncoder.encode(uniqueKey, UTF8));
			postDataBuilder.append("&").append("data."+TSTAMP).append("=").append(new Date().getTime());
			byte[] postData = postDataBuilder.toString().getBytes(UTF8);

			// Hit the dm URL.

			URL url = new URL("https://android.clients.google.com/c2dm/send");
			HttpsURLConnection.setDefaultHostnameVerifier(new CustomizedHostnameVerifier());
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setUseCaches(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset="+UTF8);
			conn.setRequestProperty("Content-Length", Integer.toString(postData.length));
			conn.setRequestProperty("Authorization", "GoogleLogin auth=" + authenticationToken);

			OutputStream out = conn.getOutputStream();
			out.write(postData);
			out.close();

			int responseCode = conn.getResponseCode();
			if (responseCode != 200) {
				service.addLog("Erreur envoi push: " + responseCode);
			}
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, "Encoding error: " + e);
		} catch (java.net.MalformedURLException e) {
			Log.e(TAG, "Malformed URL: " + e);
		} catch (java.net.ProtocolException e) {
			Log.e(TAG, "Malformed URL: " + e);
		} catch (java.io.IOException e) {
			Log.e(TAG, "IO: " + e);
		}
	}
	
	private static class CustomizedHostnameVerifier implements HostnameVerifier {
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	}

	public void getAuthenticationToken() {

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(service);
		String account = prefs.getString("c2dm_account", "");
		String password = prefs.getString("c2dm_password", "");

		// Create the post data
		// Requires a field with the email and the password
		StringBuilder builder = new StringBuilder();
		builder.append("Email=").append(account);
		builder.append("&Passwd=").append(password);
		builder.append("&accountType=GOOGLE");
		builder.append("&source=com.remi.remidomo");
		builder.append("&service=ac2dm");

		authenticationToken = null;

		try {
			// Setup the Http Post
			byte[] data = builder.toString().getBytes();
			URL url = new URL("https://www.google.com/accounts/ClientLogin");
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setUseCaches(false);
			con.setDoOutput(true);
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			con.setRequestProperty("Content-Length", Integer.toString(data.length));

			// Issue the HTTP POST request
			OutputStream output = con.getOutputStream();
			output.write(data);
			output.close();

			// Read the response
			BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("Auth=")) {
					authenticationToken = line.substring(5);
					Log.d(TAG, "Got push authentication token");
					service.addLog("Token authentification push obtenu");
				}
			}

		} catch (java.net.MalformedURLException e) {
			Log.e(TAG, "Malformed URL: " + e);
		} catch (java.net.ProtocolException e) {
			Log.e(TAG, "Malformed URL: " + e);
		} catch (java.io.IOException e) {
			Log.e(TAG, "IO: " + e);
		}
		
		if (authenticationToken == null) {
			Log.e(TAG, "Failed to get authentication token");
			service.addLog("Push: impossible d'obtenir le token d'authentification");
			return;
		}
	}
}