package com.sen.audio.mp3codecdecoder.utils;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 说明:
 *
 * @author wangshengxing  01.29 2020
 */
public class AssetsUtil {

    /**
     * 将 Asset 目录下的文件复制到指定存储路径
     *
     * @param context context
     * @param inputPath 需要复制的 assert 文件路径
     * @param outputPath 目标路径
     * @return 如果成功返回 true
     */
    public static boolean copyFile(Context context, String inputPath, String outputPath) {
        boolean ret = true;

        File file = new File(outputPath);

        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        InputStream srcStream = null;
        OutputStream desStream = null;
        try {
            srcStream = context.getAssets().open(inputPath);
            desStream = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = srcStream.read(buffer)) > 0) {
                desStream.write(buffer, 0, length);
            }
        } catch (IOException e) {
            e.printStackTrace();

            ret = false;
        } finally {
            try {
                if (null != srcStream) {
                    srcStream.close();
                }
                if (null != desStream) {
                    desStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        return ret;
    }


    /**
     * 读取字符串
     */
    public static String getAssetString(Context context, String file) {
        String result = null;
        InputStream open = null;
        try {
            open = context.getAssets().open(file);
            result = IOUtils.toString(open);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

}
