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
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceView;
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
    public static CameraPreview videoCameraPreview;
    public static TextView tvLoggingMessages;
    public static String DEFAULT_RECORDING_QUALITY = "SDMEDIUM";
    private static final int MAX_LOGGING_LINES = 12;
    public static SurfaceView videoPreview;
    public static Drawable videoPreviewBGOverlay;
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
    public static Spinner videoOptsAntiband, videoOptsEffects, videoOptsCameraFlash;
    public static Spinner videoOptsFocus, videoOptsScene, videoOptsWhiteBalance, videoOptsRotation;
    public static Spinner videoOptsQuality, videoPlaybackOptsContentType, audioPlaybackOptsEffectType;
    public static boolean setDoNotDisturb = true;
    public static boolean AVRECORD_SERVICE_RUNNING = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        if(!isTaskRoot()) {

        }
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
                    ((TabHost) MainActivity.runningActivity.findViewById(R.id.tab_host)).setCurrentTab(TAB_COVERT_MODE);
                    return;
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

        viewPager.setOffscreenPageLimit(TabFragmentPagerAdapter.TAB_COUNT - 1);
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
                //"android.permission.BLUETOOTH_ADMIN",
                "android.permission.WAKE_LOCK",
                "android.permission.VIBRATE",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.BATTERY_STATS"
        };
        if (android.os.Build.VERSION.SDK_INT >= 23)
            requestPermissions(permissions, 200);
        ActivityCompat.requestPermissions(this, permissions, 200);
        for(int p = 0; p < permissions.length; p++) {
            Log.i(TAG, permissions[p] + ": " + (hasPermission(permissions[p]) ? "YES" : "NOT APPROVED"));
        }

        // and special case for the particularly important one of silencing the phone when recording:
        NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (!notifyManager.isNotificationPolicyAccessGranted()) {
            Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            startActivity(intent);
        }

        // start a timer to update the stats UI on the screen periodically:
        RuntimeStats.updateStatsUI(true);

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
        if(AVRecordingService.localService != null) {
            AVRecordingService.localService.previewOff();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(AVRecordingService.localService != null) {
            AVRecordingService.localService.previewOn();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(AVRecordingService.isRecording() || AVRECORD_SERVICE_RUNNING) {
            Intent stopRecordingService = new Intent(this, AVRecordingService.class);
            stopService(stopRecordingService);
            unbindService(recordServiceConn);
            NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notifyManager.setInterruptionFilter(AVRecordingService.dndInterruptionPolicy);
        }
        writeLoggingData("INFO", "Exiting application.");
    }

    private boolean hasPermission(String permission) {
        return getApplicationContext().checkPermission(permission, myPid(), myUid()) == PackageManager.PERMISSION_GRANTED;
    }

    public void actionButtonCovertModeToLive(View button) {
        getSupportActionBar().setTitle(getString(R.string.app_name));
        viewPager.setCurrentItem(TAB_LIVE_PANEL);
        tabLayout.setVisibility(View.VISIBLE);
        getSupportActionBar().setDisplayUseLogoEnabled(true);
        getSupportActionBar().setLogo(R.drawable.streaminglogo32);
    }

    public void actionButtonHandleNavigation(View button) {

        String navAction = ((Button) button).getTag().toString();
        if(navAction.equals("RECORD_VIDEO")) {
            if(!AVRecordingService.isRecording()) {
                AVRecordingService.LOCAL_AVSETTING = AVRecordingService.AVSETTING_AUDIO_VIDEO;
                Intent startRecordingService = new Intent(this, AVRecordingService.class);
                startRecordingService.setAction("RECORD_VIDEO");
                startRecordingService.putExtra("RECORDOPTS", navAction);
                //startForegroundService(startRecordingService);
                startService(startRecordingService);
                bindService(startRecordingService, recordServiceConn, Context.BIND_AUTO_CREATE);
            }
            else if(AVRecordingService.isPaused()) {
                if(AVRecordingService.isRecordingAudioOnly()) {
                    AVRecordingService.localService.resumeRecording();
                    AVRecordingService.localService.recordVideoNow(videoOptsQuality.getSelectedItem().toString());
                }
                else {
                    AVRecordingService.localService.resumeRecording();
                }
            }
            else if(AVRecordingService.isRecordingAudioOnly()) {
                AVRecordingService.localService.toggleAudioVideo(DEFAULT_RECORDING_QUALITY);
            }
            RuntimeStats.init(AVRecordingService.LAST_RECORDING_FILEPATH);
            RuntimeStats.updateStatsUI(true);
        }
        else if(navAction.equals("RECORD_AUDIO_ONLY")) {
            if(!AVRecordingService.isRecording()) {
                AVRecordingService.LOCAL_AVSETTING = AVRecordingService.AVSETTING_AUDIO_ONLY;
                Intent startRecordingService = new Intent(this, AVRecordingService.class);
                startRecordingService.setAction("RECORD_AUDIO_ONLY");
                startService(startRecordingService);
                //startForegroundService(startRecordingService);
                bindService(startRecordingService, recordServiceConn, Context.BIND_AUTO_CREATE);
                startRecordingService.putExtra("RECORDOPTS", navAction);
            }
            else if(AVRecordingService.isPaused()) {
                if(!AVRecordingService.isRecordingAudioOnly()) {
                    AVRecordingService.localService.resumeRecording();
                    AVRecordingService.localService.recordAudioOnlyNow(videoOptsQuality.getSelectedItem().toString());
                }
                else {
                    AVRecordingService.localService.resumeRecording();
                }
            }
            else if(!AVRecordingService.isRecordingAudioOnly()) {
                AVRecordingService.localService.toggleAudioVideo(DEFAULT_RECORDING_QUALITY);
            }
            RuntimeStats.init(AVRecordingService.LAST_RECORDING_FILEPATH);
            RuntimeStats.updateStatsUI(true);
        }
        else if(navAction.equals("PAUSE_RECORDING") && AVRecordingService.isRecording()) {
            AVRecordingService.localService.pauseRecording();
        }
        else if(navAction.equals("STOP_RECORDING") && (AVRecordingService.isRecording() || AVRECORD_SERVICE_RUNNING)) {
            Intent stopRecordingService = new Intent(this, AVRecordingService.class);
            stopService(stopRecordingService);
            unbindService(recordServiceConn);
            writeLoggingData("INFO", "Paused / stopped current recording session.");
        }
        else if(navAction.equals("PLAYBACK_RECORDING")) {
            writeLoggingData("INFO", "Navigation option \"PLAY LAST\" is currently unsupported.");
        }
        else if(navAction.equals("STREAM_RECORDING")) {
            writeLoggingData("INFO", "Navigation option \"STREAM\" is currently unsupported.");
        }

        if(AVRecordingService.getErrorState()) {
            Intent stopRecordingService = new Intent(this, AVRecordingService.class);
            stopService(stopRecordingService);
            unbindService(recordServiceConn);
            RuntimeStats.clear();
            RuntimeStats.updateStatsUI(false);
            Log.e(TAG, "AVRecording service reached error state ... turned it back off completely");
        }
        Log.i(TAG, "FILE PATH: " + AVRecordingService.LAST_RECORDING_FILEPATH);

    }

    public static void writeLoggingData(String msgPrefix, String msg) {
        String fullMessage = String.format(">> %s: %s\n", msgPrefix, msg);
        //tvLoggingMessages.setText(tvLoggingMessages.getText() + fullMessage);
        //tvLoggingMessages.scrollTo(Math.min(tvLoggingMessages.getLineCount(), MAX_LOGGING_LINES), 0);
        Log.i(TAG, fullMessage.substring(2));
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
