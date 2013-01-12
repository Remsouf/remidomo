package com.remi.pompes;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class TestDialogFragment extends DialogFragment {
	
	// Use this instance of the interface to deliver action events
    private TestDialogListener mListener;
    
    private CustomSpinner spinner;
    
    private ExerciceFragment origin; // who created this fragment

    public TestDialogFragment() {
    	origin = null;
    }

    public TestDialogFragment(ExerciceFragment origin) {
    	this.origin = origin;
    }

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		// Use the Builder class for convenient dialog construction
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(String.format(getString(R.string.test_dialog), origin.getNameOf()))
		.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// Set starting point
				mListener.onDialogPositiveClick(TestDialogFragment.this,
						spinner.getCurrent(),
						origin);
			}
		})
		.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// User cancelled the dialog
				// Do nothing
			}
		});
		// Create the AlertDialog object and return it
		AlertDialog dialog = builder.create();

		LayoutInflater inflater = getActivity().getLayoutInflater();
		View customView = inflater.inflate(R.layout.test_dialog, (ViewGroup) dialog.getCurrentFocus(), false);
		dialog.setView(customView);
		spinner = (CustomSpinner) customView.findViewById(R.id.test_spinner);
		spinner.setCurrent(Math.max(1, origin.getCurrentScore()));
		spinner.setMaximum(origin.getMaxScore());
		return dialog;
	}

	/* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface TestDialogListener {
        public void onDialogPositiveClick(DialogFragment dialog, int value, ExerciceFragment fragment);
    }    

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (TestDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement NoticeDialogListener");
        }
    }
}