package com.remi.remidomo.reloaded;

import java.net.URI;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import com.google.android.gcm.GCMConstants;
import com.remi.remidomo.common.PushSender;
import com.remi.remidomo.common.prefs.Defaults;
import com.remi.remidomo.reloaded.prefs.PrefsService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.Log;

 
/*
 * This is for the client side, receiving a pushed notification
 */
public class PushReceiver extends BroadcastReceiver {
	
	private final static String TAG = PushReceiver.class.getSimpleName();
	
	// Pref keys
	private SharedPreferences prefs;
	private final static String REGISTRATION_KEY = "registration_key";
	private final static String LAST_MSG_KEY = "last_msg_key";
	
	private Context context;
	private String registrationKey;
	private String lastMsgKey = null;

    @Override
    public void onReceive(final Context context, final Intent intent) {
    	this.context = context;

    	// Do this in a different thread,
    	// because it involves disk operations
    	new Thread(new Runnable() {
			public void run() {
				PushReceiver.this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
				PushReceiver.this.lastMsgKey = prefs.getString(LAST_MSG_KEY, null);
				if (intent.getAction().equals(GCMConstants.INTENT_FROM_GCM_REGISTRATION_CALLBACK)) {
					handleRegistration(intent);
					bounceMessage(intent);
				} else if (intent.getAction().equals(GCMConstants.INTENT_FROM_GCM_MESSAGE)) {
					bounceMessage(intent);
				}
			}
		}).start();
    }
    
    private void handleRegistration(Intent intent) {
	    registrationKey = intent.getStringExtra(GCMConstants.EXTRA_REGISTRATION_ID);
	    if (intent.getStringExtra(GCMConstants.EXTRA_ERROR) != null) {
	        // Registration failed, should try again later.
		    Log.e(TAG, "registration failed: " + intent.getStringExtra(GCMConstants.EXTRA_ERROR));
	    } else if (intent.getStringExtra(GCMConstants.EXTRA_UNREGISTERED) != null) {
	        // unregistration done, new messages from the authorized sender will be rejected
	    	Log.d(TAG, "unregistered");
	    } else if (registrationKey != null) {
	    	Log.d(TAG, "registered");

	    	// Do this in a different thread,
	    	// because it involves disk operations
	    	new Thread(new Runnable() {
				public void run() {
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
					Editor editor = prefs.edit();
					editor.putString(REGISTRATION_KEY, registrationKey);
					editor.commit();

					// Send the registration ID to the 3rd party site that is sending the messages.
					// This should be done in a separate thread.
    		
    				syncWithServer(context, registrationKey);
    			}
    		}).start();

    		// Save registration key
    		
	    }
	}
    
	public void syncWithServer(Context context, String key) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		int port = prefs.getInt("port", Defaults.DEFAULT_PORT);
		String ipAddr = prefs.getString("ip_address", Defaults.DEFAULT_IP);
		Log.d(TAG, "Client push registration connecting to " + ipAddr + ":" + port);
		//service.addLog("Connexion au serveur " + ipAddr + ":" + port + " (Enregistrement Push)");

		try {
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();

			request.setURI(new URI(ipAddr+":"+port+"/pushreg?key=" + key));
			String content = client.execute(request, new BasicResponseHandler());
			Log.d(TAG, "Registration server: " + content);

		} catch (java.net.URISyntaxException e) {
			//service.addLog("Erreur URI serveur: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
			Log.e(TAG, "Bad server URI");
		} catch (org.apache.http.conn.HttpHostConnectException e) {
			//service.addLog("Impossible de se connecter au serveur: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
			Log.e(TAG, "HostConnectException with server: " + e);
		} catch (org.apache.http.client.ClientProtocolException e) {
			//service.addLog("Erreur protocole serveur", RDService.LogLevel.HIGH);
			Log.e(TAG, "ClientProtocolException with server: " + e);
		} catch (java.net.SocketException e) {
			//service.addLog("Serveur non joignable: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
			Log.e(TAG, "SocketException with client: " + e);
		} catch (java.io.IOException e) {
			//service.addLog("Erreur I/O client: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
			Log.e(TAG, "IOException with client: " + e);
		}
	}

	private void bounceMessage(Intent intent) {
		// Bounce intent to the service,
		// if not already received
		String uniqueKey = intent.getStringExtra(PushSender.KEY);
		if ((uniqueKey == null) ||
			(lastMsgKey == null) ||
			!uniqueKey.equals(lastMsgKey)) {
			if (uniqueKey != null) {
				lastMsgKey = uniqueKey;
		    	Editor editor = prefs.edit();
		        editor.putString(LAST_MSG_KEY, uniqueKey);
				editor.commit();
			}
			intent.setClass(context, RDService.class);
			context.startService(intent);
		}
	}

}