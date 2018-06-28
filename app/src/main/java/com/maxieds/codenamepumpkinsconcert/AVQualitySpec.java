package com.maxieds.codenamepumpkinsconcert;

import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaCodec;
import android.net.rtp.AudioCodec;

import static android.media.CamcorderProfile.QUALITY_1080P;
import static android.media.CamcorderProfile.QUALITY_480P;
import static android.media.CamcorderProfile.QUALITY_LOW;

public class AVQualitySpec {

    public int videoWidth, videoHeight;
    public int videoFPS, videoBitRate;
    public int videoCodec;
    public int audioCodec;
    public int audioChannels, audioBitRate;

    public AVQualitySpec(int vw, int vh, int vfps, int vbitrate, int vcodec, int acodec, int achannels, int abitrate) {
        videoWidth = vw;
        videoHeight = vh;
        videoFPS = vfps;
        videoBitRate = vbitrate;
        videoCodec = vcodec;
        audioCodec = acodec;
        audioChannels = achannels;
        audioBitRate = abitrate;
    }

    public static AVQualitySpec fromCamcorderProfile(CamcorderProfile cprof) {
        AVQualitySpec qspec = new AVQualitySpec(cprof.videoFrameWidth, cprof.videoFrameHeight, cprof.videoFrameRate, cprof.videoCodec,
                                                cprof.audioBitRate, cprof.audioCodec, cprof.audioChannels, cprof.audioBitRate);
        return qspec;
    }

    /**
     * @ref https://developer.android.com/guide/topics/media/media-formats#video-decoding
     */
    public static final AVQualitySpec AVQUALSPEC_SDLOW = new AVQualitySpec(CamcorderProfile.get(QUALITY_LOW).videoFrameWidth,
                                                                           CamcorderProfile.get(QUALITY_LOW).videoFrameHeight,
                                                                           CamcorderProfile.get(QUALITY_LOW).videoFrameRate,
                                                                           CamcorderProfile.get(QUALITY_LOW).videoBitRate,
                                                                           CamcorderProfile.get(QUALITY_LOW).videoCodec,
                                                                           CamcorderProfile.get(QUALITY_LOW).audioCodec,
                                                                           CamcorderProfile.get(QUALITY_LOW).audioChannels,
                                                                           CamcorderProfile.get(QUALITY_LOW).audioBitRate);
    public static final AVQualitySpec AVQUALSPEC_SDMEDIUM = new AVQualitySpec(CamcorderProfile.get(QUALITY_480P).videoFrameWidth,
                                                                              CamcorderProfile.get(QUALITY_480P).videoFrameHeight,
                                                                              CamcorderProfile.get(QUALITY_480P).videoFrameRate,
                                                                              CamcorderProfile.get(QUALITY_480P).videoBitRate,
                                                                              CamcorderProfile.get(QUALITY_480P).videoCodec,
                                                                              CamcorderProfile.get(QUALITY_480P).audioCodec,
                                                                              CamcorderProfile.get(QUALITY_480P).audioChannels,
                                                                              CamcorderProfile.get(QUALITY_480P).audioBitRate);
    public static final AVQualitySpec AVQUALSPEC_HDHIGH = new AVQualitySpec(CamcorderProfile.get(QUALITY_1080P).videoFrameWidth,
                                                                            CamcorderProfile.get(QUALITY_1080P).videoFrameHeight,
                                                                            CamcorderProfile.get(QUALITY_1080P).videoFrameRate,
                                                                            CamcorderProfile.get(QUALITY_1080P).videoBitRate,
                                                                            CamcorderProfile.get(QUALITY_1080P).videoCodec,
                                                                            CamcorderProfile.get(QUALITY_1080P).audioCodec,
                                                                            CamcorderProfile.get(QUALITY_1080P).audioChannels,
                                                                            CamcorderProfile.get(QUALITY_1080P).audioBitRate);

    public static AVQualitySpec stringToDefaultSpec(String qspecStr) {
        if(qspecStr.equals("SDLOW"))
            return AVQUALSPEC_SDLOW;
        else if(qspecStr.equals("SDMEDIUM"))
            return AVQUALSPEC_SDMEDIUM;
        else if(qspecStr.equals("HDHIGH"))
            return AVQUALSPEC_HDHIGH;
        else
            return AVQualitySpec.fromCamcorderProfile(Utils.getCamcorderProfile(qspecStr));
    }

    public static CamcorderProfile stringToCamcorderSpec(String qspecStr) {
        if(qspecStr.equals("SDLOW"))
            return CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
        else if(qspecStr.equals("SDMEDIUM"))
            return CamcorderProfile.get(QUALITY_480P);
        else if(qspecStr.equals("HDHIGH"))
            return CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        else
            return Utils.getCamcorderProfile(qspecStr);
    }

}
