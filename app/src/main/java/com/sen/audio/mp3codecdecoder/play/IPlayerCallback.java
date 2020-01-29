package com.sen.audio.mp3codecdecoder.play;

public interface IPlayerCallback {

    /**
     * 开始播放
     */
    void onStart();


    /**
     * 播放成功
     */
    void onSuccess();

    /**
     * 播放结束，无论是成功还是失败都会回调，onEnd是最后调用的
     */
    void onEnd();

    /**
     * 播放发生错误
     */
    void onError();

}