package com.sen.audio.mp3codecdecoder.play;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import com.lib.common.dlog.DLog;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Vector;

/**
 * 说明:声道选择播放器 目前只支持mp3文件播放
 *
 * @author wangshengxing  09.03 2019
 */
public class ChannelNativePlayer {

    public static final int CHANNEL_STEREO = 0;
    public static final int CHANNEL_LEFT = 1;
    public static final int CHANNEL_RIGHT = 2;

    private final static String TAG = "ChannelNativePlayer";

    private List<PlayerItem> mPlayList = new Vector<>();

    private static final ChannelNativePlayer ourInstance = new ChannelNativePlayer();

    public static ChannelNativePlayer getInstance() {
        return ourInstance;
    }

    private ChannelNativePlayer() {

    }

    private static class PlayerItem {

        public static final int STATUS_WAIT_PLAY = 0;
        public static final int STATUS_WAIT_STOP = 1;
        public static final int STATUS_PLAYING = 2;
        public static final int STATUS_PAUSED = 3;
        public static final int STATUS_DEAED = 4;
        public static final int STATUS_PREPARE_PLAY = 5;


        private int playId = 0;
        /**
         * 左右声道，0为立体声，1为左声道，2为右声道
         */
        private int channel = 0;
        private String filePath = null;
        private IPlayerCallback callback;
        private float speed = 1;

        private volatile int playStatus = STATUS_WAIT_PLAY;

        public PlayerItem(int playId, int channel, String filePath) {
            this.playId = playId;
            this.channel = channel;
            this.filePath = filePath;
        }

        public boolean notEnd() {
            return playStatus != STATUS_DEAED;
        }

        public void setCallback(IPlayerCallback callback) {
            this.callback = callback;
        }

        public void setSpeed(float speed) {
            this.speed = speed;
        }

        @Override
        public String toString() {
            return "PlayerItem{" +
                "playId=" + playId +
                ", channel=" + channel +
                ", filePath='" + filePath + '\'' +
                ", callback=" + callback +
                ", speed=" + speed +
                '}';
        }
    }


    public int playLeft(File file, IPlayerCallback callback) {
        return play(file, CHANNEL_LEFT, callback);
    }

    public int playRight(File file, IPlayerCallback callback) {
        return play(file, CHANNEL_RIGHT, callback);
    }

    private int play(File file, int channel, IPlayerCallback callback) {
        int id = getPlayId();
        PlayerItem item = new PlayerItem(id, channel, file.getAbsolutePath());
        item.setCallback(callback);
        addPlayItem(item);
        checkAudioPlay();
        return id;
    }

    public void stop(int id) {
        DLog.i(TAG, "stop: " + id);
        stop(getPlayItem(id));
        checkAudioPlay();
    }


    /**
     * 暂时正在播放和缓冲队列数据
     */
    public void stopAll() {
        DLog.i(TAG, "stopAll: ");
        for (PlayerItem item : mPlayList) {
            stop(item);
        }
        if (mLeftAudioTrack != null) {
            mLeftAudioTrack.stop();
            mLeftAudioTrack.release();
            mLeftAudioTrack = null;
        }

        if (mRightAudioTrack != null) {
            mRightAudioTrack.stop();
            mRightAudioTrack.release();
            mLeftAudioTrack = null;
        }
    }


    private PlayerItem getPlayItem(int id) {
        for (PlayerItem item : mPlayList) {
            if (item.playId == id) {
                return item;
            }
        }
        return null;
    }


    private void addPlayItem(PlayerItem item) {
        mPlayList.add(item);
    }


    private int getPlayId() {
        int tid = 1;
        boolean contain = true;
        while (contain) {
            contain = false;
            for (PlayerItem item : mPlayList) {
                if (item.playId == tid) {
                    contain = true;
                }
            }
            if (!contain) {
                return tid;
            } else {
                tid = (int) (50000 * Math.random());
            }
        }
        return tid;
    }

