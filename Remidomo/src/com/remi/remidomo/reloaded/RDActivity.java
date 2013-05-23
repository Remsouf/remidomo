package com.remi.remidomo.reloaded;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import com.remi.remidomo.reloaded.prefs.PreferencesActivity;
import com.remi.remidomo.reloaded.prefs.PrefsEnergy;
import com.remi.remidomo.reloaded.prefs.PrefsService;

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
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Gravity;
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
    
    // Not an enum, because it also represents
    // index into the flipper view
    public static final int DASHBOARD_VIEW_ID = 0;
    public static final int TEMP_VIEW_ID = 1;
    public static final int POOL_VIEW_ID = 2;
    public static final int SWITCHES_VIEW_ID = 3;
    public static final int ENERGY_VIEW_ID = 4;
    public static final int LOG_VIEW_ID = 5;
    private int currentView = DASHBOARD_VIEW_ID;
 
	private ViewFlipper flipper;

	protected GestureDetector gestureScanner;
	private SharedPreferences prefs;

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
        if (Build.VERSION.SDK_INT > 8) {
        	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        setContentView(R.layout.main);

        gestureScanner = new GestureDetector(this);

        new Thread(new Runnable() {
        	public void run() {
        		prefs = PreferenceManager.getDefaultSharedPreferences(RDActivity.this);
        	}
        }).start();

        flipper = (ViewFlipper) findViewById(R.id.viewFlipper);
        flipper.setInAnimation(this, R.anim.slide_in_left);
		flipper.setOutAnimation(this, R.anim.slide_out_right);

		setupClickListeners();
		
        // Show splash screen only if we're really starting service
        boolean serviceRunning = false;
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ("com.remi.remidomo.reloaded.RDService".equals(service.service.getClassName())) {
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
        	changeView(null, intent.getIntExtra("view", DASHBOARD_VIEW_ID));
        }
    }

    private void setupClickListeners() {
        ImageButton settings = (ImageButton) findViewById(R.id.settingsButton);
        settings.setOnClickListener(new ImageButton.OnClickListener() {
        	public void onClick(View v) {
        		startActivity(new Intent(RDActivity.this, PreferencesActivity.class));
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

        ImageButton energy = (ImageButton) findViewById(R.id.energyButton);
        energy.setOnClickListener(new ImageButton.OnClickListener() {
        	public void onClick(View v) {
        		changeView(v, ENERGY_VIEW_ID);
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
        		ImageButton button = (ImageButton) findViewById(R.id.poolButton);
        		changeView(button, POOL_VIEW_ID);
        	}
        });
        
        RelativeLayout extLayout = (RelativeLayout) findViewById(R.id.ext_layout);
        extLayout.setOnClickListener(new RelativeLayout.OnClickListener() {
        	public void onClick(View v) {
        		ImageButton button = (ImageButton) findViewById(R.id.tempButton);
        		changeView(button, TEMP_VIEW_ID);
        	}
        });

        RelativeLayout verandaLayout = (RelativeLayout) findViewById(R.id.veranda_layout);
        verandaLayout.setOnClickListener(new RelativeLayout.OnClickListener() {
        	public void onClick(View v) {
        		ImageButton button = (ImageButton) findViewById(R.id.tempButton);
        		changeView(button, TEMP_VIEW_ID);
        	}
        });
        
        ImageButton refresh = (ImageButton) findViewById(R.id.refreshButton);
        refresh.setOnClickListener(new ImageButton.OnClickListener() {
        	public void onClick(View v) {
        		RefreshTask refreshTask = new RefreshTask();
        		refreshTask.execute();
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
        final ImageButton clearHistoryButton = (ImageButton) findViewById(R.id.history_clear);
        clearHistoryButton.setOnClickListener(new RelativeLayout.OnClickListener() {
        	public void onClick(View v) {
        		if (service != null) {
        			Animation anim = AnimationUtils.loadAnimation(RDActivity.this, R.anim.rotozoomout);
                	LinearLayout history = (LinearLayout) findViewById(R.id.garage_history);
                	history.startAnimation(anim);

                	// Clear history at the end of animation
                	anim.setAnimationListener(new AnimationListener() {
                		@Override
                		public void onAnimationStart(Animation animation) {}

                		@Override
                		public void onAnimationRepeat(Animation animation) {}

                		@Override
                		public void onAnimationEnd(Animation animation) {
                			if (service != null) {
                				service.getDoors().clearHistory(0);
                			}
                		}
                	});
        		}
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

        // Hooks for energy buttons
        ImageButton resetEnergy = (ImageButton) findViewById(R.id.energyreset_button);
        resetEnergy.setOnClickListener(new ImageButton.OnClickListener() {
        	public void onClick(View v) {
        		if (service != null) {
        			service.getEnergy().resetEnergyCounter();
        			updateEnergyView();
        		}
        	}
        });
    }

    private void changeView(View button, int destination) {
    	// Change flipper view + animate buttons
    	currentView = destination;
    	flipper.setDisplayedChild(destination);

    	updateMainIcons(button, destination);

    	/* Only the visible view will be updated */
    	updatePoolView();
    	updateLogView();
    	updateThermoView();
    	updateSwitchesView();
    	updateDoorsView();
    	updateTrainView();
    	updateDashboardThermo();
    	updateEnergyView();
    }

    private void updateMainIcons(View currentButton, int selection) {

    	ImageButton poolTemp = (ImageButton) findViewById(R.id.poolButton);
    	ImageButton thermo = (ImageButton) findViewById(R.id.tempButton);
    	ImageButton log = (ImageButton) findViewById(R.id.logButton);
    	ImageButton dashboard = (ImageButton) findViewById(R.id.dashboardButton);
    	ImageButton energy = (ImageButton) findViewById(R.id.energyButton);
    	ImageButton switches = (ImageButton) findViewById(R.id.switchButton);

    	// If clicked button is unknown, guess
    	if (currentButton == null) {
    		switch (selection) {
    		case 0:
    			currentButton = dashboard;
    			break;
    		case 1:
    			currentButton = thermo;
    			break;
    		case 2:
    			currentButton = poolTemp;
    			break;
    		case 3:
    			currentButton = switches;
    			break;
    		case 4:
    			currentButton = energy;
    			break;
    		case 5:
    			currentButton = log;
    			break;
    		default:
    			currentButton = null;
    		}
    	}

    	poolTemp.clearAnimation();
    	thermo.clearAnimation();
    	log.clearAnimation();
    	dashboard.clearAnimation();
    	energy.clearAnimation();
    	switches.clearAnimation();

    	if (currentButton != null) {
    		Animation anim = AnimationUtils.loadAnimation(RDActivity.this, R.anim.icon_select);
    		currentButton.startAnimation(anim);
    	}
    }

    @Override
    protected void onResume() {
    	super.onResume();

    	ImageButton refresh = (ImageButton) findViewById(R.id.refreshButton);
        ImageButton serverLogButton = (ImageButton) findViewById(R.id.rlog_button);
        ImageButton clientLogButton = (ImageButton) findViewById(R.id.log_button);
        ImageButton switchesHistoryButton = (ImageButton) findViewById(R.id.history_clear);
        ImageButton resetEnergy = (ImageButton) findViewById(R.id.energyreset_button);

    	String mode = prefs.getString("mode", PrefsService.DEFAULT_MODE);
        if ("Serveur".equals(mode)) {
        	refresh.setVisibility(View.GONE);
        	serverLogButton.setVisibility(View.GONE);
        	clientLogButton.setVisibility(View.GONE);
        	switchesHistoryButton.setVisibility(View.VISIBLE);
        	resetEnergy.setVisibility(View.VISIBLE);
        } else {
        	refresh.setVisibility(View.VISIBLE);
        	switchesHistoryButton.setVisibility(View.GONE);
        	resetEnergy.setVisibility(View.GONE);
        }

		updateListener.startRefreshAnim();
    	updatePoolView();
    	updateLogView();
    	updateThermoView();
    	updateSwitchesView();
    	updateDoorsView();
    	updateTrainView();
    	updateDashboardThermo();
    	updateEnergyView();
		updateListener.stopRefreshAnim();

    	updateTrainLastUpdate();
		updateMeteoLastUpdate();
		updateThermoLastUpdate();
		updateEnergyLastUpdate();
    }

    @Override
    protected void onDestroy() {

    	if (!prefs.getBoolean("keepservice", PrefsService.DEFAULT_KEEPSERVICE)) {
    		if (service != null) {
    			service.stopAtActivityRequest();
    		}
    	}
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
            	updateMainIcons(null, flipper.getDisplayedChild());
            } else if (e1.getX() < e2.getX() && e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
            	flipper.showPrevious();
            	updateMainIcons(null, flipper.getDisplayedChild());
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
		toast.setGravity(Gravity.CENTER, 0, 0);
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

		int port = prefs.getInt("port", PrefsService.DEFAULT_PORT);
		String ipAddr = prefs.getString("ip_address", PrefsService.DEFAULT_IP);

		remoteContent.clearView();
		remoteContent.loadUrl(ipAddr + ":" + port + "/log");
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
			if (service != null) {
				service.saveToSdcard();
			}
			return true;
		case R.id.settings:
			startActivity(new Intent(RDActivity.this, PreferencesActivity.class));
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
					updateEnergyLastUpdate();
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
	        	updateEnergyView();
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
		if ((currentView == POOL_VIEW_ID) && (service != null)) {
			series = service.getSensors().getData(Sensors.ID_POOL_T);

			SensorPlot plot = (SensorPlot) findViewById(R.id.poolTempPlot);
			plot.clear();
			plot.removeMarkers();
			plot.addSeries(series);
			plot.redraw();
		}
	}
	
	private void updateThermoView() {
		SensorData series = null;

		/* Update view only if visible */
		if ((currentView == TEMP_VIEW_ID) && (service != null)) {
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
			// Main icon
			ImageButton portail = (ImageButton) findViewById(R.id.garage_status);
			Doors.State state = service.getDoors().getState(Doors.GARAGE);
			portail.setImageResource(Doors.getResourceForState(state));

			// History
			LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			LinearLayout layout = (LinearLayout) findViewById(R.id.garage_history);
			layout.removeAllViews();

			int i = 0;
			for (Doors.Event event: service.getDoors().getHistory(Doors.GARAGE)) {
				LinearLayout inflated = (LinearLayout) inflater.inflate(R.layout.door_event, layout, true);
				RelativeLayout eventLayout = (RelativeLayout) inflated.getChildAt(i);

				ImageView icon = (ImageView) eventLayout.getChildAt(0);
				icon.setImageResource(Doors.getResourceForState(event.state));

				TextView text = (TextView) eventLayout.getChildAt(1);
				text.setText(event.tstamp.toLocaleString());

				i++;
			}
		}
	}

	private void updateEnergyView() {
		SensorData series = null;

		if (currentView != ENERGY_VIEW_ID) {
			return;
		}

		// Power
		if (service != null) {
			series = service.getEnergy().getPowerData();
		}

		SensorPlot plot = (SensorPlot) findViewById(R.id.energyPlot);
		plot.clear();
		plot.removeMarkers();
		plot.addSeries(series);
		plot.redraw();

		TextView power = (TextView) findViewById(R.id.power);
		TextView units = (TextView) findViewById(R.id.power_units);
		if ((series != null) && (series.size() > 0)) {
        	DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
            decimalFormat.applyPattern("#0.000");
            float lastValue = series.getLast().value;
            power.setText(decimalFormat.format(lastValue));
        } else {
        	power.setText("?");
        }

		Date hcHour = new Date();
		hcHour.setHours(prefs.getInt("hc_hour.hour", PrefsEnergy.DEFAULT_HCHOUR));
		hcHour.setMinutes(prefs.getInt("hc_hour.minute", 0));

		Date hpHour = new Date();
		hpHour.setHours(prefs.getInt("hp_hour.hour", PrefsEnergy.DEFAULT_HPHOUR));
		hpHour.setMinutes(prefs.getInt("hp_hour.minute", 0));

		Date now = new Date();
		if ((now.getTime() >= hpHour.getTime()) &&
			(now.getTime() < hcHour.getTime())) {
			power.setTextColor(Color.parseColor("#FF5555"));
			units.setTextColor(Color.parseColor("#FF5555"));
		} else {
			power.setTextColor(Color.parseColor("#5555FF"));
			units.setTextColor(Color.parseColor("#5555FF"));
		}

		// Energy
		TextView energy = (TextView) findViewById(R.id.energy);
        DecimalFormat decimalFormat = (DecimalFormat)DecimalFormat.getInstance();
        decimalFormat.applyPattern("#0.0");
        float value = 0.0f;
        if ((service != null) && (service.getEnergy().getEnergyValue() >= 0)) {
        	value = service.getEnergy().getEnergyValue();
        	energy.setText(decimalFormat.format(value));
        } else {
        	energy.setText("?");
        }

        // Status
        ImageView statusView = (ImageView) findViewById(R.id.power_status);
        if (service != null) {
        	boolean status = service.getEnergy().isPoweredOn();
        	if (status) {
        		statusView.setImageResource(R.drawable.power_on);
        	} else {
        		statusView.setImageResource(R.drawable.power_off);
        	}
        } else {
        	statusView.setImageResource(R.drawable.meteo_unknown);
        }

        updateEnergyLastUpdate();
	}

	public static String deltaToTimeString(long delta) {
		int hours = (int) delta / 3600000;
		int minutes = ((int)delta - (hours * 3600000)) / 60000;
		String delai = "" + hours + ":" + String.format("%02d", minutes);
		return delai;
	}

	public String deltaToDateString(long delta) {
		int days = (int) (delta / 86400000);
		return String.format(getString(R.string.days), days);
	}

	public void updateMeteoLastUpdate() {
		TextView lastUpdate = (TextView) findViewById(R.id.meteo_last_update);
		if ((service != null) && (service.getMeteo().getLastUpdate() != null)) {
			long delta = new Date().getTime() - service.getMeteo().getLastUpdate().getTime();
			lastUpdate.setText(String.format(getString(R.string.ilya), deltaToTimeString(delta)));
		} else {
			lastUpdate.setText("");
		}
		lastUpdate.invalidate();
	}

	public void updateTrainLastUpdate() {
		TextView lastUpdate = (TextView) findViewById(R.id.train_last_update);
		if ((service != null) && (service.getTrains().getLastUpdate() != null)) {
			long delta = new Date().getTime() - service.getTrains().getLastUpdate().getTime();
			lastUpdate.setText(String.format(getString(R.string.ilya), deltaToTimeString(delta)));
		} else {
			lastUpdate.setText("");
		}
		lastUpdate.invalidate();
	}

	public void updateThermoLastUpdate() {
		TextView lastUpdate = (TextView) findViewById(R.id.thermo_last_update);
		if ((service != null) && (service.getSensors().getLastUpdate() != null)) {
			long delta = new Date().getTime() - service.getSensors().getLastUpdate().getTime();
			lastUpdate.setText(String.format(getString(R.string.ilya), deltaToTimeString(delta)));
		} else {
			lastUpdate.setText("");
		}
		lastUpdate.invalidate();
	}

	public void updateEnergyLastUpdate() {
		TextView lastUpdate = (TextView) findViewById(R.id.power_last_update);
		if ((service != null) && (service.getEnergy().getLastUpdate() != null)) {
			long delta = new Date().getTime() - service.getEnergy().getLastUpdate().getTime();
			lastUpdate.setText(String.format(getString(R.string.ilya), deltaToTimeString(delta)));
		} else {
			lastUpdate.setText("");
		}
		lastUpdate.invalidate();

		TextView lastEnergyUpdate = (TextView) findViewById(R.id.energy_last_update);
		if ((service != null) && (service.getEnergy().getLastEnergyResetDate() != null)) {
			long delta = new Date().getTime() - service.getEnergy().getLastEnergyResetDate().getTime();
			lastEnergyUpdate.setText(String.format(getString(R.string.depuis), deltaToDateString(delta)));
		} else {
			lastEnergyUpdate.setText("");
		}
		lastEnergyUpdate.invalidate();
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

		 public void updateEnergy() {
			 runOnUiThread(new Runnable() {
				 public void run() {
					 updateEnergyView();
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