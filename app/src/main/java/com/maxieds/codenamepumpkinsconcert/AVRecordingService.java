package com.maxieds.codenamepumpkinsconcert;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Spinner;

import java.io.File;
import java.io.IOException;

public class AVRecordingService extends IntentService {

    private static final String TAG = AVRecordingService.class.getSimpleName();

    public static final int AVSETTING_AUDIO_ONLY = 0;
    public static final int AVSETTING_AUDIO_VIDEO = 1;
    public static int LOCAL_AVSETTING = AVSETTING_AUDIO_ONLY;
    public static final boolean USE_VIDEO_PREVIEW = true;

    private static final String AVSAVE_SUBFOLDER = "HomeBrewAVRecorder";
    public static String AVOUTPUT_FILE_PREFIX = "pumpkins-atlanta";
    public static String DEFAULT_AVQUALSPEC_ID = MainActivity.DEFAULT_RECORDING_QUALITY;
    public static final long RECORDING_SLICE_MAXBYTES = 1073741824L; // 1GB = 1024MB

    public static AVRecordingService localService = null;
    private static boolean isRecording = false;
    private static File loggingFile, nextLoggingFile;
    private static String loggingFilePath;
    private static boolean videoFeedPreviewOn = false;
    private static boolean avFeedPreviewOn = false;
    private static boolean inErrorState = false;
    private static boolean isPaused = false;
    public static String LAST_ERROR_MESSAGE = "";
    public static String LAST_RECORDING_FILEPATH = null;
    public static String recordingSliceFormat;
    public static int currentRecordingSlice;
    public static int dndInterruptionPolicy;

    private Camera videoFeed = null;
    private MediaRecorder avFeed = null;

