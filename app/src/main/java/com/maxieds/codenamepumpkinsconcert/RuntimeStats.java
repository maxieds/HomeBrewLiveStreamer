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
         updateStatsUI(false);
         statsUpdateHandler.removeCallbacks(statsUpdateRunnable);
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
         ActivityManager activityManager = (ActivityManager) MainActivity.runningActivity.getSystemService(ACTIVITY_SERVICE);
         activityManager.getMemoryInfo(memInfo);
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

    public static final int STATS_UPDATE_INTERVAL = 1000; // 8 seconds
    public static Handler statsUpdateHandler = new Handler();
    public static Runnable statsUpdateRunnable = new Runnable() {
        public void run() {
            updateStatsUI(true);
        }
    };

    public static void updateStatsUI(boolean resetTimer) {

         String recordMode = String.format("%s: %s", "Record Mode", getRecordingMode());
         String duration = String.format("%s: %s", "Record Duration", getRecordingUptime());
         String fileOutput = String.format("%s: %s", "File", getLoggingFilePath());
         String memDisk = String.format("%s: %s", "Mem / Disk", getSpaceUsedRemaining());
         String battery = String.format("%s: %s", "Battery Used", getBatteryPercentUsed());
         String video = String.format("%s %s", "", getVideoRecordingSummary());
         String audio = String.format("%s %s", "", getAudioRecordingSummary());

        ((TextView) MainActivity.runningActivity.findViewById(R.id.statsRecordMode)).setText(recordMode);
        ((TextView) MainActivity.runningActivity.findViewById(R.id.statsDuration)).setText(duration);
        ((TextView) MainActivity.runningActivity.findViewById(R.id.statsFileOutput)).setText(fileOutput);
        ((TextView) MainActivity.runningActivity.findViewById(R.id.statsMemDisk)).setText(memDisk);
        ((TextView) MainActivity.runningActivity.findViewById(R.id.statsBattery)).setText(battery);
        ((TextView) MainActivity.runningActivity.findViewById(R.id.statsVideo)).setText(video);
        ((TextView) MainActivity.runningActivity.findViewById(R.id.statsAudio)).setText(audio);

        if(resetTimer) {
            statsUpdateHandler.postDelayed(statsUpdateRunnable, STATS_UPDATE_INTERVAL);
        }

    }

}
