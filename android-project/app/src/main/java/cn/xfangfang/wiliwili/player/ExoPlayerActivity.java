package cn.xfangfang.wiliwili.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.Inflater;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@UnstableApi
public class ExoPlayerActivity extends Activity {
    private static final String TAG = "WiliwiliExoPlayer";

    public static final String EXTRA_BVID = "cn.xfangfang.wiliwili.extra.BVID";
    public static final String EXTRA_CID = "cn.xfangfang.wiliwili.extra.CID";
    public static final String EXTRA_PROGRESS = "cn.xfangfang.wiliwili.extra.PROGRESS";
    public static final String EXTRA_VIDEO_URL = "cn.xfangfang.wiliwili.extra.VIDEO_URL";
    public static final String EXTRA_VIDEO_URLS = "cn.xfangfang.wiliwili.extra.VIDEO_URLS";
    public static final String EXTRA_AUDIO_URLS = "cn.xfangfang.wiliwili.extra.AUDIO_URLS";
    public static final String EXTRA_START_SECONDS = "cn.xfangfang.wiliwili.extra.START_SECONDS";
    public static final String EXTRA_END_SECONDS = "cn.xfangfang.wiliwili.extra.END_SECONDS";
    public static final String EXTRA_COOKIE = "cn.xfangfang.wiliwili.extra.COOKIE";
    public static final String EXTRA_TITLE = "cn.xfangfang.wiliwili.extra.TITLE";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final long CONTROLS_HIDE_DELAY_MS = 5000;
    private static final long PROGRESS_UPDATE_MS = 500;
    private static final long HISTORY_REPORT_INTERVAL_MS = 15000;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private PlayerView playerView;
    private DanmakuOverlayView danmakuView;
    private ExoPlayer player;
    private FrameLayout controlsLayer;
    private View bufferingLayer;
    private TextView titleText;
    private TextView peopleText;
    private TextView centerHint;
    private TextView positionText;
    private TextView durationText;
    private TextView playText;
    private TextView qualityText;
    private TextView speedText;
    private TextView danmakuText;
    private TextView sourceText;
    private TextView detailTitle;
    private TextView detailMeta;
    private TextView detailDesc;
    private LinearLayout detailPanel;
    private LinearLayout pageStrip;
    private SeekBar seekBar;

