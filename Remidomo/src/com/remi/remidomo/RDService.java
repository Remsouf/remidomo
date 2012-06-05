package com.remi.remidomo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class RDService extends Service {

	private final static String TAG = RDService.class.getSimpleName();

	private final static String SYSFS_LEDS = "/sys/power/leds";
		
    private NotificationManager notificationMgr = null;
    
    /* Receiver for network state notifications */
    private BroadcastReceiver wifiBroadcastReceiver;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private final int NOTIFICATION_START = 1;
    private int NOTIFICATION_ALERT = 3; // Not final !
    
    public final static String ACTION_RESTORE_DATA = "com.remi.remidomo.RESTORE_DATA";

    private final IBinder mBinder = new LocalBinder();
    public IUpdateListener callback;
    
    private SharedPreferences prefs;
    
    private PowerManager pwrMgr = null;
    private PowerManager.WakeLock wakeLock = null;
    
    private Thread rfxThread = null;
    private boolean runRfxThread = true;
    private DatagramSocket rfxSocket = null;
    
    private ServerThread serverThread = null;
    private Timer clientTimer = null;
    
    private PushSender pusher = null;
    private String registrationKey = null;
	
	private Trains trains = new Trains();
	private Meteo meteo = new Meteo();
	private Sensors sensors = null;
	private Switches switches = null;
	private Doors doors = null;

    private static final int MAX_LOG_LINES = 200;
    private List<String> log = new ArrayList<String>();
    
    private Set<String> pushDevices = new HashSet<String>();

    /* Broadcast receiver for low battery */
    private BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {          
            pushToClients("lowbat", 0, "");
            Log.i(TAG, "Sending push for low battery !");
        }
    };

    @Override
    public void onCreate() {
        notificationMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (notificationMgr == null) {
            Log.e(TAG, "Failed to get Notification Manager");
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        pwrMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pwrMgr == null) {
        	Log.e(TAG, "Failed to get Power Manager");
        }
       
        wakeLock = pwrMgr.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Remidomo");
        if (wakeLock == null) {
        	Log.e(TAG, "Failed to create WakeLock");
        }

        registerReceiver(batteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_LOW));

        sensors = new Sensors(this);
        switches = new Switches(this);
        doors = new Doors(this);

        // Display a notification about us starting.  We put an icon in the status bar.
        showServiceNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	// In case of crash/restart, intent can be null
    	if ((intent != null) && PushReceiver.REGISTRATION.equals(intent.getAction())) {
    		registrationKey = intent.getStringExtra("registration_id");
    	} else if ((intent != null) && PushReceiver.RECEIVE.equals(intent.getAction())) {
    		// Don't restart, just account for received Push intent
    		handlePushedMessage(intent);
    	} else if ((intent != null) && ACTION_RESTORE_DATA.equals(intent.getAction())) {
    		log.add("Lecture des données depuis la carte SD");
    		new Thread(new Runnable() {
            	public synchronized void run() {
            		sensors.readFromSdcard();
            	};
            }).start();
    	} else {
    		Log.i(TAG, "Start service");
    		addLog("Service (re)démarré");

    		if ((intent != null) && intent.getBooleanExtra("FORCE_RESTART", true)) {
    			// Deep cleaning: push registration
    			registrationKey = null;
    		}

    		// Stop all threads and clean everything
    		cleanObjects();

    		// Start threads
    		String mode = prefs.getString("mode", Preferences.DEFAULT_MODE);
    		if ("Serveur".equals(mode)) {
    			rfxThread = new Thread(new RfxThread());
    			rfxThread.start();

    			serverThread = new ServerThread(this);
    			serverThread.start();

    			if (wakeLock != null) {
    				wakeLock.acquire();
    				addLog("WakeLock acquis");
    				Log.d(TAG, "Acquired wakelock");
    			}

    			pusher = new PushSender(this);
    		} else {
    			clientTimer = new Timer();
    			int period = Integer.parseInt(prefs.getString("client_poll", Preferences.DEFAULT_CLIENT_POLL));
    			// 30s graceful period, to let the service read data from FS,
    			// before attempting updates from the server
    			clientTimer.scheduleAtFixedRate(new ClientTask(this), 30000, period*60*1000);

    			registerPushMessaging();
    		}

    		// Timer for train updates
    		Timer timerTrains = new Timer();
    		int period = Integer.parseInt(prefs.getString("sncf_poll", Preferences.DEFAULT_SNCF_POLL));
    		timerTrains.scheduleAtFixedRate(new TrainsTask(), 1, period*60*1000);

    		// Timer for weather updates
    		Timer timerMeteo = new Timer();
    		period = Integer.parseInt(prefs.getString("meteo_poll", Preferences.DEFAULT_METEO_POLL));
    		timerMeteo.scheduleAtFixedRate(new MeteoTask(), 1, period*60*60*1000);
    	}
    
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
    
    private void cleanObjects() {
    	if ((wakeLock != null) && (wakeLock.isHeld())) {
    		wakeLock.release();
    		addLog("WakeLock libéré");
        	Log.d(TAG, "Released wakelock");
    	}
    	
        if (rfxThread != null) {
        	runRfxThread = false;
        	Thread.yield();
        }
        
        if (serverThread != null) {
        	serverThread.destroy();
        }
    	
    	if (clientTimer != null) {
    		clientTimer.cancel();
    		clientTimer = null;
    	}
    	
        // Stop listening to wifi events, if registered
        if (wifiBroadcastReceiver != null) {
            try {
                unregisterReceiver(wifiBroadcastReceiver);
            } catch (IllegalArgumentException ignored) {}
        }
        
        resetLeds();
    }
    
    @Override
    public void onDestroy() {
    	Log.i(TAG, "Stop service");
   
    	cleanObjects();
        
        // Cancel the persistent notification.
        notificationMgr.cancel(NOTIFICATION_START);
        super.onDestroy();
    }

    public class LocalBinder extends Binder {
        RDService getService() {
            return RDService.this;
        }
        
        public void registerCallbacks(IUpdateListener callback) {
        	RDService.this.callback = callback;
        	resetLeds();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
    	return false;
    }
    
    /**
     * Show a notification while this service is running.
     */
    public void showServiceNotification() {
        CharSequence text = getText(R.string.service_started);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.service_icon, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, RDActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.service_label),
                       text, contentIntent);

        // Never clear notif, even if user clicks Clear button
        notification.flags |= Notification.FLAG_NO_CLEAR;

        /*
        notification.flags |= Notification.FLAG_SHOW_LIGHTS;
        notification.ledARGB = 0xFF0000ff;
        notification.ledOnMS = 50;
        notification.ledOffMS = 100;
        */

        // Send the notification.
        notificationMgr.notify(NOTIFICATION_START, notification);
    }

    /**
     * Alert notifications
     */
    public void showAlertNotification(String text, int soundResId, Date tstamp) {
        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.app_icon, text,
                									 tstamp.getTime());

        // The PendingIntent to launch our activity if the user selects this notification
        Intent intent = new Intent(this, RDActivity.class);
        intent.putExtra("view", R.id.switchesView);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
        														intent,
        														PendingIntent.FLAG_UPDATE_CURRENT);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.service_alert),
                       text, contentIntent);

        // Auto-cancel the notification when clicked
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        // Set sound if any (and if prefs allow)
        if ((soundResId != 0) && prefs.getBoolean("notif_sound", true)) {
        	notification.sound = Uri.parse("android.resource://"+getPackageName()+"/"+soundResId);
        }

        // Send the notification.
        notificationMgr.notify(NOTIFICATION_ALERT++, notification);
    }

	public void postToast(final String msg) {
		Handler h = new Handler(getMainLooper());

	    h.post(new Runnable() {
	        public void run() {
				Toast.makeText(RDService.this, msg, Toast.LENGTH_LONG).show();
				Looper.loop();
	        }
	    });
	}
	
	public void forceRefresh() {
		switches.syncWithServer();
		sensors.syncWithServer();
		doors.syncWithServer();
	}

    /****************************** SENSORS ******************************/
	public DatagramSocket getRfxSocket() {
		return rfxSocket;
	}
	
    public class RfxThread implements Runnable {
    	
    	public void run() {
    		int port = Integer.parseInt(prefs.getString("rfx_port", Preferences.DEFAULT_RFX_PORT));
    		
    		rfxSocket = null;
    		
    		// Needed for handler in doors
    		Looper.prepare();

    		int counter = 10;
			while (counter > 0) {
				// Try until it works !
				try {
					rfxSocket = new DatagramSocket(port);
					rfxSocket.setReuseAddress(true);
					break;
				} catch (java.net.SocketException ignored) {
					try {
						Thread.sleep(3000);
					} catch (java.lang.InterruptedException e) {}
				}
				counter = counter - 1;
			}
			
			if (counter == 0) {
				addLog("Erreur RFX: impossible d'ouvrir le socket (rx)");
			    Log.e(TAG, "IO Error for RFX: Failed to create socket (rx)");
			    errorLeds();
			    return;
			} else {
				Log.d(TAG, "RFX Thread starting on port " + port);
	    		addLog("Ecoute RFX-Lan sur le port " + port);
			}   	
			
			runRfxThread = true;

			byte[] buffer = new byte[1024];
    		while (runRfxThread) {
    			try {
    				final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    				rfxSocket.receive(packet);
    				readMessage(packet);
    			} catch (Exception e) {
    				Log.e(TAG, "Error receiving: " + e.getLocalizedMessage());
    				addLog("Erreur socket RFX (rx): " + e.getLocalizedMessage());
    				errorLeds();
    				break;
    			}
    		}
    		
    		if (rfxSocket != null) {
    			rfxSocket.close();
    		}

			Log.d(TAG, "RFX Thread ended.");
    	}
 
    	private void readMessage(DatagramPacket packet) {
    		String received = new String(packet.getData(), 0, packet.getLength());

    		try {
    			xPLMessage msg = new xPLMessage(received);
    			if (msg.getType() == xPLMessage.MessageType.STATUS) {
    				if ("basic".equals(msg.getSchemaType()) &&
    					"hbeat".equals(msg.getSchemaClass())) {
    					addLog("Heart beat reçu de " + msg.getSource());
    					Log.d(TAG, "heartbeat from " + msg.getSource());
    					flashLeds();
    				}
    			} else if (msg.getType() == xPLMessage.MessageType.TRIGGER) {
    				if ("basic".equals(msg.getSchemaType())) {
    					if ("sensor".equals(msg.getSchemaClass())) {
    						// Sensor
    						if ("battery".equals(msg.getNamedValue("type"))) {
    							String device = msg.getNamedValue("device");
    							String batt = msg.getNamedValue("current");
    							if (Integer.parseInt(batt) < 20) {
    								String txt = "Batterie faible pour le capteur '" + device + "'";
    								addLog(txt);
    								Log.d(TAG, "Low batt on " + device);
    								postToast(txt);
    							}
    						} else if ("temp".equals(msg.getNamedValue("type"))) {
    							sensors.updateData(RDService.this, msg);
    							if (callback != null) {
    								callback.updateThermo();
    							}
    						} else if ("humidity".equals(msg.getNamedValue("type"))) {
    							sensors.updateData(RDService.this, msg);
    							if (callback != null) {
    								callback.updateThermo();
    							}
    						} else {
    							String log = "Unknown msg type '" + msg.getNamedValue("type") + "' for device " + msg.getNamedValue("device");
    							addLog("Message RFX inconnu reçu: " + msg.getNamedValue("type"));
    							Log.d(TAG, log);
    						}
    					}
    				} else if ("security".equals(msg.getSchemaType())) {
    					if ("x10".equals(msg.getSchemaClass())) {
    						// AC
    						doors.syncWithHardware(msg);
    					}
    				} // x10
    				
    			} // trig

    		} catch (xPLMessage.xPLParseException e) {
    			Log.e(TAG, "Error parsing xPL message: " + e);
    			addLog("Erreur de parsing xPL: " + e);
    		}

    		if (callback != null) {
	    		callback.updateThermo();
	    	}
    	}
    }

	public synchronized Sensors getSensors() {
		return sensors;
	}
	
    /****************************** TRAINS ******************************/
	public synchronized Trains getTrains() {
		return trains;
	}
	
	private class TrainsTask extends TimerTask {
		@Override
		public void run() {
			trains.updateData(RDService.this);
	        
	    	if (callback != null) {
	    		callback.updateTrains();
	    	}
		}
	}
		
	/****************************** METEO ******************************/
	public synchronized Meteo getMeteo() {
		return meteo;
	}

	private class MeteoTask extends TimerTask {
		@Override
		public void run() {
			meteo.updateData(RDService.this);
			
			if (callback != null) {
				callback.updateMeteo();
			}
		}
	}
	
	/****************************** LOG ******************************/
    public synchronized void addLog(String msg) {
    	String prev_msg = null;
    	if (!log.isEmpty()) {
    		prev_msg = log.get(log.size()-1);
    	}
    	if (msg.equals(prev_msg)) {
    		// Exact match -> x2
    		log.set(log.size()-1, msg + " (x2)");
    	} else {
    		if (prev_msg != null) {
    			int count;
    			if (prev_msg.startsWith(msg)) {
    				int beg = prev_msg.lastIndexOf("(x") + 2;
    				int end = prev_msg.lastIndexOf(")");
    				if ((beg != -1) && (end != -1)) {
    					count = Integer.parseInt(prev_msg.substring(beg, end));
    					log.set(log.size()-1, msg + " (x" + (count+1) + ")");
    				} else {
    					log.add(msg);
    				}
    			} else {
    				// No match
    				log.add(msg);
    			}
    		} else {
    			// No previous message
    			log.add(msg);
    		}
    	}
    	if (log.size() > MAX_LOG_LINES) {
    		log = (List<String>)log.subList(0, MAX_LOG_LINES);
    	}
    	
    	if (callback != null) {
    		callback.updateLog();
    	}
    }
    
    public synchronized void clearLog() {
    	log.clear();
    }

    public synchronized String getLogMessages() {
    	return TextUtils.join("\n", log);
    }
    
	/****************************** LEDs ******************************/
    public synchronized void resetLeds() {
    	PrintWriter outStream = null;
        try {
            FileOutputStream fos = new FileOutputStream(SYSFS_LEDS);
            outStream = new PrintWriter(new OutputStreamWriter(fos));
            outStream.println("0 0 0");
        } catch (Exception ignored) {
        } finally {
            if (outStream != null)
                outStream.close();
        }
        
        if (callback != null) {
    		callback.resetLeds();
    	}
    }
    
    public synchronized void errorLeds() {
    	PrintWriter outStream = null;
        try {
            FileOutputStream fos = new FileOutputStream(SYSFS_LEDS);
            outStream = new PrintWriter(new OutputStreamWriter(fos));
            outStream.println("1 0 0");
        } catch (Exception ignored) {
        } finally {
            if (outStream != null)
                outStream.close();
        }
        
        if (callback != null) {
    		callback.errorLeds();
    	}
    }
    
    public synchronized void blinkLeds() {
    	
        new Thread(new Runnable() {
            public void run() {

            	// Read current LEDs status
            	String current = null;
            	Scanner scanner = null;
            	try {
            		scanner = new Scanner(new File(SYSFS_LEDS));
            		current = scanner.nextLine();
            	} catch (java.io.FileNotFoundException ignored) {
            	} finally {
            		if (scanner != null) {
            			scanner.close();
            		}
            	}

            	if (current == null) {
            		return;
            	}

            	StringBuilder builder = new StringBuilder(current);
            	builder.setCharAt(2, '1');
            	PrintWriter outStream = null;
            	try {
            		FileOutputStream fos = new FileOutputStream(SYSFS_LEDS);
            		outStream = new PrintWriter(new OutputStreamWriter(fos));
            		outStream.println(builder.toString());
            		outStream.flush();
            		Thread.sleep(100);

            		builder.setCharAt(4, '1');
            		outStream.println(builder.toString());
            		outStream.flush();
            		Thread.sleep(300);

            		builder.setCharAt(2, '0');
            		outStream.println(builder.toString());
            		outStream.flush();
            		Thread.sleep(100);

            		builder.setCharAt(4, '0');
            		outStream.println(builder.toString());
            	} catch (Exception ignored) {
            	} finally {
            		if (outStream != null)
            			outStream.close();
            	}
            }
        }).start();
        
        if (callback != null) {
    		callback.blinkLeds();
    	}
    }

    public synchronized void flashLeds() {
    	
        new Thread(new Runnable() {
            public void run() {

            	// Read current LEDs status
            	String current = null;
            	Scanner scanner = null;
            	try {
            		scanner = new Scanner(new File(SYSFS_LEDS));
            		current = scanner.nextLine();
            	} catch (java.io.FileNotFoundException ignored) {
            	} finally {
            		if (scanner != null) {
            			scanner.close();
            		}
            	}

            	if (current == null) {
            		return;
            	}

            	StringBuilder builder = new StringBuilder(current);
            	builder.setCharAt(2, '1');
            	PrintWriter outStream = null;
            	try {
            		FileOutputStream fos = new FileOutputStream(SYSFS_LEDS);
            		outStream = new PrintWriter(new OutputStreamWriter(fos));
            		outStream.println(builder.toString());
            		outStream.flush();
            		Thread.sleep(50);

            		builder.setCharAt(2, '0');
            		outStream.println(builder.toString());
            		outStream.flush();
            	} catch (Exception ignored) {
            	} finally {
            		if (outStream != null)
            			outStream.close();
            	}
            }
        }).start();
        
        if (callback != null) {
    		callback.flashLeds();
    	}
    }
    
    
    /****************************** SWITCHESs ******************************/
    public synchronized boolean toggleSwitch(int index) {
    	boolean result = switches.toggle(index);
    	if (callback != null) {
    		callback.updateSwitches();
    	}
		postToast(getString(R.string.cmd_sent));
    	return result;
    }
    
    public Switches getSwitches() {
    	return switches;
    }
    
    
    /****************************** DOORs ******************************/
    public Doors getDoors() {
    	return doors;
    }

    
    /****************************** PUSH ******************************/
    
    // For clients
    private void registerPushMessaging() {
    	if (registrationKey == null) {
    		Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
    		registrationIntent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0));
    		registrationIntent.putExtra("sender", "rpeuvergne.c2dm@gmail.com");
    		startService(registrationIntent);
    	}
    }
    
    // For server
    public void addPushDevice(String key) {
    	pushDevices.add(key);
    	addLog("Nouvel abonnement pour mode push. " + pushDevices.size() + " abonnés.");
    }
    
    public void pushToClients(String target, int index, String data) {
    	addLog("Envoi Push vers abonnés: " + target);
    	for (String key: pushDevices) {
    		pusher.pushMsg(key, target, index, data);
    	}
    }
    
    private void handlePushedMessage(Intent intent) {
    	String target = intent.getStringExtra(PushSender.TARGET);

		if (target == null) {
			Log.e(TAG, "Pushed intent misses extras");
		} else {
			Log.d(TAG, "Push received: " + target);
			addLog("Push reçu: " + target);

			if ("switch".equals(target)) {
				switches.setFromPushedIntent(intent);
			} else if ("door".equals(target)) {
				doors.setFromPushedIntent(intent);
			} else if ("lowbat".equals(target)) {
				Date tstamp = new Date(Long.parseLong(intent.getStringExtra(PushSender.TSTAMP)));
				showAlertNotification(getString(R.string.low_bat), R.raw.garage_alert, tstamp);
			} else {
				Log.e(TAG, "Unknown push target: " + target);
				addLog("Cible push inconnue: " + target);
			}
		}
    }

}
