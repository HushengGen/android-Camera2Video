package com.example.android.camera2video;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by wangt on 6/4/15.
 */
public class Camera2Wrapper {
    public static final String TAG = "Camera2Wrapper";

    private CameraDevice _Device;
    private CameraCaptureSession _Session;
    private String _CurrentCameraId;
    private Size _PreviewSize;
    private Size _VideoSize;
    private CaptureRequest.Builder _RequestBuilder;



    public void onCreate() {

    }

    public void onPause() {
        closeCamera();

    }

    public void onResume(Context context, int width, int height) {
        openCamera(context, width, height);
    }

    public void onDestroy() {

    }

    public void openCamera(Context context, int width, int height) {
        CameraManager manager = (CameraManager) context.getSystemService(Activity.CAMERA_SERVICE);
        try {
            _CurrentCameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(_CurrentCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            _VideoSize = chooseVideoSize(map.getOutputSizes(MediaCodec.class));
            _PreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, _VideoSize);
            int orientation = context.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                _TextureView.setAspectRatio(_PreviewSize.getWidth(), _PreviewSize.getHeight());
            } else {
                _TextureView.setAspectRatio(_PreviewSize.getHeight(), _PreviewSize.getWidth());
            }
            configureTransform(context, width, height);

            manager.openCamera(_CurrentCameraId, _DeviceStateCallback, null); //Handler 暂时设为UI主线程
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        if (_Session != null) {
            _Session.close();
            _Session = null;
        }

        if (_Device != null) {
            _Device.close();
            _Device = null;
        }
    }

    private Surface _RecordSurface;

    public void setRecordSurface(Surface surface) {
        _RecordSurface = surface;
    }

    private ArrayList<Surface> _Surfaces;

    public void addSurface(Surface surface) {
        if (_Surfaces == null) {
            _Surfaces = new ArrayList<>();
        }
        _Surfaces.add(surface);
    }

    public void removeSurface(Surface surface) {
        if (_Surfaces == null ) {
            return;
        }
        _Surfaces.remove(surface);
    }

    private AutoFitTextureView _TextureView;

    public void setFitTextureView(AutoFitTextureView view) {
        _TextureView = view;
    }

    public void startRecord() {
        addSurface(_RecordSurface);
        startPreview();
    }

    public void stopRecord() {
        removeSurface(_RecordSurface);
        startPreview();
    }

    private void startPreview() {
        if (_Session != null) {
            try {
                _Session.abortCaptures();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        try {
            _RequestBuilder = _Device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            _RequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        ArrayList<Surface> surfaces = new ArrayList<>();
        if (_TextureView.isAvailable()) {
            SurfaceTexture texture = _TextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(_PreviewSize.getWidth(), _PreviewSize.getHeight());
            Surface surface = new Surface(texture);
            _RequestBuilder.addTarget(surface);
            surfaces.add(surface);
        }

        if (_Surfaces != null && _Surfaces.size() != 0) {
            for (Surface s : _Surfaces) {
                _RequestBuilder.addTarget(s);
                surfaces.add(s);
            }
        }

        try {
            _Device.createCaptureSession(surfaces, _SessionStateCallBack, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private CameraDevice.StateCallback _DeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            _Device = cameraDevice;
            startPreview();

        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            _Device.close();
            _Device = null;
        }

        @Override
        public void onClosed(CameraDevice camera) {
            super.onClosed(camera);
        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            _Device.close();
            _Device = null;
            Log.e(TAG, "CameraDevice onError code " + i);
        }
    };

    private CameraCaptureSession.StateCallback _SessionStateCallBack = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
            _Session = cameraCaptureSession;
            assert _Device != null;
            try {
                _Session.setRepeatingRequest(_RequestBuilder.build(), null, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onClosed(CameraCaptureSession session) {
            super.onClosed(session);
        }

        @Override
        public void onActive(CameraCaptureSession session) {
            super.onActive(session);
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
            if (_Session != null) {
                _Session.close();
            }
        }
    };

    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private void configureTransform(Context context, int viewWidth, int viewHeight) {
        if (null == _TextureView || null == _PreviewSize) {
            return;
        }
        int rotation = ((Activity)context).getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, _PreviewSize.getHeight(), _PreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / _PreviewSize.getHeight(),
                    (float) viewWidth / _PreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        _TextureView.setTransform(matrix);
    }
}