    private ArrayList<String> videoUrls = new ArrayList<>();
    private ArrayList<String> audioUrls = new ArrayList<>();
    private ArrayList<PageInfo> pages = new ArrayList<>();
    private ArrayList<QualityInfo> qualities = new ArrayList<>();
    private int videoIndex;
    private int audioIndex;
    private int selectedPageIndex;
    private int requestedQuality = 116;
    private int startSeconds;
    private int endSeconds = -1;
    private long aid;
    private long cid;
    private String bvid;
    private String videoUrl;
    private String cookie;
    private String referer = "https://www.bilibili.com/";
    private String title = "wiliwili";
    private String owner = "";
    private String desc = "";
    private boolean controlsVisible = true;
    private boolean userSeeking;
    private boolean fillScreen;
    private float playbackSpeed = 1.0f;
    private long lastProgressReportMs;

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            setControlsVisible(false);
        }
    };
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, PROGRESS_UPDATE_MS);
        }
    };
    private final Runnable endChecker = new Runnable() {
        @Override
        public void run() {
            if (player != null && endSeconds > 0 && player.getCurrentPosition() >= endSeconds * 1000L) {
                reportProgress("clip-end");
                finish();
                return;
            }
            handler.postDelayed(this, 500);
        }
    };

    public static void openBv(Context context, String bvid, long cid, int progress, String cookie) {
        if (context == null || TextUtils.isEmpty(bvid)) {
            return;
        }
        Intent intent = new Intent(context, ExoPlayerActivity.class);
        intent.putExtra(EXTRA_BVID, bvid);
        intent.putExtra(EXTRA_CID, cid);
        intent.putExtra(EXTRA_PROGRESS, progress);
        intent.putExtra(EXTRA_COOKIE, cookie);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveMode();
        readIntent();
        buildLayout();
        handler.post(progressRunnable);
        if (endSeconds > 0) {
            handler.postDelayed(endChecker, 500);
        }

        if (!TextUtils.isEmpty(bvid)) {
            loadVideoDetail();
        } else if (!videoUrls.isEmpty()) {
            applyDirectUrls();
        } else {
            showCenterHint("无法打开视频");
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        reportProgress("pause");
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        reportProgress("destroy");
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    private void readIntent() {
        bvid = getIntent().getStringExtra(EXTRA_BVID);
        cid = getIntent().getLongExtra(EXTRA_CID, 0);
        int progress = getIntent().getIntExtra(EXTRA_PROGRESS, -1);
        startSeconds = getIntent().getIntExtra(EXTRA_START_SECONDS, progress);
        endSeconds = getIntent().getIntExtra(EXTRA_END_SECONDS, -1);
        cookie = getIntent().getStringExtra(EXTRA_COOKIE);

        ArrayList<String> videos = getIntent().getStringArrayListExtra(EXTRA_VIDEO_URLS);
        if (videos != null) {
            videoUrls = distinctNonEmpty(videos);
        }
        String singleVideo = getIntent().getStringExtra(EXTRA_VIDEO_URL);
        if (!TextUtils.isEmpty(singleVideo) && !videoUrls.contains(singleVideo)) {
            videoUrls.add(0, singleVideo);
        }
        ArrayList<String> audios = getIntent().getStringArrayListExtra(EXTRA_AUDIO_URLS);
        if (audios != null) {
            audioUrls = distinctNonEmpty(audios);
        }
        String extraTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (!TextUtils.isEmpty(extraTitle)) {
            title = extraTitle;
        }
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setOnClickListener(v -> toggleControls());
        root.addView(playerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        danmakuView = new DanmakuOverlayView(this);
        danmakuView.setPositionProvider(new DanmakuOverlayView.PositionProvider() {
            @Override
            public long getPositionMs() {
                return player == null ? 0 : player.getCurrentPosition();
            }

            @Override
            public boolean isPlaying() {
                return player != null && player.isPlaying();
            }
        });
        root.addView(danmakuView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        bufferingLayer = buildBufferingLayer();
        root.addView(bufferingLayer);

        controlsLayer = new FrameLayout(this);
        controlsLayer.setOnClickListener(v -> scheduleControlsHide());
        controlsLayer.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                scheduleControlsHide();
            }
            return false;
        });
        root.addView(controlsLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        controlsLayer.addView(buildTopBar());
        controlsLayer.addView(buildBottomBar());
        controlsLayer.addView(buildDetailPanel());
        setContentView(root);
    }

    private View buildBufferingLayer() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(0x00000000);
        layout.setVisibility(View.VISIBLE);
        ProgressBar progressBar = new ProgressBar(this);
        layout.addView(progressBar, new LinearLayout.LayoutParams(dp(68), dp(68)));
        centerHint = new TextView(this);
        centerHint.setText("加载中");
        centerHint.setTextColor(Color.WHITE);
        centerHint.setTextSize(15);
        centerHint.setGravity(Gravity.CENTER);
        centerHint.setBackground(rounded(0x88303030, dp(4)));
        centerHint.setPadding(dp(18), dp(7), dp(18), dp(7));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(14);
        layout.addView(centerHint, hintParams);
        return layout;
    }

    private View buildTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(20), dp(14), dp(16), dp(22));
        top.setBackground(topGradient());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(92), Gravity.TOP);
        top.setLayoutParams(params);

        TextView back = iconText("<", 30);
        back.setOnClickListener(v -> {
            reportProgress("back");
            finish();
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleBoxParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleBoxParams.leftMargin = dp(10);
        top.addView(titleBox, titleBoxParams);

        titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(17);
        titleText.setSingleLine(true);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        titleBox.addView(titleText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        peopleText = new TextView(this);
        peopleText.setText("准备播放");
        peopleText.setTextColor(0xccffffff);
        peopleText.setTextSize(12);
        peopleText.setSingleLine(true);
        LinearLayout.LayoutParams peopleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        peopleParams.topMargin = dp(4);
        titleBox.addView(peopleText, peopleParams);

        TextView cast = pillText("投屏", false);
        cast.setOnClickListener(v -> showCenterHint("ExoPlayer 模式暂不支持投屏"));
        top.addView(cast, new LinearLayout.LayoutParams(dp(70), dp(40)));

        TextView setting = pillText("设置", false);
        setting.setOnClickListener(v -> showSettingsDialog());
        LinearLayout.LayoutParams settingParams = new LinearLayout.LayoutParams(dp(70), dp(40));
        settingParams.leftMargin = dp(8);
        top.addView(setting, settingParams);
        return top;
    }

    private View buildBottomBar() {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(20), dp(10), dp(16), dp(18));
        bottom.setBackground(bottomGradient());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(128), Gravity.BOTTOM);
        bottom.setLayoutParams(params);

        playText = iconText("Pause", 16);
        playText.setOnClickListener(v -> togglePlayPause());
        bottom.addView(playText, new LinearLayout.LayoutParams(dp(92), dp(92)));

        LinearLayout progressBox = new LinearLayout(this);
        progressBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams progressBoxParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        progressBoxParams.leftMargin = dp(8);
        bottom.addView(progressBox, progressBoxParams);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setPadding(0, 0, 0, 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || player == null) {
                    return;
                }
                long duration = player.getDuration();
                if (duration > 0 && duration != C.TIME_UNSET) {
                    positionText.setText(formatTime(duration * progress / 1000));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
                setControlsVisible(true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (player != null) {
                    long duration = player.getDuration();
                    if (duration > 0 && duration != C.TIME_UNSET) {
                        player.seekTo(duration * seekBar.getProgress() / 1000);
                        danmakuView.notifySeek();
                    }
                }
                userSeeking = false;
                scheduleControlsHide();
            }
        });
        progressBox.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        progressBox.addView(timeRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        positionText = makeTimeText("00:00", Gravity.RIGHT);
        timeRow.addView(positionText, new LinearLayout.LayoutParams(dp(80), dp(42)));
        TextView slash = makeTimeText("/", Gravity.CENTER);
        timeRow.addView(slash, new LinearLayout.LayoutParams(dp(18), dp(42)));
        durationText = makeTimeText("--:--", Gravity.LEFT);
        timeRow.addView(durationText, new LinearLayout.LayoutParams(dp(86), dp(42)));

        sourceText = new TextView(this);
        sourceText.setTextColor(0xccffffff);
        sourceText.setTextSize(11);
        sourceText.setSingleLine(true);
        sourceText.setText("Android ExoPlayer");
        progressBox.addView(sourceText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(76));
        actionsParams.leftMargin = dp(14);
        bottom.addView(actions, actionsParams);

        TextView volume = iconText("Vol", 13);
        volume.setOnClickListener(v -> showCenterHint("音量请使用系统音量键"));
        actions.addView(volume, new LinearLayout.LayoutParams(dp(60), dp(60)));

        TextView dmSetting = iconText("DM", 13);
        dmSetting.setOnClickListener(v -> showDanmakuDialog());
        actions.addView(dmSetting, new LinearLayout.LayoutParams(dp(60), dp(60)));

        danmakuText = iconText("弹", 18);
        danmakuText.setOnClickListener(v -> toggleDanmaku());
        actions.addView(danmakuText, new LinearLayout.LayoutParams(dp(60), dp(60)));

        qualityText = pillText("画质", true);
        qualityText.setOnClickListener(v -> showQualityDialog());
        actions.addView(qualityText, new LinearLayout.LayoutParams(dp(86), dp(54)));

        speedText = pillText("倍速", true);
        speedText.setOnClickListener(v -> showSpeedDialog());
        actions.addView(speedText, new LinearLayout.LayoutParams(dp(78), dp(54)));

        TextView detail = pillText("详情", true);
        detail.setOnClickListener(v -> toggleDetailPanel());
        actions.addView(detail, new LinearLayout.LayoutParams(dp(78), dp(54)));

        TextView audio = pillText("音频", true);
        audio.setOnClickListener(v -> showAudioSourceDialog());
        actions.addView(audio, new LinearLayout.LayoutParams(dp(78), dp(54)));

        TextView fullscreen = iconText("全", 18);
        fullscreen.setOnClickListener(v -> toggleResizeMode());
        actions.addView(fullscreen, new LinearLayout.LayoutParams(dp(60), dp(60)));
        return bottom;
    }

    private View buildDetailPanel() {
        LinearLayout panel = new LinearLayout(this);
        detailPanel = panel;
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(10), dp(20), dp(10));
        panel.setBackground(rounded(0x88000000, dp(6)));
        panel.setVisibility(View.GONE);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(560), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        params.rightMargin = dp(20);
        panel.setLayoutParams(params);

        detailTitle = new TextView(this);
        detailTitle.setText(title);
        detailTitle.setTextColor(Color.WHITE);
        detailTitle.setTextSize(18);
        detailTitle.setTypeface(Typeface.DEFAULT_BOLD);
        detailTitle.setMaxLines(2);
        panel.addView(detailTitle);

        detailMeta = new TextView(this);
        detailMeta.setTextColor(0xccffffff);
        detailMeta.setTextSize(12);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(8);
        panel.addView(detailMeta, metaParams);

        HorizontalScrollView pageScroll = new HorizontalScrollView(this);
        pageScroll.setHorizontalScrollBarEnabled(false);
        pageStrip = new LinearLayout(this);
        pageStrip.setOrientation(LinearLayout.HORIZONTAL);
        pageScroll.addView(pageStrip);
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        pageParams.topMargin = dp(10);
        panel.addView(pageScroll, pageParams);

        ScrollView descScroll = new ScrollView(this);
        detailDesc = new TextView(this);
        detailDesc.setTextColor(0xccffffff);
        detailDesc.setTextSize(12);
        detailDesc.setLineSpacing(dp(2), 1.0f);
        descScroll.addView(detailDesc);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(128));
        descParams.topMargin = dp(8);
        panel.addView(descScroll, descParams);
        return panel;
    }

    private void loadVideoDetail() {
        showCenterHint("加载视频详情");
        String url = "https://api.bilibili.com/x/web-interface/view?bvid=" + urlEncode(bvid);
        requestJson(url, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                JSONObject data = json.optJSONObject("data");
                if (data == null) {
                    onFailure(new IOException("missing detail data"));
                    return;
                }
                aid = data.optLong("aid", 0);
                title = data.optString("title", title);
                desc = data.optString("desc", "");
                JSONObject ownerObj = data.optJSONObject("owner");
                owner = ownerObj == null ? "" : ownerObj.optString("name", "");
                JSONObject statObj = data.optJSONObject("stat");
                String meta = owner;
                if (statObj != null) {
                    meta += "  播放 " + compactCount(statObj.optLong("view", 0))
                            + "  弹幕 " + compactCount(statObj.optLong("danmaku", 0));
                }
                JSONArray pageArray = data.optJSONArray("pages");
                pages.clear();
                if (pageArray != null) {
                    for (int i = 0; i < pageArray.length(); i++) {
                        JSONObject item = pageArray.optJSONObject(i);
                        if (item == null) continue;
                        pages.add(new PageInfo(item.optLong("cid", 0),
                                item.optString("part", "P" + (i + 1)), item.optInt("duration", 0)));
                    }
                }
                if (pages.isEmpty() && cid > 0) {
                    pages.add(new PageInfo(cid, title, 0));
                }
                if (cid <= 0 && !pages.isEmpty()) {
                    cid = pages.get(0).cid;
                }
                selectedPageIndex = findPageIndex(cid);
                updateDetailViews(meta);
                requestPlayUrl();
                requestDanmaku();
            }

            @Override
            public void onFailure(Throwable error) {
                showCenterHint("详情加载失败: " + shortMessage(error));
            }
        });
    }

    private void requestPlayUrl() {
        if (cid <= 0) {
            showCenterHint("缺少 cid");
            return;
        }
        showCenterHint("请求播放地址");
        HashMap<String, String> params = new HashMap<>();
        params.put("bvid", bvid);
        params.put("cid", String.valueOf(cid));
        params.put("gaia_source", "view-card");
        params.put("from_client", "BROWSER");
        params.put("is_main_page", "false");
        params.put("need_fragment", "false");
        params.put("isGaiaAvoided", "true");
        params.put("voice_balance", "1");
        params.put("web_location", "1315873");
        params.put("dolby", "5");
        params.put("qn", String.valueOf(requestedQuality));
        params.put("fourk", "1");
        params.put("fnval", "4048");
        params.put("fnver", "0");

        ensureWbiKeys(new WbiCallback() {
            @Override
            public void onKeys(String imgKey, String subKey) {
                String url = "https://api.bilibili.com/x/player/wbi/playurl?" + signedQuery(params, imgKey, subKey);
                requestJson(url, new JsonCallback() {
                    @Override
                    public void onSuccess(JSONObject json) {
                        JSONObject data = json.optJSONObject("data");
                        if (data == null) {
                            onFailure(new IOException("missing playurl data"));
                            return;
                        }
                        parsePlayUrl(data);
                        initializePlayer();
                        requestDanmaku();
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        showCenterHint("播放地址失败: " + shortMessage(error));
                    }
                });
            }

            @Override
            public void onFailure(Throwable error) {
                showCenterHint("WBI 失败: " + shortMessage(error));
            }
        });
    }

    private void parsePlayUrl(JSONObject data) {
        videoUrls.clear();
        audioUrls.clear();
        qualities.clear();
        JSONArray acceptQn = data.optJSONArray("accept_quality");
        JSONArray acceptDesc = data.optJSONArray("accept_description");
        if (acceptQn != null) {
            for (int i = 0; i < acceptQn.length(); i++) {
                String label = acceptDesc != null ? acceptDesc.optString(i, String.valueOf(acceptQn.optInt(i))) : String.valueOf(acceptQn.optInt(i));
                qualities.add(new QualityInfo(acceptQn.optInt(i), label));
            }
        }
        JSONObject dash = data.optJSONObject("dash");
        if (dash != null) {
            JSONArray videos = dash.optJSONArray("video");
            JSONObject selectedVideo = selectVideo(videos, data.optInt("quality", requestedQuality));
            addBaseAndBackups(videoUrls, selectedVideo);
            JSONObject audio = selectAudio(dash);
            addBaseAndBackups(audioUrls, audio);
        }
        if (videoUrls.isEmpty()) {
            JSONArray durl = data.optJSONArray("durl");
            if (durl != null) {
                for (int i = 0; i < durl.length(); i++) {
                    String url = durl.optJSONObject(i) == null ? "" : durl.optJSONObject(i).optString("url", "");
                    if (!TextUtils.isEmpty(url)) videoUrls.add(url);
                }
            }
        }
        requestedQuality = data.optInt("quality", requestedQuality);
        videoIndex = 0;
        audioIndex = 0;
        updateQualityText();
        updateSourceText();
        Log.i(TAG, "Parsed playurl quality=" + requestedQuality + " videos=" + videoUrls.size() + " audios=" + audioUrls.size());
    }

    private JSONObject selectVideo(JSONArray videos, int quality) {
        if (videos == null || videos.length() == 0) {
            return null;
        }
        JSONObject fallback = videos.optJSONObject(0);
        for (int i = 0; i < videos.length(); i++) {
            JSONObject item = videos.optJSONObject(i);
            if (item == null) continue;
            if (item.optInt("id") == quality && item.optInt("codecid") == 12) {
                return item;
            }
        }
        for (int i = 0; i < videos.length(); i++) {
            JSONObject item = videos.optJSONObject(i);
            if (item != null && item.optInt("id") == quality) {
                return item;
            }
        }
        return fallback;
    }

    private JSONObject selectAudio(JSONObject dash) {
        JSONArray audios = dash.optJSONArray("audio");
        if (audios != null && audios.length() > 0) {
            int[] preferred = {30280, 30232, 30216};
            for (int id : preferred) {
                for (int i = 0; i < audios.length(); i++) {
                    JSONObject item = audios.optJSONObject(i);
                    if (item != null && item.optInt("id") == id) {
                        return item;
                    }
                }
            }
            return audios.optJSONObject(0);
        }
        JSONArray dolby = dash.optJSONArray("dolby");
        if (dolby != null && dolby.length() > 0) {
            return dolby.optJSONObject(0);
        }
        return null;
    }

    private void addBaseAndBackups(ArrayList<String> target, JSONObject media) {
        if (media == null) {
            return;
        }
        String base = media.optString("baseUrl", media.optString("base_url", ""));
        if (!TextUtils.isEmpty(base)) {
            target.add(base);
        }
        JSONArray backups = media.optJSONArray("backupUrl");
        if (backups == null) {
            backups = media.optJSONArray("backup_url");
        }
        if (backups != null) {
            for (int i = 0; i < backups.length(); i++) {
                String url = backups.optString(i, "");
                if (!TextUtils.isEmpty(url)) {
                    target.add(url);
                }
            }
        }
        ArrayList<String> deduped = distinctNonEmpty(target);
        target.clear();
        target.addAll(deduped);
    }

    private void requestDanmaku() {
        if (cid <= 0) {
            return;
        }
        String url = "https://api.bilibili.com/x/v1/dm/list.so?oid=" + cid;
        Request request = baseRequest(url).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Danmaku load failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                byte[] bytes = response.body() == null ? new byte[0] : response.body().bytes();
                if (!response.isSuccessful()) {
                    Log.w(TAG, "Danmaku HTTP " + response.code());
                    return;
                }
                String body = decodeDanmakuBody(bytes);
                ArrayList<DanmakuOverlayView.Item> items = parseDanmakuXml(body);
                runOnUiThread(() -> {
                    danmakuView.setDanmakus(items);
                    showCenterHint("弹幕 " + items.size() + " 条");
                });
            }
        });
    }

    private ArrayList<DanmakuOverlayView.Item> parseDanmakuXml(String xml) {
        ArrayList<DanmakuOverlayView.Item> items = new ArrayList<>();
        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(new StringReader(xml));
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "d".equals(parser.getName())) {
                    String p = parser.getAttributeValue(null, "p");
                    String text = parser.nextText();
                    if (p != null && text != null) {
                        String[] parts = p.split(",");
                        long timeMs = (long) (Float.parseFloat(parts[0]) * 1000f);
                        int type = parts.length > 1 ? parseInt(parts[1], 1) : 1;
                        int color = parts.length > 3 ? parseInt(parts[3], 0xffffff) : 0xffffff;
                        items.add(new DanmakuOverlayView.Item(timeMs, type, color, text));
                    }
                }
                event = parser.next();
            }
        } catch (Throwable error) {
            Log.w(TAG, "Parse danmaku failed: " + error.getMessage());
        }
        Collections.sort(items);
        return items;
    }

    private void applyDirectUrls() {
        titleText.setText(title);
        detailTitle.setText(title);
        detailMeta.setText("Android ExoPlayer");
        initializePlayer();
    }

    private void initializePlayer() {
        if (videoUrls.isEmpty()) {
            showCenterHint("没有可用播放地址");
            return;
        }
        releasePlayer();
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBackBuffer(DefaultLoadControl.DEFAULT_MAX_BUFFER_MS, true)
                .build();
        player = new ExoPlayer.Builder(this, renderersFactory)
                .setLoadControl(loadControl)
                .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
                .build();
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setPlaybackSpeed(playbackSpeed);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                logPlaybackError(error);
                if (isCurrentVideoHttpError(error) && tryNextVideo()) return;
                if (tryNextAudio()) return;
                if (tryNextVideo()) return;
                showCenterHint("播放失败: " + error.getErrorCodeName());
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updateBuffering(playbackState);
                if (playbackState == Player.STATE_READY) {
                    Log.i(TAG, "Playback ready duration=" + player.getDuration()
                            + " audioIndex=" + audioIndex + " videoIndex=" + videoIndex);
                } else if (playbackState == Player.STATE_ENDED) {
                    reportProgress("ended");
                }
                updateProgress();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                playText.setText(isPlaying ? "Pause" : "Play");
                danmakuView.invalidate();
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                updateStatus();
            }
        });
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onVideoInputFormatChanged(EventTime eventTime, Format format,
                                                  DecoderReuseEvaluation decoderReuseEvaluation) {
                Log.i(TAG, "Video format mime=" + format.sampleMimeType
                        + " codec=" + format.codecs
                        + " size=" + format.width + "x" + format.height
                        + " color=" + format.colorInfo);
                updateStatus();
            }

            @Override
            public void onAudioInputFormatChanged(EventTime eventTime, Format format,
                                                  DecoderReuseEvaluation decoderReuseEvaluation) {
                Log.i(TAG, "Audio format mime=" + format.sampleMimeType
                        + " codec=" + format.codecs
                        + " channels=" + format.channelCount
                        + " rate=" + format.sampleRate);
                updateStatus();
            }

            @Override
            public void onVideoDecoderInitialized(EventTime eventTime, String decoderName,
                                                  long initializedTimestampMs,
                                                  long initializationDurationMs) {
                Log.i(TAG, "Video decoder initialized decoder=" + decoderName
                        + " initMs=" + initializationDurationMs);
            }

            @Override
            public void onAudioDecoderInitialized(EventTime eventTime, String decoderName,
                                                  long initializedTimestampMs,
                                                  long initializationDurationMs) {
                Log.i(TAG, "Audio decoder initialized decoder=" + decoderName
                        + " initMs=" + initializationDurationMs);
            }

            @Override
            public void onLoadError(EventTime eventTime, LoadEventInfo loadEventInfo,
                                    MediaLoadData mediaLoadData, IOException error,
                                    boolean wasCanceled) {
                Log.e(TAG, "Load error track=" + mediaLoadData.trackType
                        + " uri=" + shortenForLog(String.valueOf(loadEventInfo.uri))
                        + " cause=" + describeThrowable(error), error);
            }
        });
        setMediaSource(false);
    }

    private void releasePlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private void setMediaSource(boolean preservePosition) {
        if (player == null || videoUrls.isEmpty()) {
            return;
        }
        long positionMs = preservePosition ? Math.max(0, player.getCurrentPosition()) : startSeconds * 1000L;
        videoUrl = videoUrls.get(videoIndex);
        MediaSource videoSource = mediaSource("video", videoUrl, rotated(videoUrls, videoIndex));
        MediaSource source = videoSource;
        if (!audioUrls.isEmpty() && audioIndex < audioUrls.size()) {
            source = new MergingMediaSource(true, true, videoSource,
                    mediaSource("audio", audioUrls.get(audioIndex), rotated(audioUrls, audioIndex)));
        }
        player.setMediaSource(source);
        if (positionMs > 0) {
            player.seekTo(positionMs);
        }
        player.prepare();
        player.play();
        updateSourceText();
        scheduleControlsHide();
    }

    private MediaSource mediaSource(String kind, String url, List<String> candidates) {
        DataSource.Factory httpFactory = createCdnDataSourceFactory(kind, candidates);
        DefaultDataSource.Factory factory = new DefaultDataSource.Factory(this, httpFactory);
        return new DefaultMediaSourceFactory(factory).createMediaSource(MediaItem.fromUri(Uri.parse(url)));
    }

    private DataSource.Factory createCdnDataSourceFactory(String kind, List<String> urls) {
        DataSource.Factory upstream = createResolvingHttpFactory(kind);
        List<Uri> uris = new ArrayList<>();
        for (String url : urls) {
            if (!TextUtils.isEmpty(url)) {
                uris.add(Uri.parse(url.trim()));
            }
        }
        if (uris.size() <= 1) {
            return upstream;
        }
        return new CdnFailoverDataSourceFactory(upstream, new CdnFailoverState(kind, uris));
    }

    private DataSource.Factory createResolvingHttpFactory(String kind) {
        HashMap<String, String> headers = biliHeaders();
        TransferListener listener = new TransferListener() {
            @Override
            public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
            }

            @Override
            public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
                if (isNetwork && dataSpec.uri.getHost() != null) {
                    Log.i(TAG, "HTTP start kind=" + kind
                            + " host=" + dataSpec.uri.getHost().toLowerCase(Locale.US)
                            + " pos=" + dataSpec.position
                            + " len=" + dataSpec.length);
                }
            }

            @Override
            public void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
            }

            @Override
            public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
            }
        };

        OkHttpDataSource.Factory httpFactory = new OkHttpDataSource.Factory(httpClient)
                .setUserAgent(USER_AGENT)
                .setDefaultRequestProperties(headers)
                .setTransferListener(listener);
        return new ResolvingDataSource.Factory(httpFactory, dataSpec -> {
            if (dataSpec.position == 0 && dataSpec.length == -1
                    && String.valueOf(dataSpec.uri).contains(".m4s")) {
                HashMap<String, String> rangeHeader = new HashMap<>();
                rangeHeader.put("Range", "bytes=0-");
                return dataSpec.withAdditionalHeaders(rangeHeader);
            }
            return dataSpec;
        });
    }

    private void updateDetailViews(String meta) {
        titleText.setText(title);
        detailTitle.setText(title);
        detailMeta.setText(meta);
        detailDesc.setText(desc);
        peopleText.setText(meta);
        pageStrip.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            int index = i;
            TextView item = pillText("P" + (i + 1) + " " + pages.get(i).part, true);
            item.setSelected(i == selectedPageIndex);
            item.setOnClickListener(v -> {
                selectedPageIndex = index;
                cid = pages.get(index).cid;
                startSeconds = 0;
                requestPlayUrl();
                requestDanmaku();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            params.rightMargin = dp(8);
            pageStrip.addView(item, params);
        }
    }

    private void showQualityDialog() {
        if (qualities.isEmpty()) {
            Toast.makeText(this, "暂无画质列表", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[qualities.size()];
        for (int i = 0; i < qualities.size(); i++) {
            QualityInfo q = qualities.get(i);
            items[i] = (q.id == requestedQuality ? "* " : "") + q.label;
        }
        new AlertDialog.Builder(this)
                .setTitle("画质")
                .setItems(items, (dialog, which) -> {
                    requestedQuality = qualities.get(which).id;
                    requestPlayUrl();
                })
                .show();
    }

    private void showAudioSourceDialog() {
        if (audioUrls.isEmpty()) {
            Toast.makeText(this, "暂无独立音频", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[audioUrls.size()];
        for (int i = 0; i < audioUrls.size(); i++) {
            items[i] = (i == audioIndex ? "* " : "") + "音频 " + (i + 1) + "  " + hostOf(audioUrls.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle("音频")
                .setItems(items, (dialog, which) -> {
                    audioIndex = which;
                    setMediaSource(true);
                })
                .show();
    }

    private void showSpeedDialog() {
        float[] values = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
        String[] items = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            items[i] = (Math.abs(values[i] - playbackSpeed) < 0.01f ? "* " : "") + trimFloat(values[i]) + "x";
        }
        new AlertDialog.Builder(this)
                .setTitle("倍速")
                .setItems(items, (dialog, which) -> {
                    playbackSpeed = values[which];
                    if (player != null) player.setPlaybackSpeed(playbackSpeed);
                    speedText.setText(trimFloat(playbackSpeed) + "x");
                })
                .show();
    }

    private void showDanmakuDialog() {
        String[] items = {
                "显示弹幕: " + (danmakuView.isDanmakuEnabled() ? "开" : "关"),
                "重新加载弹幕"
        };
        new AlertDialog.Builder(this)
                .setTitle("弹幕")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        toggleDanmaku();
                    } else {
                        requestDanmaku();
                    }
                })
                .show();
    }

    private void showSettingsDialog() {
        String[] items = {"画面比例: " + (fillScreen ? "填充" : "适应"), "详情面板", "当前解码信息"};
        new AlertDialog.Builder(this)
                .setTitle("播放设置")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        toggleResizeMode();
                    } else if (which == 1) {
                        toggleDetailPanel();
                    } else {
                        showCenterHint(peopleText.getText().toString());
                    }
                })
                .show();
    }

    private void toggleDanmaku() {
        boolean next = !danmakuView.isDanmakuEnabled();
        danmakuView.setDanmakuEnabled(next);
        danmakuText.setText(next ? "弹" : "关");
        showCenterHint(next ? "弹幕已开启" : "弹幕已关闭");
    }

    private void toggleDetailPanel() {
        if (detailPanel == null) {
            return;
        }
        detailPanel.setVisibility(detailPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        scheduleControlsHide();
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) player.pause(); else player.play();
        updateProgress();
        scheduleControlsHide();
    }

    private void toggleResizeMode() {
        fillScreen = !fillScreen;
        playerView.setResizeMode(fillScreen
                ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        showCenterHint(fillScreen ? "画面填充" : "画面适应");
        scheduleControlsHide();
    }

    private boolean tryNextAudio() {
        if (audioIndex + 1 >= audioUrls.size()) return false;
        audioIndex++;
        setMediaSource(true);
        return true;
    }

    private boolean tryNextVideo() {
        if (videoIndex + 1 >= videoUrls.size()) return false;
        videoIndex++;
        audioIndex = 0;
        setMediaSource(true);
        return true;
    }

    private void toggleControls() {
        setControlsVisible(!controlsVisible);
        if (controlsVisible) scheduleControlsHide();
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        controlsLayer.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible && detailPanel != null) {
            detailPanel.setVisibility(View.GONE);
        }
        if (visible) applyImmersiveMode();
    }

    private void scheduleControlsHide() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS);
    }

    private void updateProgress() {
        if (player == null) return;
        long position = Math.max(0, player.getCurrentPosition());
        long duration = player.getDuration();
        if (!userSeeking) {
            positionText.setText(formatTime(position));
            if (duration > 0 && duration != C.TIME_UNSET) {
                durationText.setText(formatTime(duration));
                seekBar.setProgress((int) Math.min(1000, position * 1000 / duration));
            } else {
                durationText.setText("--:--");
                seekBar.setProgress(0);
            }
        }
        playText.setText(player.isPlaying() ? "Pause" : "Play");
        long now = System.currentTimeMillis();
        if (now - lastProgressReportMs >= HISTORY_REPORT_INTERVAL_MS) {
            reportProgress("tick");
            lastProgressReportMs = now;
        }
    }

    private void updateBuffering(int playbackState) {
        bufferingLayer.setVisibility(playbackState == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
        if (playbackState == Player.STATE_BUFFERING) {
            centerHint.setText("缓冲中");
        }
    }

    private void updateStatus() {
        if (player == null) return;
        VideoSize size = player.getVideoSize();
        StringBuilder builder = new StringBuilder();
        if (size.width > 0 && size.height > 0) builder.append(size.width).append("x").append(size.height);
        Format videoFormat = player.getVideoFormat();
        if (videoFormat != null && videoFormat.codecs != null) builder.append(" ").append(videoFormat.codecs);
        Format audioFormat = player.getAudioFormat();
        if (audioFormat != null && audioFormat.codecs != null) builder.append(" / ").append(audioFormat.codecs);
        if (builder.length() > 0) peopleText.setText(builder.toString());
    }

    private void updateQualityText() {
        String label = "画质";
        for (QualityInfo q : qualities) {
            if (q.id == requestedQuality) {
                label = q.label;
                break;
            }
        }
        qualityText.setText(label);
    }

    private void updateSourceText() {
        if (videoUrls.isEmpty()) return;
        String text = "Video " + (videoIndex + 1) + "/" + videoUrls.size() + " " + hostOf(videoUrls.get(videoIndex));
        if (!audioUrls.isEmpty()) {
            text += "    Audio " + (audioIndex + 1) + "/" + audioUrls.size() + " " + hostOf(audioUrls.get(audioIndex));
        }
        sourceText.setText(text);
    }

    private void reportProgress(String reason) {
        if (player == null) return;
        long position = Math.max(0, player.getCurrentPosition());
        long duration = player.getDuration();
        Log.i(TAG, "Progress reason=" + reason
                + " aid=" + aid + " cid=" + cid
                + " position=" + (position / 1000)
                + " duration=" + (duration > 0 && duration != C.TIME_UNSET ? duration / 1000 : -1));
    }

    private boolean isCurrentVideoHttpError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpDataSource.InvalidResponseCodeException) {
                HttpDataSource.InvalidResponseCodeException httpError =
                        (HttpDataSource.InvalidResponseCodeException) current;
                return videoUrls.contains(String.valueOf(httpError.dataSpec.uri));
            }
            current = current.getCause();
        }
        return false;
    }

    private void requestJson(String url, JsonCallback callback) {
        Request request = baseRequest(url).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> callback.onFailure(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> callback.onFailure(new IOException("HTTP " + response.code())));
                    return;
                }
                try {
                    JSONObject json = new JSONObject(body);
                    int code = json.optInt("code", 0);
                    if (code != 0) {
                        throw new IOException(json.optString("message", json.optString("msg", "code " + code)));
                    }
                    runOnUiThread(() -> callback.onSuccess(json));
                } catch (Throwable error) {
                    runOnUiThread(() -> callback.onFailure(error));
                }
            }
        });
    }

    private Request.Builder baseRequest(String url) {
        Request.Builder builder = new Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", referer)
                .header("Accept", "*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        if (!TextUtils.isEmpty(cookie)) builder.header("Cookie", cookie);
        return builder;
    }

    private String decodeDanmakuBody(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        try {
            if (bytes.length >= 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
                return readStream(new GZIPInputStream(new ByteArrayInputStream(bytes)));
            }
            if (bytes.length >= 2 && (bytes[0] & 0xff) == 0x78) {
                return readStream(new InflaterInputStream(new ByteArrayInputStream(bytes)));
            }
            return readStream(new InflaterInputStream(new ByteArrayInputStream(bytes), new Inflater(true)));
        } catch (Throwable error) {
            Log.w(TAG, "Danmaku decompress failed, fallback to utf8: " + error.getMessage()
                    + " head=" + hexHead(bytes));
        }
        try {
            return new String(bytes, "UTF-8");
        } catch (Exception ignored) {
            return new String(bytes);
        }
    }

    private String readStream(java.io.InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        StringBuilder builder = new StringBuilder();
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            builder.append(new String(buffer, 0, read));
        }
        inputStream.close();
        return builder.toString();
    }

    private HashMap<String, String> biliHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Referer", referer);
        headers.put("Accept", "*/*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Sec-Fetch-Dest", "video");
        headers.put("Sec-Fetch-Mode", "no-cors");
        headers.put("Sec-Fetch-Site", "cross-site");
        if (!TextUtils.isEmpty(cookie)) headers.put("Cookie", cookie);
        return headers;
    }

    private void ensureWbiKeys(WbiCallback callback) {
        requestJson("https://api.bilibili.com/x/web-interface/nav", new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                JSONObject data = json.optJSONObject("data");
                JSONObject wbi = data == null ? null : data.optJSONObject("wbi_img");
                String img = wbi == null ? "" : keyFromUrl(wbi.optString("img_url", ""));
                String sub = wbi == null ? "" : keyFromUrl(wbi.optString("sub_url", ""));
                if (TextUtils.isEmpty(img) || TextUtils.isEmpty(sub)) {
                    callback.onFailure(new IOException("missing wbi keys"));
                } else {
                    callback.onKeys(img, sub);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                callback.onFailure(error);
            }
        });
    }

    private String signedQuery(Map<String, String> input, String imgKey, String subKey) {
        TreeMap<String, String> params = new TreeMap<>(input);
        params.put("wts", String.valueOf(System.currentTimeMillis() / 1000));
        String mixinKey = mixinKey(imgKey + subKey);
        StringBuilder query = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) query.append('&');
            first = false;
            query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(filterWbiValue(entry.getValue())));
        }
        String rid = md5(query + mixinKey);
        return query + "&w_rid=" + rid;
    }

    private String mixinKey(String raw) {
        int[] table = {46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
                33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
                61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
                36, 20, 34, 44, 52};
        StringBuilder out = new StringBuilder();
        for (int index : table) {
            if (index >= 0 && index < raw.length()) out.append(raw.charAt(index));
        }
        return out.substring(0, Math.min(32, out.length()));
    }

    private String md5(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes());
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format(Locale.US, "%02x", b));
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String keyFromUrl(String url) {
        String file = url.substring(url.lastIndexOf('/') + 1);
        int dot = file.indexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }

    private String filterWbiValue(String value) {
        return value == null ? "" : value.replaceAll("[!'()*]", "");
    }

    private void showCenterHint(String text) {
        Log.i(TAG, text);
        if (centerHint != null) {
            centerHint.setText(text);
            centerHint.setVisibility(View.VISIBLE);
            centerHint.removeCallbacks(null);
        }
    }

    private TextView makeTimeText(String text, int gravity) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setGravity(gravity | Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView iconText(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setBackground(rounded(0x22000000, dp(30)));
        return view;
    }

    private TextView pillText(String text, boolean compact) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(compact ? 14 : 13);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setBackground(rounded(0x22000000, dp(8)));
        return view;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable topGradient() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xdd000000, 0x00000000});
    }

    private GradientDrawable bottomGradient() {
        return new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xdd000000, 0x00000000});
    }

    private void logPlaybackError(PlaybackException error) {
        Log.e(TAG, "Playback error audioIndex=" + audioIndex
                + " videoIndex=" + videoIndex
                + " code=" + error.errorCode
                + " name=" + error.getErrorCodeName()
                + " message=" + error.getMessage()
                + " cause=" + describeThrowable(error), error);
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int findPageIndex(long targetCid) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).cid == targetCid) return i;
        }
        return 0;
    }

    private static ArrayList<String> distinctNonEmpty(List<String> input) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String item : input) {
            if (!TextUtils.isEmpty(item)) set.add(item.trim());
        }
        return new ArrayList<>(set);
    }

    private static List<String> rotated(List<String> input, int start) {
        ArrayList<String> result = new ArrayList<>();
        if (input.isEmpty()) return result;
        for (int i = 0; i < input.size(); i++) result.add(input.get((start + i) % input.size()));
        return result;
    }

    private static String hostOf(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host == null ? "" : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String formatTime(long timeMs) {
        if (timeMs < 0 || timeMs == C.TIME_UNSET) return "--:--";
        long totalSeconds = timeMs / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        if (hours > 0) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private static String compactCount(long value) {
        if (value >= 100000000) return String.format(Locale.US, "%.1f亿", value / 100000000f);
        if (value >= 10000) return String.format(Locale.US, "%.1f万", value / 10000f);
        return String.valueOf(value);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String trimFloat(float value) {
        if (Math.abs(value - Math.round(value)) < 0.01f) return String.valueOf((int) value);
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return "";
        }
    }

    private static String shortMessage(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return TextUtils.isEmpty(message) ? error.getClass().getSimpleName() : message;
    }

    private static String hexHead(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(bytes == null ? 0 : bytes.length, 8);
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(' ');
            builder.append(String.format(Locale.US, "%02x", bytes[i] & 0xff));
        }
        return builder.toString();
    }

    public static String shortenForLog(String value) {
        if (value == null) return "";
        return value.length() <= 120 ? value : value.substring(0, 120) + "...";
    }

    private static String describeThrowable(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) builder.append(" <- ");
            builder.append(current.getClass().getSimpleName());
            if (current.getMessage() != null) builder.append(": ").append(current.getMessage());
            if (current instanceof HttpDataSource.InvalidResponseCodeException) {
                HttpDataSource.InvalidResponseCodeException httpError =
                        (HttpDataSource.InvalidResponseCodeException) current;
                builder.append(" responseCode=").append(httpError.responseCode);
                Map<String, java.util.List<String>> responseHeaders = httpError.headerFields;
                if (responseHeaders != null) builder.append(" responseHeaders=").append(responseHeaders.keySet());
            }
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }

    private interface JsonCallback {
        void onSuccess(JSONObject json);
        void onFailure(Throwable error);
    }

    private interface WbiCallback {
        void onKeys(String imgKey, String subKey);
        void onFailure(Throwable error);
    }

    private static final class PageInfo {
        final long cid;
        final String part;
        final int duration;

        PageInfo(long cid, String part, int duration) {
            this.cid = cid;
            this.part = part;
            this.duration = duration;
        }
    }

    private static final class QualityInfo {
        final int id;
        final String label;

        QualityInfo(int id, String label) {
            this.id = id;
            this.label = label;
        }
    }
}
