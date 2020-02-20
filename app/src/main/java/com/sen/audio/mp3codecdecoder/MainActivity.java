package com.sen.audio.mp3codecdecoder;

import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.lib.audio.mp3.codec.Mp3DecodeListener;
import com.lib.audio.mp3.codec.system.AsynMp3SystemDecoder;
import com.lib.audio.mp3.util.Mp3Util;
import com.sen.audio.mp3codecdecoder.play.ChannelNativePlayer;
import com.sen.audio.mp3codecdecoder.play.IPlayerCallback;
import com.sen.audio.mp3codecdecoder.play.NativePlayer;
import com.sen.audio.mp3codecdecoder.utils.AILog;
import com.sen.audio.mp3codecdecoder.utils.AssetsUtil;
import com.sen.audio.mp3codecdecoder.utils.AudioUtil;
import com.sen.audio.mp3codecdecoder.utils.GlobalContext;
import com.sen.audio.mp3codecdecoder.utils.HandlerUtil;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivityTag";

    private int mLastPlayId=-1;

    private String mMp3FilePath;
    private String mWavFilePath;

    private Button bt_mp3_to_wav;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        GlobalContext.set(getApplication());
        initViews();
    }

    private void initViews() {
        mMp3FilePath=GlobalContext.get().getFilesDir().getPath()+"/test.mp3";
        mWavFilePath=GlobalContext.get().getFilesDir().getPath()+"/test.wav";
        if (!new File(mMp3FilePath).exists()) {
            AssetsUtil.copyFile(GlobalContext.get(),"test.mp3",mMp3FilePath);
        }
        findViewById(R.id.bt_media_play).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                AssetFileDescriptor descriptor = null;
                try {
                    NativePlayer.getInstance().cancel(mLastPlayId);
                    descriptor = getAssets().openFd("test.mp3");
                    mLastPlayId=NativePlayer.getInstance().play(descriptor,null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.bt_audio_track_play).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                ChannelNativePlayer.getInstance().stop(mLastPlayId);
                mLastPlayId=ChannelNativePlayer.getInstance().playLeft(new File(mMp3FilePath), new IPlayerCallback() {
                    @Override
                    public void onStart() {
                        AILog.i(TAG, "onStart: ");
                    }

                    @Override
                    public void onSuccess() {

                    }

                    @Override
                    public void onEnd() {
                        AILog.i(TAG, "onEnd: ");
                    }

                    @Override
                    public void onError() {

                    }
                });
            }
        });



        bt_mp3_to_wav=findViewById(R.id.bt_mp3_to_wav);
        bt_mp3_to_wav.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                bt_mp3_to_wav.setEnabled(false);
                long rtc=System.currentTimeMillis();
                Mp3Util.decode2wav(mMp3FilePath,mWavFilePath);
                AILog.i(TAG, "onClick: decode cost "+(System.currentTimeMillis()-rtc));
                bt_mp3_to_wav.setEnabled(true);
            }
        });

        findViewById(R.id.bt_wav_play).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                NativePlayer.getInstance().cancel(mLastPlayId);
                mLastPlayId=NativePlayer.getInstance().play(new File(mWavFilePath),null);
            }
        });

    }

    private void asycDecode() {
        AsynMp3SystemDecoder mp3Decoder = new AsynMp3SystemDecoder(mMp3FilePath);
        mp3Decoder.setCodecListener(new Mp3DecodeListener() {
            private String mTempPcmPath;
            private FileOutputStream mOutputStream;

            private int mSimpleRate;
            private int mChannel;

            @Override
            public void onStart(int simpleRate, int channel) {
                AILog.i(TAG, String.format("onStart: simpleRate=%s,channel=%s",simpleRate,channel));
                mSimpleRate=simpleRate;
                mChannel=channel;
                try {
                    mTempPcmPath=GlobalContext.get().getFilesDir().getPath()+"/tmp.pcm";
                    mOutputStream=new FileOutputStream(new File(mTempPcmPath));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onEnd() {
                AILog.i(TAG, "onEnd: ");
                try {
                    mOutputStream.flush();
                    mOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                AudioUtil.convertWaveFile(mChannel,mSimpleRate,mTempPcmPath,mWavFilePath);

                HandlerUtil.postInMainThread(new Runnable() {
                    @Override
                    public void run() {
                        bt_mp3_to_wav.setEnabled(true);
                    }
                });
            }

            @Override
            public void onData(byte[] data, int len) {
//                        AILog.i(TAG, "onData: "+len);
                try {
                    mOutputStream.write(data,0,len);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(int code) {
                AILog.i(TAG, "onError: "+code);
                try {
                    mOutputStream.flush();
                    mOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                HandlerUtil.postInMainThread(new Runnable() {
                    @Override
                    public void run() {
                        bt_mp3_to_wav.setEnabled(true);
                    }
                });
            }
        });
        AILog.i(TAG, "onClick: start decode");
        mp3Decoder.start();
    }

}
