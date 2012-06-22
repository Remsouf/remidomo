package com.remi.remidomo;

import java.net.URI;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.util.Log;

class MeteoWorldWeather extends Meteo {
	private final static String TAG = RDActivity.class.getSimpleName();

	private final static String LOCATION = "le+touvet,france";
	private final static String METEO_URL = "http://free.worldweatheronline.com/feed/weather.ashx?format=json";
	private final static String API_KEY = "18d9f01634202138120203";

	private final static String url = METEO_URL+"&q="+LOCATION+"&key="+API_KEY+"&num_of_days="+NB_DAYS;
	
	private final static Map<Integer, Integer> resourcesMap;

	static {
		Map<Integer, Integer> map = new HashMap<Integer, Integer> ();

		map.put(113, R.drawable.meteo_sunny);    // Clear/Sunny
		map.put(116, R.drawable.meteo_voile);    // Partly Cloudy
		map.put(119, R.drawable.meteo_cloud);    // Cloudy
		map.put(122, R.drawable.meteo_ncloud);   // Overcast
		map.put(143, R.drawable.meteo_mist);     // Mist
		map.put(176, R.drawable.meteo_mist);     // Patchy rain nearby
		map.put(179, R.drawable.meteo_snow);     // Patchy snow nearby
		map.put(182, R.drawable.meteo_verglas);  // Patchy sleet
		map.put(185, R.drawable.meteo_verglas);  // Patchy freezing drizzle nearby
		map.put(200, R.drawable.meteo_ltonerre); // Thundery outbreaks in nearby
		map.put(227, R.drawable.meteo_vsnow);    // Blowing snow
		map.put(230, R.drawable.meteo_vsnow);    // Blizzard
		map.put(248, R.drawable.meteo_pcloud);   // Fog
		map.put(260, R.drawable.meteo_gfog);     // Freezing Fog
		map.put(263, R.drawable.meteo_lrain);    // Patchy light drizzle
		map.put(266, R.drawable.meteo_mist);     // Light drizzle
		map.put(281, R.drawable.meteo_verglas);  // Freezing drizzle
		map.put(284, R.drawable.meteo_verglas);  // Heavy freezing drizzle
		map.put(293, R.drawable.meteo_lrain);    // Patchy light rain
		map.put(296, R.drawable.meteo_lrain);    // Light rain
		map.put(299, R.drawable.meteo_rain);     // Moderate rain at times
		map.put(302, R.drawable.meteo_rain);     // Moderate rain
		map.put(305, R.drawable.meteo_hrain);    // Heavy rain at times
		map.put(308, R.drawable.meteo_hrain);    // Heavy rain
		map.put(311, R.drawable.meteo_verglas);  // Light freezing rain
		map.put(314, R.drawable.meteo_hfrain);   // Moderate or Heavy freezing rain
		map.put(317, R.drawable.meteo_verglas);  // Light sleet
		map.put(320, R.drawable.meteo_lsnow);    // Moderate or heavy sleet
		map.put(323, R.drawable.meteo_lsnow);    // Patchy light snow
		map.put(326, R.drawable.meteo_lsnow);    // Light snow
		map.put(329, R.drawable.meteo_snow);     // Patchy moderate snow
		map.put(332, R.drawable.meteo_snow);     // Moderate snow
		map.put(335, R.drawable.meteo_hsnow);    // Patchy heavy snow
		map.put(338, R.drawable.meteo_hsnow);    // Heavy snow
		map.put(350, R.drawable.meteo_grele);    // Ice pellets
		map.put(353, R.drawable.meteo_lrain);    // Light rain shower
		map.put(356, R.drawable.meteo_srain);    // Moderate or heavy rain shower
		map.put(359, R.drawable.meteo_torrent);  // Torrential rain shower
		map.put(362, R.drawable.meteo_lrain);    // Light sleet showers
		map.put(365, R.drawable.meteo_rain);     // Moderate or heavy sleet showers
		map.put(368, R.drawable.meteo_lsnow);    // Light snow showers
		map.put(371, R.drawable.meteo_snow);     // Moderate or heavy snow showers
		map.put(374, R.drawable.meteo_grele);    // Light showers of ice pellets
		map.put(377, R.drawable.meteo_hgrele);   // Moderate or heavy showers of ice pellets
		map.put(386, R.drawable.meteo_ltonerre); // Patchy light rain in area with thunder
		map.put(389, R.drawable.meteo_tonerre);  // Moderate or heavy rain in area with thunder
		map.put(392, R.drawable.meteo_snowt);    // Patchy light snow in area with thunder
		map.put(395, R.drawable.meteo_snowt);    // Moderate or heavy snow in area with thunder

		resourcesMap = Collections.unmodifiableMap(map);
	}

	@Override
	public void updateData(RDService service) {

		ArrayList<MeteoData> newMeteo = new ArrayList<MeteoData>();
		try {
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();

			request.setURI(new URI(url));
			String content = client.execute(request, new BasicResponseHandler());
			JSONObject entries = new JSONObject(content);
			JSONObject data = entries.getJSONObject("data");
			JSONArray weather = data.getJSONArray("weather");
			for (int i=0; i<NB_DAYS; i++) {
				JSONObject dayForecast = weather.getJSONObject(i);

				String tstamp = dayForecast.getString("date");
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
				ParsePosition pos = new ParsePosition(0);
				Date date = sdf.parse(tstamp, pos);

				String code = dayForecast.getString("weatherCode");
				int weatherCode = Integer.parseInt(code);

				String minTC = dayForecast.getString("tempMinC");
				float minTemp = Float.parseFloat(minTC);

				String maxTC = dayForecast.getString("tempMaxC");
				float maxTemp = Float.parseFloat(maxTC);

				MeteoData dayData = new MeteoData();
				dayData.date = date;
				dayData.resourceId = getMeteoResource(weatherCode, maxTemp);
				dayData.minTemp = minTemp;
				dayData.maxTemp = maxTemp;
				dayData.details = null;
				newMeteo.add(dayData);
			}

			if (!newMeteo.isEmpty()) {
				meteoData = newMeteo;
			}

			service.addLog("Mise à jour des données météo (" + NB_DAYS + " jours)", RDService.LogLevel.UPDATE);
		} catch (java.net.URISyntaxException e) {
			Log.e(TAG, "Bad URI: " + url);
		} catch (org.apache.http.client.ClientProtocolException e) {
			Log.e(TAG, "ClientProtocolException with URI: " + url + ", " + e);
		} catch (java.io.IOException e) {
			Log.e(TAG, "IOException with URI: " + url + ", " + e);
		} catch (org.json.JSONException e) {
			Log.e(TAG, "JSON error with URI: " + url + ", " + e);
		}

		if (!meteoData.isEmpty()) {
			lastUpdate = new Date();
		}
	}

	public int getMeteoResource(int weatherCode, float maxTemp) {
		Integer resId = resourcesMap.get(weatherCode);
		if (resId == null) {
		 	Log.d(TAG, "Unknown weather code: " + weatherCode);
			return R.drawable.meteo_unknown;
		}

		if ((weatherCode == 113) && (maxTemp > 28)) {
			resId = R.drawable.meteo_canicule;
		}

	    return resId;	   
	}
}