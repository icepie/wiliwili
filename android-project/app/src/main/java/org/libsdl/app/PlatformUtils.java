package org.libsdl.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Message;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;

public class PlatformUtils {
    public static boolean isBatterySupported() {
        Context context = SDLActivity.getContext();
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return batteryIntent != null;
    }

    public static int getBatteryLevel() {
        Context context = SDLActivity.getContext();

        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent == null) {
            return 0;
        }
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if (level >= 0 && scale > 0) {
            return (level * 100) / scale;
        }

        return 0;
    }

    public static boolean isBatteryCharging() {
        Context context = SDLActivity.getContext();

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
    }

    public static boolean isEthernetConnected() {
        Context context = SDLActivity.getContext();

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = connectivityManager.getAllNetworks();
        for (Network network : networks) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWifiSupported() {
        Context context = SDLActivity.getContext();

        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    public static boolean isWifiConnected() {
        Context context = SDLActivity.getContext();

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifiInfo != null && wifiInfo.isConnected();
    }

    public static boolean isSystemInDarkMode() {
        Context context = SDLActivity.getContext();
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int getWifiSignalStrength() {
        Context context = SDLActivity.getContext();

        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        return wifiInfo.getRssi();
    }

    public static void openBrowser(String url) {
        Context context = SDLActivity.getContext();

        Uri webpage = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, webpage);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        }
    }

    public static void openExoPlayer(String videoUrl, String[] audioUrls, int startSeconds, int endSeconds,
                                     String cookie) {
        String[] videos = videoUrl == null || videoUrl.isEmpty() ? new String[0] : new String[] { videoUrl };
        openExoPlayerWithVideos(videos, audioUrls, startSeconds, endSeconds, cookie);
    }

    public static void openExoPlayerBv(String bvid, long cid, int progress, String cookie) {
        Context context = SDLActivity.getContext();
        if (context == null || bvid == null || bvid.isEmpty()) {
            return;
        }
        cn.xfangfang.wiliwili.player.ExoPlayerActivity.openBv(context, bvid, cid, progress, cookie);
    }

    public static void openExoPlayerWithVideos(String[] videoUrls, String[] audioUrls, int startSeconds, int endSeconds,
                                               String cookie) {
        Context context = SDLActivity.getContext();
        if (context == null || videoUrls == null || videoUrls.length == 0) {
            return;
        }

        Intent intent = new Intent(context, cn.xfangfang.wiliwili.player.ExoPlayerActivity.class);
        ArrayList<String> videos = new ArrayList<>();
        for (String videoUrl : videoUrls) {
            if (videoUrl != null && !videoUrl.isEmpty()) {
                videos.add(videoUrl);
            }
        }
        if (videos.isEmpty()) {
            return;
        }
        intent.putExtra(cn.xfangfang.wiliwili.player.ExoPlayerActivity.EXTRA_VIDEO_URL, videos.get(0));
        intent.putStringArrayListExtra(cn.xfangfang.wiliwili.player.ExoPlayerActivity.EXTRA_VIDEO_URLS, videos);
        intent.putExtra(cn.xfangfang.wiliwili.player.ExoPlayerActivity.EXTRA_START_SECONDS, startSeconds);
        intent.putExtra(cn.xfangfang.wiliwili.player.ExoPlayerActivity.EXTRA_END_SECONDS, endSeconds);
        intent.putExtra(cn.xfangfang.wiliwili.player.ExoPlayerActivity.EXTRA_COOKIE, cookie);
        ArrayList<String> audios = new ArrayList<>();
        if (audioUrls != null) {
            for (String audioUrl : audioUrls) {
                if (audioUrl != null && !audioUrl.isEmpty()) {
                    audios.add(audioUrl);
                }
            }
        }
        intent.putStringArrayListExtra(cn.xfangfang.wiliwili.player.ExoPlayerActivity.EXTRA_AUDIO_URLS, audios);
        context.startActivity(intent);
    }

    public static float getSystemScreenBrightness(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        return Settings.System.getInt(contentResolver,
                Settings.System.SCREEN_BRIGHTNESS, 125) * 1.0f / 255.0f;
    }

    public static BorealisHandler borealisHandler = null;

    public static void setAppScreenBrightness(Activity activity, float value) {
        Message message = Message.obtain();
        message.obj = activity;
        message.arg1 = (int)(value * 255);
        message.what = 0;
        if(borealisHandler != null) borealisHandler.sendMessage(message);
    }

    public static float getAppScreenBrightness(Activity activity) {
        Window window = activity.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        if (lp.screenBrightness < 0) return getSystemScreenBrightness(activity);
        return lp.screenBrightness;
    }
}