    public static SurfaceView videoPreview;
    public static Surface persistentVideoSurface;
    public static SurfaceHolder videoPreviewHolder;
    public static Drawable videoPreviewBGOverlay;
    public static Spinner videoOptsAntiband, videoOptsEffects, videoOptsCameraFlash;
    public static Spinner videoOptsFocus, videoOptsScene, videoOptsWhiteBalance, videoOptsRotation;
    public static Spinner videoOptsQuality, videoPlaybackOptsContentType, audioPlaybackOptsEffectType;
    public static PowerManager.WakeLock bgWakeLock;

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
        if(videoOptsQuality != null) {
            DEFAULT_AVQUALSPEC_ID = videoOptsQuality.getSelectedItem().toString();
        }
        if(videoPreviewBGOverlay != null) {
            videoPreviewBGOverlay.setAlpha(0);
        }
        try {
            videoPreviewHolder = videoPreview.getHolder();
            videoPreviewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        } catch(NullPointerException npe) {}
        if(MainActivity.setDoNotDisturb) {
            NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            dndInterruptionPolicy = notifyManager.getCurrentInterruptionFilter();
            notifyManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        }
        if(bgWakeLock != null && !bgWakeLock.isHeld()) {
            bgWakeLock.acquire();
        }
        recordingSliceFormat = getRecordingSliceFormatString();
        currentRecordingSlice = 0;
        // acquire camera + mic resources + external storage file path:
        try {
            videoFeed = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK); // open the back-facing camera
            updateVideoFeedParams();
            if(USE_VIDEO_PREVIEW) {
                videoFeed.setPreviewDisplay(videoPreviewHolder);
                videoFeed.startPreview();
                videoFeedPreviewOn = true;
            }
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
        if(USE_VIDEO_PREVIEW) {
            videoParams.setPreviewSize(qspec.videoWidth, qspec.videoHeight);
        }
        videoParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        try {
            videoParams.setAntibanding(Utils.parseVideoConstantString(videoOptsAntiband.getSelectedItem().toString()));
            videoParams.setColorEffect(Utils.parseVideoConstantString(videoOptsEffects.getSelectedItem().toString()));
            videoParams.setFlashMode(Utils.parseVideoConstantString(videoOptsCameraFlash.getSelectedItem().toString()));
            videoParams.setFocusMode(Utils.parseVideoConstantString(videoOptsFocus.getSelectedItem().toString()));
            videoParams.setSceneMode(Utils.parseVideoConstantString(videoOptsScene.getSelectedItem().toString()));
            videoParams.setWhiteBalance(Utils.parseVideoConstantString(videoOptsWhiteBalance.getSelectedItem().toString()));
        } catch(NullPointerException npe) {}
        videoParams.setVideoStabilization(true);
        videoParams.setRecordingHint(true);
        videoFeed.setParameters(videoParams);
        try {
            videoFeed.setDisplayOrientation(Integer.valueOf(videoOptsRotation.getSelectedItem().toString()));
        } catch(NullPointerException npe) {}
    }

    public void setupMediaRecorder(int audioSrc, int videoSrc, int outputFormat, String recordQuality) {
        try{
            avFeed = new MediaRecorder();
            videoFeed.unlock();
            if(LOCAL_AVSETTING == AVSETTING_AUDIO_ONLY) {
                //try {
                //    persistentVideoSurface = MediaCodec.createPersistentInputSurface();
                //    avFeed.setInputSurface(persistentVideoSurface);
                //} catch(NullPointerException npe) {}
                //avFeed.setVideoSource(videoSrc);
                avFeed.setAudioSource(audioSrc);
                CamcorderProfile aprof = AVQualitySpec.stringToCamcorderSpec(DEFAULT_AVQUALSPEC_ID);
                avFeed.setOutputFormat(outputFormat);
                avFeed.setAudioEncodingBitRate(aprof.audioBitRate);
                avFeed.setAudioChannels(aprof.audioChannels);
                avFeed.setAudioSamplingRate(aprof.audioSampleRate);
                avFeed.setAudioEncoder(aprof.audioCodec);
            }
            else {
                try {
                    if(USE_VIDEO_PREVIEW) {
                        avFeed.setPreviewDisplay(videoPreviewHolder.getSurface());
                        avFeedPreviewOn = true;
                    }
                } catch(NullPointerException npe) {}
                avFeed.setCamera(videoFeed);
                avFeed.setAudioSource(audioSrc);
                avFeed.setVideoSource(videoSrc);
                CamcorderProfile cprof = AVQualitySpec.stringToCamcorderSpec(DEFAULT_AVQUALSPEC_ID);
                cprof.fileFormat = outputFormat;
                avFeed.setProfile(cprof);
                avFeed.setVideoEncodingProfileLevel(MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline, MediaCodecInfo.CodecProfileLevel.AVCLevel1);
                try {
                    avFeed.setOrientationHint(Integer.valueOf(videoOptsRotation.getSelectedItem().toString()));
                } catch(NullPointerException npe) {}
            }
            loggingFile = generateNewOutputFilePath();
            LAST_RECORDING_FILEPATH = loggingFile.getAbsolutePath();
            avFeed.setOutputFile(loggingFile);
            avFeed.setMaxFileSize(RECORDING_SLICE_MAXBYTES);
            setLocationAttributes();
            setupAVFeedErrorHandling();
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
                    //case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                    //    LAST_ERROR_MESSAGE = "INFO : NOT SEEKABLE";
                    //    break;
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
                    case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                        LAST_ERROR_MESSAGE = "INFO : MAX FILE SIZE REACHED";
                        performOutputFileSwap();
                        break;
                    case MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED:
                        LAST_ERROR_MESSAGE = "INFO : NEXT SLICE STARTED";
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
        super.onDestroy();
        if(LOCAL_AVSETTING == AVSETTING_AUDIO_ONLY && persistentVideoSurface != null) {
            persistentVideoSurface.release();
        }
        releaseMediaRecorder();
        releaseCamera();
        if(videoPreviewBGOverlay != null) {
            videoPreviewBGOverlay.setAlpha(255);
        }
        if(MainActivity.setDoNotDisturb) {
            NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notifyManager.setInterruptionFilter(dndInterruptionPolicy);
        }
        RuntimeStats.clear();
        RuntimeStats.updateStatsUI(false, true);
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        MainActivity.AVRECORD_SERVICE_RUNNING = false;
        localService = null;
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

    private static final int AVSERVICE_PROCID = 97736153;

    @Override
    protected void onHandleIntent(Intent intent) {
        if(intent == null) {
            Log.w(TAG, "onHandleIntent passed a NULL intent object.");
            return;
        }
        String intentAction = intent.getAction();
        if(intentAction != null && intentAction.equals(MainActivity.RECORD_VIDEO)) {
            startForeground(AVSERVICE_PROCID, getForegroundServiceNotify("We are currently recording audio / video media now."));
            RuntimeStats.updateStatsUI(true, false);
            recordVideoNow(DEFAULT_AVQUALSPEC_ID);
        }
        else if(intentAction != null && intentAction.equals(MainActivity.RECORD_AUDIO)) {
            startForeground(AVSERVICE_PROCID, getForegroundServiceNotify("We are currently recording audio only media now."));
            RuntimeStats.updateStatsUI(true, false);
            recordAudioOnlyNow(DEFAULT_AVQUALSPEC_ID);
        }
    }

    public Notification getForegroundServiceNotify(String bannerMsg) {
        Intent intent = new Intent(this, AVRecordingService.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder fgNotify = new NotificationCompat.Builder(this, NCHANNELID_TASK);
        fgNotify.setOngoing(true);
        fgNotify.setContentTitle("Home Brew Live Streamer")
                .setColor(0xAEEEEE)
                .setContentText(bannerMsg)
                .setOngoing(true)
                .setSmallIcon(R.drawable.drumset64)
                .setWhen(System.currentTimeMillis())
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent);
        return fgNotify.build();
    }

    public void updateNotificationBanner(String bannerMsg) {
        Notification nextNotifyBanner = getForegroundServiceNotify(bannerMsg);
        NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notifyManager.notify(AVSERVICE_PROCID, nextNotifyBanner);
    }

    public static String getRecordingSliceFormatString() {
        return String.format("%s-%s-slice%%d.mp4", AVOUTPUT_FILE_PREFIX, Utils.getTimestamp());
    }

    public static File generateNewOutputFilePath() {
        String extStoragePathPrefix = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        String outputFilePath = String.format(recordingSliceFormat, ++currentRecordingSlice);
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
        return outputFile;
    }

    public void performOutputFileSwap() {
        try {
            loggingFile = nextLoggingFile;
            LAST_RECORDING_FILEPATH = loggingFilePath = loggingFile.getAbsolutePath();
            nextLoggingFile = generateNewOutputFilePath();
            avFeed.setNextOutputFile(nextLoggingFile);
        } catch(IOException ioe) {
            Log.e(TAG, "Setting next (# " + currentRecordingSlice + ") recording slice : " + ioe.getMessage());
        }
    }

    public static double getCurrentDiskUsage() {
        if(localService == null || loggingFile == null)
            return 0.0;
        return loggingFile.getTotalSpace() / 1024.0; // in MB
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
        isPaused = false;
        Log.i(TAG, "Now recording audio ONLY to \"" + loggingFilePath + "\" ...");
        return true;
    }

    public boolean recordVideoNow(String recordingQuality) {
        LOCAL_AVSETTING = AVSETTING_AUDIO_VIDEO;
        inErrorState = false;
        setupMediaRecorder(MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.VideoSource.CAMERA, MediaRecorder.OutputFormat.MPEG_4, recordingQuality);
        isRecording = true;
        isPaused = false;
        Log.i(TAG, "Now recording A/V data to \"" + loggingFilePath + "\" ...");
        return true;
    }

}
