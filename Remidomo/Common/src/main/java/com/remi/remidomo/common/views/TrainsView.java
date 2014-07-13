package com.remi.remidomo.common.views;

import com.remi.remidomo.common.R;
import com.remi.remidomo.common.data.Trains;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;

public class TrainsView extends LinearLayout {
	
	private final static String TAG = "Remidomo-Common";

	public TrainsView(Context context) {
		super(context);
	}
	
	public TrainsView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void updateView(Activity activity, ArrayList<Trains.TrainData> data) {

		if (data.isEmpty()) {
			removeAllViews();
			return;
		}

		// Something to display, either old or new
		LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		removeAllViews();
		for (int i=0; i<data.size(); i++) {
			LinearLayout inflated = (LinearLayout) inflater.inflate(R.layout.train, TrainsView.this, true);
			RelativeLayout train = (RelativeLayout) inflated.getChildAt(i);
			TextView heure = (TextView) train.getChildAt(0);
			heure.setText(data.get(i).heure);

			ImageView icon = (ImageView) train.getChildAt(1);
			if (data.get(i).status) {
				icon.setImageResource(R.drawable.check);
			} else {
				icon.setImageResource(R.drawable.cross);
			}
			
			String info = data.get(i).info;
			TextView infoView = (TextView) train.getChildAt(2);
			if (info == null) {
				infoView.setText("");
			} else {
				infoView.setText(info+" ");
			}
		}
	}

}