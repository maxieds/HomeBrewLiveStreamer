package com.maxieds.codenamepumpkinsconcert;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInApi;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.tasks.Task;

import java.lang.reflect.Field;

import static android.os.Process.killProcess;
import static android.os.Process.myPid;
import static android.os.Process.myUid;
import static com.maxieds.codenamepumpkinsconcert.TabFragment.TAB_ABOUT;
import static com.maxieds.codenamepumpkinsconcert.TabFragment.TAB_COVERT_MODE;
import static com.maxieds.codenamepumpkinsconcert.TabFragment.TAB_LIVE_PANEL;
import static com.maxieds.codenamepumpkinsconcert.TabFragment.TAB_SETTINGS;
import static com.maxieds.codenamepumpkinsconcert.TabFragment.TAB_TOOLS;

public class MainActivity extends AppCompatActivity {

    public static final int MEDIA_STATE_IDLE = 0;
    public static final int MEDIA_STATE_PLAYBACK_MODE = 1;
    public static final int MEDIA_STATE_RECORDING_MODE = 2;
    public static final int MEDIA_STATE_STREAMING_MODE = 3;
    public static int mediaState = MEDIA_STATE_IDLE;

    private static final String TAG = MainActivity.class.getSimpleName();
    public static MainActivity runningActivity;
    public static LayoutInflater defaultInflater;

