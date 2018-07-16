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

import com.google.android.gms.auth.api.signin.GoogleSignInApi;
import com.google.api.client.auth.oauth.OAuthCredentialsResponse;
import com.google.api.client.auth.oauth.OAuthGetAccessToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.OAuth2Utils;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.Lists;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.CdnSettings;
import com.google.api.services.youtube.model.LiveBroadcast;
import com.google.api.services.youtube.model.LiveStream;
import com.google.api.services.youtube.model.LiveStreamSnippet;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.maxieds.codenamepumpkinsconcert.GoogleAPISamples.Auth;

import java.io.IOException;
import java.util.List;

import static android.accounts.AccountManager.get;

public class YouTubeStreamingService extends IntentService {

    private static final String TAG = YouTubeStreamingService.class.getSimpleName();

    private static final int YTSTREAMING_SERVICE_PROCID = 97736161;
    public static final String NCHANNELID_SERVICE = "com.maxieds.codenamepumpkinsconcert.YouTubeStreamingService";
    public static final String NCHANNELID_TASK = "com.maxieds.codenamepumpkinsconcert.YouTubeStreamingService.info";

    private static YouTube youtubeInst;
    private static LiveBroadcast returnedLiveBroadcast;
    private static LiveStream returnedLiveStream;
    private static YouTube.LiveStreams.Insert liveStreamInsert;
    private static String broadcastURL = "";
    public static TextView tvBroadcastTitle;
    public static Spinner youtubePrivacySpinner, youtubeCDNSettingsSpinner, youtubeIngestionTypeSpinner;

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

    public void startLiveBroadcast() {

        List<String> scopesList = Lists.newArrayList();
        scopesList.add("https://www.googleapis.com/auth/youtube.force-ssl");

        try {
            // Authorize the request:

            // TODO
            //Credential credential = Auth.authorize(scopesList, "createbroadcast");
            String authToken = AccountManager.get(getApplicationContext()).getAuthTokenByFeatures("com.google", "oauth2:https://gdata.youtube.com", null, MainActivity.runningActivity,
                    null, null, new AccountManagerCallback<Bundle>() {
                        @Override
                        public void run(AccountManagerFuture<Bundle> future) {
                            try {
                                Bundle bundle = future.getResult();
                                String acctName = bundle.getString(AccountManager.KEY_ACCOUNT_NAME);
                                String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                                Log.d(TAG, "name: " + acctName + "; token: " + authToken);
                            } catch (Exception e) {
                                Log.e(TAG, e.getMessage());
                            }
                        }
                    }, null).getResult().getString(AccountManager.KEY_AUTHTOKEN);
            GoogleCredential gCred = new GoogleCredential().setAccessToken(authToken);
            if(gCred == null) {
                Log.e(TAG, "Invalid YouTube / Google Auth credential returned.");
                stopSelf();
                MainActivity.runningActivity.stopAVRecordingService();
                return;
            }
            gCred.createScoped(scopesList);

            // This object is used to make YouTube Data API requests.
            youtubeInst = new YouTube.Builder(Auth.HTTP_TRANSPORT, Auth.JSON_FACTORY, gCred)
                    .setApplicationName(getString(R.string.app_name))
                    .build();

            // Create a snippet with the title and scheduled start and end
            // times for the broadcast. Currently, those times are hard-coded.
            String bcTitle = tvBroadcastTitle.getText().toString() + " -- " + Utils.getTimestamp();
            LiveBroadcastSnippet broadcastSnippet = new LiveBroadcastSnippet();
            broadcastSnippet.setTitle(bcTitle);
            broadcastSnippet.setScheduledStartTime(new DateTime("2024-01-30T00:00:00.000Z"));
            broadcastSnippet.setScheduledEndTime(new DateTime("2024-01-31T00:00:00.000Z"));
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


            // Construct and execute a request to bind the new broadcast
            // and stream.
            YouTube.LiveBroadcasts.Bind liveBroadcastBind =
                    youtubeInst.liveBroadcasts().bind(returnedLiveBroadcast.getId(), "id,contentDetails");
            liveBroadcastBind.setStreamId(returnedLiveStream.getId());
            returnedLiveBroadcast = liveBroadcastBind.execute();
            returnedLiveBroadcast.getStatus().setRecordingStatus("notRecording"); // by default this should be live stream only
            broadcastURL = "https://www.youtube.com/watch?v=" + returnedLiveBroadcast.getId();

            // have the MainActivity display a popup to the user with the effective URL of the string:
            Intent displayURLIntent = new Intent(getBaseContext(), MainActivity.class);
            displayURLIntent.setAction("YOUTUBE_STREAM_STATUS_READY");
            displayURLIntent.putExtra("broadcastURL", broadcastURL);
            LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(displayURLIntent);

        } catch (GoogleJsonResponseException e) {
            Log.e(TAG, "GoogleJsonResponseException code: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
            Log.e(TAG, Log.getStackTraceString(e));
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            Log.e(TAG, Log.getStackTraceString(e));
        } catch (Throwable t) {
            Log.e(TAG, "Throwable: " + t.getMessage());
            Log.e(TAG, Log.getStackTraceString(t));
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        liveStreamInsert.clear();
        returnedLiveStream.clear();
        returnedLiveBroadcast.clear();
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

}