    /**
     * 确认是否有音频可播放，自动开始播放
     */
    private void checkAudioPlay() {
        //缓冲队列没有待播放音频
        if (mPlayList.size() <= 0) {
            DLog.i(TAG, "checkAudioPlay: play buffer is null");
            return;
        }
        final PlayerItem firstItem = mPlayList.get(0);

        //当前双通道音频在播放
        if (firstItem.channel == CHANNEL_STEREO && firstItem.playStatus == PlayerItem.STATUS_PLAYING) {
            DLog.i(TAG, "checkAudioPlay: stereo play , busy ...");
            return;
        }
        //当前没有音频在播放
        if (firstItem.playStatus == PlayerItem.STATUS_WAIT_PLAY) {
            DLog.i(TAG, "checkAudioPlay: not busy , play " + firstItem.filePath);
            firstItem.playStatus = PlayerItem.STATUS_PREPARE_PLAY;
            new Thread(new Runnable() {
                public void run() {
                    realPlay(firstItem);
                    mPlayList.remove(firstItem);
                    //继续检测是否有空余通道
                    checkAudioPlay();
                }

            }).start();
        }

        //继续确认有没有空闲通道可播放
        if ((firstItem.playStatus == PlayerItem.STATUS_PLAYING || firstItem.playStatus == PlayerItem.STATUS_PREPARE_PLAY) && mPlayList.size() > 1) {
            for (int i = 1; i < mPlayList.size(); i++) {
                PlayerItem item = mPlayList.get(i);
                if (item.channel == CHANNEL_STEREO) {
                    DLog.i(TAG, "checkAudioPlay: next audio is stereo , cant play");
                    return;
                }
                if (item.channel != firstItem.channel && (item.playStatus == PlayerItem.STATUS_PLAYING || item.playStatus == PlayerItem.STATUS_PREPARE_PLAY)) {
                    DLog.i(TAG, "checkAudioPlay: current channel is busy");
                    return;
                } else if (item.channel != firstItem.channel && item.channel != CHANNEL_STEREO && item.playStatus == PlayerItem.STATUS_WAIT_PLAY) {
                    DLog.i(TAG, "checkAudioPlay: another channel not busy , play " + item.filePath);
                    final PlayerItem playerItem = item;
                    playerItem.playStatus = PlayerItem.STATUS_PREPARE_PLAY;
                    new Thread(new Runnable() {
                        public void run() {
                            realPlay(playerItem);
                            mPlayList.remove(playerItem);
                            checkAudioPlay();
                        }

                    }).start();
                } else {
                    DLog.d(TAG, "checkAudioPlay: itme " + item);
                }
            }
        }


    }


    private void stop(PlayerItem item) {
        if (item != null) {
            item.playStatus = PlayerItem.STATUS_WAIT_STOP;
        }
    }


    private volatile AudioTrack mLeftAudioTrack = null;
    private volatile AudioTrack mRightAudioTrack = null;

