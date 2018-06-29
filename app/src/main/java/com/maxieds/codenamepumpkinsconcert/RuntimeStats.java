package com.maxieds.codenamepumpkinsconcert;

import android.app.ActivityManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.widget.TextView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

import static android.content.Context.ACTIVITY_SERVICE;

public class RuntimeStats {

     public static Time recordingStartTime;
     public static String loggingFilePath;
     public static long startBatteryCapacity = 0L;

     public static void init(String loggingPath) {
         recordingStartTime = new Time();
         recordingStartTime.setToNow();
         loggingFilePath = loggingPath;
         startBatteryCapacity = BatteryManager.BATTERY_PROPERTY_CAPACITY;
     }

     public static void clear() {
         recordingStartTime = null;
         loggingFilePath = null;
         startBatteryCapacity = 0L;
         updateStatsUI(false, true);
         updateStatsUI(false, false);
         statsUpdateHandler.removeCallbacks(statsUpdateRunnableForeground);
         statsUpdateHandler.removeCallbacks(statsUpdateRunnableBackground);
     }

     public static String getRecordingMode() {
         if(AVRecordingService.localService == null)
             return "OFF";
         else if(AVRecordingService.isRecordingAudioOnly())
             return "AUD";
         else
             return "A/V";
     }

     public static String getRecordingUptime() {
         if(recordingStartTime == null)
             return "00:00:00";
         Time currentTime = new Time();
         currentTime.setToNow();
         long diffms = currentTime.toMillis(true) - recordingStartTime.toMillis(true);
         //long diffms = AVRecordingService.localService.getMediaTimestamp();
         SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss");
         Date diffTime = new Date();
         diffTime.setTime(diffms);
         return String.format("%02d:%02d:%02d", diffms / (60 * 60 * 1000) % 24, diffms / (60 * 1000) % 60, diffms / 1000 % 60);
     }

     public static String getLoggingFilePath() {
         if(loggingFilePath == null)
             return "NONE";
         return loggingFilePath.replaceAll("\\/.*\\/", "$0");
     }

     public static String getSpaceUsedRemaining() {
         ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
         try {
             ActivityManager activityManager;
             if(MainActivity.runningActivity == null)
                 activityManager = (ActivityManager) AVRecordingService.localService.getSystemService(ACTIVITY_SERVICE);
             else
                 activityManager = (ActivityManager) MainActivity.runningActivity.getSystemService(ACTIVITY_SERVICE);
             activityManager.getMemoryInfo(memInfo);
         } catch(NullPointerException npe) {}
         long memoryAvailableMB = memInfo.availMem / 1048576L;
         StatFs statfs = new StatFs(Environment.getExternalStorageDirectory().getPath());
         long bytesAvailable = (long) statfs.getBlockSize() * (long) statfs.getBlockCount();
         long diskAvailableMB = bytesAvailable / 1048576L;
         return String.format("%d MB / %d MB", memoryAvailableMB, diskAvailableMB);
     }

     public static String getBatteryPercentUsed() {
         if(startBatteryCapacity == 0L)
             return "0 %";
         long currentCap = BatteryManager.BATTERY_PROPERTY_CAPACITY;
         return String.format("%02.1g %%", (startBatteryCapacity - currentCap) / 100.0);
     }

     public static String getVideoRecordingSummary() {
         AVQualitySpec qspec = AVQualitySpec.stringToDefaultSpec(AVRecordingService.DEFAULT_AVQUALSPEC_ID);
         return String.format("V: %dx%d @ %d fps / %d Kbps", qspec.videoWidth, qspec.videoHeight, qspec.videoFPS, qspec.videoBitRate);
     }

    public static String getAudioRecordingSummary() {
        AVQualitySpec qspec = AVQualitySpec.stringToDefaultSpec(AVRecordingService.DEFAULT_AVQUALSPEC_ID);
        return String.format("A: %s @ %d Kbps", qspec.audioChannels == 1 ? "MONO" : "STEREO", qspec.audioBitRate);
    }

    public static final int RECORD_MODE_STATUS = 0;
    public static final int DURATION_STATUS = 1;
    public static final int FILE_OUTPUT_STATUS = 2;
    public static final int RUNTIME_ALERT_STATUS = 3;
    public static final int MEMDISK_STATUS = 4;
    public static final int BATTERY_STATUS = 5;
    public static final int VIDEO_STATUS = 6;
    public static final int AUDIO_STATUS = 7;
    public static final int DISK_USAGE_STATUS = 8;

