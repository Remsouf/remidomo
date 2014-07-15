package com.remi.remidomo.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class LogActivity extends Activity {

    private final static String TAG = RDActivity.class.getSimpleName();

    public RDService service;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Remove title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.log);

        setupClickListeners();

        Intent intent = new Intent(this, RDService.class);
        if (!bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to bind to service");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLogView();
    }

    @Override
    protected void onDestroy() {
        unbindService(serviceConnection);
        super.onDestroy();
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder serviceBind) {
            RDService.LocalBinder binder = (RDService.LocalBinder) serviceBind;
            service = (RDService) binder.getService();
            updateLogView();
        }

        public void onServiceDisconnected(ComponentName className) {
            service = null;
        }
    };

    private void setupClickListeners() {
        final ImageButton clearLogButton = (ImageButton) findViewById(R.id.trash_button);
        clearLogButton.setOnClickListener(new RelativeLayout.OnClickListener() {
            public void onClick(View v) {
                clearLog();
            }
        });
    }

    private void updateLogView() {
        TextView messages = (TextView) findViewById(R.id.logtext);

        messages.setText("");
        if (service != null) {
            messages.setText(service.getLogMessages(), TextView.BufferType.SPANNABLE);
        }

        // Scroll to the bottom
        final ScrollView sv = (ScrollView) findViewById(R.id.logscroller);
        sv.post(new Runnable() {
            public void run() {
                sv.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void clearLog() {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_delete)
                .setTitle(R.string.clear_log_title)
                .setMessage(R.string.clear_log_msg)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Animation anim = AnimationUtils.loadAnimation(LogActivity.this, R.anim.rotozoomout);
                        TextView log = (TextView) findViewById(R.id.logtext);
                        log.startAnimation(anim);

                        // Clear log at the end of animation
                        anim.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {}

                            @Override
                            public void onAnimationRepeat(Animation animation) {}

                            @Override
                            public void onAnimationEnd(Animation animation) {
                                if (service != null) {
                                    service.clearLog();
                                    updateLogView();
                                }
                            }
                        });

                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }
}
