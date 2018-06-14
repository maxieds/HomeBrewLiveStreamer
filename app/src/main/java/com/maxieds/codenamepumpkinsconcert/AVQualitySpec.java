package com.maxieds.codenamepumpkinsconcert;

import android.media.CamcorderProfile;
import android.net.rtp.AudioCodec;

public class AVQualitySpec {

    public int videoWidth, videoHeight;
    public int videoFPS, videoBitRate;
    public android.net.rtp.AudioCodec audioCodec;
    int audioChannels, audioBitRate;

    public AVQualitySpec(int vw, int vh, int vfps, int vbitrate, android.net.rtp.AudioCodec acodec, int achannels, int abitrate) {
        videoWidth = vw;
        videoHeight = vh;
        videoFPS = vfps;
        videoBitRate = vbitrate;
        audioCodec = acodec;
        audioChannels = achannels;
        audioBitRate = abitrate;
    }

    /**
     * @ref https://developer.android.com/guide/topics/media/media-formats#video-decoding
     */
    public static final AVQualitySpec AVQUALSPEC_SDLOW = new AVQualitySpec(320, 240, 12, 56, AudioCodec.AMR, 1, 24);
    public static final AVQualitySpec AVQUALSPEC_SDMEDIUM = new AVQualitySpec(480, 640, 30, 500, AudioCodec.AMR, 2, 128);
    public static final AVQualitySpec AVQUALSPEC_HDHIGH = new AVQualitySpec(1920, 1080, 30, 2000, AudioCodec.AMR, 2, 192);

    public static AVQualitySpec stringToDefaultSpec(String qspecStr) {
        if(qspecStr.equals("SDLOW"))
            return AVQUALSPEC_SDLOW;
        else if(qspecStr.equals("SDMEDIUM"))
            return AVQUALSPEC_SDMEDIUM;
        else if(qspecStr.equals("HDHIGH"))
            return AVQUALSPEC_HDHIGH;
        else
            return null;
    }

    public static CamcorderProfile stringToCamcorderSpec(String qspecStr) {
        if(qspecStr.equals("SDLOW"))
            return CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
        else if(qspecStr.equals("SDMEDIUM"))
            return CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        else if(qspecStr.equals("HDHIGH"))
            return CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        else
            return null;
    }

}
