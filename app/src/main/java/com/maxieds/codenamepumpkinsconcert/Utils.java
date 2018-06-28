package com.maxieds.codenamepumpkinsconcert;

import android.media.AudioAttributes;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.text.format.Time;
import android.util.Log;

public class Utils {

    private static final String TAG = Utils.class.getSimpleName();

    public static String parseVideoConstantString(String androidConstStr) {
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

    public static CamcorderProfile getCamcorderProfile(String cprofStr) {
        switch(cprofStr) {
            case "CIF":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_CIF);
            case "LOW":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
            case "HIGH":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
            case "QCIF":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_QCIF);
            case "QVGA":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_QVGA);
            case "480P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            case "720P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
            case "1080P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
            case "2160P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_2160P);
            case "HIGH_SPEED_LOW":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH_SPEED_LOW);
            case "HIGH_SPEED_HIGH":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH_SPEED_HIGH);
            case "HIGH_SPEED_480P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH_SPEED_480P);
            case "HIGH_SPEED_720P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH_SPEED_720P);
            case "HIGH_SPEED_1080P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH_SPEED_1080P);
            case "HIGH_SPEED_2160P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH_SPEED_2160P);
            case "TIME_LAPSE_CIF":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_CIF);
            case "TIME_LAPSE_LOW":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_LOW);
            case "TIME_LAPSE_HIGH":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_HIGH);
            case "TIME_LAPSE_QCIF":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_QCIF);
            case "TIME_LAPSE_QVGA":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_QVGA);
            case "TIME_LAPSE_480P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_480P);
            case "TIME_LAPSE_720P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_720P);
            case "TIME_LAPSE_1080P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_1080P);
            case "TIME_LAPSE_2160P":
                return CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_2160P);
            default:
                return CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH_SPEED_HIGH);
        }
    }

    public static int getAVPlaybackContentType(String playbackStr) {
        switch(playbackStr) {
            case "CONTENT_TYPE_MUSIC":
                return AudioAttributes.CONTENT_TYPE_MUSIC;
            case "CONTENT_TYPE_MOVIE":
                return AudioAttributes.CONTENT_TYPE_MOVIE;
            case "CONTENT_TYPE_GAME":
                return AudioAttributes.CONTENT_TYPE_SONIFICATION;
            case "CONTENT_TYPE_VOICE":
                return AudioAttributes.CONTENT_TYPE_SPEECH;
            default:
                return AudioAttributes.CONTENT_TYPE_UNKNOWN;
        }
    }

    public static java.util.UUID getAVPlaybackAudioEffectType(String effectStr) {
        switch(effectStr) {
            case "AUTO_ECHO_CANCELLATION":
                return AudioEffect.EFFECT_TYPE_AEC;
            case "AUTO_GAIN_CONTROL":
                return AudioEffect.EFFECT_TYPE_AGC;
            case "BASS_BOOST":
                return AudioEffect.EFFECT_TYPE_BASS_BOOST;
            case "ENV_REVERB":
                return AudioEffect.EFFECT_TYPE_ENV_REVERB;
            case "EQUALIZER":
                return AudioEffect.EFFECT_TYPE_EQUALIZER;
            case "LOUDNESS_ENHANCER":
                return AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER;
            case "NOISE_SUPPRESSOR":
                return AudioEffect.EFFECT_TYPE_NS;
            case "PRESET_REVERB":
                return AudioEffect.EFFECT_TYPE_PRESET_REVERB;
            case "VIRTUALIZER":
                return AudioEffect.EFFECT_TYPE_VIRTUALIZER;
            default:
                return null;
        }
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


}
