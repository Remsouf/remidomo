package com.remi.pompes;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CompoundButton;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class ExerciceFragment extends Fragment {

	public enum TabIndex { POMPES,
						   TRACTIONS,
						   ABDOS
						 };

	private WelcomeActivity activity;

	private SharedPreferences prefs;

	private TabIndex index;
	private int currentModule = 0;
	private int currentSemaine = 0;
	private int currentSeance = 0;

	private String name;
	private String nameOf;

	private ArrayList<Module> data = null;

    private int currentCountdown;
    private Timer timer = null;

	public ExerciceFragment() {
	}

	public final String getNameOf() {
		return nameOf;
	}

	public final String getName() {
		return name;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		activity = (WelcomeActivity) getActivity();

		Bundle args = getArguments();
		if (args != null) {
			index = TabIndex.valueOf(args.getString("index"));
		}

		prefs = PreferenceManager.getDefaultSharedPreferences(activity);

		readCurrentProgress();
	}

	public TabIndex getIndex() {
		return index;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		updateView();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		setRetainInstance(true);

		View view = inflater.inflate(R.layout.exercice, container, false);

		ImageView image = (ImageView) view.findViewById(R.id.icon);
		if (index == TabIndex.POMPES) {
			image.setImageResource(R.drawable.pompes);
			name = getString(R.string.title_pompes).toLowerCase();
			nameOf = getString(R.string.de_pompes);
		} else if (index == TabIndex.TRACTIONS) {
			image.setImageResource(R.drawable.tractions);
			name = getString(R.string.title_tractions).toLowerCase();
			nameOf = getString(R.string.de_tractions);
		} else if (index == TabIndex.ABDOS) {
			image.setImageResource(R.drawable.abdos);
			name = getString(R.string.title_abdos).toLowerCase();
			nameOf = getString(R.string.de_abdos);
		}
		
		ImageButton abandon = (ImageButton) view.findViewById(R.id.abandon);
		abandon.setOnClickListener(new ImageButton.OnClickListener() {
			public void onClick(View v) {
				LayoutInflater inflater = activity.getLayoutInflater();
				View dialogView = inflater.inflate(R.layout.abandon,
				                               	(ViewGroup) activity.findViewById(R.id.abandon_layout_root));
				AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        		builder.setView(dialogView)
        		.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
        			public void onClick(DialogInterface dialog, int id) {
        				// Just move calendar event ahead
        				updateCalendar(2);
        			}
        		})
        		.create()
        		.show();
			}
		});

		return view;
	}

	private void readCurrentProgress() {
		data = activity.getData(index);
		if (index == TabIndex.POMPES) {
			currentModule = prefs.getInt("pompes_module", 0);
			currentSemaine = prefs.getInt("pompes_semaine", 0);
			currentSeance = prefs.getInt("pompes_seance", 0);
		} else if (index == TabIndex.TRACTIONS) {
			currentModule = prefs.getInt("tractions_module", 0);
			currentSemaine = prefs.getInt("tractions_semaine", 0);
			currentSeance = prefs.getInt("tractions_seance", 0);
		} else if (index == TabIndex.ABDOS) {
			currentModule = prefs.getInt("abdos_module", 0);
			currentSemaine = prefs.getInt("abdos_semaine", 0);
			currentSeance = prefs.getInt("abdos_seance", 0);
		}
	}

	private void saveCurrentProgress() {
		SharedPreferences.Editor editor = prefs.edit();
		if (index == TabIndex.POMPES) {
			editor.putInt("pompes_module", currentModule);
			editor.putInt("pompes_semaine", currentSemaine);
			editor.putInt("pompes_seance", currentSeance);
		} else if (index == TabIndex.TRACTIONS) {
			editor.putInt("tractions_module", currentModule);
			editor.putInt("tractions_semaine", currentSemaine);
			editor.putInt("tractions_seance", currentSeance);
		} else if (index == TabIndex.ABDOS) {
			editor.putInt("abdos_module", currentModule);
			editor.putInt("abdos_semaine", currentSemaine);
			editor.putInt("abdos_seance", currentSeance);
		}
		editor.apply();
	}

	public void initCurrentProgress(int moduleIndex) {
		currentModule = moduleIndex;
		currentSemaine = 0;
		currentSeance = 0;
		saveCurrentProgress();
		updateView();
	}

	public void resetCurrentProgress() {
		initCurrentProgress(0);
	}
	
	private void incrementCurrentProgress() {
		Module module = data.get(currentModule);
		Semaine semaine = module.getSemaine(currentSemaine);
		
		int restDays = 0;

		// Try to move to next seance
		boolean isLastSerie = false;
		if (currentSeance < semaine.size()-1) {
			currentSeance++;
			restDays = 1;
		} else {
			if (currentSemaine < module.size()-1) {
				currentSemaine++;
				currentSeance = 0;
				restDays = 2;
			} else {
				if (currentModule < data.size()-1) {
					currentModule++;
					currentSemaine = 0;
					currentSeance = 0;
					restDays = 2;
				} else {
					restDays = 1;

					// Programme terminé !
					isLastSerie = true;

					LayoutInflater inflater = activity.getLayoutInflater();
					View dialogView = inflater.inflate(R.layout.complete,
					                               	(ViewGroup) activity.findViewById(R.id.complete_layout_root));
					AlertDialog.Builder builder = new AlertDialog.Builder(activity);
	        		builder.setView(dialogView)
	        		.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
	        			public void onClick(DialogInterface dialog, int id) {
	        				// Do nothing, just dismiss
	        			}
	        		})
	        		.create()
	        		.show();
				}
			}
		}

		if (!isLastSerie) {
			// Show a custom toast
			LayoutInflater inflater = activity.getLayoutInflater();
			View toastView = inflater.inflate(R.layout.bravo,
					(ViewGroup) activity.findViewById(R.id.toast_layout_root));
			Toast toast = new Toast(activity);
			toast.setDuration(Toast.LENGTH_LONG);
			toast.setView(toastView);
			toast.show();
		}

		saveCurrentProgress();
		updateCalendar(restDays + 1);
	}

	public int getCurrentScore() {
		if ((data != null) && (!data.isEmpty())) {
			return data.get(currentModule).getStart();
		} else {
			return 0;
		}
	}

	public int getMaxScore() {
		if (index == TabIndex.POMPES) {
			return 100;
		} else if (index == TabIndex.TRACTIONS) {
			return 50;
		} else if (index == TabIndex.ABDOS) {
			return 300;
		} else {
			assert(false);
			return 0;
		}
	}

	public void updateView() {
		if (getView() == null) {
			// View not created yet
			return;
		}

		TextView timer = (TextView) getView().findViewById(R.id.timer);
		timer.setVisibility(View.INVISIBLE);

		GridLayout layout = (GridLayout) getView().findViewById(R.id.series);
		layout.removeAllViews();

		TextView seanceText = (TextView) getView().findViewById(R.id.seance);
		seanceText.setVisibility(View.INVISIBLE);

		ProgressBar progress = (ProgressBar) getView().findViewById(R.id.progress);
		progress.setProgress(0);

		TextView detailText = (TextView) getView().findViewById(R.id.detail);
		detailText.setVisibility(View.GONE);

		if ((data != null) && (data.size() > 0)) {
			final Module module = data.get(currentModule);
			final Semaine semaine = module.getSemaine(currentSemaine);
			final Seance seance = semaine.getSeance(currentSeance);

			seanceText.setText(String.format(getString(R.string.seance), currentSemaine+1, currentSeance+1));
			seanceText.setVisibility(View.VISIBLE);

			timer.setText(R.string.go);
			timer.setVisibility(View.VISIBLE);

			progress.setProgress(module.getStart());

			if (module.getDetail() != null) {
				detailText.setText(module.getDetail());
				detailText.setVisibility(View.VISIBLE);
			}

			for (int i=0; i<seance.size(); i++) {
				final String serie = seance.getSerie(i);
				ToggleButton button = new ToggleButton(getView().getContext());

				button.setText(serie);
				button.setTextOn(serie);
				button.setTextOff(serie);

				button.setClickable(i == 0);
				button.setId(i);

				button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						if (isChecked) {
							if (!checkCompletion()) {
								startTimer(seance.getTempsRepos());
								
								// Enable only next button, all other disabled
								GridLayout layout = (GridLayout) getView().findViewById(R.id.series);
								for (int j=0; j<layout.getChildCount(); j++) {
									ToggleButton child = (ToggleButton) layout.getChildAt(j);
									child.setClickable(child.getId() == buttonView.getId() + 1);
								}
							}
						}
					}
				});

				layout.addView(button);
			}
		}

		Animation anim = AnimationUtils.loadAnimation(activity, R.anim.update);
    	getView().startAnimation(anim);
	}

	private boolean checkCompletion() {
		GridLayout layout = (GridLayout) getView().findViewById(R.id.series);
		boolean complete = true;
		for (int i=0; i < layout.getChildCount(); i++) {
			ToggleButton button = (ToggleButton) layout.getChildAt(i);
			if (!button.isChecked()) {
				complete = false;
				break;
			}
		}

		if (complete) {
			stopTimer();

			incrementCurrentProgress();
			updateView();
		}
		return complete;
	}

	private void updateCalendar(int restDays) {
		CalendarUpdater.updateEvent(this, restDays);
	}

	private void startTimer(int seconds) {
    	stopTimer();

    	currentCountdown = seconds;

    	timer = new Timer();
        timer.scheduleAtFixedRate(new CountdownTask(), 0, 1000);  // 1s
	}

	private void stopTimer() {
		if (timer != null) {
    		timer.cancel();
    		timer.purge();
    		timer = null;
    	}
	}

    private class CountdownTask extends TimerTask {

    	public void run() {
    		activity.runOnUiThread(new Runnable() {
				public void run() {
					if (getView() == null) {
						return;
					}
					TextView timerText = (TextView) getView().findViewById(R.id.timer);

					int seconds = currentCountdown % 60;
			    	int minutes = currentCountdown / 60;
			    	timerText.setText(String.format("%d:%02d", minutes, seconds));

					if (currentCountdown <= 0) {
						timerText.setText(R.string.go);
						stopTimer();
					}

					currentCountdown = currentCountdown - 1;
        		}
        	});
        }
    }
}