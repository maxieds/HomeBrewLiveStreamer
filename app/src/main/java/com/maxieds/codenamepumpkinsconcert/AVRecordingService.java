package com.maxieds.codenamepumpkinsconcert;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaCodecInfo;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class AVRecordingService extends IntentService {

    private static final String TAG = AVRecordingService.class.getSimpleName();

    public static final int AVSETTING_AUDIO_ONLY = 0;
    public static final int AVSETTING_AUDIO_VIDEO = 1;
    public static int LOCAL_AVSETTING = AVSETTING_AUDIO_ONLY;

    private static final String AVSAVE_SUBFOLDER = "HomeBrewAVRecorder";
    public static String DEFAULT_AVQUALSPEC_ID = MainActivity.DEFAULT_RECORDING_QUALITY;
    public static String AVOUTPUT_FILE_PREFIX = "pumpkins-atlanta";

    public static AVRecordingService localService = null;
    private static boolean isRecording = false;
    private static boolean isRecordingAudioOnly = true;
    private static File loggingFile;
    private static String loggingFilePath;
    private static boolean videoFeedPreviewOn = false;
    private static boolean avFeedPreviewOn = false;
    private static boolean inErrorState = false;
    private static boolean isPaused = false;
    public static String LAST_ERROR_MESSAGE = "";
    public static String LAST_RECORDING_FILEPATH = null;
    public static int dndInterruptionPolicy;

    private Camera videoFeed = null;
    private MediaRecorder avFeed = null;

    public AVRecordingService() {
        super("AVRecordingService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    public static final String NCHANNELID_SERVICE = "com.maxieds.codenamepumpkinsconcert.AVRecordingService";
    public static final String NCHANNELID_TASK = "com.maxieds.codenamepumpkinsconcert.AVRecordingService.info";

    public void initNotifyChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Oreo 8.1 breaks startForground significantly
            NotificationManager notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notifyManager.createNotificationChannel(new NotificationChannel(NCHANNELID_SERVICE, "HomeBrewLiveStreamer A/V Recording Service", NotificationManager.IMPORTANCE_HIGH));
            notifyManager.createNotificationChannel(new NotificationChannel(NCHANNELID_TASK, "HomeBrewLiveStreamer Task Download Info", NotificationManager.IMPORTANCE_HIGH));
        }
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Started AV recorder service in the background...");
        super.onCreate();
        initNotifyChannel();
        localService = this;
        MainActivity.AVRECORD_SERVICE_RUNNING = true;
        LAST_ERROR_MESSAGE = "NO ERROR";
        DEFAULT_AVQUALSPEC_ID = MainActivity.videoOptsQuality.getSelectedItem().toString();
        MainActivity.videoPreviewBGOverlay.setAlpha(0);
        if(MainActivity.setDoNotDisturb) {
            NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            dndInterruptionPolicy = notifyManager.getCurrentInterruptionFilter();
            notifyManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        }
        if(!MainActivity.bgWakeLock.isHeld()) {
            MainActivity.bgWakeLock.acquire();
        }
        // acquire camera + mic resources + external storage file path:
        try {
            videoFeed = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK); // open the back-facing camera
            updateVideoFeedParams();
            videoFeed.setPreviewDisplay(MainActivity.videoPreview.getHolder());
            videoFeed.startPreview();
            videoFeedPreviewOn = true;
            inErrorState = false;
        }
        catch(Exception ce) {
            inErrorState = true;
            Utils.printDebuggingInfo(ce);
            Log.e(TAG, "Opening camera device and/or MediaRecorder : " + ce.getMessage());
        }

    }

    public void updateVideoFeedParams() {
        if(videoFeed == null)
            return;
        Camera.Parameters videoParams = videoFeed.getParameters();
        videoParams.set("cam_mode", 1);
        AVQualitySpec qspec = AVQualitySpec.stringToDefaultSpec(MainActivity.DEFAULT_RECORDING_QUALITY);
        videoParams.setPreviewSize(qspec.videoWidth, qspec.videoHeight);
        videoParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        videoParams.setAntibanding(Utils.parseVideoConstantString(MainActivity.videoOptsAntiband.getSelectedItem().toString()));
        videoParams.setColorEffect(Utils.parseVideoConstantString(MainActivity.videoOptsEffects.getSelectedItem().toString()));
        videoParams.setFlashMode(Utils.parseVideoConstantString(MainActivity.videoOptsCameraFlash.getSelectedItem().toString()));
        videoParams.setFocusMode(Utils.parseVideoConstantString(MainActivity.videoOptsFocus.getSelectedItem().toString()));
        videoParams.setSceneMode(Utils.parseVideoConstantString(MainActivity.videoOptsScene.getSelectedItem().toString()));
        videoParams.setWhiteBalance(Utils.parseVideoConstantString(MainActivity.videoOptsWhiteBalance.getSelectedItem().toString()));
        videoParams.setVideoStabilization(true);
        videoFeed.setParameters(videoParams);
        videoFeed.setDisplayOrientation(Integer.valueOf(MainActivity.videoOptsRotation.getSelectedItem().toString()));
    }

    public void setupMediaRecorder(int audioSrc, int videoSrc, int outputFormat, String recordQuality) {
        try{
            avFeed = new MediaRecorder();
            videoFeed.unlock();
            avFeed.setCamera(videoFeed);
            avFeed.setAudioSource(audioSrc);
            avFeed.setVideoSource(videoSrc);
            Log.i(TAG, "About to set specific AUD / AV settings ...");
            if(LOCAL_AVSETTING == AVSETTING_AUDIO_ONLY) {
                //avFeed.setInputSurface(MainActivity.videoPreview.getHolder().getSurface());
                CamcorderProfile vprof;
                if(DEFAULT_AVQUALSPEC_ID.length() >= 10 && !DEFAULT_AVQUALSPEC_ID.substring(0, 9).equals("TIME_LAPSE"))
                     vprof = AVQualitySpec.stringToCamcorderSpec("TIME_LAPSE_" + DEFAULT_AVQUALSPEC_ID);
                else
                     vprof = AVQualitySpec.stringToCamcorderSpec(DEFAULT_AVQUALSPEC_ID);
                vprof.fileFormat = outputFormat;
                Log.i(TAG, "AUD Recorder Profile: " + vprof.toString());
                avFeed.setProfile(vprof);
                avFeed.setCaptureRate(0.1); // one frame per 10 seconds
            }
            else {
                CamcorderProfile cprof = AVQualitySpec.stringToCamcorderSpec(DEFAULT_AVQUALSPEC_ID);
                cprof.fileFormat = outputFormat;
                Log.i(TAG, "AV Recorder Profile: " + cprof.toString());
                avFeed.setProfile(cprof);
                avFeed.setVideoEncodingProfileLevel(MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline, MediaCodecInfo.CodecProfileLevel.AVCLevel1);
                avFeed.setOrientationHint(Integer.valueOf(MainActivity.videoOptsRotation.getSelectedItem().toString()));
            }
            loggingFile = generateNewOutputFilePath();
            avFeed.setOutputFile(loggingFile);
            setLocationAttributes();
            setupAVFeedErrorHandling();
            avFeed.setPreviewDisplay(MainActivity.videoPreview.getHolder().getSurface());
            avFeedPreviewOn = true;
            avFeed.prepare();
            avFeed.start();
            RuntimeStats.init(loggingFilePath);
            inErrorState = false;
            isRecording = true;
        } catch (Exception ce) {
            inErrorState = true;
            Utils.printDebuggingInfo(ce);
            Log.e(TAG, "Setting parameters on the MediaRecorder : " + ce.getMessage());
        }
    }

    public void setupAVFeedErrorHandling() {
        avFeed.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                if(what == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
                    LAST_ERROR_MESSAGE = "UNKNOWN";
                }
                else if(what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                    LAST_ERROR_MESSAGE = "SERV DIED";
                }
                if(extra == MediaPlayer.MEDIA_ERROR_IO) {
                    LAST_ERROR_MESSAGE += " / IO";
                }
                else if(extra == MediaPlayer.MEDIA_ERROR_MALFORMED) {
                    LAST_ERROR_MESSAGE += " / MALFORMED";
                }
                else if(extra == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
                    LAST_ERROR_MESSAGE += " / INV PROG PLAYBACK";
                }
                else if(extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED) {
                    LAST_ERROR_MESSAGE += " / UNSUPPORTED";
                }
                else if(extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
                    LAST_ERROR_MESSAGE += " / TIMED OUT";
                }
                Log.e(TAG, LAST_ERROR_MESSAGE);
                AVRecordingService.inErrorState = true;
            }
        });
        avFeed.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                switch(what) {
                    case MediaPlayer.MEDIA_INFO_AUDIO_NOT_PLAYING:
                        LAST_ERROR_MESSAGE = "INFO : AUDIO NOT PLAYING";
                        break;
                    case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                        LAST_ERROR_MESSAGE = "INFO : BAD INTERLEAVING";
                        break;
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        LAST_ERROR_MESSAGE = "INFO : BUFFER END";
                        break;
                    case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                        LAST_ERROR_MESSAGE = "INFO : BUFFER START";
                        break;
                    case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                        LAST_ERROR_MESSAGE = "INFO : METADATA UPDATE";
                        break;
                    case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                        LAST_ERROR_MESSAGE = "INFO : NOT SEEKABLE";
                        break;
                    case MediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:
                        LAST_ERROR_MESSAGE = "INFO : SUBTITLE TIMEOUT";
                        break;
                    case MediaPlayer.MEDIA_INFO_UNKNOWN:
                        LAST_ERROR_MESSAGE = "INFO : UNK STATUS";
                        break;
                    case MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:
                        LAST_ERROR_MESSAGE = "INFO : UNSUPP SUBTITLE";
                        break;
                    case MediaPlayer.MEDIA_INFO_VIDEO_NOT_PLAYING:
                        LAST_ERROR_MESSAGE = "INFO : VIDEO NOT PLAYING";
                        break;
                    case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                        LAST_ERROR_MESSAGE = "INFO : VIDEO TRACK LAGGING";
                        break;
                    case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                        LAST_ERROR_MESSAGE = "INFO : VIDEO RENDER START";
                        break;
                    default:
                        LAST_ERROR_MESSAGE = "INFO : UNCAUGHT MSG";
                        break;
                }
                Log.i(TAG, LAST_ERROR_MESSAGE);
            }
        });
    }

    public void setLocationAttributes() {
        LocationManager locationMan = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String bestProvider = locationMan.getBestProvider(criteria, false);
        try {
            Location gpsLocation = locationMan.getLastKnownLocation(bestProvider);
            avFeed.setLocation((float) gpsLocation.getLatitude(), (float) gpsLocation.getLongitude());
        } catch (NullPointerException npe) {
            LAST_ERROR_MESSAGE = "INFO : NO GPS LOC AVAILABLE";
        } catch(SecurityException se) {
            LAST_ERROR_MESSAGE = "INFO : NO GPS LOC AVAILABLE";
        }
    }

    @Override
    public void onDestroy() {
        releaseMediaRecorder();
        releaseCamera();
        localService = null;
        MainActivity.videoPreviewBGOverlay.setAlpha(255);
        if(MainActivity.setDoNotDisturb) {
            NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notifyManager.setInterruptionFilter(dndInterruptionPolicy);
        }
        RuntimeStats.clear();
        RuntimeStats.updateStatsUI(false);
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        MainActivity.AVRECORD_SERVICE_RUNNING = false;
        Log.i(TAG, "onDestroy() called");
    }

    public void releaseMediaRecorder() {
        if(avFeed == null)
            return;
        Log.i(TAG, "Releasing MediaRecorder");
        if(isRecording) {
            try {
                avFeed.stop();
            } catch(IllegalStateException ise) { // cleanup by deleting the empty file:
                File emptyFile = new File(loggingFilePath);
                if(emptyFile != null)
                     emptyFile.delete();
            }
            isRecording = false;
        }
        avFeed.release();
        //avFeed.reset();
        if(loggingFile != null) {
            Log.d(TAG, "loggingFile Total Space: " + loggingFile.getTotalSpace() / 1024 + " MB");
        }
        videoFeed.lock();
    }

    public void releaseCamera() {
        if(videoFeed == null)
            return;
        Log.i(TAG, "Releasing Camera");
        videoFeed.stopPreview();
        videoFeed.release();
    }

    public void previewOn() {
        if(videoFeed != null && !videoFeedPreviewOn) {
            try {
                videoFeed.lock();
                videoFeed.reconnect();
                videoFeed.setPreviewDisplay(MainActivity.videoPreview.getHolder());
                videoFeed.startPreview();
                if(isPaused())
                    resumeRecording();
                videoFeedPreviewOn = true;
            } catch(IOException ioe) {
                Log.e(TAG, "Error in camera preview: " + ioe.getMessage());
                inErrorState = true;
            }
        }
        if(avFeed != null && !avFeedPreviewOn) {
            avFeed.setPreviewDisplay(MainActivity.videoPreview.getHolder().getSurface());
            avFeedPreviewOn = true;
        }
    }

    public void previewOff() {
        avFeedPreviewOn = false;
        if(videoFeed != null && videoFeedPreviewOn) {
            videoFeed.stopPreview();
            pauseRecording();
            // unlock camera here?
            videoFeed.unlock();
            videoFeedPreviewOn = false;
        }
    }

    private static final int AVSERVICE_PROCID = 97736153;

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, intent.getAction());
        String intentAction = intent.getAction();
        Log.i(TAG, "onHandleIntent: action = " + intentAction);
        if(intentAction != null && intentAction.equals(MainActivity.RECORD_VIDEO)) {
            startForeground(AVSERVICE_PROCID, getForegroundServiceNotify());
            recordVideoNow(DEFAULT_AVQUALSPEC_ID);
        }
        else if(intentAction != null && intentAction.equals(MainActivity.RECORD_AUDIO)) {
            startForeground(AVSERVICE_PROCID, getForegroundServiceNotify());
            recordAudioOnlyNow(DEFAULT_AVQUALSPEC_ID);
        }
    }

    public Notification getForegroundServiceNotify() {
        Intent intent = new Intent(this, AVRecordingService.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder fgNotify = new NotificationCompat.Builder(this, NCHANNELID_TASK);
        fgNotify.setOngoing(true);
        fgNotify.setContentTitle("Home Brew Live Streamer")
                .setContentText("We are currently recording / streaming audio video media now.")
                .setSmallIcon(R.drawable.splogo)
                .setWhen(System.currentTimeMillis())
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent);
        return fgNotify.build();
    }

    public static File generateNewOutputFilePath() {
        String extStoragePathPrefix = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        String outputFilePath = String.format("%s-%s.mp4", AVOUTPUT_FILE_PREFIX, Utils.getTimestamp());
        File outputFile;
        try {
            File outputDir = new File(extStoragePathPrefix, AVSAVE_SUBFOLDER);
            Log.i(TAG, outputDir.getAbsolutePath());
            if(!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IOException("Unable to create directories for writing.");
            }
            outputFile = new File(outputDir.getPath() + File.separator + outputFilePath);
            Log.i(TAG, "OUTPUT FILE: " + outputFile.getAbsolutePath());
            outputFile.createNewFile();
            outputFile.setReadable(true, false);
            outputFilePath = outputFile.getAbsolutePath();
        }
        catch(IOException ioe) {
            inErrorState = true;
            Log.e(TAG, "Unable to create file in path for writing: " + ioe.getMessage(), ioe);
            return null;
        }
        loggingFilePath = outputFilePath;
        LAST_RECORDING_FILEPATH = loggingFilePath;
        return outputFile;
    }

    public static boolean isRecording() {
        return isRecording;
    }

    public static boolean isRecordingAudioOnly() {
        return LOCAL_AVSETTING == AVSETTING_AUDIO_ONLY;
    }

    public static boolean getErrorState() {
        return inErrorState;
    }

    public void pauseRecording() {
        avFeed.pause();
        isPaused = true;
    }

    public void resumeRecording() {
        if(isPaused()) {
            avFeed.resume();
            isPaused = false;
        }
        else {
            Log.e(TAG, "Trying to resume an unpaused recording!");
        }
    }

    public static boolean isPaused() {
        return isPaused;
    }

    public void pause() {
        releaseMediaRecorder();
        isRecording = false;
        videoFeed.lock();
    }

    public boolean toggleAudioVideo(String recordingQuality) {
        Log.i(TAG, "Insider of toggleAV");
        if(!isRecording())
            return false;
        else if(isRecordingAudioOnly()) {
            pause();
            return recordVideoNow(recordingQuality);
        }
        else {
            pause();
            return recordAudioOnlyNow(recordingQuality);
        }
    }

    public boolean recordAudioOnlyNow(String recordingQuality) {
        LOCAL_AVSETTING = AVSETTING_AUDIO_ONLY;
        inErrorState = false;
        setupMediaRecorder(MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.VideoSource.SURFACE, MediaRecorder.OutputFormat.AAC_ADTS, recordingQuality);
        isRecording = true;
        isRecordingAudioOnly = true;
        isPaused = false;
        Log.i(TAG, "Now recording audio ONLY to \"" + loggingFilePath + "\" ...");
        return true;
    }

    public boolean recordVideoNow(String recordingQuality) {
        LOCAL_AVSETTING = AVSETTING_AUDIO_VIDEO;
        inErrorState = false;
        setupMediaRecorder(MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.VideoSource.CAMERA, MediaRecorder.OutputFormat.MPEG_4, recordingQuality);
        isRecording = true;
        isRecordingAudioOnly = false;
        isPaused = false;
        Log.i(TAG, "Now recording A/V data to \"" + loggingFilePath + "\" ...");
        return true;
    }

}
