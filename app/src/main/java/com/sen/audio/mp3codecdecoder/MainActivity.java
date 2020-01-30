package com.sen.audio.mp3codecdecoder;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.sen.audio.mp3codecdecoder.codec.CodecListener;
import com.sen.audio.mp3codecdecoder.codec.Mp3Decoder;
import com.sen.audio.mp3codecdecoder.play.ChannelNativePlayer;
import com.sen.audio.mp3codecdecoder.play.IPlayerCallback;
import com.sen.audio.mp3codecdecoder.play.NativePlayer;
import com.sen.audio.mp3codecdecoder.utils.AILog;
import com.sen.audio.mp3codecdecoder.utils.AssetsUtil;
import com.sen.audio.mp3codecdecoder.utils.AudioUtil;
import com.sen.audio.mp3codecdecoder.utils.GlobalContext;
import com.sen.audio.mp3codecdecoder.utils.HandlerUtil;
import com.sen.audio.mp3codecdecoder.utils.ToastUtil;
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
                Mp3Decoder mp3Decoder = new Mp3Decoder(mMp3FilePath);
                mp3Decoder.setCodecListener(new CodecListener() {
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
        });

        findViewById(R.id.bt_wav_share).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
//                Intent intent = new Intent(Intent.ACTION_SEND);
//                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(mMp3FilePath)));
//                intent.setType("*/*");
//                startActivity(intent);

                shareFile2(MainActivity.this,new File(mWavFilePath));
            }
        });

    }

    // 調用系統方法分享文件
    public static void shareFile2(Context context, File file) {
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.addCategory("android.intent.category.DEFAULT");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //data是file类型,忘了复制过来
            uri = FileProvider.getUriForFile(context, "com.sen.audio.mp3codecdecoder.fileprovider", file);
        } else {
            uri=Uri.fromFile(file);
        }
        //pdf文件要被读取所以加入读取权限
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//        intent.setDataAndType(uri, "application/pdf");
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void shareFile(Context context, File file) {
        if (null != file && file.exists()) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            share.setType(getMimeType(file.getAbsolutePath()));//此处可发送多种文件
            share.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(share, "分享文件"));
        } else {
            ToastUtil.showToast("分享文件不存在");
        }
    }

    // 根据文件后缀名获得对应的MIME类型。
    private static String getMimeType(String filePath) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        String mime = "*/*";
        if (filePath != null) {
            try {
                mmr.setDataSource(filePath);
                mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            } catch (IllegalStateException e) {
                return mime;
            } catch (IllegalArgumentException e) {
                return mime;
            } catch (RuntimeException e) {
                return mime;
            }
        }
        return mime;
    }
}
