package com.remi.remidomo;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;
import android.widget.ViewFlipper;
import android.util.Log;

public class RDActivity extends Activity implements OnGestureListener {
	
	private final static String TAG = RDActivity.class.getSimpleName();

	private static final int SWIPE_MIN_DISTANCE = 60;
    private static final int SWIPE_THRESHOLD_VELOCITY = 100;
    
    private static final int DASHBOARD_VIEW_ID = 0;
    private static final int TEMP_VIEW_ID = 1;
    private static final int POOL_VIEW_ID = 2;
    private static final int SWITCHES_VIEW_ID = 3;
    private static final int LOG_VIEW_ID = 4;
 
	private ViewFlipper flipper;

	protected GestureDetector gestureScanner;

	public RDService service;
	
	// Class to manage the Refresh button
    private class RefreshTask extends AsyncTask<Void, Void, Void>
    {
    	@Override
    	protected void onPreExecute() {
    		super.onPreExecute();
    		updateListener.startRefreshAnim();            
        }

        @Override
        protected Void doInBackground(Void... arg0) {
        	service.forceRefresh();
            return null;
        }
     
        @Override
        protected void onPostExecute(Void result) {
        	super.onPostExecute(result);
        	updateListener.stopRefreshAnim();
        }
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Remove title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Request landscape orientation (90 or 270)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        setContentView(R.layout.main);

        gestureScanner = new GestureDetector(this);

        flipper = (ViewFlipper) findViewById(R.id.viewFlipper);
        flipper.setInAnimation(this, R.anim.slide_in_left);
		flipper.setOutAnimation(this, R.anim.slide_out_right);

		setupClickListeners();
		
        // Show splash screen only if we're really starting service
        boolean serviceRunning = false;
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ("com.remi.remidomo.RDService".equals(service.service.getClassName())) {
                serviceRunning = true;
                break;
            }
        }

        TextView title = (TextView) findViewById(R.id.log_title);
        try {
        	String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        	title.setText(getString(R.string.log) + " (v" + versionName + ")");
        } catch (PackageManager.NameNotFoundException ignored) {
        	title.setText(getString(R.string.log));
        }
        
        if(!serviceRunning) {
        	showSplash();
        }
      
        // Start service *before* binding,
        // for it to survive the activity
        Intent intent = new Intent(this, RDService.class);
        startService(intent);
        if (!bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
        	Log.e(TAG, "Failed to bind to service");
        }

        // Timer for updating "Il y a ..." texts
        Timer timerIlya = new Timer("Il y a");
        timerIlya.scheduleAtFixedRate(new IlyaTask(), 1, 60000);  // 1min