    /**
     * 解码并播放音频
     */
    private void realPlay(final PlayerItem item) {
        final String filePath = item.filePath;
        DLog.i(TAG, String.format("realPlay: filePath=%s,channel=%s", item.filePath, item.channel));
        item.playStatus = PlayerItem.STATUS_PLAYING;
        if (item.callback != null) {
            item.callback.onStart();
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaExtractor extractor = new MediaExtractor();

        try {
            extractor.setDataSource(filePath);

            DLog.d(TAG, String.format("TRACKS #: %d", extractor.getTrackCount()));
            MediaFormat trackFormat = extractor.getTrackFormat(0);
            DLog.i(TAG, "realPlay: trackFormat "+trackFormat);
            String mime = trackFormat.getString(MediaFormat.KEY_MIME);
            extractor.selectTrack(0);
//            extractor.seekTo(100 * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            int channelConfig = (trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1) ? AudioFormat.CHANNEL_CONFIGURATION_MONO
                : AudioFormat.CHANNEL_CONFIGURATION_STEREO;
            int sampleRateInHz = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int bufsize = AudioTrack.getMinBufferSize(sampleRateInHz,//每秒8K个点
                channelConfig,//
                AudioFormat.ENCODING_PCM_16BIT);//一个采样点16比特-2个字节
            //注意，按照数字音频的知识，这个算出来的是一秒钟buffer的大小。

            boolean createOk = false;
            AudioTrack mAudioTrack = null;
            if (item.channel == CHANNEL_LEFT) {
                mAudioTrack = mLeftAudioTrack;
            } else if (item.channel == CHANNEL_RIGHT) {
                mAudioTrack = mRightAudioTrack;
            }
            long firstTime = System.currentTimeMillis();
            int createCnt = 0;
            DLog.i(TAG, String.format("realPlay:channelConfig=%s,sampleRateInHz=%s,bufsize=%s ", channelConfig, sampleRateInHz, bufsize));
            while (mAudioTrack == null && !createOk && (System.currentTimeMillis() - firstTime) < 5000) {
                //创建AudioTrack
                mAudioTrack = new AudioTrack(6, sampleRateInHz,
                    channelConfig, AudioFormat.ENCODING_PCM_16BIT, bufsize,
                    AudioTrack.MODE_STREAM);//
//                    mAudioTrack.setStereoVolume(1,0);

                mAudioTrack.setPlaybackRate(trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                mAudioTrack.setVolume(1.0f);
                if (mAudioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
                    try {
                        createCnt++;
                        DLog.i(TAG, "realPlay: create AudioTrack error " + createCnt);
                        Thread.sleep(100);
                        mAudioTrack = null;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    createOk = true;
                    mAudioTrack.play();
                    if (item.channel == CHANNEL_LEFT) {
                        mLeftAudioTrack = mAudioTrack;
                        mAudioTrack.setStereoVolume(1, 0);
                    } else if (item.channel == CHANNEL_RIGHT) {
                        mRightAudioTrack = mAudioTrack;
                        mAudioTrack.setStereoVolume(0, 1);
                    } else {
                        mAudioTrack.setStereoVolume(1, 1);
                    }
                }
                DLog.i(TAG, "realPlay: AudioTrack State " + mAudioTrack.getState());

            }
            if (mAudioTrack == null || mAudioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
                DLog.e(TAG, "realPlay: AudioTrack init error");
                if (item.callback != null) {
                    item.callback.onError();
                }
                return;
            }

            // Create Decoder
            MediaCodec decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(trackFormat, null /* surface */, null /* crypto */, 0 /* flags */);
            decoder.start();

            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;

            while (!sawInputEOS && !sawOutputEOS && (item.playStatus != PlayerItem.STATUS_WAIT_STOP)) {
//                        DLog.d(TAG, "for loop");
                // Read from mp3
                int inputBufferId = decoder.dequeueInputBuffer(-1);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    // fill inputBuffer with valid data

                    long presentationTimeUs = 0;
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    extractor.advance();
//                            DLog.d(TAG, "read sampleSize:" + sampleSize);
                    if (sampleSize < 0) {
                        sawInputEOS = true;
                        sampleSize = 0;
                        DLog.d(TAG, "saw EOF in input");
                    } else {
                        presentationTimeUs = extractor.getSampleTime();
                    }
                    decoder.queueInputBuffer(inputBufferId,
                        0, //offset
                        sampleSize,
                        presentationTimeUs,
                        sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                } else if (inputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    DLog.w(TAG, "INFO_TRY_AGAIN_LATER");

                } else if (inputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    DLog.w(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                    trackFormat = decoder.getOutputFormat();
                } else {
                    DLog.w(TAG, "unknown error dequeueInputBuffer");
                }

                // decode
//                        DLog.d(TAG, "decoding....");
                int outputBufferId = decoder.dequeueOutputBuffer(info, -1);

                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputBufferId);
                    trackFormat = decoder.getOutputFormat(outputBufferId); // option A
                    // bufferFormat is identical to outputFormat
                    // outputBuffer is ready to be processed or rendered.

                    //Audio Rendering
                    final byte[] chunk = new byte[info.size];
                    outputBuffer.get(chunk); // Read the buffer all at once
                    outputBuffer.clear(); // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN

                    if (chunk.length > 0) {
//                                DLog.d(TAG, "writing chunk:" + chunk.length);
                        //In streaming mode, the write will normally block until all the data has been enqueued
                        mAudioTrack.write(chunk, 0, chunk.length);
                    }
                    decoder.releaseOutputBuffer(outputBufferId, false /* render */);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                        DLog.d(TAG, "saw output EOS");
                    }


                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Subsequent data will conform to new format.
                    // Can ignore if using getOutputFormat(outputBufferId)
                    trackFormat = decoder.getOutputFormat(); // option B
                }
            }
            item.playStatus = PlayerItem.STATUS_DEAED;
            DLog.d(TAG, "done foe loop");
            if (item.channel == CHANNEL_LEFT) {
                if (mLeftAudioTrack == null) {
                    decoder.stop();
                    decoder.release();
                }
            } else if (item.channel == CHANNEL_RIGHT) {
                if (mRightAudioTrack == null) {
                    decoder.stop();
                    decoder.release();
                }
            }

            if (item.callback != null) {
                item.callback.onEnd();
            }
        } catch (IOException ex) {
            DLog.e(TAG, "realPlay exception : " + ex.toString());
            item.playStatus = PlayerItem.STATUS_DEAED;
            if (item.callback != null) {
                item.callback.onError();
            }
        }
    }

}
