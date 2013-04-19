package com.remi.remidomo.reloaded;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ViewFlipper;

/*
 * Workaround crashes in ViewFlipper with Android >= 2.1
 * (IllegalArgumentException in ViewFlipper)
 * http://code.google.com/p/android/issues/detail?id=6191
 */
public class CustomViewFlipper extends ViewFlipper {

	private final static String TAG = RDService.class.getSimpleName();

	public CustomViewFlipper(Context context) {
		super(context);
	}

	public CustomViewFlipper(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onDetachedFromWindow() {
		try {
			super.onDetachedFromWindow();
		} catch(IllegalArgumentException e) {
			Log.d(TAG, "Android project  issue 6191  workaround");
		} finally {
			// Call stopFlipping() in order to kick off updateRunning()
			super.stopFlipping();
		}
	}
}
