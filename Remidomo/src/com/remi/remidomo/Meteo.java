package com.remi.remidomo;

import java.net.URI;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

class Meteo {
	private final static String TAG = RDActivity.class.getSimpleName();

	private final static String LOCATION = "le+touvet,france";
	private final static String METEO_URL = "http://free.worldweatheronline.com/feed/weather.ashx?format=json";
	private final static String API_KEY = "18d9f01634202138120203";
	private final static int NB_DAYS = 5;

	private final static String url = METEO_URL+"&q="+LOCATION+"&key="+API_KEY+"&num_of_days="+NB_DAYS;

	private ArrayList<MeteoData> meteoData = new ArrayList<MeteoData>();
	
	private Date lastUpdate = null;

	private static class MeteoData {
		public String day;
		public int weatherCode;
		public float minTemp;
		public float maxTemp;
	}
	
	public void updateView(RDActivity activity) {
	
		TextView dayText;
		ImageView dayPic;
		TextView minText;
		TextView maxText;
		
		if (meteoData.isEmpty()) {
			dayText = (TextView) activity.findViewById(R.id.meteo_day1);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic1);
			minText = (TextView) activity.findViewById(R.id.meteo_min1);
			maxText = (TextView) activity.findViewById(R.id.meteo_max1);
			dayText.setText("");
			dayPic.setImageResource(R.drawable.meteo_unknown);
			minText.setText("");
			maxText.setText("");
			
			dayText = (TextView) activity.findViewById(R.id.meteo_day2);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic2);
			minText = (TextView) activity.findViewById(R.id.meteo_min2);
			maxText = (TextView) activity.findViewById(R.id.meteo_max2);
			dayText.setText("");
			dayPic.setImageResource(R.drawable.meteo_unknown);
			minText.setText("");
			maxText.setText("");
			
