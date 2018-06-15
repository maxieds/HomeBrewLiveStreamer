package com.maxieds.codenamepumpkinsconcert;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.text.format.Time;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.FrameLayout;

import java.io.File;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.List;

public class AVRecordingService extends IntentService {

    private static final String TAG = AVRecordingService.class.getSimpleName();

    public static final int AVSETTING_AUDIO_ONLY = 0;
    public static final int AVSETTING_AUDIO_VIDEO = 1;
    public static int LOCAL_AVSETTING = AVSETTING_AUDIO_ONLY;

    private static final String AVSAVE_SUBFOLDER = "HomeBrewAVRecorder";
    public static String DEFAULT_AVQUALSPEC_ID = "SDMEDIUM";
    public static String AVOUTPUT_FILE_PREFIX = "pumpkins-atlanta";

    public static AVRecordingService localService = null;
    private static boolean isRecording = false;
    private static boolean isRecordingAudioOnly = true;
    private static File loggingFile;
    private static String loggingFilePath;
    private static boolean videoFeedPreviewOn = false;
    private static boolean avFeedPreviewOn = false;
    private int dndInterruptionPolicy;

    private Camera videoFeed = null;
    private MediaRecorder avFeed = null;

    public AVRecordingService() {
        super("AVRecordingService");
    }

    public static String parseConstantString(String androidConstStr) {
        switch(androidConstStr) {
            case "ANTIBANDING_50HZ":
                return "50hz";
            case "ANTIBANDING_60HZ":
                return "60hz";
            case "ANTIBANDING_AUTO":
                return "auto";
            case "ANTIBANDING_OFF":
                return "off";
            case "EFFECT_AQUA":
                return "aqua";
            case "EFFECT_BLACKBOARD":
                return "blackboard";
            case "EFFECT_MONO":
                return "mono";
            case "EFFECT_NEGATIVE":
                return "negative";
            case "EFFECT_NONE":
                return "none";
            case "EFFECT_POSTERIZE":
                return "posterize";
            case "EFFECT_SEPIA":
                return "sepia";
            case "EFFECT_SOLARIZE":
                return "solarize";
            case "EFFECT_WHITEBOARD":
                return "whiteboard";
            case "FLASH_MODE_AUTO":
                return "auto";
            case "FLASH_MODE_OFF":
                return "off";
            case "FLASH_MODE_ON":
                return "on";
            case "FLASH_MODE_RED_EYE":
                return "red-eye";
            case "FLASH_MODE_TORCH":
                return "torch";
            case "FOCUS_MODE_AUTO":
                return "auto";
            case "FOCUS_MODE_CONTINUOUS_PICTURE":
                return "continuous-picture";
            case "FOCUS_MODE_CONTINUOUS_VIDEO":
                return "continuous-video";
            case "FOCUS_MODE_EDOF":
                return "edof";
            case "FOCUS_MODE_FIXED":
                return "fixed";
            case "FOCUS_MODE_INFINITY":
                return "infinity";
            case "FOCUS_MODE_MACRO":
                return "macro";
            case "SCENE_MODE_ACTION":
                return "action";
            case "SCENE_MODE_AUTO":
                return "auto";
            case "SCENE_MODE_BARCODE":
                return "barcode";
            case "SCENE_MODE_BEACH":
                return "beach";
            case "SCENE_MODE_CANDLELIGHT":
                return "candlelight";
            case "SCENE_MODE_FIREWORKS":
                return "fireworks";
            case "SCENE_MODE_HDR":
                return "hdr";
            case "SCENE_MODE_LANDSCAPE":
                return "landscape";
            case "SCENE_MODE_NIGHT":
                return "night";
            case "SCENE_MODE_NIGHT_PORTRAIT":
                return "night-portrait";
            case "SCENE_MODE_PARTY":
                return "party";
            case "SCENE_MODE_PORTRAIT":
                return "portrait";
            case "SCENE_MODE_SNOW":
                return "snow";
            case "SCENE_MODE_SPORTS":
                return "sports";
            case "SCENE_MODE_STEADYPHOTO":
                return "steadyphoto";
            case "SCENE_MODE_SUNSET":
                return "sunset";
            case "SCENE_MODE_THEATRE":
                return "theatre";
            case "WHITE_BALANCE_AUTO":
                return "auto";
            case "WHITE_BALANCE_CLOUDY_DAYLIGHT":
                return "cloudy-daylight";
            case "WHITE_BALANCE_DAYLIGHT":
                return "daylight";
            case "WHITE_BALANCE_FLUORESCENT":
                return "fluorescent";
            case "WHITE_BALANCE_INCANDECSENT":
                return "incandescent";
            case "WHITE_BALANCE_SHADE":
                return "shade";
            case "WHITE_BALANCE_TWILIGHT":
                return "twilight";
            case "WHITE_BALANCE_WARM_FLUORESCENT":
                return "warm-fluorescent";
            default:
                break;
        }
        return "auto";
    }

