package com.remi.remidomo.reloaded.open.sense;

import android.hardware.Sensor;
import android.util.Log;

import com.remi.remidomo.reloaded.RDService;
import com.remi.remidomo.reloaded.data.Energy;
import com.remi.remidomo.reloaded.data.SensorData;
import com.remi.remidomo.reloaded.data.Sensors;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class OpenSenseServer {
    private final static String TAG = OpenSenseServer.class.getSimpleName();

    private final static String OPENSENSE_URL = "http://api.sen.se/events/";
    private final static String OPENSENSE_KEY_HEADER = "sense_key";
    private final static String OPENSENSE_API_KEY = "U2LAIT3k7kqbzD-G0CNLHw";

    private final static int PUSH_THRESHOLD = 1;

    private static final Map<String, Integer> FEED_IDS = new HashMap<String, Integer>() {{
        put(Sensors.ID_POOL_T, 52040);
        put(Sensors.ID_EXT_T, 52041);
        put(Sensors.ID_EXT_H, 52042);
        put(Sensors.ID_VERANDA_T, 52043);
        put(Sensors.ID_VERANDA_H, 52044);
        put(Energy.ID_POWER, 52045);
        put(Energy.ID_ENERGY, 52052);
    }};

    // This is a singleton
    private static OpenSenseServer INSTANCE = null;

    private Map<Integer, List<SensorData.Pair>> queues = new HashMap<Integer, List<SensorData.Pair>> ();

    private RDService service;

    class OpenSenseException extends RuntimeException {
        public OpenSenseException() {}

        public OpenSenseException(String message) {
            super(message);
        }
    }

    private OpenSenseServer(RDService service) {
        this.service = service;
    }

    public static synchronized OpenSenseServer getInstance(RDService service) {
        if (INSTANCE == null) {
            INSTANCE = new OpenSenseServer(service);
        }

        return INSTANCE;
    }

    public void enqueueEvent(final String sensorName, SensorData.Pair event) {
        int feedId = getFeedId(sensorName);
        if (!queues.containsKey(feedId)) {
            queues.put(feedId, new LinkedList<SensorData.Pair>());
        }
        queues.get(feedId).add(event);

        processQueues();
    }

    private void processQueues() {
        for (Map.Entry<Integer, List<SensorData.Pair>> entry: queues.entrySet()) {
            if (entry.getValue().size() > PUSH_THRESHOLD) {
                boolean result = postData(entry.getKey(), entry.getValue());
                if (result) {
                    entry.getValue().clear();
                }
            }
        }
    }

    private boolean postData(int feedId, List<SensorData.Pair> events) {
        Log.d(TAG, "Posting data to Open.Sen.se for feed #" + feedId);
        service.addLog("Envoi de données vers Open.Sen.se pour le flux #" + feedId);

        try {
            HttpClient client = new DefaultHttpClient();
            HttpPost request = new HttpPost();

            request.setURI(new URI(OPENSENSE_URL));
            request.addHeader(OPENSENSE_KEY_HEADER, OPENSENSE_API_KEY);
            request.setHeader("User-Agent", "com.remi.remodomo.reloaded");

            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+00:00");
            df.setTimeZone(tz);

            JSONArray jsonData = new JSONArray();
            for (SensorData.Pair event: events) {
                String timeTag = df.format(new Date(event.time));

                JSONObject jsonEvent = new JSONObject();
                jsonEvent.put("feed_id", feedId);
                jsonEvent.put("timetag", timeTag);
                jsonEvent.put("value", event.value);

                jsonData.put(jsonEvent);
            }

            StringEntity entity = new StringEntity(jsonData.toString(), HTTP.UTF_8);
            entity.setContentType("application/json");
            request.setEntity(entity);

            String content = client.execute(request, new BasicResponseHandler());
            JSONArray response = new JSONArray(content);

            return true;
        } catch (java.net.URISyntaxException e) {
            service.addLog("Erreur URI serveur Open.Sen.se: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
            Log.e(TAG, "Bad server URI for Open.Sen.se");
        } catch (org.apache.http.conn.HttpHostConnectException e) {
            service.addLog("Impossible de se connecter au serveur Open.Sen.se: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
            Log.e(TAG, "HostConnectException with Open.Sen.se server: " + e);
        } catch (org.apache.http.client.ClientProtocolException e) {
            service.addLog("Erreur protocole serveur Open.Sen.se", RDService.LogLevel.HIGH);
            Log.e(TAG, "ClientProtocolException with Open.Sen.se server: " + e);
        } catch (java.net.SocketException e) {
            service.addLog("Serveur Open.Sen.senon joignable: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
            Log.e(TAG, "SocketException with Open.Sen.se client: " + e);
        } catch (java.io.IOException e) {
            service.addLog("Erreur I/O client Open.Sen.se: " + e.getLocalizedMessage(), RDService.LogLevel.HIGH);
            Log.e(TAG, "IOException with Open.Sen.se client: " + e);
        } catch (org.json.JSONException e) {
            service.addLog("Erreur JSON serveur Open.Sen.se", RDService.LogLevel.HIGH);
            Log.e(TAG, "JSON error with Open.Sen.se server: " + e);
        }

        return false;
    }

    private int getFeedId(final String internalID) {
        if (FEED_IDS.containsKey(internalID)) {
            return FEED_IDS.get(internalID);
        } else {
            throw new IllegalArgumentException("Unknown sensor ID: " + internalID);
        }
    }
}
