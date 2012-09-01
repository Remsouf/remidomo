package com.remi.remidomo;

/*
 * http://www.twodee.org/weblog/?p=1037
 */

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

/**
 * Custom preference for time selection. Hour and minute are persistent and
 * stored separately as ints in the underlying shared preferences under keys
 * KEY.hour and KEY.minute, where KEY is the preference's key.
 */
public class CustomTimePickerPreference extends DialogPreference {
	/** The widget for picking a time. */
	private TimePicker timePicker;

	/** Default hour */
	private int defaultHour = 0;

	/** Default minute */
	private int defaultMinute = 0;

	/**
	 * Creates a preference for choosing a time based on its XML declaration.
	 * 
	 * @param context
	 * @param attributes
	 */
	public CustomTimePickerPreference(Context context,
			AttributeSet attributes) {
		super(context, attributes);

		TypedArray a = context.obtainStyledAttributes(attributes,
                									  R.styleable.CustomTimePickerPreference);
		defaultHour = a.getInt(R.styleable.CustomTimePickerPreference_defaultValue, 0);
		defaultMinute = 0;
	}

	/**
	 * Initialize time picker to currently stored time preferences.
	 */
	@Override
	public void onBindDialogView(View view) {
		timePicker = (TimePicker) view.findViewById(R.id.prefTimePicker);
		timePicker.setIs24HourView(true);
		timePicker.setCurrentHour(getSharedPreferences().getInt(getKey() + ".hour", defaultHour));
		timePicker.setCurrentMinute(getSharedPreferences().getInt(getKey() + ".minute", defaultMinute));
	}

	/**
	 * Handles button click. If positive button is hit, selected hour and minute
	 * are stored in the preferences under with keys KEY.hour and KEY.minute,
	 * where KEY is the preference's KEY.
	 */
	@Override
	public void onClick(DialogInterface dialog,
			int button) {
		if (button == Dialog.BUTTON_POSITIVE) {
			if (this.isPersistent()) {
				SharedPreferences.Editor editor = getEditor();
				editor.putInt(getKey() + ".hour", timePicker.getCurrentHour());
				editor.putInt(getKey() + ".minute", timePicker.getCurrentMinute());
				editor.commit();
			}

			if (!callChangeListener(timePicker.getCurrentHour())) {
	            return;
	        }
		}
	}
}
