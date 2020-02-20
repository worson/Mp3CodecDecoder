package com.lib.audio.mp3.util;

import com.lib.audio.mp3.codec.system.Mp3SystemDecoder;
import com.lib.audio.mp3.codec.Mp3DecodeListener;
import com.lib.audio.wav.WavFileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 说明:
 *
 * @author wangshengxing  02.20 2020
 */
public class Mp3Util {

    /**
     * 转换为wav文件
     * @param inputFile
     * @param outputFile
     * @return
     */
    public static boolean decode2wav(String inputFile,final String outputFile){
        final AtomicBoolean ret=new AtomicBoolean(false);
        final WavFileWriter wavFileWriter=new WavFileWriter();
        Mp3SystemDecoder.decodeFile(inputFile, new Mp3DecodeListener() {
            @Override
            public void onStart(int simpleRate, int channel) {
                try {
                    wavFileWriter.openFile(outputFile,simpleRate,channel,16);
                } catch (IOException e) {
                    e.printStackTrace();

                }
            }

            @Override
            public void onEnd() {
                try {
                    ret.set(wavFileWriter.closeFile());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onData(byte[] data, int len) {
                wavFileWriter.writeData(data,0,len);
            }

            @Override
            public void onError(int code) {
                try {
                    wavFileWriter.closeFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        return ret.get();
    }
}