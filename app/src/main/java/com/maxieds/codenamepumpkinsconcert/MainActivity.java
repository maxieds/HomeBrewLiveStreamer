package com.maxieds.codenamepumpkinsconcert;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TextView;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.hardware.Camera;

import com.singhajit.sherlock.core.Sherlock;

import static android.os.Process.killProcess;
import static android.os.Process.myPid;
import static android.os.Process.myUid;
import static com.maxieds.codenamepumpkinsconcert.TabFragment.TAB_ABOUT;
import static com.maxieds.codenamepumpkinsconcert.TabFragment.TAB_COVERT_MODE;
import static com.maxieds.codenamepumpkinsconcert.TabFragment.TAB_LIVE_PANEL;
import static com.maxieds.codenamepumpkinsconcert.TabFragment.TAB_SETTINGS;
import static com.maxieds.codenamepumpkinsconcert.TabFragment.TAB_TOOLS;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static MainActivity runningActivity;
    public static LayoutInflater defaultInflater;

    private static ViewPager viewPager;
    private static TabLayout tabLayout;
    private static int selectedTab = TAB_LIVE_PANEL;
    private static ViewPager.OnPageChangeListener tabChangeListener = null;
    public static TextView tvLoggingMessages;
    public static String DEFAULT_RECORDING_QUALITY = "SDMEDIUM";
    private static ServiceConnection recordServiceConn = new ServiceConnection() {
        public AVRecordingService recService;
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Log.d("ServiceConnection","connected");
            recService = (AVRecordingService) binder;
        }
        public void onServiceDisconnected(ComponentName className) {
            Log.d("ServiceConnection","disconnected");
            recService = null;
        }
    };
    public static boolean setDoNotDisturb = true;
    public static boolean AVRECORD_SERVICE_RUNNING = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        Sherlock.init(this); // for non-production sanity with debugging A/V under Android ...
        if(!isTaskRoot()) {}
        setContentView(R.layout.activity_main);
        runningActivity = this;
        defaultInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setLogo(R.drawable.streaminglogo32);
        toolbar.setContentInsetStartWithNavigation(0);
        toolbar.setContentInsetsRelative(0, 0);
        toolbar.setTitleMarginStart(0);
        toolbar.setPaddingRelative(0, 0, 0, 0);
        setSupportActionBar(toolbar);

        viewPager = (ViewPager) findViewById(R.id.tab_pager);
        TabFragmentPagerAdapter tfPagerAdapter = new TabFragmentPagerAdapter(getSupportFragmentManager(), MainActivity.this);
        viewPager.setAdapter(tfPagerAdapter);
        if (tabChangeListener != null) {
            viewPager.removeOnPageChangeListener(tabChangeListener);
        }
        tabChangeListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                if (position == TAB_COVERT_MODE) {
                    getSupportActionBar().setTitle("");
                    MainActivity.tabLayout.setVisibility(View.INVISIBLE);
                    MainActivity.runningActivity.getSupportActionBar().setDisplayUseLogoEnabled(false);
                }
                else if(MainActivity.selectedTab == TAB_COVERT_MODE) {
                    actionButtonCovertModeToLive(null); // restore the navigation if the user flicks screens
                }
                MainActivity.selectedTab = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        };
        viewPager.addOnPageChangeListener(tabChangeListener);

        tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.removeAllTabs();
        tabLayout.addTab(tabLayout.newTab().setText(tfPagerAdapter.getPageTitle(TAB_COVERT_MODE)));
        tabLayout.addTab(tabLayout.newTab().setText(tfPagerAdapter.getPageTitle(TAB_LIVE_PANEL)));
        tabLayout.addTab(tabLayout.newTab().setText(tfPagerAdapter.getPageTitle(TAB_TOOLS)));
        tabLayout.addTab(tabLayout.newTab().setText(tfPagerAdapter.getPageTitle(TAB_SETTINGS)));
        tabLayout.addTab(tabLayout.newTab().setText(tfPagerAdapter.getPageTitle(TAB_ABOUT)));
        tabLayout.setupWithViewPager(viewPager);

        viewPager.setOffscreenPageLimit(TabFragmentPagerAdapter.TAB_COUNT);
        viewPager.setCurrentItem(selectedTab);
        tfPagerAdapter.notifyDataSetChanged();

        // the view pager hides the tab icons by default, so we reset them:
        tabLayout.getTabAt(TAB_COVERT_MODE).setIcon(R.drawable.coverttab24);
        tabLayout.getTabAt(TAB_LIVE_PANEL).setIcon(R.drawable.livetab24v2);
        tabLayout.getTabAt(TAB_TOOLS).setIcon(R.drawable.toolstab24);
        tabLayout.getTabAt(TAB_SETTINGS).setIcon(R.drawable.settingstab24);
        tabLayout.getTabAt(TAB_ABOUT).setIcon(R.drawable.infotab24);

        // setup logging of service and runtime-related logging data:
        if(tvLoggingMessages == null)
            tvLoggingMessages = new TextView(this);
        writeLoggingData("STATUS", "Application succesfully started!");

        // setup the many permissions needed by an app of this nature:
        String[] permissions = {
                "android.permission.CAMERA",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.RECORD_AUDIO",
                "android.permission.RECORD_VIDEO",
                "android.permission.INTERNET",
                "android.permission.ACCESS_NOTIFICATION_POLICY",
                "android.permission.BLUETOOTH",
                "android.permission.WAKE_LOCK",
                "android.permission.VIBRATE",
                "android.permission.ACCESS_COARSE_LOCATION"
        };
        if (android.os.Build.VERSION.SDK_INT >= 23)
            requestPermissions(permissions, 200);
        else
            ActivityCompat.requestPermissions(this, permissions, 200);
        for(int p = 0; p < permissions.length; p++) {
            if(!hasPermission(permissions[p])) {
                Log.w(TAG, "Lacking permission " + permissions[p] + " from the user.");
            }
        }

        // and special case for the particularly important one of silencing the phone when recording:
        NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (!notifyManager.isNotificationPolicyAccessGranted()) {
            Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            startActivity(intent);
        }

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                Log.e(TAG,"Cleaning up after uncaught exception.");
                stopAVRecordingService();
                paramThread.getThreadGroup().destroy();
                Log.e(TAG, Log.getStackTraceString(paramThrowable));
                Log.e(TAG, "UNCAUGHT EXCPT(" + paramThrowable.getClass().getSimpleName() + ") : " + paramThrowable.getMessage());
                killProcess(myPid());
                System.exit(-1);
            }
        });
        RuntimeStats.updateStatsUI(false, true);

    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(intent == null)
            return;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // do not disable anything when the phone is paused to keep the recording service running throughout:
        if(AVRecordingService.localService != null) {
            AVRecordingService.localService.videoPreviewOff();
        }
        if(AVRECORD_SERVICE_RUNNING) {
            RuntimeStats.statsUpdateHandler.removeCallbacks(RuntimeStats.statsUpdateRunnableForeground);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // do not disable anything else but the screen when the phone is paused to keep the recording service running throughout:
        if(AVRecordingService.localService != null) { // reconnect the surface to the camera:
            AVRecordingService.localService.videoPreviewOn();
        }
        if(AVRECORD_SERVICE_RUNNING) {
            RuntimeStats.updateStatsUI(true, true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(AVRecordingService.isRecording() || AVRECORD_SERVICE_RUNNING) {
            stopAVRecordingService();
            NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notifyManager.setInterruptionFilter(AVRecordingService.dndInterruptionPolicy);
        }
        releaseWakeLock();
        writeLoggingData("INFO", "Exiting application.");
    }

    private boolean hasPermission(String permission) {
        return getApplicationContext().checkPermission(permission, myPid(), myUid()) == PackageManager.PERMISSION_GRANTED;
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        AVRecordingService.bgWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Homebrew Live Streamer");
        AVRecordingService.bgWakeLock.acquire();
    }

    private void releaseWakeLock() {
        if(AVRecordingService.bgWakeLock == null) {
            Log.w(TAG, "BGWakeLock is NULL ... Cannot release it.");
            return;
        }
        AVRecordingService.bgWakeLock.release(1);
        AVRecordingService.bgWakeLock = null;
    }

    private void turnOffDisplayScreen() {
        WindowManager.LayoutParams lparams = new WindowManager.LayoutParams();
        lparams.flags |= WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;
        lparams.screenBrightness = 1;
        getWindow().setAttributes(lparams);
        //setShowWhenLocked(true);
        //setTurnScreenOn(false);
    }

    private void restoreDisplayScreen() {
        WindowManager.LayoutParams lparams = getWindow().getAttributes();
        lparams.screenBrightness = -1;
        getWindow().setAttributes(lparams);
        setShowWhenLocked(false);
        setTurnScreenOn(true);
    }

    public void actionButtonCovertModeToLive(View button) {
        getSupportActionBar().setTitle(getString(R.string.app_name));
        viewPager.setCurrentItem(TAB_LIVE_PANEL);
        tabLayout.setVisibility(View.VISIBLE);
        getSupportActionBar().setDisplayUseLogoEnabled(true);
        getSupportActionBar().setLogo(R.drawable.streaminglogo32);
    }

    public static final String RECORD_VIDEO = "RECORD_VIDEO";
    public static final String RECORD_AUDIO = "RECORD_AUDIO_ONLY";
    public static final String PAUSE_RECORDING = "PAUSE_RECORDING";
    public static final String STOP_RECORDING = "STOP_RECORDING";
    public static final String PLAYBACK_RECORDING = "PLAYBACK_RECORDING";
    public static final String STREAM_RECORDING = "STREAM_RECORDING";

    public void actionButtonHandleNavigation(View button) {

        String navAction = ((Button) button).getTag().toString();
        boolean restartStatsTimer = false;
        if(navAction.equals(RECORD_VIDEO) || navAction.equals(RECORD_AUDIO)) {
            if(!AVRecordingService.isRecording()) {
                AVRecordingService.LOCAL_AVSETTING = navAction.equals(RECORD_VIDEO) ? AVRecordingService.AVSETTING_AUDIO_VIDEO : AVRecordingService.AVSETTING_AUDIO_ONLY;
                Intent startRecordingService = new Intent(this, AVRecordingService.class);
                startRecordingService.setAction(navAction);
                //startForegroundService(startRecordingService);
                acquireWakeLock();
                startService(startRecordingService);
                bindService(startRecordingService, recordServiceConn, Context.BIND_AUTO_CREATE);
                restartStatsTimer = true;
            }
            else if(AVRecordingService.isPaused()) {
                if(AVRecordingService.isRecordingAudioOnly() && navAction.equals(RECORD_VIDEO)) {
                    AVRecordingService.localService.resumeRecording();
                    AVRecordingService.localService.recordVideoNow(AVRecordingService.videoOptsQuality.getSelectedItem().toString());
                }
                else if(!AVRecordingService.isRecordingAudioOnly() && navAction.equals(RECORD_AUDIO)) {
                    AVRecordingService.localService.resumeRecording();
                    AVRecordingService.localService.recordAudioOnlyNow(AVRecordingService.videoOptsQuality.getSelectedItem().toString());
                }
                else {
                    AVRecordingService.localService.resumeRecording();
                }
            }
            else if(AVRecordingService.isRecordingAudioOnly() && navAction.equals(RECORD_VIDEO) ||
                    !AVRecordingService.isRecordingAudioOnly() && navAction.equals(RECORD_AUDIO)) {
                AVRecordingService.localService.toggleAudioVideo(DEFAULT_RECORDING_QUALITY);
            }
            RuntimeStats.init(AVRecordingService.LAST_RECORDING_FILEPATH);
            RuntimeStats.updateStatsUI(restartStatsTimer, true);
            //turnOffDisplayScreen();
        }
        else if(navAction.equals(PAUSE_RECORDING) && AVRecordingService.isRecording()) {
            AVRecordingService.localService.pauseRecording();
        }
        else if(navAction.equals(STOP_RECORDING) && (AVRecordingService.isRecording() || AVRECORD_SERVICE_RUNNING)) {
            stopAVRecordingService();
        }
        else if(navAction.equals(PLAYBACK_RECORDING)) {
            writeLoggingData("INFO", "Navigation option \"PLAY LAST\" is currently unsupported.");
        }
        else if(navAction.equals(STREAM_RECORDING)) {
            writeLoggingData("INFO", "Navigation option \"STREAM\" is currently unsupported.");
        }
        if(AVRecordingService.getErrorState()) {
            stopAVRecordingService();
            Log.e(TAG, "AVRecording service reached error state ... turned it back off completely");
        }
    }

    public void stopAVRecordingService() {
        if(!AVRecordingService.isRecording() && !AVRECORD_SERVICE_RUNNING) {
            Log.w(TAG, "AVRecordingService is NOT running ... Unable to stop it.");
            return;
        }
        try {
            Intent stopRecordingService = new Intent(this, AVRecordingService.class);
            releaseWakeLock();
            stopService(stopRecordingService);
            unbindService(recordServiceConn);
            RuntimeStats.clear();
            //restoreDisplayScreen();
        } catch(Exception ise) {}
        writeLoggingData("INFO", "Paused / stopped current recording session.");
    }

    public static void writeLoggingData(String msgPrefix, String msg) {
        String fullMessage = String.format("%s\n", msg);
        switch(msgPrefix) {
            case "WARNING":
                Log.w(TAG, fullMessage);
                break;
            case "ERROR":
                Log.e(TAG, fullMessage);
                break;
            default:
                Log.i(TAG, fullMessage);
                break;
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
}
