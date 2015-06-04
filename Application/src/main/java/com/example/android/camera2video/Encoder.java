package com.example.android.camera2video;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by wangt on 6/2/15.
 */
public class Encoder extends MediaCodec.Callback {
    public static final String TAG = "Encoder";
    private MediaCodec _Codec;
    private MediaMuxer _Muxer;
    private MediaCodec.BufferInfo _BufferInfo;
    private int _TrackedIndex;
    private boolean _MuxerStarted;
    private Surface _InputSurface;
    private boolean _Accepted = false;


    public void onCreate(Context context, String type, int width, int height) { //1440x1080

        prepareCodec(type, width, height);
        prepareMuxer(context);
    }

    public void onDestroy() {
        destroyCodec();
        destroyMuxer();
    }

    public final Surface getSurface() {
        return _InputSurface;
    }

    public void start() {
        setAccepted(true);
    }

    public void stop() {
        setAccepted(false);
    }

    private void setAccepted(boolean accepted) {
        _Accepted = accepted;
    }

    @Override
    public void onInputBufferAvailable(MediaCodec mediaCodec, int i) {
        Log.e(TAG, "onInputBufferAvailable");
    }

    @Override
    public void onOutputBufferAvailable(MediaCodec mediaCodec, int i, MediaCodec.BufferInfo bufferInfo) {
        if (_Accepted && _MuxerStarted) {
            ByteBuffer outBuffer = mediaCodec.getOutputBuffer(i);
            outBuffer.position(bufferInfo.offset);
            outBuffer.limit(bufferInfo.offset + bufferInfo.size);
            _Muxer.writeSampleData(_TrackedIndex, outBuffer, bufferInfo);
            mediaCodec.releaseOutputBuffer(i,false);
        }
    }

    @Override
    public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
        Log.e(TAG,e.toString());
    }

    @Override
    public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {
        _TrackedIndex = _Muxer.addTrack(mediaFormat);
        if (_TrackedIndex != -1) {
            _MuxerStarted = true;
            _Muxer.start();
        }

    }

    private void prepareCodec(String type, int width, int height) {
        _TrackedIndex = -1;
        _BufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(type, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE,125000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE,30);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);

        try {
            _Codec = MediaCodec.createEncoderByType(type);//video/avc
        } catch (IOException e) {
            e.printStackTrace();
        }
        _Codec.setCallback(this);
        _Codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        _InputSurface = _Codec.createInputSurface();
        _Codec.start();
    }

    private void prepareMuxer(Context context) {
        String path = context.getExternalFilesDir(null).getAbsolutePath();
        try {
            _Muxer = new MediaMuxer(path + "/h264.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        _MuxerStarted = false;
    }

    private void destroyCodec() {
        _Codec.stop();
        _Codec.release();
    }

    private void destroyMuxer() {
        _Muxer.stop();
        _Muxer.release();
    }
}
