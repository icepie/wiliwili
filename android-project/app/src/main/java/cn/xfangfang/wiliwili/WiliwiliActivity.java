package cn.xfangfang.wiliwili;

import android.os.Bundle;

import java.util.ArrayList;

import org.libsdl.app.BorealisHandler;
import org.libsdl.app.PlatformUtils;
import org.libsdl.app.SDLActivity;

public class WiliwiliActivity extends SDLActivity {
    public static final String EXTRA_OPEN_BV = "cn.xfangfang.wiliwili.extra.OPEN_BV";

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
    protected String[] getArguments() {
        ArrayList<String> arguments = new ArrayList<>();
        String openBV = getIntent().getStringExtra(EXTRA_OPEN_BV);
        if (openBV != null && !openBV.isEmpty()) {
            arguments.add("--open-bv");
            arguments.add(openBV);
        }
        return arguments.toArray(new String[0]);
    }

    @Override
    public void loadLibraries() {
        super.loadLibraries();
        nativeInitFFmpegAndroid(getApplicationContext());
    }
}
