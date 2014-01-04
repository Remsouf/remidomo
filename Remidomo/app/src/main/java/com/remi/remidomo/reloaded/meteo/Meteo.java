package com.remi.remidomo.reloaded.meteo;

import com.remi.remidomo.reloaded.*;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import android.app.Dialog;
import android.content.res.Resources;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public abstract class Meteo {
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

		Resources rsrc = activity.getResources();
		final String packageName = activity.getPackageName();

		if (meteoData.isEmpty()) {
			for (int i=1; i<=NB_DAYS; i++) {
				int rId = rsrc.getIdentifier("meteo_day" + i, "id", packageName);
				dayText = (TextView) activity.findViewById(rId);

				rId = rsrc.getIdentifier("meteo_pic" + i, "id", packageName);
				dayPic = (ImageView) activity.findViewById(rId);

				rId = rsrc.getIdentifier("meteo_min" + i, "id", packageName);
				minText = (TextView) activity.findViewById(rId);

				rId = rsrc.getIdentifier("meteo_max" + i, "id", packageName);
				maxText = (TextView) activity.findViewById(rId);

				dayText.setText("");
				dayPic.setImageResource(R.drawable.meteo_unknown);
				minText.setText("");
				maxText.setText("");
			}
			
		} else {
			MeteoData dayData;
			DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("#0.#");

            SimpleDateFormat hr = new SimpleDateFormat("E", Locale.getDefault());
            String dayName;

            for (int i=1; i<=NB_DAYS; i++) {
				dayData = meteoData.get(i-1);

				int rId = rsrc.getIdentifier("meteo_day" + i, "id", packageName);
				dayText = (TextView) activity.findViewById(rId);

				rId = rsrc.getIdentifier("meteo_pic" + i, "id", packageName);
				dayPic = (ImageView) activity.findViewById(rId);

				rId = rsrc.getIdentifier("meteo_min" + i, "id", packageName);
				minText = (TextView) activity.findViewById(rId);

				rId = rsrc.getIdentifier("meteo_max" + i, "id", packageName);
				maxText = (TextView) activity.findViewById(rId);

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
			SimpleDateFormat hr = new SimpleDateFormat("EEEE", Locale.getDefault());

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