        // Start with dashboard view
        ImageButton dashboard = (ImageButton) findViewById(R.id.dashboardButton);
        changeView(dashboard, DASHBOARD_VIEW_ID);
    }
    
    /**
     * Activity (re-)started, see if we're asked to put
     * a view on the foreground.
     */
    public void onNewIntent(Intent intent) {
        if (intent.hasExtra("view")) {
        	// Someone else started us (notification for ex.),
        	// asking for a view to be brought to front
        	if (intent.getIntExtra("view", 0) == R.id.dashboardView) {
        		flipper.setDisplayedChild(DASHBOARD_VIEW_ID);
        	} else if (intent.getIntExtra("view", 0) == R.id.thermoView) {
        		flipper.setDisplayedChild(TEMP_VIEW_ID);
        	} else if (intent.getIntExtra("view", 0) == R.id.poolView) {
        		flipper.setDisplayedChild(POOL_VIEW_ID);
        	} else if (intent.getIntExtra("view", 0) == R.id.switchesView) {
        		flipper.setDisplayedChild(SWITCHES_VIEW_ID);
        	} else if (intent.getIntExtra("view", 0) == R.id.logView) {
        		flipper.setDisplayedChild(LOG_VIEW_ID);
        	}
        }
    }

    private void setupClickListeners() {
        ImageButton settings = (ImageButton) findViewById(R.id.settingsButton);
        settings.setOnClickListener(new ImageButton.OnClickListener() {
        	public void onClick(View v) {
        		startActivity(new Intent(RDActivity.this, Preferences.class));
        	}
        });

        ImageButton poolTemp = (ImageButton) findViewById(R.id.poolButton);
        poolTemp.setOnClickListener(new ImageButton.OnClickListener() {
        	public void onClick(View v) {
        		changeView(v, POOL_VIEW_ID);
        	}
        });

        ImageButton thermo = (ImageButton) findViewById(R.id.tempButton);
        thermo.setOnClickListener(new ImageButton.OnClickListener() {
        	public void onClick(View v) {
        		changeView(v, TEMP_VIEW_ID);
        	}
        });

        ImageButton dashboard = (ImageButton) findViewById(R.id.dashboardButton);
        dashboard.setOnClickListener(new ImageButton.OnClickListener() {
        	public void onClick(View v) {
        		changeView(v, DASHBOARD_VIEW_ID);
        	}
        });
      
        ImageButton log = (ImageButton) findViewById(R.id.logButton);
        log.setOnClickListener(new ImageButton.OnClickListener() {
        	public void onClick(View v) {
        		changeView(v, LOG_VIEW_ID);
        	}
        });

        ImageButton switches = (ImageButton) findViewById(R.id.switchButton);
        switches.setOnClickListener(new ImageButton.OnClickListener() {
        	public void onClick(View v) {
        		changeView(v, SWITCHES_VIEW_ID);
        	}
        });
        
        ImageButton trainNote = (ImageButton) findViewById(R.id.train_note);
        trainNote.setOnClickListener(new TrainsView.OnClickListener() {
        	public void onClick(View v) {
        		//	String gare = prefs.getString("gare", Preferences.DEFAULT_GARE);
        		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.gares-en-mouvement.com/fr/frhvx/horaires-temps-reel/dep/"));
        		startActivity(browserIntent);
        	}
        });

        RelativeLayout poolLayout = (RelativeLayout) findViewById(R.id.pool_layout);
        poolLayout.setOnClickListener(new RelativeLayout.OnClickListener() {
        	public void onClick(View v) {
        		changeView(v, POOL_VIEW_ID);
        	}
        });
        
        RelativeLayout extLayout = (RelativeLayout) findViewById(R.id.ext_layout);
        extLayout.setOnClickListener(new RelativeLayout.OnClickListener() {
        	public void onClick(View v) {
        		changeView(v, TEMP_VIEW_ID);
        	}
        });

        RelativeLayout verandaLayout = (RelativeLayout) findViewById(R.id.veranda_layout);
        verandaLayout.setOnClickListener(new RelativeLayout.OnClickListener() {
        	public void onClick(View v) {
        		changeView(v, TEMP_VIEW_ID);
        	}
        });
        
        ImageButton refresh = (ImageButton) findViewById(R.id.refreshButton);
        refresh.setOnClickListener(new ImageButton.OnClickListener() {
        	public void onClick(View v) {
        		new Thread(new Runnable() {
        			public void run() {
        				RefreshTask refreshTask = new RefreshTask();
        				refreshTask.execute();
        			}
        		}).start();
        	}
        });
        
        // Hooks for switches buttons
        ImageButton switchButton = (ImageButton) findViewById(R.id.switch1_cmd);
        switchButton.setOnClickListener(new TrainsView.OnClickListener() {
        	public void onClick(View v) {
        		service.toggleSwitch(0);
        	}
        });
        
        // Hooks for log buttons
        final ImageButton clearLogButton = (ImageButton) findViewById(R.id.trash_button);
        clearLogButton.setOnClickListener(new RelativeLayout.OnClickListener() {
        	public void onClick(View v) {
        		clearLog();
        	}
        });

        final ImageButton serverLogButton = (ImageButton) findViewById(R.id.rlog_button);
        final ImageButton clientLogButton = (ImageButton) findViewById(R.id.log_button);
        serverLogButton.setOnClickListener(new RelativeLayout.OnClickListener() {
        	public void onClick(View v) {
        		ScrollView localLog = (ScrollView) findViewById(R.id.logscroller);
        		localLog.setVisibility(View.GONE);

        		ScrollView remoteLog = (ScrollView) findViewById(R.id.rlogscroller);
				remoteLog.setVisibility(View.VISIBLE);

        		serverLogButton.setVisibility(View.GONE);
        		clientLogButton.setVisibility(View.VISIBLE);
        		clearLogButton.setVisibility(View.GONE);

        		showRemoteLog();
        	}
        });

        clientLogButton.setOnClickListener(new RelativeLayout.OnClickListener() {
        	public void onClick(View v) {
        		ScrollView localLog = (ScrollView) findViewById(R.id.logscroller);
        		localLog.setVisibility(View.VISIBLE);

        		ScrollView remoteLog = (ScrollView) findViewById(R.id.rlogscroller);
        		remoteLog.setVisibility(View.GONE);

        		ProgressBar progress = (ProgressBar) findViewById(R.id.log_progress);
        		progress.setVisibility(View.GONE);

        		serverLogButton.setVisibility(View.VISIBLE);
        		clientLogButton.setVisibility(View.GONE);
        		clearLogButton.setVisibility(View.VISIBLE);
        	}
        });
    }

    private void changeView(View button, int destination) {
    	// Change flipper view + animate buttons
    	flipper.setDisplayedChild(destination);

    	ImageButton poolTemp = (ImageButton) findViewById(R.id.poolButton);
    	ImageButton thermo = (ImageButton) findViewById(R.id.tempButton);
    	ImageButton log = (ImageButton) findViewById(R.id.logButton);
    	ImageButton dashboard = (ImageButton) findViewById(R.id.dashboardButton);
    	ImageButton switches = (ImageButton) findViewById(R.id.switchButton);

    	poolTemp.clearAnimation();
    	thermo.clearAnimation();
    	log.clearAnimation();
    	dashboard.clearAnimation();
    	switches.clearAnimation();

		Animation anim = AnimationUtils.loadAnimation(RDActivity.this, R.anim.icon_select);
		button.startAnimation(anim);
    }

    @Override
    protected void onResume() {
    	super.onResume();
    	
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    	ImageButton refresh = (ImageButton) findViewById(R.id.refreshButton);
        ImageButton serverLogButton = (ImageButton) findViewById(R.id.rlog_button);
        ImageButton clientLogButton = (ImageButton) findViewById(R.id.log_button);

    	String mode = prefs.getString("mode", Preferences.DEFAULT_MODE);
        if ("Serveur".equals(mode)) {
        	refresh.setVisibility(View.GONE);
        	serverLogButton.setVisibility(View.GONE);
        	clientLogButton.setVisibility(View.GONE);
        } else {
        	refresh.setVisibility(View.VISIBLE);
        }

		updateListener.startRefreshAnim();
    	updatePoolView();
    	updateLogView();
    	updateThermoView();
    	updateSwitchesView();
    	updateDoorsView();
    	updateTrainView();
    	updateDashboardThermo();
		updateListener.stopRefreshAnim();

    	updateTrainLastUpdate();
		updateMeteoLastUpdate();
		updateThermoLastUpdate();
    }

    @Override
    protected void onDestroy() {
    	unbindService(serviceConnection);
    	super.onDestroy();
    }
    
	@Override
	public boolean onTouchEvent(MotionEvent me) {
		return gestureScanner.onTouchEvent(me);
	}

	public boolean onDown(MotionEvent e) {
		return true;
	}

	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		try {
			if (velocityX > 0) {
				flipper.setInAnimation(this, R.anim.slide_in_left);
				flipper.setOutAnimation(this, R.anim.slide_out_right);
			} else {
				flipper.setInAnimation(this, R.anim.slide_in_right);
				flipper.setOutAnimation(this, R.anim.slide_out_left);
			}
            if (e1.getX() > e2.getX() && Math.abs(e1.getX() - e2.getX()) > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
            	flipper.showNext();
            } else if (e1.getX() < e2.getX() && e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
            	flipper.showPrevious();
            }
        } catch (Exception e) {
            // nothing
        }
        return true;
	}
	
	public void onLongPress(MotionEvent e) {}	

	public boolean onScroll(MotionEvent e1,MotionEvent e2,float distanceX,float distanceY) {
		return true;
	}

	public void onShowPress(MotionEvent e) {}

	public boolean onSingleTapUp(MotionEvent e) {
		return true;
	}
	
	private void showSplash() {
		LayoutInflater inflater = getLayoutInflater();
		View layout = inflater.inflate(R.layout.splash,
					(ViewGroup) findViewById(R.id.splash));
		Toast toast = new Toast(this);
		toast.setDuration(Toast.LENGTH_LONG);
		toast.setView(layout);
		toast.show();
	}
	
	private void clearLog() {
		new AlertDialog.Builder(RDActivity.this)
        .setIcon(android.R.drawable.ic_delete)
        .setTitle(R.string.clear_log_title)
        .setMessage(R.string.clear_log_msg)
        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            	Animation anim = AnimationUtils.loadAnimation(RDActivity.this, R.anim.rotozoomout);
            	TextView log = (TextView) findViewById(R.id.logtext);
            	WebView rlog = (WebView) findViewById(R.id.rlogtext);
            	if (log.getVisibility() == View.VISIBLE) {
            		log.startAnimation(anim);
            	} else if (rlog.getVisibility() == View.VISIBLE) {
            		rlog.startAnimation(anim);
            	}
            	// Clear log at the end of animation
            	anim.setAnimationListener(new AnimationListener() {
            		@Override
            		public void onAnimationStart(Animation animation) {}

            		@Override
            		public void onAnimationRepeat(Animation animation) {}

            		@Override
            		public void onAnimationEnd(Animation animation) {
            			if (service != null) {
            				service.clearLog();
            			}
            		}
            	});

            }
        })
        .setNegativeButton(android.R.string.no, null)
        .show();
	}

	private void showRemoteLog() {
		final ProgressBar progress = (ProgressBar) findViewById(R.id.log_progress);
		progress.setVisibility(View.VISIBLE);

		WebView remoteContent = (WebView) findViewById(R.id.rlogtext);

		// WebViewClient for errors
		remoteContent.setWebViewClient(new WebViewClient() {

			@Override
			public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
				Toast.makeText(RDActivity.this, R.string.rlog_failed, Toast.LENGTH_SHORT).show();
				if (service != null) {
					service.addLog(service.getString(R.string.rlog_failed) + description);
					Log.d(TAG, service.getString(R.string.rlog_failed) + description);
				}
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				progress.setVisibility(View.GONE);
			}
		});

		// WebChromeClient for progress
		remoteContent.setWebChromeClient(new WebChromeClient() {

			@Override
			public void onProgressChanged(WebView view, int progr) {
				progress.setProgress(progr);
			}

		});

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(RDActivity.this);
		int port = Integer.parseInt(prefs.getString("port", Preferences.DEFAULT_PORT));
		String ipAddr = prefs.getString("ip_address", Preferences.DEFAULT_IP);

		remoteContent.clearView();
		remoteContent.loadUrl("http://" + ipAddr + ":" + port + "/log");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options, menu);
		return true;
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.save_sdcard:
			service.getSensors().saveToSdcard();
			return true;
		case R.id.settings:
			startActivity(new Intent(RDActivity.this, Preferences.class));
			return true;
		}

		return super.onMenuItemSelected(featureId, item);
	}

	private class IlyaTask extends TimerTask {
		@Override
		public void run() {
			runOnUiThread(new Runnable() {
				public void run() {
					updateTrainLastUpdate();
					updateMeteoLastUpdate();
					updateThermoLastUpdate();
				}
			});
		}
	}
	
	private ServiceConnection serviceConnection = new ServiceConnection() {
	      public void onServiceConnected(ComponentName className, IBinder serviceBind) {
	    	  	RDService.LocalBinder binder = (RDService.LocalBinder) serviceBind;
	    	  	service = binder.getService();
	    	  	
	    	  	binder.registerCallbacks(updateListener);
        	
	    	  	// Once connected to service, update views
	        	updatePoolView();
	        	updateLogView();
	        	updateThermoView();
	        	updateSwitchesView();
	        	updateDoorsView();
	        	updateTrainView();
	        	updateDashboardThermo();
	        }

	        public void onServiceDisconnected(ComponentName className) {
	            service = null;
	        }
	    };

	private void updatePoolView() {
		SensorData series = null;
		
		// Pool temp
		if (service != null) {
			series = service.getSensors().getData(Sensors.ID_POOL_T);
		}

		SensorPlot plot = (SensorPlot) findViewById(R.id.poolTempPlot);
		plot.clear();
		plot.removeMarkers();
		plot.addSeries(series);
		plot.redraw();
	}
	
	private void updateThermoView() {
		SensorData series = null;

		if (service != null) {

			SensorPlot plot = (SensorPlot) findViewById(R.id.ThermoPlot);
			plot.clear();
			plot.removeMarkers();

			// Pool
			series = service.getSensors().getData(Sensors.ID_POOL_T);
			if (series != null) {
				plot.addSeries(series);
			}

			// Ext
			series = service.getSensors().getData(Sensors.ID_EXT_T);
			if (series != null) {
				plot.addSeries(series);
			}

			// Veranda
			series = service.getSensors().getData(Sensors.ID_VERANDA_T);
			if (series != null) {
				plot.addSeries(series);
			}
			
			plot.redraw();
			plot.redraw();
		}
	}
	
	private void updateDashboardThermo() {

		final Animation anim = AnimationUtils.loadAnimation(this, R.anim.zoomin);
		final LinearLayout layout = (LinearLayout) findViewById(R.id.temps_layout);
		layout.startAnimation(anim);

		// Pool
		SensorData series = null;
		if (service != null) {
			series = service.getSensors().getData(Sensors.ID_POOL_T);
		}
        TextView textView = (TextView) findViewById(R.id.pool_temp);
        if ((series != null) && (series.size() > 0)) {
        	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("#0.0#");
            float lastValue = series.getLast().value;
            textView.setText(decimalFormat.format(lastValue)+getString(R.string.degC));
        } else {
        	textView.setText("?");
        }

        // Ext
        series = null;
		if (service != null) {
			series = service.getSensors().getData(Sensors.ID_EXT_T);
		}
		textView = (TextView) findViewById(R.id.ext_temp);
        if ((series != null) && (series.size() > 0)) {
        	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("#0.0#");
            float lastValue = series.getLast().value;
            textView.setText(decimalFormat.format(lastValue)+getString(R.string.degC));
        } else {
        	textView.setText("?");
        }
		if (service != null) {
			series = service.getSensors().getData(Sensors.ID_EXT_H);
		}
		textView = (TextView) findViewById(R.id.ext_humi);
        if ((series != null) && (series.size() > 0)) {
        	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("##");
            float lastValue = series.getLast().value;
            textView.setText(decimalFormat.format(lastValue)+"%");
        } else {
        	textView.setText("?");
        }
        
        // Veranda
        series = null;
		if (service != null) {
			series = service.getSensors().getData(Sensors.ID_VERANDA_T);
		}
		textView = (TextView) findViewById(R.id.veranda_temp);
        if ((series != null) && (series.size() > 0)) {
        	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("#0.0#");
            float lastValue = series.getLast().value;
            textView.setText(decimalFormat.format(lastValue)+getString(R.string.degC));
        } else {
        	textView.setText("?");
        }
		if (service != null) {
			series = service.getSensors().getData(Sensors.ID_VERANDA_H);
		}
		textView = (TextView) findViewById(R.id.veranda_humi);
        if ((series != null) && (series.size() > 0)) {
        	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("##");
            float lastValue = series.getLast().value;
            textView.setText(decimalFormat.format(lastValue)+"%");
        } else {
        	textView.setText("?");
        }

        updateThermoLastUpdate();
	}

	private void updateLogView() {
		TextView messages = (TextView) findViewById(R.id.logtext);

		messages.setText("");
		if (service != null) {
			messages.setText(service.getLogMessages(), BufferType.SPANNABLE);
		}
		
		// Scroll to the bottom
		final ScrollView sv = (ScrollView) findViewById(R.id.logscroller);
		sv.post(new Runnable() {
		    public void run() {
		        sv.fullScroll(ScrollView.FOCUS_DOWN);
		    }
		});
	}
	
	private void updateTrainView() {
		if (service != null) {
			TrainsView trainsView = (TrainsView) findViewById(R.id.trains);
			trainsView.updateView(this, service.getTrains().getData());
			
			ImageView unknown = (ImageView) findViewById(R.id.train_unknown);
			if (service.getTrains().getData().isEmpty()) {
				unknown.setVisibility(View.VISIBLE);
			} else {
				unknown.setVisibility(View.GONE);
			}
		}
		updateTrainLastUpdate();
	}

	private void updateMeteoView() {
		if (service != null) {
			service.getMeteo().updateView(this);
		}

		updateMeteoLastUpdate();
	}
	
	private void updateSwitchesView() {
		if (service != null) {
			ImageButton button = (ImageButton) findViewById(R.id.switch1_cmd);
			if (service.getSwitches().getState(0)) {
				button.setBackgroundResource(R.drawable.switched_on);
			} else {
				button.setBackgroundResource(R.drawable.switched_off);
			}
		}
	}
	
	private void updateDoorsView() {
		if (service != null) {
			ImageButton portail = (ImageButton) findViewById(R.id.garage_status);
			Doors.State state = service.getDoors().getState(Doors.GARAGE);
			if (state == Doors.State.UNKNOWN) {
				portail.setImageResource(R.drawable.meteo_unknown);
			} else if (state == Doors.State.CLOSED) {
				portail.setImageResource(R.drawable.garage_closed);
			} else if (state == Doors.State.OPENED) {
				portail.setImageResource(R.drawable.garage_opened);
			} else if (state == Doors.State.MOVING) {
				portail.setImageResource(R.drawable.garage_moving);
			} else if (state == Doors.State.ERROR) {
				portail.setImageResource(R.drawable.garage_error);
			}
		}
	}
	
	private String deltaToString(long delta) {
		int hours = (int) delta / 3600000;
		int minutes = ((int)delta - (hours * 3600000)) / 60000;
		String delai = "" + hours + ":" + String.format("%02d", minutes);
		return delai;
	}
	
	public void updateMeteoLastUpdate() {
		TextView lastUpdate = (TextView) findViewById(R.id.meteo_last_update);
		if ((service != null) && (service.getMeteo().getLastUpdate() != null)) {
			long delta = new Date().getTime() - service.getMeteo().getLastUpdate().getTime();
			lastUpdate.setText(String.format(getString(R.string.ilya), deltaToString(delta)));
		} else {
			lastUpdate.setText("");
		}
		lastUpdate.invalidate();
	}
	
	public void updateTrainLastUpdate() {
		TextView lastUpdate = (TextView) findViewById(R.id.train_last_update);
		if ((service != null) && (service.getTrains().getLastUpdate() != null)) {
			long delta = new Date().getTime() - service.getTrains().getLastUpdate().getTime();
			lastUpdate.setText(String.format(getString(R.string.ilya), deltaToString(delta)));
		} else {
			lastUpdate.setText("");
		}
		lastUpdate.invalidate();
	}

	public void updateThermoLastUpdate() {
		TextView lastUpdate = (TextView) findViewById(R.id.thermo_last_update);
		if ((service != null) && (service.getSensors().getLastUpdate() != null)) {
			long delta = new Date().getTime() - service.getSensors().getLastUpdate().getTime();
			lastUpdate.setText(String.format(getString(R.string.ilya), deltaToString(delta)));
		} else {
			lastUpdate.setText("");
		}
		lastUpdate.invalidate();
	}

	// Callback for Service
	private IUpdateListener updateListener = new IUpdateListener() {
		 public void updateLog() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 updateLogView();
				 }
			 });
		 }
		 public void updateTrains() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 updateTrainView();
				 }
			 });
		 }		 
		 
		 public void updateMeteo() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 updateMeteoView();
				 }
			 });
		 }

		 public void updateThermo() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 updatePoolView();
					 updateThermoView();
					 updateDashboardThermo();
				 }
			 });
		 }
		 
		 public void updateSwitches() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 updateSwitchesView();
				 }
			 });
		 }
		 
		 public void updateDoors() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 updateDoorsView();
				 }
			 });
		 }
		 
		 public void resetLeds() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 ImageView led = (ImageView) findViewById(R.id.redLed);
					 led.setVisibility(View.GONE);
					 led = (ImageView) findViewById(R.id.blueLed);
					 led.setVisibility(View.GONE);
				 }
			 });			 
		 }
		 
		 public void errorLeds() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 ImageView led = (ImageView) findViewById(R.id.redLed);
					 led.setVisibility(View.VISIBLE);
				 }
			 });			 
		 }
		 
		 public void blinkLeds() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 ImageView led = (ImageView) findViewById(R.id.blueLed);
					 led.setVisibility(View.VISIBLE);
					 Animation anim = AnimationUtils.loadAnimation(RDActivity.this, R.anim.blink);
					 led.startAnimation(anim);
				 }
			 });			 
		 }

		 public void flashLeds() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 ImageView led = (ImageView) findViewById(R.id.blueLed);
					 led.setVisibility(View.VISIBLE);
					 Animation anim = AnimationUtils.loadAnimation(RDActivity.this, R.anim.flash);
					 led.startAnimation(anim);
				 }
			 });			 
		 }
		 
		 public void startRefreshAnim() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 ImageButton refresh = (ImageButton) findViewById(R.id.refreshButton);
					 refresh.setEnabled(false);
					 Animation anim = AnimationUtils.loadAnimation(RDActivity.this, R.anim.rotate);
					 refresh.setAnimation(anim);
				 }
			 });
		 }
		 
		 public void stopRefreshAnim() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 ImageButton refresh = (ImageButton) findViewById(R.id.refreshButton);
			         refresh.setEnabled(true);
			         refresh.clearAnimation();
				 }
			 });
		 }

		 public void postToast(final String text) {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 Toast.makeText(RDActivity.this, text, Toast.LENGTH_LONG).show();
				 }
			 });
		 }
	};
}