    private static ViewPager viewPager;
    private static TabLayout tabLayout;
    private static int selectedTab = TAB_LIVE_PANEL;
    private static ViewPager.OnPageChangeListener tabChangeListener = null;
    public static TextView tvLoggingMessages;
    public static Spinner cameraWhichSpinner, streamingTypeSpinner;
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
    private static ServiceConnection fbStreamServiceConn = new ServiceConnection() {
        public FacebookLiveStreamingService streamingService;
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Log.d("ServiceConnection","connected");
            streamingService = (FacebookLiveStreamingService) binder;
        }
        public void onServiceDisconnected(ComponentName className) {
            Log.d("ServiceConnection","disconnected");
            streamingService = null;
        }
    };
    private static ServiceConnection ytStreamServiceConn = new ServiceConnection() {
        public YouTubeStreamingService streamingService;
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Log.d("ServiceConnection","connected");
            streamingService = (YouTubeStreamingService) binder;
        }
        public void onServiceDisconnected(ComponentName className) {
            Log.d("ServiceConnection","disconnected");
            streamingService = null;
        }
    };
    public static boolean setDoNotDisturb = true;
    public static boolean AVRECORD_SERVICE_RUNNING = false;
    public static String streamServiceType = "";

    public static LoginButton fbLoginButton;
    public static CallbackManager fbLoginCallback;
    public static boolean fbInitialized = false;

    private BroadcastReceiver youTubeURLReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals("YOUTUBE_STREAM_STATUS_READY")) {
                final String broadcastURL = intent.getStringExtra("broadcastURL");
                TextView tvBroadcastURL = new TextView(MainActivity.runningActivity);
                tvBroadcastURL.setText(broadcastURL);
                tvBroadcastURL.setTextAppearance(MainActivity.runningActivity, R.style.SpinnerTheme);
                AlertDialog.Builder adBuilder = new AlertDialog.Builder(MainActivity.runningActivity);
                adBuilder.setTitle("YouTube Stream Broadcast URL");
                adBuilder.setMessage("The live YouTube broadcast is being streamed to the following URL: ");
                adBuilder.setView(tvBroadcastURL);
                adBuilder.setPositiveButton("Done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                adBuilder.setNeutralButton("Copy URL", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData urlClipEntry = ClipData.newPlainText("YOUTUBE_STREAMING_URL", broadcastURL);
                        clipboard.setPrimaryClip(urlClipEntry);
                    }
                });
                adBuilder.show();
            }
        }
    };
    private LocalBroadcastManager localBroadcastManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
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
        TextView titleTextView = (TextView) toolbar.findViewById(R.id.toolbar_title);
        titleTextView.setText(toolbar.getTitle());
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        ImageView toolbarLogoRHS = (ImageView) toolbar.findViewById(R.id.toolbarLogoRHS);
        toolbarLogoRHS.getDrawable().setBounds(toolbar.getLogo().getBounds());

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
                    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
                    toolbar.setVisibility(View.INVISIBLE);
                    ((GridLayout) findViewById(R.id.statsWindowGridLayout)).setVisibility(View.INVISIBLE);
                    tabLayout.setVisibility(View.INVISIBLE);
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
                //"android.permission.READ_PHONE_STATE"
                //"android.permission.ACCESS_NETWORK_STATE",
                //"android.permission.GET_ACCOUNTS"
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

        // initialize the Facebook API for the live streaming component of the application:
        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);

        // setup the handler to display the YouTube stream URL once it is obtained from the service:
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter bcIntentFilter = new IntentFilter();
        bcIntentFilter.addAction("YOUTUBE_STREAM_STATUS_READY");
        localBroadcastManager.registerReceiver(youTubeURLReceiver, bcIntentFilter);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(FacebookSdk.isFacebookRequestCode(requestCode)) {
            fbLoginCallback.onActivityResult(requestCode, resultCode, data);
            super.onActivityResult(requestCode, resultCode, data);
        }
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
        if(mediaState == MEDIA_STATE_RECORDING_MODE && AVRecordingService.localService != null) {
            if(AVRecordingService.LOCAL_AVSETTING == AVRecordingService.AVSETTING_AUDIO_VIDEO) { // shutdown the camera:
                AVRecordingService.localService.releaseMediaRecorder();
                AVRecordingService.localService.releaseCamera();
            }
            else { // keep MediaRecorder running to record audio in the meantime:
                AVRecordingService.localService.releaseCamera();
                //AVRecordingService.localService.videoPreviewOff();
            }
        }
        else if(mediaState == MEDIA_STATE_PLAYBACK_MODE || mediaState == MEDIA_STATE_STREAMING_MODE) {
            stopAVRecordingService();
        }
        if(AVRECORD_SERVICE_RUNNING) {
            RuntimeStats.statsUpdateHandler.removeCallbacks(RuntimeStats.statsUpdateRunnableForeground);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mediaState == MEDIA_STATE_RECORDING_MODE && AVRecordingService.localService != null) { // reconnect the surface to the camera:
            if(AVRecordingService.LOCAL_AVSETTING == AVRecordingService.AVSETTING_AUDIO_VIDEO) { // restore the camera:
                AVRecordingService.localService.initAVParams(true);
                AVRecordingService.localService.recordVideoNow(AVRecordingService.DEFAULT_AVQUALSPEC_ID);
            }
            else { // keep MediaRecorder still running to record audio in the meantime (this resets the display surface on screen):
                AVRecordingService.localService.initAVParams(false);
                //AVRecordingService.localService.videoPreviewOn();
            }
            AVRecordingService.videoPreview = new SurfaceTexture(0);
            AVRecordingService.videoPreview.detachFromGLContext();
        }
        if(AVRECORD_SERVICE_RUNNING) {
            RuntimeStats.updateStatsUI(true, true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
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
        localBroadcastManager.unregisterReceiver(youTubeURLReceiver);
        saveConfiguration();
        writeLoggingData("INFO", "Exiting application.");
    }

    public void restoreConfiguration() {
        SharedPreferences configPrefs = getSharedPreferences(getString(R.string.configPrefsKey), Context.MODE_PRIVATE);
        AVRecordingService.tvOutputFilePrefix.setText(configPrefs.getString("outputFilePrefix", "pumpkins-atlanta"));
        AVRecordingService.tvMaxFileSliceSize.setText(configPrefs.getString("maxFileSliceSize", "1024"));
        boolean setDNDWhileRecording = configPrefs.getBoolean("setDNDWhileRecording", true);
        setDoNotDisturb = setDNDWhileRecording;
        AVRecordingService.cbDNDWhileRecording.setChecked(setDNDWhileRecording);
        AVRecordingService.videoOptsQuality.setSelection(configPrefs.getInt("AVRecordingQualityIndex", 0));
        YouTubeStreamingService.tvBroadcastTitle.setText(configPrefs.getString("broadcastTitle", "Home Brew Live Streamer"));
        cameraWhichSpinner.setSelection(configPrefs.getInt("whichCameraIndex", 0));
        streamingTypeSpinner.setSelection(configPrefs.getInt("streamingHighLevelProtocolIndex", 0));
        AVRecordingService.videoPlaybackOptsContentType.setSelection(configPrefs.getInt("mediaContentTypeIndex", 0));
        FacebookLiveStreamingService.streamingMediaTypeSpinner.setSelection(configPrefs.getInt("mediaAVTypeIndex", 0));
        FacebookLiveStreamingService.tvPostURL.setText(configPrefs.getString("fbLiveStreamingURL", getString(R.string.FBStreamDefaultPostURL)));
        FacebookLiveStreamingService.tvStreamKey.setText(configPrefs.getString("fbStreamKey", getString(R.string.FBStreamDefaultStreamKey)));
        YouTubeStreamingService.youtubePrivacySpinner.setSelection(configPrefs.getInt("youtubePrivacyIndex", 0));
        YouTubeStreamingService.youtubeCDNSettingsSpinner.setSelection(configPrefs.getInt("youtubeCDNIndex", 0));
        YouTubeStreamingService.youtubeIngestionTypeSpinner.setSelection(configPrefs.getInt("youtubeIngestionTypeIndex", 0));
    }

    private void saveConfiguration() {
        SharedPreferences configPrefs = getSharedPreferences(getString(R.string.configPrefsKey), Context.MODE_PRIVATE);
        SharedPreferences.Editor cfgEditor = configPrefs.edit();
        cfgEditor.putString("outputFilePrefix", AVRecordingService.tvOutputFilePrefix.getText().toString());
        cfgEditor.putString("maxFileSliceSize", AVRecordingService.tvMaxFileSliceSize.getText().toString());
        cfgEditor.putBoolean("setDNDWhileRecording", AVRecordingService.cbDNDWhileRecording.isChecked());
        cfgEditor.putInt("AVRecordingQualityIndex", AVRecordingService.videoOptsQuality.getSelectedItemPosition());
        cfgEditor.putInt("whichCameraIndex", cameraWhichSpinner.getSelectedItemPosition());
        cfgEditor.putInt("streamingHighLevelProtocolIndex", streamingTypeSpinner.getSelectedItemPosition());
        cfgEditor.putInt("mediaContentTypeIndex", AVRecordingService.videoPlaybackOptsContentType.getSelectedItemPosition());
        cfgEditor.putString("broadcastTitle", YouTubeStreamingService.tvBroadcastTitle.getText().toString());
        cfgEditor.putInt("mediaAVTypeIndex", FacebookLiveStreamingService.streamingMediaTypeSpinner.getSelectedItemPosition());
        cfgEditor.putString("fbLiveStreamingURL", FacebookLiveStreamingService.tvPostURL.getText().toString());
        cfgEditor.putString("fbStreamKey", FacebookLiveStreamingService.tvStreamKey.getText().toString());
        cfgEditor.putInt("youtubePrivacyIndex", YouTubeStreamingService.youtubePrivacySpinner.getSelectedItemPosition());
        cfgEditor.putInt("youtubeCDNIndex", YouTubeStreamingService.youtubeCDNSettingsSpinner.getSelectedItemPosition());
        cfgEditor.putInt("youtubeIngestionTypeIndex", YouTubeStreamingService.youtubeIngestionTypeSpinner.getSelectedItemPosition());
        //cfgEditor.clear();
        cfgEditor.commit();
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

    public void actionButtonCovertModeToLive(View button) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setVisibility(View.VISIBLE);
        ((GridLayout) findViewById(R.id.statsWindowGridLayout)).setVisibility(View.VISIBLE);
        tabLayout.setVisibility(View.VISIBLE);
        viewPager.setCurrentItem(TAB_LIVE_PANEL);
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
        if(navAction.equals(STREAM_RECORDING)) {
            streamServiceType = streamingTypeSpinner.getSelectedItem().toString();
            Log.i(TAG, "About to start stream type: " + streamServiceType);
        }
        if((navAction.equals(RECORD_VIDEO) || navAction.equals(RECORD_AUDIO)) && mediaState == MEDIA_STATE_IDLE) {
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
            mediaState = MEDIA_STATE_RECORDING_MODE;
        }
        else if(navAction.equals(PAUSE_RECORDING) && (AVRecordingService.isRecording() || mediaState == MEDIA_STATE_PLAYBACK_MODE)) {
            AVRecordingService.localService.pauseRecording();
        }
        else if(navAction.equals(PAUSE_RECORDING) || navAction.equals(STOP_RECORDING)) {
            stopAVRecordingService();
        }
        else if(navAction.equals(PLAYBACK_RECORDING) && mediaState == MEDIA_STATE_IDLE) {
            AVRecordingService.LOCAL_AVSETTING = AVRecordingService.AVSETTING_PLAYBACK;
            Intent startPlaybackService = new Intent(this, AVRecordingService.class);
            startPlaybackService.setAction(navAction);
            startService(startPlaybackService);
            bindService(startPlaybackService, recordServiceConn, Context.BIND_AUTO_CREATE);
            RuntimeStats.updateStatsUI(true, true);
            mediaState = MEDIA_STATE_PLAYBACK_MODE;
        }
        else if(navAction.equals(STREAM_RECORDING) && streamServiceType.equals("FACEBOOK-LIVE-STREAM")) {
            FacebookLiveStreamingService.LOCAL_AVSETTING = FacebookLiveStreamingService.streamingMediaTypeSpinner.getSelectedItemPosition() == 0 ? AVRecordingService.AVSETTING_AUDIO_VIDEO : AVRecordingService.AVSETTING_AUDIO_ONLY;
            Intent startStreamingService = new Intent(this, FacebookLiveStreamingService.class);
            startStreamingService.setAction(navAction);
            startService(startStreamingService);
            bindService(startStreamingService, fbStreamServiceConn, Context.BIND_AUTO_CREATE);
            RuntimeStats.updateStatsUI(true, true);
            mediaState = MEDIA_STATE_STREAMING_MODE;
            Log.i(TAG, "Started Facebook streaming service.");
        }
        else if(navAction.equals(STREAM_RECORDING) && streamServiceType.equals("YOUTUBE-LIVE-BROADCAST")) {
            YouTubeStreamingService.LOCAL_AVSETTING = FacebookLiveStreamingService.streamingMediaTypeSpinner.getSelectedItemPosition() == 0 ? AVRecordingService.AVSETTING_AUDIO_VIDEO : AVRecordingService.AVSETTING_AUDIO_ONLY;
            Intent startStreamingService = new Intent(this, YouTubeStreamingService.class);
            startStreamingService.setAction(navAction);
            startService(startStreamingService);
            bindService(startStreamingService, ytStreamServiceConn, Context.BIND_AUTO_CREATE);
            RuntimeStats.updateStatsUI(true, true);
            mediaState = MEDIA_STATE_STREAMING_MODE;
            Log.i(TAG, "Started YouTube streaming service.");
        }
    }

    public void stopAVRecordingService() {
        if(mediaState == MEDIA_STATE_RECORDING_MODE || mediaState == MEDIA_STATE_PLAYBACK_MODE) {
            try {
                Intent stopRecordingService = new Intent(this, AVRecordingService.class);
                unbindService(recordServiceConn);
                stopService(stopRecordingService);
                releaseWakeLock();
            } catch (Exception ise) {
                Log.e(TAG, ise.getMessage());
            }
            mediaState = MEDIA_STATE_IDLE;
            RuntimeStats.clear();
            RuntimeStats.updateStatsUI(false, true);
            writeLoggingData("INFO", "Paused / stopped current recording and/or playback session.");
        }
        else if(mediaState == MEDIA_STATE_STREAMING_MODE && streamServiceType.equals("FACEBOOK-LIVE-STREAM")) {
            try {
                Intent stopStreamingService = new Intent(this, FacebookLiveStreamingService.class);
                unbindService(fbStreamServiceConn);
                stopService(stopStreamingService);
                //releaseWakeLock();
            } catch (Exception ise) {
                Log.e(TAG, ise.getMessage());
            }
            mediaState = MEDIA_STATE_IDLE;
            RuntimeStats.clear();
            RuntimeStats.updateStatsUI(false, true);
            writeLoggingData("INFO", "Paused / stopped current facebook streaming session.");
        }
        else if(mediaState == MEDIA_STATE_STREAMING_MODE && streamServiceType.equals("YOUTUBE-LIVE-BROADCAST")) {
            try {
                Intent stopStreamingService = new Intent(this, YouTubeStreamingService.class);
                unbindService(ytStreamServiceConn);
                stopService(stopStreamingService);
                //releaseWakeLock();
            } catch (Exception ise) {
                Log.e(TAG, ise.getMessage());
            }
            mediaState = MEDIA_STATE_IDLE;
            RuntimeStats.clear();
            RuntimeStats.updateStatsUI(false, true);
            writeLoggingData("INFO", "Paused / stopped current youtube streaming session.");
        }
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
