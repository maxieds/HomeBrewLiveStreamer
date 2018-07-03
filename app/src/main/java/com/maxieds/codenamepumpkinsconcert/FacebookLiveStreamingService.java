package com.maxieds.codenamepumpkinsconcert;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.CamcorderProfile;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookSdk;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

import com.maxieds.codenamepumpkinsconcert.libstreaming.Session;
import com.maxieds.codenamepumpkinsconcert.libstreaming.SessionBuilder;
import com.maxieds.codenamepumpkinsconcert.libstreaming.audio.AudioQuality;
import com.maxieds.codenamepumpkinsconcert.libstreaming.rtsp.RtspClient;
import com.maxieds.codenamepumpkinsconcert.libstreaming.video.VideoQuality;
import com.pedro.rtplibrary.rtmp.RtmpCamera1;

import net.ossrs.rtmp.ConnectCheckerRtmp;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FacebookLiveStreamingService extends IntentService implements ConnectCheckerRtmp {

    private static final String TAG = FacebookLiveStreamingService.class.getSimpleName();

    public static final List<String> FBPERMISSIONS = Arrays.asList("publish_actions");
                private static final int FBSTREAMING_SERVICE_PROCID = 97736155;
                public static final String NCHANNELID_SERVICE = "com.maxieds.codenamepumpkinsconcert.FacebookLiveStreamingService";
                public static final String NCHANNELID_TASK = "com.maxieds.codenamepumpkinsconcert.FacebookLiveStreamingService.info";

    public static LoginResult fbLoginResult;
    public static TextView tvPostURL, tvStreamKey;
    public static Spinner streamingMediaTypeSpinner;

    private static String FBSTREAM_POSTURL;
    private static String FBSTREAM_SKEY;
    public static boolean fbInitialized = false;
    public static boolean shuttingDown = false;
    private static AccessToken loginAccessToken;

    public static String DEFAULT_AVQUALSPEC = MainActivity.DEFAULT_RECORDING_QUALITY;
    public static int LOCAL_AVSETTING = AVRecordingService.AVSETTING_AUDIO_VIDEO;

    private static Session streamingSession;
    private static RtspClient streamingClient;
    private static RtmpCamera1 rtmpCamera1;

    public FacebookLiveStreamingService() {
        super("FacebookLiveStreamingService");
    }

    public void initNotifyChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Oreo 8.1 breaks startForground significantly
            NotificationManager notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notifyManager.createNotificationChannel(new NotificationChannel(NCHANNELID_SERVICE, "HomeBrewLiveStreamer Facebook Streaming Service", NotificationManager.IMPORTANCE_DEFAULT));
            notifyManager.createNotificationChannel(new NotificationChannel(NCHANNELID_TASK, "HomeBrewLiveStreamer Facebook Streaming Task Download Info", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    @Override
    public void onCreate() {

        Log.i(TAG, "inside onCreate()");
        super.onCreate();
        shuttingDown = false;
        initNotifyChannel();

        // configure Facebook with the appropriate permissions:
        if(!fbInitialized) {
            LoginManager.getInstance().logInWithPublishPermissions(MainActivity.runningActivity, FBPERMISSIONS);
            loginAccessToken = AccessToken.getCurrentAccessToken();
            boolean fbLoggedIn = loginAccessToken != null && !loginAccessToken.isExpired();
            if(!fbLoggedIn) {
                Log.w(TAG, "Facebook user not logged in .. stopping service.");
                preemtivelyTerminateService()
            }
        }

        // configure the local user's live streaming settings:
        FBSTREAM_POSTURL = MainActivity.runningActivity.getResources().getString(R.string.FBStreamDefaultPostURL);
        if(tvPostURL != null) {
            FBSTREAM_POSTURL = tvPostURL.getText().toString();
        }
        FBSTREAM_SKEY = MainActivity.runningActivity.getResources().getString(R.string.FBStreamDefaultStreamKey);
        if(tvStreamKey != null) {
            FBSTREAM_SKEY = tvStreamKey.getText().toString();
        }
        if(AVRecordingService.videoOptsQuality != null) {
            DEFAULT_AVQUALSPEC = AVRecordingService.videoOptsQuality.getSelectedItem().toString();
        }

    }

    public void startStreamingClient() {

        rtmpCamera1 = new RtmpCamera1(AVRecordingService.videoPreviewView, this);
        if (rtmpCamera1.prepareAudio() && rtmpCamera1.prepareVideo()) {
            rtmpCamera1.startStream(FBSTREAM_POSTURL + FBSTREAM_SKEY);
            Log.i(TAG, "Streaming to: " + FBSTREAM_POSTURL + FBSTREAM_SKEY);
        }
        else {
            Log.e(TAG, "Unable to start the RTMP camera.");
            rtmpCamera1.stopStream();
            preemtivelyTerminateService()
        }
    }

    public void startStreamingClientv2() {
        // setup the RTSP streaming client to the Facebook server:
        AVQualitySpec qspec = AVQualitySpec.stringToDefaultSpec(DEFAULT_AVQUALSPEC);
        CamcorderProfile cprof = AVQualitySpec.stringToCamcorderSpec(DEFAULT_AVQUALSPEC);
        int videoEncoder = LOCAL_AVSETTING == AVRecordingService.AVSETTING_AUDIO_ONLY ? SessionBuilder.VIDEO_NONE : SessionBuilder.VIDEO_H264;
        try {
            int cameraPosition = MainActivity.cameraWhichSpinner.getSelectedItemPosition();
            streamingSession = SessionBuilder.getInstance()
                    .setContext(getApplicationContext())
                    .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                    .setAudioQuality(new AudioQuality(cprof.audioSampleRate, cprof.audioBitRate))
                    .setVideoEncoder(videoEncoder)
                    .setVideoQuality(new VideoQuality(cprof.videoFrameWidth, cprof.videoFrameHeight, cprof.videoFrameRate, cprof.videoBitRate))
                    .setFlashEnabled(false)
                    .setCamera(cameraPosition)
                    .build();
        } catch(Exception ioe) {
            Log.e(TAG, ioe.getMessage());
            MainActivity.runningActivity.stopAVRecordingService();
        }
        streamingClient = new RtspClient();
        streamingClient.setSession(streamingSession);

        String ip, port, path;
        Pattern uri = Pattern.compile("rtmps://(.+):(\\d*)/(.+)");
        Matcher match = uri.matcher(FBSTREAM_POSTURL);
        match.find();
        ip = match.group(1);
        port = match.group(2);
        path = match.group(3);

        try {
            streamingClient.setCredentials("", "");
            streamingClient.setServerAddress(ip, Integer.parseInt(port));
            streamingClient.setStreamPath("/" + path + FBSTREAM_SKEY);
            streamingClient.startStream();
        } catch(Exception ioe) {
            Log.e(TAG, ioe.getMessage());
            MainActivity.runningActivity.stopAVRecordingService();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shuttingDown = true;
        rtmpCamera1.stopStream();
        streamingSession = null;
        streamingClient = null;
    }

    private void preemtivelyTerminateService() {
        stopSelf();
        MainActivity.runningActivity.stopAVRecordingService();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, "onHandleIntent");
        if (intent == null) {
            Log.w(TAG, "onHandleIntent passed a NULL intent object.");
            return;
        }
        String intentAction = intent.getAction();
        if(intentAction != null && intentAction.equals(MainActivity.STREAM_RECORDING)) {
            startForeground(FBSTREAMING_SERVICE_PROCID, getForegroundServiceNotify("We are currently streaming media via Facebook Live."));
            RuntimeStats.updateStatsUI(true, false);
            startStreamingClient();
        }
    }

    public Notification getForegroundServiceNotify(String bannerMsg) {
        Intent intent = new Intent(this, AVRecordingService.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder fgNotify = new NotificationCompat.Builder(this, NCHANNELID_TASK);
        fgNotify.setOngoing(true);
        fgNotify.setContentTitle("Home Brew Live Streamer (Facebook Live)")
                .setColor(0xAEEEEE)
                .setTicker(bannerMsg)
                .setContentText(bannerMsg)
                .setOngoing(true)
                .setSmallIcon(R.drawable.drumset64)
                .setWhen(System.currentTimeMillis())
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent);
        return fgNotify.build();
    }

    @Override
    public void onConnectionSuccessRtmp() {
        Log.i(TAG, "Successfully connected RTMP for Facebook streaming.");
    }

    @Override
    public void onConnectionFailedRtmp(final String reason) {
        Log.e(TAG, "Unable to connect RTMP for Facebook streaming.");
        preemtivelyTerminateService();
    }

    @Override
    public void onDisconnectRtmp() {
        Log.i(TAG, "Disconnected to RTMP stream for Facebook");
        if(!shuttingDown) {
            preemtivelyTerminateService();
        }
    }

    @Override
    public void onAuthErrorRtmp() {
        Log.e(TAG, "Authentication error with RTMP for Facebook.");
        preemtivelyTerminateService();
    }

    @Override
    public void onAuthSuccessRtmp() {
        Log.i(TAG, "RTMP Auth successful for Facebook streaming.");
    }

}
