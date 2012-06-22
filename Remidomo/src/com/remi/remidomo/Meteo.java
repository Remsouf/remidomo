package com.remi.remidomo;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.app.Dialog;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

abstract class Meteo {
	// private final static String TAG = RDActivity.class.getSimpleName();

	protected ArrayList<MeteoData> meteoData = new ArrayList<MeteoData>();

	protected final static int NB_DAYS = 5;

	protected Date lastUpdate = null;

	protected static class MeteoData {
		public Date date;
		public int resourceId;
		public float minTemp;
		public float maxTemp;
		public String details;
	}

	public abstract void updateData(RDService service);

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

            SimpleDateFormat hr = new SimpleDateFormat("EEE");
            String dayName;

			dayData = meteoData.get(0);
			dayText = (TextView) activity.findViewById(R.id.meteo_day1);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic1);
			minText = (TextView) activity.findViewById(R.id.meteo_min1);
			maxText = (TextView) activity.findViewById(R.id.meteo_max1);
			dayName = hr.format(dayData.date);
			dayText.setText(dayName);
			dayPic.setImageResource(dayData.resourceId);
			minText.setText(decimalFormat.format(dayData.minTemp)+" ");
			maxText.setText(decimalFormat.format(dayData.maxTemp)+" ");
			dayPic.setOnClickListener(new ImageButton.OnClickListener() {
				public void onClick(View v) {
					displayDialog(v);
				}
			});

			dayData = meteoData.get(1);
			dayText = (TextView) activity.findViewById(R.id.meteo_day2);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic2);
			minText = (TextView) activity.findViewById(R.id.meteo_min2);
			maxText = (TextView) activity.findViewById(R.id.meteo_max2);
			dayName = hr.format(dayData.date);
			dayText.setText(dayName);
			dayPic.setImageResource(dayData.resourceId);
			minText.setText(decimalFormat.format(dayData.minTemp)+" ");
			maxText.setText(decimalFormat.format(dayData.maxTemp)+" ");
			dayPic.setOnClickListener(new ImageButton.OnClickListener() {
				public void onClick(View v) {
					displayDialog(v);
				}
			});

			dayData = meteoData.get(2);
			dayText = (TextView) activity.findViewById(R.id.meteo_day3);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic3);
			minText = (TextView) activity.findViewById(R.id.meteo_min3);
			maxText = (TextView) activity.findViewById(R.id.meteo_max3);
			dayName = hr.format(dayData.date);
			dayText.setText(dayName);
			dayPic.setImageResource(dayData.resourceId);
			minText.setText(decimalFormat.format(dayData.minTemp)+" ");
			maxText.setText(decimalFormat.format(dayData.maxTemp)+" ");
			dayPic.setOnClickListener(new ImageButton.OnClickListener() {
				public void onClick(View v) {
					displayDialog(v);
				}
			});

			dayData = meteoData.get(3);
			dayText = (TextView) activity.findViewById(R.id.meteo_day4);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic4);
			minText = (TextView) activity.findViewById(R.id.meteo_min4);
			maxText = (TextView) activity.findViewById(R.id.meteo_max4);
			dayName = hr.format(dayData.date);
			dayText.setText(dayName);
			dayPic.setImageResource(dayData.resourceId);
			minText.setText(decimalFormat.format(dayData.minTemp)+" ");
			maxText.setText(decimalFormat.format(dayData.maxTemp)+" ");
			dayPic.setOnClickListener(new ImageButton.OnClickListener() {
				public void onClick(View v) {
					displayDialog(v);
				}
			});

			dayData = meteoData.get(4);
			dayText = (TextView) activity.findViewById(R.id.meteo_day5);
			dayPic = (ImageView) activity.findViewById(R.id.meteo_pic5);
			minText = (TextView) activity.findViewById(R.id.meteo_min5);
			maxText = (TextView) activity.findViewById(R.id.meteo_max5);
			dayName = hr.format(dayData.date);
			dayText.setText(dayName);
			dayPic.setImageResource(dayData.resourceId);
			minText.setText(decimalFormat.format(dayData.minTemp)+" ");
			maxText.setText(decimalFormat.format(dayData.maxTemp)+" ");
			dayPic.setOnClickListener(new ImageButton.OnClickListener() {
				public void onClick(View v) {
					displayDialog(v);
				}
			});
		}
	}
	

	
	public Date getLastUpdate() {
		return lastUpdate;
	}
	
	private void displayDialog(View v) {
		MeteoData data = null;
		if (v == (ImageView) v.findViewById(R.id.meteo_pic1)) {
			data = meteoData.get(0);
		} else if (v == (ImageView) v.findViewById(R.id.meteo_pic2)) {
			data = meteoData.get(1);
		} else if (v == (ImageView) v.findViewById(R.id.meteo_pic3)) {
			data = meteoData.get(2);
		} else if (v == (ImageView) v.findViewById(R.id.meteo_pic4)) {
			data = meteoData.get(3);
		} else if (v == (ImageView) v.findViewById(R.id.meteo_pic5)) {
			data = meteoData.get(4);
		}

		if (data.details != null) {
			SimpleDateFormat hr = new SimpleDateFormat("EEEEE");

			final Dialog dialog = new Dialog(v.getContext(), R.style.MeteoDialog);
			dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			dialog.setContentView(R.layout.meteo_details);
			dialog.setTitle("");
			dialog.setCancelable(true);

			TextView text = (TextView) dialog.findViewById(R.id.meteodetails_text);
			text.setText(data.details);

			TextView day = (TextView) dialog.findViewById(R.id.meteodetails_day);
			day.setText(hr.format(data.date));

			ImageButton img = (ImageButton) dialog.findViewById(R.id.meteodetails_img);
			img.setImageResource(data.resourceId);

			ImageButton button = (ImageButton) dialog.findViewById(R.id.meteodetails_button);
			button.setOnClickListener(new ImageButton.OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
				}
			});

			dialog.show();
		}
	}
}