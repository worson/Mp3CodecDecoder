package com.lib.audio.mp3.codec;

/**
 * 说明:
 *
 * @author wangshengxing  01.30 2020
 */
public interface Mp3DecodeListener {

    void onStart(int simpleRate,int channel);

    void onEnd();

    void onData(byte[] data,int len);

    void onError(int code);

}
