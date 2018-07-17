package com.maxieds.codenamepumpkinsconcert;

import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.Lists;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.CdnSettings;
import com.google.api.services.youtube.model.LiveBroadcast;
import com.google.api.services.youtube.model.LiveStream;
import com.google.api.services.youtube.model.LiveStreamSnippet;
import com.google.api.services.youtube.model.*;
import com.pedro.rtplibrary.rtmp.RtmpCamera1;

import net.ossrs.rtmp.ConnectCheckerRtmp;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class YouTubeStreamingService extends IntentService implements ConnectCheckerRtmp {

    private static final String TAG = YouTubeStreamingService.class.getSimpleName();

    /**
     * Define a global instance of the HTTP transport.
     */
    public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /**
     * Define a global instance of the JSON factory.
     */
    public static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private static final int YTSTREAMING_SERVICE_PROCID = 97736161;
    public static final String NCHANNELID_SERVICE = "com.maxieds.codenamepumpkinsconcert.YouTubeStreamingService";
    public static final String NCHANNELID_TASK = "com.maxieds.codenamepumpkinsconcert.YouTubeStreamingService.info";

    private static YouTube youtubeInst;
    private static LiveBroadcast returnedLiveBroadcast;
    private static LiveStream returnedLiveStream;
    private static YouTube.LiveStreams.Insert liveStreamInsert;
    private static String broadcastURL = "";
    private static String liveStreamName = "";
    private static String YOUTUBE_STREAMURL = "";
    public static int LOCAL_AVSETTING = AVRecordingService.AVSETTING_AUDIO_VIDEO;
    public static TextView tvBroadcastTitle;
    public static Spinner youtubePrivacySpinner, youtubeCDNSettingsSpinner, youtubeIngestionTypeSpinner;
    private boolean shuttingDown = false;

    private static RtmpCamera1 rtmpCamera1;

    public YouTubeStreamingService() {
        super("YouTubeStreamingService");
    }

    public void initNotifyChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Oreo 8.1 breaks startForground significantly
            NotificationManager notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notifyManager.createNotificationChannel(new NotificationChannel(NCHANNELID_SERVICE, "HomeBrewLiveStreamer You Tube Streaming Service", NotificationManager.IMPORTANCE_DEFAULT));
            notifyManager.createNotificationChannel(new NotificationChannel(NCHANNELID_TASK, "HomeBrewLiveStreamer You Tube Streaming Task Download Info", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initNotifyChannel();
    }

    public boolean startLiveBroadcast() {

        List<String> scopesList = Lists.newArrayList();
        scopesList.add("https://www.googleapis.com/auth/youtube.force-ssl");

        try {
            // Authorize the request:
            //Credential credential = Auth.authorize(scopesList, "createbroadcast");
            String authToken = AccountManager.get(getApplicationContext()).getAuthTokenByFeatures("com.google", "oauth2:https://gdata.youtube.com", null, MainActivity.runningActivity,
                    null, null, new AccountManagerCallback<Bundle>() {
                        @Override
                        public void run(AccountManagerFuture<Bundle> future) {
                            try {
                                Bundle bundle = future.getResult();
                                String acctName = bundle.getString(AccountManager.KEY_ACCOUNT_NAME);
                                String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                                Log.d(TAG, "AcctName: " + acctName + "; AuthToken: " + authToken);
                            } catch (Exception e) {
                                Log.e(TAG, e.getMessage());
                            }
                        }
                    }, null).getResult().getString(AccountManager.KEY_AUTHTOKEN);
            GoogleCredential gCred = new GoogleCredential().setAccessToken(authToken);
            if(gCred == null) {
                Log.e(TAG, "Invalid YouTube / Google Auth credential returned.");
                preemtivelyTerminateService();
                return false;
            }
            gCred.createScoped(scopesList);

            // This object is used to make YouTube Data API requests.
            youtubeInst = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, gCred)
                    .setApplicationName(getString(R.string.app_name))
                    .build();

            // Create a snippet with the title and scheduled start and end
            // times for the broadcast.
            String bcTitle = tvBroadcastTitle.getText().toString() + " -- " + Utils.getTimestamp();
            LiveBroadcastSnippet broadcastSnippet = new LiveBroadcastSnippet();
            broadcastSnippet.setTitle(bcTitle);

            ZonedDateTime dateSpec = ZonedDateTime.now();
            String projectedStartTime = dateSpec.format(DateTimeFormatter.ISO_INSTANT);
            broadcastSnippet.setScheduledStartTime(new DateTime(projectedStartTime));
            broadcastSnippet.setScheduledEndTime(new DateTime(projectedStartTime));

            LiveBroadcastStatus bcStatus = new LiveBroadcastStatus();
            bcStatus.setPrivacyStatus(youtubePrivacySpinner.getSelectedItem().toString());

            LiveBroadcast liveBroadcast = new LiveBroadcast();
            liveBroadcast.setKind("youtube#liveBroadcast");
            liveBroadcast.setSnippet(broadcastSnippet);
            liveBroadcast.setStatus(bcStatus);

            // Construct and execute the API request to insert the broadcast.
            YouTube.LiveBroadcasts.Insert liveBroadcastInsert = youtubeInst.liveBroadcasts().insert("snippet,status", liveBroadcast);
            returnedLiveBroadcast = liveBroadcastInsert.execute();

            // Create a snippet with the video stream's title.
            LiveStreamSnippet streamSnippet = new LiveStreamSnippet();
            streamSnippet.setTitle(bcTitle);

            // Define the content distribution network settings for the
            // video stream. The settings specify the stream's format and
            // ingestion type.
            CdnSettings cdnSettings = new CdnSettings();
            cdnSettings.setFormat(youtubeCDNSettingsSpinner.getSelectedItem().toString());
            cdnSettings.setIngestionType(youtubeIngestionTypeSpinner.getSelectedItem().toString());

            LiveStream liveStream = new LiveStream();
            liveStream.setKind("youtube#liveStream");
            liveStream.setSnippet(streamSnippet);
            liveStream.setCdn(cdnSettings);

            // Construct and execute the API request to insert the stream.
            liveStreamInsert = youtubeInst.liveStreams().insert("snippet,cdn", liveStream);
            returnedLiveStream = liveStreamInsert.execute();
            if(returnedLiveStream == null) {
                Log.e(TAG, "Returned Live Broadcast is NULL: " + liveStreamInsert.getLastStatusMessage());
                Log.e(TAG, "Returned Live Broadcast is NULL: " + liveStreamInsert.getLastResponseHeaders());
                preemtivelyTerminateService();
                return false;
            }
            //returnedLiveStream.setStatus(new LiveStreamStatus().setStreamStatus("active")); // by default this should be live stream only

            // Construct and execute a request to bind the new broadcast
            // and stream.
            YouTube.LiveBroadcasts.Bind liveBroadcastBind =
                    youtubeInst.liveBroadcasts().bind(returnedLiveBroadcast.getId(), "id,contentDetails");
            liveBroadcastBind.setStreamId(returnedLiveStream.getId());
            liveBroadcastBind.setPrettyPrint(true);
            returnedLiveBroadcast = liveBroadcastBind.execute();
            if(returnedLiveBroadcast == null) {
                Log.e(TAG, "Returned Live Broadcast is NULL: " + liveBroadcastBind.getLastStatusMessage());
                Log.e(TAG, "Returned Live Broadcast is NULL: " + liveBroadcastBind.getLastResponseHeaders());
                preemtivelyTerminateService();
                return false;
            }
            broadcastURL = "https://www.youtube.com/watch?v=" + returnedLiveBroadcast.getId();
            Log.i(TAG, "Now Beaming To: " + broadcastURL);

            // setup the camera feed:
            try {
                liveStreamName = returnedLiveStream.getCdn().getIngestionInfo().getStreamName();
                YOUTUBE_STREAMURL = returnedLiveStream.getCdn().getIngestionInfo().getIngestionAddress() + "/" + liveStreamName;
            } catch(Exception npe) {
                Log.e(TAG, "Getting CDN / Ingestion Info: " + npe.getMessage());
                preemtivelyTerminateService();
                return false;
            }

            try {
                boolean toggleCamera = MainActivity.cameraWhichSpinner.getSelectedItemPosition() > 0;
                boolean disableVideo = LOCAL_AVSETTING == AVRecordingService.AVSETTING_AUDIO_ONLY;
                rtmpCamera1 = new RtmpCamera1(AVRecordingService.videoPreviewView, this);
                if(!rtmpCamera1.prepareAudio()) {
                    Log.e(TAG, "Unable to prepare RTMP audio for YouTube streaming.");
                    preemtivelyTerminateService();
                    return false;
                }
                if (disableVideo) {
                    Log.i(TAG, "Disabling video");
                    rtmpCamera1.disableVideo();
                }
                else if(!rtmpCamera1.prepareVideo()) {
                    Log.e(TAG, "Unable to prepare RTMP video for YouTube streaming.");
                    preemtivelyTerminateService();
                    return false;
                }
                if (toggleCamera) {
                    Log.i(TAG, "Toggling camera");
                    rtmpCamera1.switchCamera();
                }
            } catch(Exception e) {
                Log.e(TAG, "Starting YouTube streaming service : " + e.getMessage());
                preemtivelyTerminateService();
                return false;
            }
            rtmpCamera1.startStream(YOUTUBE_STREAMURL);
            Log.i(TAG, "Created YouTube streaming service.");

            // have the MainActivity display a popup to the user with the effective URL of the string:
            Intent displayURLIntent = new Intent(getBaseContext(), MainActivity.class);
            displayURLIntent.setAction("YOUTUBE_STREAM_STATUS_READY");
            displayURLIntent.putExtra("broadcastURL", broadcastURL);
            //LocalBroadcastManager.getInstance(MainActivity.runningActivity.getBaseContext()).sendBroadcast(displayURLIntent);
            LocalBroadcastManager.getInstance(this).sendBroadcast(displayURLIntent);

        } catch (GoogleJsonResponseException e) {
            Log.e(TAG, "GoogleJsonResponseException code: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
            Log.e(TAG, Log.getStackTraceString(e));
            preemtivelyTerminateService();
            return false;
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            Log.e(TAG, Log.getStackTraceString(e));
            preemtivelyTerminateService();
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "Throwable: " + t.getMessage());
            Log.e(TAG, Log.getStackTraceString(t));
            preemtivelyTerminateService();
            return false;
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shuttingDown = true;
        rtmpCamera1.stopPreview();
        rtmpCamera1.stopRecord();
        rtmpCamera1.stopStream();
        rtmpCamera1 = null;;
        liveStreamInsert.clear();
        returnedLiveStream.clear();
        returnedLiveBroadcast.clear();
    }

    private void preemtivelyTerminateService() {
        stopSelf();
        MainActivity.runningActivity.stopAVRecordingService();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            Log.w(TAG, "onHandleIntent passed a NULL intent object.");
            return;
        }
        String intentAction = intent.getAction();
        if(intentAction != null && intentAction.equals(MainActivity.STREAM_RECORDING)) {
            startForeground(YTSTREAMING_SERVICE_PROCID, getForegroundServiceNotify("We are currently streaming media via You Tube live broadcast."));
            RuntimeStats.updateStatsUI(true, false);
            startLiveBroadcast();
        }
    }

    public Notification getForegroundServiceNotify(String bannerMsg) {
        Intent intent = new Intent(this, AVRecordingService.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder fgNotify = new NotificationCompat.Builder(this, NCHANNELID_TASK);
        fgNotify.setOngoing(true);
        fgNotify.setContentTitle("Home Brew Live Streamer (You Tube Live)")
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
        Log.i(TAG, "Successfully connected RTMP for YouTube streaming.");
    }

    @Override
    public void onConnectionFailedRtmp(final String reason) {
        Log.e(TAG, "Unable to connect RTMP for YouTube streaming.");
        preemtivelyTerminateService();
    }

    @Override
    public void onDisconnectRtmp() {
        Log.i(TAG, "Disconnected to RTMP stream for YouTube");
        if(!shuttingDown) {
            preemtivelyTerminateService();
        }
    }

    @Override
    public void onAuthErrorRtmp() {
        Log.e(TAG, "Authentication error with RTMP for YouTube.");
        preemtivelyTerminateService();
    }

    @Override
    public void onAuthSuccessRtmp() {
        Log.i(TAG, "RTMP Auth successful for YouTube streaming.");
    }

}
