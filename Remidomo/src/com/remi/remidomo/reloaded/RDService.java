package com.remi.remidomo.reloaded;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import com.google.android.gcm.GCMConstants;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

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
    
    public final static String ACTION_RESTORE_DATA = "com.remi.remidomo.reloaded.RESTORE_DATA";
    public final static String ACTION_BOOTKICK = "com.remi.remidomo.reloaded.BOOTKICK";
    public final static String ACTION_BATTERYLOW = "com.remi.remidomo.reloaded.BATLOW";
    public final static String ACTION_POWERCONNECT = "com.remi.remidomo.reloaded.POWER_CONN";
    public final static String ACTION_POWERDISCONNECT = "com.remi.remidomo.reloaded.POWER_DISC";

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
    
    private final static String GCM_PROJECT_ID = "25944642123";
    private PushSender pusher = null;
    private String registrationKey = null;
	
	private Trains trains = new Trains();
	private Meteo meteo = new MeteoVilles();
	private Sensors sensors = null;
	private Switches switches = null;
	private Doors doors = null;
	private Energy energy = null;

    private static final int MAX_LOG_LINES = 200;
    private static class LogEntry {
    		public String msg;
    		public LogLevel level;
    		public int repeat;
    }
    private LinkedList<LogEntry> log = new LinkedList<LogEntry>();
    
    private Set<String> pushDevices = new HashSet<String>();
    
    public enum LogLevel { HIGH,
    					   MEDIUM,
    					   LOW,
    					   UPDATE };

    @Override
    public void onCreate() {
        notificationMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (notificationMgr == null) {
            Log.e(TAG, "Failed to get Notification Manager");
        }

        new Thread(new Runnable() {
        	public synchronized void run() {
        		prefs = PreferenceManager.getDefaultSharedPreferences(RDService.this);
        		upgradeData();

        		sensors = new Sensors(RDService.this);
        		switches = new Switches(RDService.this);
        		doors = new Doors(RDService.this);
        		energy = new Energy(RDService.this);
        	}
        }).start();

        pwrMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pwrMgr == null) {
        	Log.e(TAG, "Failed to get Power Manager");
        } else {
        	wakeLock = pwrMgr.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Remidomo");
        	if (wakeLock == null) {
        		Log.e(TAG, "Failed to create WakeLock");
        	}
        }

        // Display a notification about us starting.  We put an icon in the status bar.
        showServiceNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

    	try {
    		while (prefs == null) {
    			Thread.sleep(100);
    		}
    	} catch (java.lang.InterruptedException ignored) {}

    	String mode = prefs.getString("mode", Preferences.DEFAULT_MODE);

    	// In case of crash/restart, intent can be null
    	if ((intent != null) && GCMConstants.INTENT_FROM_GCM_REGISTRATION_CALLBACK.equals(intent.getAction())) {
    		registrationKey = intent.getStringExtra("registration_id");
    		Log.d(TAG, "Got GCM registration key");
    	} else if ((intent != null) && GCMConstants.INTENT_FROM_GCM_MESSAGE.equals(intent.getAction())) {
    		// Don't restart, just account for received Push intent
    		handlePushedMessage(intent);
    	} else if ((intent != null) && ACTION_RESTORE_DATA.equals(intent.getAction())) {
    		addLog("Lecture des données depuis la carte SD");
    		new Thread(new Runnable() {
            	public void run() {
            		sensors.readFromSdcard();
            		energy.readFromSdcard();
            		if (callback != null) {
            			callback.postToast(getString(R.string.load_complete));
            		}
            	};
            }, "sdcard read").start();
    	} else if ((intent != null) && ACTION_BOOTKICK.equals(intent.getAction())) {
    		boolean kickboot = prefs.getBoolean("bootkick", Preferences.DEFAULT_BOOTKICK);
    		if (!kickboot) {
    			Log.i(TAG, "Exit service to ignore boot event");
    			cleanObjects();
    			stopSelf();
    		}
    	} else if ((intent != null) && ACTION_BATTERYLOW.equals(intent.getAction())) {
    		if ("Serveur".equals(mode)) {
    			pushToClients(PushSender.LOWBAT, 0, "");
    			Log.i(TAG, "Sending push for low battery !");
    			addLog("Batterie faible", LogLevel.HIGH);
    		}
		} else if ((intent != null) && ACTION_POWERCONNECT.equals(intent.getAction())) {
			if ("Serveur".equals(mode)) {
				try {
					while (true) {
						if (energy == null) {
							Thread.sleep(1000);
						} else {
							energy.updatePowerStatus(true);
							break;
						}
					}
				} catch (java.lang.InterruptedException e) {}

				if (callback != null) {
					callback.updateEnergy();
				}
			}
		} else if ((intent != null) && ACTION_POWERDISCONNECT.equals(intent.getAction())) {
			if ("Serveur".equals(mode)) {
				try {
					while (true) {
						if (energy == null) {
							Thread.sleep(1000);
						} else {
							energy.updatePowerStatus(false);
							break;
						}
					}
				} catch (java.lang.InterruptedException e) {}

				if (callback != null) {
					callback.updateEnergy();
				}
			}
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
    		if ("Serveur".equals(mode)) {
    			rfxThread = new Thread(new RfxThread(), "rfx");
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
    			clientTimer = new Timer("Client");
    			int period = prefs.getInt("client_poll", Preferences.DEFAULT_CLIENT_POLL);
    			// 15s graceful period, to let the service read data from FS,
    			// before attempting updates from the server
    			clientTimer.scheduleAtFixedRate(new ClientTask(this), 15000, 1000L*60*period);

    			registerPushMessaging();
    		}

    		// Timer for train updates
    		Timer timerTrains = new Timer("Trains");
    		int period = prefs.getInt("sncf_poll", Preferences.DEFAULT_SNCF_POLL);
    		timerTrains.scheduleAtFixedRate(new TrainsTask(), 1, 1000L*60*period);

    		// Timer for weather updates
    		Timer timerMeteo = new Timer("Meteo");
    		period = prefs.getInt("meteo_poll", Preferences.DEFAULT_METEO_POLL);
    		timerMeteo.scheduleAtFixedRate(new MeteoTask(), 1, 1000L*60*60*period);
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

    public void stopAtActivityRequest() {
    	Log.i(TAG, "Stop service at activity request");
    	cleanObjects();
    	stopSelf();
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
        // The PendingIntent to launch our activity if the user selects this notification
        Intent intent = new Intent(this, RDActivity.class);
        intent.putExtra("view", RDActivity.DASHBOARD_VIEW_ID);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.service_icon);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
            .setContentText(getText(R.string.service_started))
            .setTicker(getText(R.string.service_label))
            .setWhen(System.currentTimeMillis())
            .setLargeIcon(largeIcon)
            .setSmallIcon(R.drawable.app_icon)
            .setContentIntent(contentIntent)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true);

        // Send the notification.
        notificationMgr.notify(NOTIFICATION_START, builder.build());
    }

    /**
     * Alert notifications
     */
    public void showAlertNotification(String text, int soundResId, int iconResId, int destinationView, Date tstamp) {

        // The PendingIntent to launch our activity if the user selects this notification
        Intent intent = new Intent(this, RDActivity.class);
        intent.putExtra("view", destinationView);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
        														intent,
        														PendingIntent.FLAG_UPDATE_CURRENT);

    	NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
    		.setSmallIcon(R.drawable.app_icon)
    		.setContentIntent(contentIntent)
    		.setTicker(text)
    		.setContentText(text)
    		.setContentTitle(getText(R.string.service_alert))
    		.setWhen(tstamp.getTime())
    		.setAutoCancel(true);
    	
    	if (iconResId != 0) {
    	    Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), iconResId);
    	    builder.setLargeIcon(largeIcon);
    	}

        // Set sound if any (and if prefs allow)
        if ((soundResId != 0) && prefs.getBoolean("notif_sound", true)) {
        	builder.setSound(Uri.parse("android.resource://"+getPackageName()+"/"+soundResId));
        }

        // Send the notification.
        notificationMgr.notify(NOTIFICATION_ALERT++, builder.build());
    }
	
	public void forceRefresh() {
		switches.syncWithServer();
		sensors.syncWithServer();
		doors.syncWithServer();
		energy.syncWithServer();
	}

    /****************************** SENSORS ******************************/
	public DatagramSocket getRfxSocket() {
		return rfxSocket;
	}
	
    public class RfxThread implements Runnable {
    	
    	public void run() {
    		int port = prefs.getInt("rfx_port", Preferences.DEFAULT_RFX_PORT);
    		
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
				addLog("Erreur RFX: impossible d'ouvrir le socket (rx)", LogLevel.HIGH);
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
    				Log.e(TAG, "Error receiving: ", e);
    				addLog("Erreur socket RFX (rx): " + e.getLocalizedMessage(), LogLevel.HIGH);
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
    					String details = msg.getSource() + " (v" + msg.getNamedValue("version")+")";
    					addLog("Heart beat reçu de " + details, LogLevel.UPDATE);
    					Log.d(TAG, "heartbeat from " + details);
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
    								addLog(txt, LogLevel.MEDIUM);
    								Log.d(TAG, "Low batt on " + device);
    								if (callback != null) {
    									callback.postToast(txt);
    								}
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
    						} else if ("energy".equals(msg.getNamedValue("type"))) {
    							energy.updateData(RDService.this, msg);
    							if (callback != null) {
    								callback.updateEnergy();
    							}
    						} else if ("power".equals(msg.getNamedValue("type"))) {
    							energy.updateData(RDService.this, msg);
    							if (callback != null) {
    								callback.updateEnergy();
    							}
    						} else {
    							String log = "Unknown msg type '" + msg.getNamedValue("type") + "' for device " + msg.getNamedValue("device");
    							addLog("Message RFX inconnu reçu: " + msg.getNamedValue("type"), LogLevel.MEDIUM);
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
    			addLog("Erreur de parsing xPL: " + e, LogLevel.HIGH);
    		}

    		if (callback != null) {
	    		callback.updateThermo();
	    	}
    	}
    }

	public Sensors getSensors() {
		return sensors;
	}

	public void saveToSdcard() {
		new Thread(new Runnable() {
        	public void run() {
        		sensors.saveToSdcard();
        		energy.saveToSdcard();
        		if (callback != null) {
        			callback.postToast(getString(R.string.save_complete));
        		}
        	}
		}).start();
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
	public void addLog(String msg) {
		addLog(msg, LogLevel.LOW);
	}

    public synchronized void addLog(String msg, LogLevel level) {
    	String prev_msg = null;
    	if (!log.isEmpty()) {
    		prev_msg = log.getLast().msg;
    	}
    	if (msg.equals(prev_msg)) {
    		// Exact match -> +1
    		LogEntry entry = log.getLast();
			entry.repeat = entry.repeat + 1;
			log.removeLast();
			log.addLast(entry);
    	} else {
    		LogEntry newEntry = new LogEntry();
    		newEntry.msg = msg;
    		newEntry.level = level;
    		newEntry.repeat = 1;
    		log.addLast(newEntry);
    	}
    	// Since we're adding one msg at a time,
    	// we should be ok removing only the oldest one
    	if (log.size() > MAX_LOG_LINES) {
    		log.removeFirst();
    	}
    	
    	if (callback != null) {
    		callback.updateLog();
    	}
    }
    
    public synchronized void clearLog() {
    	log.clear();
    	if (callback != null) {
    		callback.updateLog();
    	}
    }

    public synchronized SpannableStringBuilder getLogMessages() {
    	SpannableStringBuilder text = new SpannableStringBuilder();
    	
    	for (LogEntry entry: log) {
    		int startPos = text.length();
    		text.append(entry.msg);
    		if (entry.repeat > 1) {
    			text.append(" (x" + entry.repeat + ")");
    		}
    		text.append("\n");
    		int endPos = text.length();

        	int color;
        	switch (entry.level) {
        	case HIGH:
        		color = Color.rgb(255, 160, 160);
        		break;
        	case MEDIUM:
        		color = Color.rgb(255, 200, 100);
        		break;
        	case LOW:
        		color = Color.WHITE;
        		break;
        	case UPDATE:
        		color = Color.rgb(200, 255, 200);
        		break;
        	default:
        		color = Color.GRAY;
        		break;
        	}
        	text.setSpan(new ForegroundColorSpan(color), startPos, endPos, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    	}
    	
    	return text;
    }
    
	/****************************** LEDs ******************************/
    public synchronized void resetLeds() {
    	new Thread(new Runnable() {
        	public void run() {

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
        	}
    	}, "LED reset").start();

        if (callback != null) {
    		callback.resetLeds();
    	}
    }
    
    public synchronized void errorLeds() {
    	new Thread(new Runnable() {
        	public void run() {
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
        	}
        }, "LED error").start();

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
            	} catch (java.io.IOException ignored) {
            	} catch (java.lang.InterruptedException ignored) {
            	} finally {
            		if (outStream != null)
            			outStream.close();
            	}
            }
        }, "LED blink").start();
        
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
            	} catch (java.io.IOException ignored) {
            	} catch (java.lang.InterruptedException ignored) {
            	} finally {
            		if (outStream != null)
            			outStream.close();
            	}
            }
        }, "LED flash").start();
        
        if (callback != null) {
    		callback.flashLeds();
    	}
    }
    
    
    /****************************** SWITCHESs ******************************/
    public synchronized boolean toggleSwitch(int index) {
    	boolean result = switches.toggle(index);
    	if (callback != null) {
    		callback.updateSwitches();
    		// Don't do this directly from the activity,
    		// or it won't prove anything about the message
    		// being sent.
    		callback.postToast(getString(R.string.cmd_sent));
    	}

    	return result;
    }
    
    public Switches getSwitches() {
    	return switches;
    }
    
    
    /****************************** DOORs ******************************/
    public Doors getDoors() {
    	return doors;
    }


    /****************************** DOORs ******************************/
    public Energy getEnergy() {
    	return energy;
    }


    /****************************** PUSH ******************************/
    
    // For clients
    private void registerPushMessaging() {
    	if (registrationKey == null) {
    		Intent registrationIntent = new Intent(GCMConstants.INTENT_TO_GCM_REGISTRATION);
    		registrationIntent.putExtra(GCMConstants.EXTRA_APPLICATION_PENDING_INTENT,
    									PendingIntent.getBroadcast(this, 0, new Intent(), 0));
    		registrationIntent.putExtra(GCMConstants.EXTRA_SENDER, GCM_PROJECT_ID);
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
    	if (pusher != null) {
    		pusher.pushMsg(new ArrayList<String>(pushDevices), target, index, data);
    	}
    }
    
    private void handlePushedMessage(Intent intent) {
    	String target = intent.getStringExtra(PushSender.TARGET);

		if (target == null) {
			Log.e(TAG, "Pushed intent misses extras");
		} else {
			Log.d(TAG, "Push received: " + target);
			addLog("Push reçu: " + target, LogLevel.UPDATE);

			if (PushSender.SWITCH.equals(target)) {
				switches.setFromPushedIntent(intent);
			} else if (PushSender.DOOR.equals(target)) {
				doors.setFromPushedIntent(intent);
			} else if (PushSender.LOWBAT.equals(target)) {
				Date tstamp = new Date(Long.parseLong(intent.getStringExtra(PushSender.TSTAMP)));
				showAlertNotification(getString(R.string.low_bat), R.raw.garage_alert, R.drawable.battery_low, RDActivity.TEMP_VIEW_ID, tstamp);
			} else if (PushSender.POWER_DROP.equals(target)) {
				// In case the server still has connectivity...
				Date tstamp = new Date(Long.parseLong(intent.getStringExtra(PushSender.TSTAMP)));
				showAlertNotification(getString(R.string.power_dropped), R.raw.garage_alert, R.drawable.energy, RDActivity.DASHBOARD_VIEW_ID, tstamp);
				energy.updatePowerStatus(false);
				if (callback != null) {
					callback.updateEnergy();
				}
			} else if (PushSender.POWER_RESTORE.equals(target)) {
				Date tstamp = new Date(Long.parseLong(intent.getStringExtra(PushSender.TSTAMP)));
				String duration = intent.getStringExtra(PushSender.STATE);
				showAlertNotification(String.format(getString(R.string.power_restored), duration), R.raw.garage_alert, R.drawable.energy, RDActivity.DASHBOARD_VIEW_ID, tstamp);
				energy.updatePowerStatus(true);
				if (callback != null) {
					callback.updateEnergy();
				}
			} else if (PushSender.MISSING_SENSOR.equals(target)) {
				Date tstamp = new Date(Long.parseLong(intent.getStringExtra(PushSender.TSTAMP)));
				String sensorName = intent.getStringExtra(PushSender.STATE);
				showAlertNotification(String.format(getString(R.string.missing_sensor), sensorName), R.raw.garage_alert, R.drawable.temperature2, RDActivity.TEMP_VIEW_ID, tstamp);
			} else {
				Log.e(TAG, "Unknown push target: " + target);
				addLog("Cible push inconnue: " + target, LogLevel.HIGH);
			}
		}
    }

    /****************************** UPGRADE ******************************/
    /*
     * This method upgrades data such as preferences, to deal with format
     * changes between versions.
     */
    private void upgradeData() {
    	Log.i(TAG, "Upgrading data...");

    	SharedPreferences.Editor editor = prefs.edit();

    	// 103 : prefs converted to ints
    	if (prefs.getInt("version", 0) <= 103) {
    		String keys[] = {"rfx_port", "port", "loglimit", "sncf_poll",
    				"meteo_poll", "client_poll", "plot_limit" };
    		for (String key:keys) {
    			try {
    				prefs.getInt(key, 0);
    			} catch (java.lang.ClassCastException e) {
    				String oldValue = prefs.getString(key, "");
    				editor.remove(key);
    				editor.remove(key+".int");
    				editor.putInt(key, Integer.parseInt(oldValue));
    			}
    		}
    	}

    	int versionCode;
    	try {
    		versionCode = getPackageManager().getPackageInfo("com.remi.remidomo", 0).versionCode;
    		editor.putInt("version", versionCode);
    	} catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}

    	editor.commit();
    }
}