    public static String[] getBannerStrings() {
        String recordMode = String.format("%s: %s", "Record Mode", getRecordingMode());
        String duration = String.format("%s: %s", "Record Duration", getRecordingUptime());
        String fileOutput = String.format("%s: %s", "File", getLoggingFilePath());
        String appStatusString = "";
        if(AVRecordingService.getErrorState())
            appStatusString = AVRecordingService.LAST_ERROR_MESSAGE;
        else if(AVRecordingService.isPaused())
            appStatusString = "PAUSED / ";
        else if(AVRecordingService.isRecording())
            appStatusString = "REC / ";
        appStatusString += AVRecordingService.LAST_ERROR_MESSAGE;
        String runtimeAlertStatus = String.format("%s: %s", AVRecordingService.getErrorState() ? "Error" : "Status", appStatusString);
        String memDisk = String.format("%s: %s", "Mem / Disk", getSpaceUsedRemaining());
        String battery = String.format("%s: %s", "Battery Used", getBatteryPercentUsed());
        String video = String.format("%s %s", "", getVideoRecordingSummary());
        String audio = String.format("%s %s", "", getAudioRecordingSummary());
        String currentDiskUsage = String.format("%s: %.2g MB", "Recording Space Used", AVRecordingService.getCurrentDiskUsage());
        return new String[] {recordMode, duration, fileOutput, runtimeAlertStatus, memDisk, battery, video, audio, currentDiskUsage};
    }

    public static final int FOREGROUND_STATS_UPDATE_INTERVAL = 1000; // 1 second
    public static final int BACKGROUND_STATS_UPDATE_INTERVAL = 15000; // 15 seconds
    public static Handler statsUpdateHandler = new Handler();
    public static Runnable statsUpdateRunnableForeground = new Runnable() {
        public void run() {
            updateStatsUI(true, true);
        }
    };
    public static Runnable statsUpdateRunnableBackground = new Runnable() {
        public void run() {
            updateStatsUI(true, false);
        }
    };
    public static boolean UPDATING_LIVE_STATS = false;

    public static void updateStatsUI(boolean resetTimer, boolean foregroundMode) {

        String[] bannerStats = getBannerStrings();
        if(foregroundMode && MainActivity.runningActivity != null) {
            ((TextView) MainActivity.runningActivity.findViewById(R.id.statsRecordMode)).setText(bannerStats[RECORD_MODE_STATUS]);
            ((TextView) MainActivity.runningActivity.findViewById(R.id.statsDuration)).setText(bannerStats[DURATION_STATUS]);
            ((TextView) MainActivity.runningActivity.findViewById(R.id.statsFileOutput)).setText(bannerStats[FILE_OUTPUT_STATUS]);
            ((TextView) MainActivity.runningActivity.findViewById(R.id.statsAlert)).setText(bannerStats[RUNTIME_ALERT_STATUS]);
            ((TextView) MainActivity.runningActivity.findViewById(R.id.statsMemDisk)).setText(bannerStats[MEMDISK_STATUS]);
            ((TextView) MainActivity.runningActivity.findViewById(R.id.statsBattery)).setText(bannerStats[BATTERY_STATUS]);
            ((TextView) MainActivity.runningActivity.findViewById(R.id.statsVideo)).setText(bannerStats[VIDEO_STATUS]);
            ((TextView) MainActivity.runningActivity.findViewById(R.id.statsAudio)).setText(bannerStats[AUDIO_STATUS]);
        }
        else if(!foregroundMode && AVRecordingService.localService != null) {
            String notificationBanner = String.format("%s\n%s\n%s\n%s\n%s", bannerStats[AUDIO_STATUS], bannerStats[VIDEO_STATUS],
                                                      bannerStats[DISK_USAGE_STATUS], bannerStats[DURATION_STATUS], bannerStats[RUNTIME_ALERT_STATUS]);
            AVRecordingService.localService.updateNotificationBanner(notificationBanner);
        }

        if(resetTimer && foregroundMode) {
            statsUpdateHandler.postDelayed(statsUpdateRunnableForeground, FOREGROUND_STATS_UPDATE_INTERVAL);
            UPDATING_LIVE_STATS = true;
        }
        else if(resetTimer) {
            statsUpdateHandler.postDelayed(statsUpdateRunnableForeground, BACKGROUND_STATS_UPDATE_INTERVAL);
            UPDATING_LIVE_STATS = true;
        }
        else {
            UPDATING_LIVE_STATS = false;
        }

    }

}
