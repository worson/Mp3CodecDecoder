package com.sen.audio.mp3codecdecoder.codec;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import com.sen.audio.mp3codecdecoder.utils.AILog;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 说明:
 *
 * @author wangshengxing  01.30 2020
 */
public class Mp3Decoder {

    private final static String TAG = "Mp3Decoder";

    private CodecListener mCodecListener;
    private AtomicBoolean mDecoding=new AtomicBoolean(false);
    private String mFilePath;

    public Mp3Decoder(String filePath) {
        mFilePath = filePath;
    }

    public int start(){
        if (mFilePath == null || mCodecListener==null) {
            AILog.e(TAG, "start: paras is error");
            return -1;
        }
        if (mDecoding.get()) {
            AILog.i(TAG, "start: busy ....");
            return -1;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                mDecoding.set(true);
                decodeFile(mFilePath,mCodecListener);
                mDecoding.set(false);
            }
        }).start();

        return 0;
    }

    public void stop(){
        mDecoding.set(false);
    }

    public void setCodecListener(CodecListener codecListener) {
        mCodecListener = codecListener;
    }

    /**
     * 解码并播放音频
     */
    private void decodeFile(final String filePath,final CodecListener callback) {
        if (callback == null) {
            return;
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaExtractor extractor = new MediaExtractor();

        try {
            extractor.setDataSource(filePath);

            AILog.d(TAG, String.format("TRACKS #: %d", extractor.getTrackCount()));
            MediaFormat trackFormat = extractor.getTrackFormat(0);
            AILog.i(TAG, "realPlay: trackFormat "+trackFormat);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            extractor.selectTrack(0);
            int channelConfig = (trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1) ? AudioFormat.CHANNEL_CONFIGURATION_MONO
                : AudioFormat.CHANNEL_CONFIGURATION_STEREO;
            int sampleRateInHz = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            //注意，按照数字音频的知识，这个算出来的是一秒钟buffer的大小。

            // Create Decoder
            MediaCodec decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(trackFormat, null /* surface */, null /* crypto */, 0 /* flags */);
            decoder.start();

            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;

            callback.onStart(sampleRateInHz,trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));

            while (!sawInputEOS && !sawOutputEOS && mDecoding.get()) {
                // Returns the index of an input buffer to be filled with valid data
                // 获取解码器可输入缓冲区的id号
                int inputBufferId = decoder.dequeueInputBuffer(-1);
                if (inputBufferId >= 0) {
                    //根据id号获取输入缓冲Buffer
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    // fill inputBuffer with valid data
                    long presentationTimeUs = 0;
                    //Retrieve the current encoded sample and store it in the byte buffer
                    //获取需要解码的数据
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    //Advance to the next sample.
                    //跳入下一帧数据
                    extractor.advance();
                    if (sampleSize < 0) {
                        sawInputEOS = true;
                        sampleSize = 0;
                        AILog.d(TAG, "saw EOF in input");
                    } else {
                        //Returns the current sample's presentation time in microseconds.
                        //获取当前帧对应的时长
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    //After filling a range of the input buffer at the specified index
                    //     * submit it to the component.
                    decoder.queueInputBuffer(inputBufferId,
                        0, //offset
                        sampleSize,
                        presentationTimeUs,
                        sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                } else if (inputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    AILog.w(TAG, "INFO_TRY_AGAIN_LATER");

                } else if (inputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    AILog.w(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                    trackFormat = decoder.getOutputFormat();
                } else {
                    AILog.w(TAG, "unknown error dequeueInputBuffer");
                }

                // decode
                //Dequeue an output buffer, block at most "timeoutUs" microseconds.
                //解码数据，可指定最多阻塞时长，返回解决数据的id
                int outputBufferId = decoder.dequeueOutputBuffer(info, -1);

                if (outputBufferId >= 0) {
                    //获取解码后的数据
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferId);
                    trackFormat = decoder.getOutputFormat(outputBufferId); // option A
                    // bufferFormat is identical to outputFormat
                    // outputBuffer is ready to be processed or rendered.

                    //Audio Rendering
                    final byte[] chunk = new byte[info.size];
                    // Read the buffer all at once
                    outputBuffer.get(chunk);
                    // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN
                    outputBuffer.clear();

                    if (chunk.length > 0) {
                        callback.onData(chunk,chunk.length);
                    }
                    //If you are done with a buffer, use this call to return the buffer to the codec or to render it on the output surface.
                    decoder.releaseOutputBuffer(outputBufferId, false /* render */);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                        AILog.d(TAG, "saw output EOS");
                    }


                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Subsequent data will conform to new format.
                    // Can ignore if using getOutputFormat(outputBufferId)
                    trackFormat = decoder.getOutputFormat(); // option B
                }
            }
            callback.onEnd();
        } catch (IOException ex) {
            AILog.e(TAG, "realPlay exception : " + ex.toString());
            callback.onError(-1);
        }
    }
}
