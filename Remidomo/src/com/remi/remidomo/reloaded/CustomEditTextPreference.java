package com.remi.remidomo.reloaded;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.webkit.URLUtil;
import android.widget.Button;

/*
 * Because we cannot override setText() in EditTextPreference,
 * we cannot prevent the ancestor from persisting value as a string.
 * If the value is an int, then we persist <key>.int as well.
 */
public class CustomEditTextPreference extends EditTextPreference
{
	private EditTextWatcher watcher;
	private boolean isIpValidator = false;
	private boolean isIntegerValidator = false;
	private int minInteger = Integer.MIN_VALUE;
	private int maxInteger = Integer.MAX_VALUE;

	public CustomEditTextPreference(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		watcher = new EditTextWatcher(context);

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CustomEditTextPreference);
		isIpValidator = a.getBoolean(R.styleable.CustomEditTextPreference_validator_ip, false);
		isIntegerValidator = a.getBoolean(R.styleable.CustomEditTextPreference_validator_int, false);
		minInteger = a.getInt(R.styleable.CustomEditTextPreference_min_integer, Integer.MIN_VALUE);
		maxInteger = a.getInt(R.styleable.CustomEditTextPreference_max_integer, Integer.MAX_VALUE);
	}

	public CustomEditTextPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		watcher = new EditTextWatcher(context);

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CustomEditTextPreference);
		isIpValidator = a.getBoolean(R.styleable.CustomEditTextPreference_validator_ip, false);
		isIntegerValidator = a.getBoolean(R.styleable.CustomEditTextPreference_validator_int, false);
		minInteger = a.getInt(R.styleable.CustomEditTextPreference_min_integer, Integer.MIN_VALUE);
		maxInteger = a.getInt(R.styleable.CustomEditTextPreference_max_integer, Integer.MAX_VALUE);
	}

	private class EditTextWatcher implements TextWatcher
	{
		private Context context;

		public EditTextWatcher(Context context) {
			this.context = context;
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count){}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int before, int count){}

		@Override
		public void afterTextChanged(Editable s)
		{        
			onEditTextChanged(context, s);
		}
	}

	protected void onEditTextChanged(Context context, Editable s)
	{
		getEditText().setError(null);

		if (isIpValidator) {
	    	if (!URLUtil.isValidUrl(s.toString())) {
	    		getEditText().setError(context.getString(R.string.invalid_url));
	    		// Do not fix URL, even URLUtil knows how to do it
	    	}
		}
		if (isIntegerValidator) {
			try {
				int Number = Integer.parseInt(s.toString());
				if (Number < minInteger) {
					getEditText().setError(String.format(context.getString(R.string.invalid_int_lower),
							minInteger-1));
				} else if (Number > maxInteger) {
					getEditText().setError(String.format(context.getString(R.string.invalid_int_higher),
							maxInteger+1));
				}
			} catch (java.lang.NumberFormatException e) {
				getEditText().setError(context.getString(R.string.invalid_int));
			}
		}

		// If there's something wrong, disable the OK button
		Dialog dlg = getDialog();
		if(dlg instanceof AlertDialog) {
			AlertDialog alertDlg = (AlertDialog)dlg;
			Button btn = alertDlg.getButton(AlertDialog.BUTTON_POSITIVE);

			if (getEditText().getError() != null) {
				btn.setEnabled(false);
			} else {
				btn.setEnabled(true);
			}
		}
	}

	@Override
	protected void showDialog(Bundle state)
	{
		super.showDialog(state);

		getEditText().removeTextChangedListener(watcher);
		getEditText().addTextChangedListener(watcher);

		//onEditTextChanged();
	}

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            String value = getEditText().getText().toString();
            if (callChangeListener(value)) {
                setText(value);

                if (isIntegerValidator) {
                	SharedPreferences.Editor editor = getEditor();
    				editor.putInt(getKey() + ".int", Integer.parseInt(value));
    				editor.commit();
                }
            }
        }
    }
}
