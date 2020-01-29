package com.sen.audio.mp3codecdecoder;

import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import androidx.appcompat.app.AppCompatActivity;
import com.sen.audio.mp3codecdecoder.play.ChannelNativePlayer;
import com.sen.audio.mp3codecdecoder.play.NativePlayer;
import com.sen.audio.mp3codecdecoder.utils.AssetsUtil;
import com.sen.audio.mp3codecdecoder.utils.GlobalContext;
import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private int mLastPlayId=-1;

    private String mMp3FilePath;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        GlobalContext.set(getApplication());
        initViews();
    }

    private void initViews() {
        mMp3FilePath=GlobalContext.get().getFilesDir().getPath()+"/test.mp3";
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
                mLastPlayId=ChannelNativePlayer.getInstance().playLeft(new File(mMp3FilePath),null);
            }
        });

    }
}