			dayText = (TextView) activity.findViewById(R.id.meteo_day3);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic3);
			minText = (TextView) activity.findViewById(R.id.meteo_min3);
			maxText = (TextView) activity.findViewById(R.id.meteo_max3);
			dayText.setText("");
			dayPic.setImageResource(R.drawable.meteo_unknown);
			minText.setText("");
			maxText.setText("");
			
			dayText = (TextView) activity.findViewById(R.id.meteo_day4);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic4);
			minText = (TextView) activity.findViewById(R.id.meteo_min4);
			maxText = (TextView) activity.findViewById(R.id.meteo_max4);
			dayText.setText("");
			dayPic.setImageResource(R.drawable.meteo_unknown);
			minText.setText("");
			maxText.setText("");
			
			dayText = (TextView) activity.findViewById(R.id.meteo_day5);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic5);
			minText = (TextView) activity.findViewById(R.id.meteo_min5);
			maxText = (TextView) activity.findViewById(R.id.meteo_max5);
			dayText.setText("");
			dayPic.setImageResource(R.drawable.meteo_unknown);
			minText.setText("");
			maxText.setText("");
			
		} else {
			MeteoData dayData;
			DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("#0.#");

			dayData = meteoData.get(0);
			dayText = (TextView) activity.findViewById(R.id.meteo_day1);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic1);
			minText = (TextView) activity.findViewById(R.id.meteo_min1);
			maxText = (TextView) activity.findViewById(R.id.meteo_max1);
			dayText.setText(dayData.day);
			dayPic.setImageResource(getMeteoResource(dayData.weatherCode, dayData.maxTemp));
			minText.setText(decimalFormat.format(dayData.minTemp)+" ");
			maxText.setText(decimalFormat.format(dayData.maxTemp)+" ");

			dayData = meteoData.get(1);
			dayText = (TextView) activity.findViewById(R.id.meteo_day2);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic2);
			minText = (TextView) activity.findViewById(R.id.meteo_min2);
			maxText = (TextView) activity.findViewById(R.id.meteo_max2);
			dayText.setText(dayData.day);
			dayPic.setImageResource(getMeteoResource(dayData.weatherCode, dayData.maxTemp));
			minText.setText(decimalFormat.format(dayData.minTemp)+" ");
			maxText.setText(decimalFormat.format(dayData.maxTemp)+" ");
			
			dayData = meteoData.get(2);
			dayText = (TextView) activity.findViewById(R.id.meteo_day3);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic3);
			minText = (TextView) activity.findViewById(R.id.meteo_min3);
			maxText = (TextView) activity.findViewById(R.id.meteo_max3);
			dayText.setText(dayData.day);
			dayPic.setImageResource(getMeteoResource(dayData.weatherCode, dayData.maxTemp));
			minText.setText(decimalFormat.format(dayData.minTemp)+" ");
			maxText.setText(decimalFormat.format(dayData.maxTemp)+" ");

			dayData = meteoData.get(3);
			dayText = (TextView) activity.findViewById(R.id.meteo_day4);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic4);
			minText = (TextView) activity.findViewById(R.id.meteo_min4);
			maxText = (TextView) activity.findViewById(R.id.meteo_max4);
			dayText.setText(dayData.day);
			dayPic.setImageResource(getMeteoResource(dayData.weatherCode, dayData.maxTemp));
			minText.setText(decimalFormat.format(dayData.minTemp)+" ");
			maxText.setText(decimalFormat.format(dayData.maxTemp)+" ");

			dayData = meteoData.get(4);
			dayText = (TextView) activity.findViewById(R.id.meteo_day5);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic5);
			minText = (TextView) activity.findViewById(R.id.meteo_min5);
			maxText = (TextView) activity.findViewById(R.id.meteo_max5);
			dayText.setText(dayData.day);
			dayPic.setImageResource(getMeteoResource(dayData.weatherCode, dayData.maxTemp));
			minText.setText(decimalFormat.format(dayData.minTemp)+" ");
			maxText.setText(decimalFormat.format(dayData.maxTemp)+" ");
		}
	}
	
	public int getMeteoResource(int weatherCode, float maxTemp) {	
		if (weatherCode == 113) {          // Clear/Sunny
			if (maxTemp > 28) {
				return R.drawable.meteo_canicule;
			} else {
				return R.drawable.meteo_sunny;
			}
		} else if (weatherCode == 116) {   // Partly Cloudy
			return R.drawable.meteo_voile;
		} else if (weatherCode == 119) {   // Cloudy
			return R.drawable.meteo_cloud;
		} else if (weatherCode == 122) {   // Overcast
			return R.drawable.meteo_ncloud;
		} else if (weatherCode == 143) {   // Mist
			return R.drawable.meteo_mist;
		} else if (weatherCode == 176) {   // Patchy rain nearby
			return R.drawable.meteo_mist;
		} else if (weatherCode == 179) {   // Patchy snow nearby
			return R.drawable.meteo_snow;
		} else if (weatherCode == 182) {   // Patchy sleet
			return R.drawable.meteo_verglas;
		} else if (weatherCode == 185) {   // Patchy freezing drizzle nearby
			return R.drawable.meteo_verglas;
		} else if (weatherCode == 200) {   // Thundery outbreaks in nearby
			return R.drawable.meteo_ltonerre;
		} else if (weatherCode == 227) {   // Blowing snow
			return R.drawable.meteo_vsnow;
		} else if (weatherCode == 230) {   // Blizzard
			return R.drawable.meteo_vsnow;
		} else if (weatherCode == 248) {   // Fog
			return R.drawable.meteo_pcloud;
		} else if (weatherCode == 260) {   // Freezing Fog
			return R.drawable.meteo_gfog;
		} else if (weatherCode == 263) {   // Patchy light drizzle
			return R.drawable.meteo_lrain;
		} else if (weatherCode == 266) {   // Light drizzle
			return R.drawable.meteo_mist;
		} else if (weatherCode == 281) {   // Freezing drizzle
			return R.drawable.meteo_verglas;
		} else if (weatherCode == 284) {   // Heavy freezing drizzle
			return R.drawable.meteo_verglas;
		} else if (weatherCode == 293) {   // Patchy light rain
			return R.drawable.meteo_lrain;
		} else if (weatherCode == 296) {   // Light rain
			return R.drawable.meteo_lrain;
		} else if (weatherCode == 299) {   // Moderate rain at times
			return R.drawable.meteo_rain;
		} else if (weatherCode == 302) {   // Moderate rain
			return R.drawable.meteo_rain;
		} else if (weatherCode == 305) {   // Heavy rain at times
			return R.drawable.meteo_hrain;
		} else if (weatherCode == 308) {   // Heavy rain
			return R.drawable.meteo_hrain;
		} else if (weatherCode == 311) {   // Light freezing rain
			return R.drawable.meteo_verglas;
		} else if (weatherCode == 314) {   // Moderate or Heavy freezing rain
			return R.drawable.meteo_hfrain;
		} else if (weatherCode == 317) {   // Light sleet
			return R.drawable.meteo_verglas;
		} else if (weatherCode == 320) {   // Moderate or heavy sleet
			return R.drawable.meteo_lsnow;
		} else if (weatherCode == 323) {   // Patchy light snow
			return R.drawable.meteo_lsnow;
		} else if (weatherCode == 326) {   // Light snow
			return R.drawable.meteo_lsnow;
		} else if (weatherCode == 329) {   // Patchy moderate snow
			return R.drawable.meteo_snow;
		} else if (weatherCode == 332) {   // Moderate snow
			return R.drawable.meteo_snow;
		} else if (weatherCode == 335) {   // Patchy heavy snow
			return R.drawable.meteo_hsnow;
		} else if (weatherCode == 338) {   // Heavy snow
			return R.drawable.meteo_hsnow;
		} else if (weatherCode == 350) {   // Ice pellets
			return R.drawable.meteo_grele;
		} else if (weatherCode == 353) {   // Light rain shower
			return R.drawable.meteo_lrain;
		} else if (weatherCode == 356) {   // Moderate or heavy rain shower
			return R.drawable.meteo_srain;
		} else if (weatherCode == 359) {   // Torrential rain shower
			return R.drawable.meteo_torrent;
		} else if (weatherCode == 362) {   // Light sleet showers
			return R.drawable.meteo_lrain;
		} else if (weatherCode == 365) {   // Moderate or heavy sleet showers
			return R.drawable.meteo_rain;
		} else if (weatherCode == 368) {   // Light snow showers
			return R.drawable.meteo_lsnow;
		} else if (weatherCode == 371) {   // Moderate or heavy snow showers
			return R.drawable.meteo_snow;
		} else if (weatherCode == 374) {   // Light showers of ice pellets
			return R.drawable.meteo_grele;
		} else if (weatherCode == 377) {   // Moderate or heavy showers of ice pellets
			return R.drawable.meteo_hgrele;
		} else if (weatherCode == 386) {   // Patchy light rain in area with thunder
			return R.drawable.meteo_ltonerre;
		} else if (weatherCode == 389) {   // Moderate or heavy rain in area with thunder
			return R.drawable.meteo_tonerre;
		} else if (weatherCode == 392) {   // Patchy light snow in area with thunder
			return R.drawable.meteo_snowt;
		} else if (weatherCode == 395) {   // Moderate or heavy snow in area with thunder
			return R.drawable.meteo_snowt;
		} else {
			Log.d(TAG, "Unknown weather code: " + weatherCode);
			return R.drawable.meteo_unknown;
		}
	}
	
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
				SimpleDateFormat hr = new SimpleDateFormat("EEE");
				String dayName = hr.format(date);

				String code = dayForecast.getString("weatherCode");
				int weatherCode = Integer.parseInt(code);

				String minTC = dayForecast.getString("tempMinC");
				float minTemp = Float.parseFloat(minTC);

				String maxTC = dayForecast.getString("tempMaxC");
				float maxTemp = Float.parseFloat(maxTC);

				MeteoData dayData = new MeteoData();
				dayData.day = dayName;
				dayData.weatherCode = weatherCode;
				dayData.minTemp = minTemp;
				dayData.maxTemp = maxTemp;
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
	
	public Date getLastUpdate() {
		return lastUpdate;
	}
}