    public static final String NCHANNELID_SERVICE = "com.maxieds.codenamepumpkinsconcert.AVRecordingService";
    public static final String NCHANNELID_TASK = "com.maxieds.codenamepumpkinsconcert.AVRecordingService.info";

    public void initNotifyChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Oreo 8.1 breaks startForground significantly
            NotificationManager notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notifyManager.createNotificationChannel(new NotificationChannel(NCHANNELID_SERVICE, "HomeBrewLiveStreamer A/V Recording Service", NotificationManager.IMPORTANCE_DEFAULT));
            notifyManager.createNotificationChannel(new NotificationChannel(NCHANNELID_TASK, "HomeBrewLiveStreamer Task Download Info", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Started AV recorder service in the background...");
        super.onCreate();
        initNotifyChannel();
        localService = this;
        MainActivity.videoPreviewBGOverlay.setAlpha(0);
        if(MainActivity.setDoNotDisturb) {
            NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            dndInterruptionPolicy = notifyManager.getCurrentInterruptionFilter();
            notifyManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        }
        // acquire camera + mic resources + external storage file path:
        try {
            videoFeed = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK); // open the back-facing camera
            updateVideoFeedParams();
            //MainActivity.videoCameraPreview = new CameraPreview(MainActivity.runningActivity, videoFeed, avFeed);
            videoFeed.setPreviewDisplay(MainActivity.videoPreview.getHolder());
            videoFeed.startPreview();
            videoFeedPreviewOn = true;
            /*List<Camera.Size> cameraResList = videoFeed.getParameters().getSupportedPreviewSizes();
            for(int r = 0; r < cameraResList.size(); r++)
                 Log.i(TAG, String.format("%d X %d", cameraResList.get(r).width, cameraResList.get(r).height));*/
        }
        catch(Exception ce) {
            printDebuggingInfo(ce);
            MainActivity.runningActivity.writeLoggingData("ERROR", "Opening camera device and/or MediaRecorder : " + ce.getMessage());
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
        videoParams.setAntibanding(parseConstantString(MainActivity.videoOptsAntiband.getSelectedItem().toString()));
        videoParams.setColorEffect(parseConstantString(MainActivity.videoOptsEffects.getSelectedItem().toString()));
        videoParams.setFlashMode(parseConstantString(MainActivity.videoOptsCameraFlash.getSelectedItem().toString()));
        videoParams.setFocusMode(parseConstantString(MainActivity.videoOptsFocus.getSelectedItem().toString()));
        videoParams.setSceneMode(parseConstantString(MainActivity.videoOptsScene.getSelectedItem().toString()));
        videoParams.setWhiteBalance(parseConstantString(MainActivity.videoOptsWhiteBalance.getSelectedItem().toString()));
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
            if(LOCAL_AVSETTING == AVSETTING_AUDIO_ONLY) {
                CamcorderProfile vprof = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_HIGH);
                CamcorderProfile aprof = AVQualitySpec.stringToCamcorderSpec(DEFAULT_AVQUALSPEC_ID);
                vprof.audioCodec = aprof.audioCodec;
                vprof.audioBitRate = aprof.audioBitRate;
                vprof.audioChannels = aprof.audioChannels;
                vprof.audioSampleRate = aprof.audioSampleRate;
                avFeed.setProfile(vprof);
                avFeed.setAudioEncoder(vprof.audioCodec);
                avFeed.setCaptureRate(0.1); // one frame per 10 seconds
            }
            else {
                AVQualitySpec qspec = AVQualitySpec.stringToDefaultSpec(MainActivity.DEFAULT_RECORDING_QUALITY);
                CamcorderProfile cprof = AVQualitySpec.stringToCamcorderSpec(DEFAULT_AVQUALSPEC_ID);
                cprof.videoFrameWidth = qspec.videoWidth;
                cprof.videoFrameHeight = qspec.videoHeight;
                avFeed.setProfile(cprof);
            }
            loggingFile = generateNewOutputFilePath();
            avFeed.setOutputFile(loggingFile);
            avFeed.setPreviewDisplay(MainActivity.videoPreview.getHolder().getSurface());
            avFeedPreviewOn = true;
            avFeed.prepare();
            avFeed.start();
            RuntimeStats.init(loggingFilePath);
        } catch (Exception ce) {
            printDebuggingInfo(ce);
            MainActivity.runningActivity.writeLoggingData("ERROR", "Setting parameters on the MediaRecorder : " + ce.getMessage());
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
            Log.d(TAG, "loggingFile Total Space: " + loggingFile.getTotalSpace());
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
                videoFeed.reconnect();
                videoFeed.setPreviewDisplay(MainActivity.videoPreview.getHolder());
                videoFeed.startPreview();
                videoFeedPreviewOn = true;
            } catch(IOException ioe) {
                Log.w(TAG, "Error in camera preview: " + ioe.getMessage());
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
            videoFeedPreviewOn = false;
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, intent.getAction());
        startForeground(6153, getForegroundServiceNotify());
        String intentAction = intent.getAction();
        if(intentAction != null && intentAction.equals("RECORD_VIDEO")) {
            recordVideoNow(MainActivity.DEFAULT_RECORDING_QUALITY);
        }
        else if(intentAction != null && intentAction.equals("RECORD_AUDIO_ONLY")) {
            recordAudioOnlyNow(MainActivity.DEFAULT_RECORDING_QUALITY);
        }

    }

    public Notification getForegroundServiceNotify() {
        Intent intent = new Intent(this, AVRecordingService.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder fgNotify = new NotificationCompat.Builder(this, NCHANNELID_TASK);
        fgNotify.setOngoing(true);
        fgNotify.setContentTitle("Home Brew Live Streamer")
                .setContentText("We are currently recording / streaming audio video media now.")
                .setSmallIcon(R.drawable.streaminglogo32)
                .setWhen(System.currentTimeMillis())
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent);
        return fgNotify.build();
    }

    /**
     * Returns a standard timestamp of the current Android device's time.
     * @return String timestamp (format: %Y-%m-%d-%T)
     */
    public static String getTimestamp() {
        Time currentTime = new Time();
        currentTime.setToNow();
        return currentTime.format("%Y-%m-%d-%T");
    }

    public static void printDebuggingInfo(Exception e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        String fullClassName = stackTrace[stackTrace.length - 1].getClassName();
        String className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
        String methodName = stackTrace[stackTrace.length - 1].getMethodName();
        int lineNumber = stackTrace[stackTrace.length - 1].getLineNumber();
        Log.e(TAG, String.format("in %s -- %s @ line #%d", className, methodName, lineNumber));
        Log.e(TAG, "STACKTRACE");
        Log.e(TAG, Log.getStackTraceString(e));
    }

    public static File generateNewOutputFilePath() {
        String extStoragePathPrefix = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        String outputFilePath = String.format("%s-%s.mp4", AVOUTPUT_FILE_PREFIX, getTimestamp());
        File outputFile;
        try {
            File outputDir = new File(extStoragePathPrefix, AVSAVE_SUBFOLDER);
            Log.i(TAG, outputDir.getAbsolutePath());
            if(!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IOException("Unable to create directories for writing.");
            }
            outputFile = new File(outputDir.getPath() + File.separator + outputFilePath);
            Log.i(TAG, outputFile.getAbsolutePath());
            outputFile.createNewFile();
            outputFile.setReadable(true, false);
            outputFilePath = outputFile.getAbsolutePath();
        }
        catch(IOException ioe) {
            Log.e(TAG, "Unable to create file in path for writing.", ioe);
            return null;
        }
        loggingFilePath = outputFilePath;
        return outputFile;
    }

    public static boolean isRecording() {
        return isRecording;
    }

    public static boolean isRecordingAudioOnly() {
        return isRecordingAudioOnly;
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
        Log.i(TAG, "Inside of RecordAudioOnlyNow()");
        LOCAL_AVSETTING = AVSETTING_AUDIO_ONLY;
        setupMediaRecorder(MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.VideoSource.DEFAULT, MediaRecorder.OutputFormat.AAC_ADTS, recordingQuality);
        isRecording = true;
        isRecordingAudioOnly = true;
        MainActivity.runningActivity.writeLoggingData("STATE", "Now recording audio ONLY to \"" + loggingFilePath + "\" ...");
        return true;
    }

    public boolean recordVideoNow(String recordingQuality) {
        Log.i(TAG, "Inside of RecordVideoNow()");
        LOCAL_AVSETTING = AVSETTING_AUDIO_VIDEO;
        setupMediaRecorder(MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.VideoSource.CAMERA, MediaRecorder.OutputFormat.MPEG_4, recordingQuality);
        isRecording = true;
        isRecordingAudioOnly = false;
        MainActivity.runningActivity.writeLoggingData("STATE", "Now recording A/V data to \"" + loggingFilePath + "\" ...");
        return true;
    }

}
