package com.sen.audio.mp3codecdecoder.codec;

/**
 * 说明:
 *
 * @author wangshengxing  01.30 2020
 */
public interface CodecListener {

    void onStart(int simpleRate,int channel);

    void onEnd();

    void onData(byte[] data,int len);

    void onError(int code);

}
