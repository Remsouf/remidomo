package com.remi.remidomo.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import com.remi.remidomo.common.BaseActivity;
import com.remi.remidomo.common.BaseService;
import com.remi.remidomo.common.PushSender;
import com.remi.remidomo.common.data.Doors;
import com.remi.remidomo.common.data.xPLMessage;
import com.remi.remidomo.common.prefs.Defaults;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;

import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

public class RDService extends BaseService {

    private final static String TAG = "Remidomo-" + RDService.class.getSimpleName();

    private final static String SYSFS_LEDS = "/sys/power/leds";

    public final static String ACTION_BATTERYLOW = "com.remi.remidomo.server.BATLOW";
    public final static String ACTION_POWERCONNECT = "com.remi.remidomo.server.POWER_CONN";
    public final static String ACTION_POWERDISCONNECT = "com.remi.remidomo.server.POWER_DISC";

    private PowerManager pwrMgr = null;
    private PowerManager.WakeLock wakeLock = null;

    private Thread rfxThread = null;
    private boolean runRfxThread = true;
    private DatagramSocket rfxSocket = null;

    private ServerThread serverThread = null;

    private PushSender pusher = null;
    private Set<String> pushDevices = new HashSet<String>();

    @Override
    public void onCreate() {
        super.onCreate();

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
        showServiceNotification(RDActivity.class, BaseActivity.DASHBOARD_VIEW_ID);

        restorePushDevicesList();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        int result = super.onStartCommand(intent, flags, startId);

        // In case of crash/restart, intent can be null
        if ((intent != null) && ACTION_BATTERYLOW.equals(intent.getAction())) {
            pushToClients(PushSender.LOWBAT, 0, "");
            Log.i(TAG, "Sending push for low battery !");
            addLog("Batterie faible", LogLevel.HIGH);
        } else if ((intent != null) && ACTION_POWERCONNECT.equals(intent.getAction())) {
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
        } else if ((intent != null) && ACTION_POWERDISCONNECT.equals(intent.getAction())) {
            try {
                while (true) {
                    if (energy == null) {
                        Thread.sleep(1000);
                    } else {
                        energy.updatePowerStatus(false);
                        break;
                    }
                }
            } catch (java.lang.InterruptedException e) {
            }

            if (callback != null) {
                callback.updateEnergy();
            }
        } else if ((intent != null) && ACTION_BOOTKICK.equals(intent.getAction())) {
            boolean kickboot = prefs.getBoolean("bootkick", Defaults.DEFAULT_BOOTKICK);
            if (kickboot) {
                // Start activity
                Intent activityIntent = new Intent(this, RDActivity.class);
                activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(activityIntent);
            }
        } else {
            Log.i(TAG, "Start service");
            addLog("Service (re)démarré");

            if ((intent != null) && intent.getBooleanExtra("FORCE_RESTART", true)) {
                resetLeds();
            }

            // Stop all threads and clean everything
            cleanObjects();

            // Start threads
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
        }

        return result;
    }

    protected void cleanObjects() {
        if (serverThread != null) {
            serverThread.destroy();
        }

        if ((wakeLock != null) && (wakeLock.isHeld())) {
            wakeLock.release();
            addLog("WakeLock libéré");
            Log.d(TAG, "Released wakelock");
        }

        if (rfxThread != null) {
            runRfxThread = false;
            Thread.yield();
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Stop service");

        cleanObjects();
        resetLeds();

        super.onDestroy();
    }

    /****************************** SENSORS ******************************/
    public DatagramSocket getRfxSocket() {
        return rfxSocket;
    }

    public class RfxThread implements Runnable {

        public void run() {
            int port = prefs.getInt("rfx_port", Defaults.DEFAULT_RFX_PORT);

            // Needed for handler in doors
            Looper.prepare();

            while (true) {
                // Retry forever !
                Log.d(TAG, "RFX Thread initializing...");

                rfxSocket = null;

                while (true) {
                    // Try until it works !
                    try {
                        rfxSocket = new DatagramSocket(port);
                        rfxSocket.setReuseAddress(true);
                        Log.d(TAG, "RFX Thread starting on port " + port);
                        addLog("Ecoute RFX-Lan sur le port " + port);
                        resetLeds();
                        break;
                    } catch (java.net.SocketException ignored) {
                        addLog("Erreur RFX: impossible d'ouvrir le socket (rx). Nouvelle tentative.", LogLevel.HIGH);
                        Log.e(TAG, "IO Error for RFX: Failed to create socket (rx). Retrying.");
                        errorLeds();
                        try {
                            Thread.sleep(5000);
                        } catch (java.lang.InterruptedException ignored2) {}
                    }
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

                if (!runRfxThread) {
                    // Requested to stop from the outside
                    break;
                }
            }

            Log.d(TAG, "RFX Thread ended.");
        }

        private void readMessage(DatagramPacket packet) {
            String received = new String(packet.getData(), 0, packet.getLength());

           // Log.d("#RP", "Packet: " + received);

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
                                sensors.updateData(RDService.this, msg, RDActivity.class);
                                if (callback != null) {
                                    callback.updateThermo();
                                }
                            } else if ("humidity".equals(msg.getNamedValue("type"))) {
                                sensors.updateData(RDService.this, msg, RDActivity.class);
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
                            if (callback != null) {
                                callback.updateDoors();
                            }
                        }
                    } // x10

                } // trig

            } catch (xPLMessage.xPLParseException e) {
                Log.e(TAG, "Error parsing xPL message: " + e);
                addLog("Erreur de parsing xPL: " + e, LogLevel.HIGH);
            }
        }
    }

    /****************************** SWITCHESs ******************************/
    public synchronized boolean toggleSwitch(int index) {
        int rfxPort = prefs.getInt("rfx_port", Defaults.DEFAULT_RFX_PORT);
        boolean result = switches.toggle(index, rfxPort, getRfxSocket());
        if (callback != null) {
            callback.updateSwitches();
        }
        return result;
    }

    /****************************** LEDs ******************************/
    public void resetLeds() {
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
    }

    public void errorLeds() {
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
    }

    public void blinkLeds() {

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
    }

    public void flashLeds() {

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
    }

    /****************************** PUSH ******************************/

    // For server
    public void addPushDevice(String key) {
        pushDevices.add(key);
        persistPushDevicesList();
        addLog("Nouvel abonnement pour mode push. " + pushDevices.size() + " abonnés.");
    }

    public void pushToClients(String target, int index, String data) {
        addLog("Envoi Push vers abonnés: " + target);
        if (pusher != null) {
            pusher.pushMsg(new ArrayList<String>(pushDevices), target, index, data);
        }
    }

    private void persistPushDevicesList() {
        // Persist the list of registered devices
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("push_devices", TextUtils.join(",", pushDevices));
        editor.commit();
    }

    private void restorePushDevicesList() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String persistedString = prefs.getString("push_devices", Defaults.DEFAULT_PUSH_DEVICES);

        String[] persistedArray = TextUtils.split(persistedString, ",");
        for (String id: persistedArray) {
            pushDevices.add(id);
        }

        addLog("" + pushDevices.size() + " abonnements pour mod push.");
        Log.d(TAG, "Restored " + pushDevices.size() + " registered push devices");
    }

    /****************************** UPGRADE ******************************/
    /*
     * This method upgrades data such as preferences, to deal with format
     * changes between versions.
     */
    protected void upgradeData() {
        // No needed yet
    }
}
