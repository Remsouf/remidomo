package com.remi.remidomo.common.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;

import com.remi.remidomo.common.R;

public class CustomSpinnerPreference extends DialogPreference {

	private CustomSpinner spinner;
	private int defaultValue;
	private int minValue;
	private int maxValue;

	public CustomSpinnerPreference(Context context,
			AttributeSet attributes) {
		super(context, attributes);

		// Hook the dialog to the layout
		setDialogLayoutResource(R.layout.spinner_preference);

		TypedArray a = context.obtainStyledAttributes(attributes,
				R.styleable.CustomSpinnerPreference);
		defaultValue = a.getInt(R.styleable.CustomSpinnerPreference_defaultVal, 0);
		minValue = a.getInt(R.styleable.CustomSpinnerPreference_min, 1);
		maxValue = a.getInt(R.styleable.CustomSpinnerPreference_max, 100);
	}

	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);

		spinner = (CustomSpinner) view.findViewById(R.id.prefSpinner);
		spinner.setMinimum(minValue);
		spinner.setMaximum(maxValue);
		spinner.setCurrent(getSharedPreferences().getInt(getKey(), defaultValue));
	}

	@Override
	public void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			if (this.isPersistent()) {
				persistInt(spinner.getCurrent());
			}

			if (!callChangeListener(spinner.getCurrent())) {
				return;
			}
		}
	}
}