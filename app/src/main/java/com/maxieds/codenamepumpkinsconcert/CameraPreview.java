package com.maxieds.codenamepumpkinsconcert;

import android.content.Context;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import java.io.IOException;

/**
 * @ref https://developer.android.com/guide/topics/media/camera#camera-preview
 */

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = CameraPreview.class.getSimpleName();

    private SurfaceHolder surfaceHolder;
    private Camera cameraInstance;
    private MediaRecorder avFeed;
    public boolean bSurfaceCreated = false;

    public CameraPreview(Context context, Camera camera, MediaRecorder avFeedParam) {
        super(context);
        cameraInstance = camera;
        avFeed = avFeedParam;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setEnabled(true);
        setActivated(true);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surfaceCreated");
        bSurfaceCreated = true;
        try {
            cameraInstance.setPreviewDisplay(holder);
        } catch(IOException ioe) {
            Log.e(TAG, ioe.getMessage());
        }
        cameraInstance.startPreview();
        Log.i(TAG, "AV Feed started!");
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surfaceDestroyed()");
        bSurfaceCreated = false;
        AVRecordingService.localService.previewOff();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
    }
}