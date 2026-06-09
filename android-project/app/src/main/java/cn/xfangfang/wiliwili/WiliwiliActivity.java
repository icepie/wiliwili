package cn.xfangfang.wiliwili;

import android.os.Bundle;

import org.libsdl.app.BorealisHandler;
import org.libsdl.app.PlatformUtils;
import org.libsdl.app.SDLActivity;

public class WiliwiliActivity extends SDLActivity {
    private native void nativeInitFFmpegAndroid(Object context);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PlatformUtils.borealisHandler = new BorealisHandler();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.exit(0);
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {
                "SDL2",
                "avutil",
                "swresample",
                "swscale",
                "avcodec",
                "avformat",
                "avfilter",
                "avdevice",
                "mpv",
                "wiliwili"
        };
    }

    @Override
    public void loadLibraries() {
        super.loadLibraries();
        nativeInitFFmpegAndroid(getApplicationContext());
    }
}
