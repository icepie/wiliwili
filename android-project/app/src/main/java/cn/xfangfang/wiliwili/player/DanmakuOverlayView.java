package cn.xfangfang.wiliwili.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DanmakuOverlayView extends View {
    public interface PositionProvider {
        long getPositionMs();
        boolean isPlaying();
    }

    public static final class Item implements Comparable<Item> {
        public final long timeMs;
        public final int type;
        public final int color;
        public final String text;

        public Item(long timeMs, int type, int color, String text) {
            this.timeMs = timeMs;
            this.type = type;
            this.color = color;
            this.text = text;
        }

        @Override
        public int compareTo(Item other) {
            return Long.compare(timeMs, other.timeMs);
        }
    }

    private static final long SCROLL_WINDOW_MS = 7000;
    private static final long CENTER_WINDOW_MS = 4200;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Item> danmakus = new ArrayList<>();
    private PositionProvider positionProvider;
    private boolean danmakuEnabled = true;
    private float textSizePx;
    private int alpha = 204;

    public DanmakuOverlayView(Context context) {
        this(context, null);
    }

    public DanmakuOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        textSizePx = sp(24);
        fillPaint.setTypeface(Typeface.DEFAULT_BOLD);
        fillPaint.setTextSize(textSizePx);
        fillPaint.setColor(Color.WHITE);
        fillPaint.setAlpha(alpha);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(3));
        strokePaint.setTypeface(Typeface.DEFAULT_BOLD);
        strokePaint.setTextSize(textSizePx);
        strokePaint.setColor(Color.BLACK);
        strokePaint.setAlpha(alpha);
    }

    public void setPositionProvider(PositionProvider provider) {
        positionProvider = provider;
    }

    public void setDanmakuEnabled(boolean enabled) {
        danmakuEnabled = enabled;
        invalidate();
    }

    public boolean isDanmakuEnabled() {
        return danmakuEnabled;
    }

    public void setDanmakus(List<Item> items) {
        danmakus.clear();
        danmakus.addAll(items);
        Collections.sort(danmakus);
        invalidate();
    }

    public void notifySeek() {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!danmakuEnabled || danmakus.isEmpty() || positionProvider == null) {
            return;
        }

        long position = Math.max(0, positionProvider.getPositionMs());
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        int scrollLines = Math.max(1, (int) ((height * 0.62f) / (textSizePx * 1.45f)));
        int topLines = Math.max(1, (int) ((height * 0.20f) / (textSizePx * 1.45f)));
        int drawn = 0;

        for (int i = 0; i < danmakus.size(); i++) {
            Item item = danmakus.get(i);
            if (item.timeMs > position + 1000) {
                break;
            }
            if (TextUtils.isEmpty(item.text)) {
                continue;
            }

            fillPaint.setColor(item.color == 0 ? Color.WHITE : item.color | 0xff000000);
            fillPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
            float textWidth = fillPaint.measureText(item.text);

            if (item.type == 4 || item.type == 5) {
                long delta = position - item.timeMs;
                if (delta < 0 || delta > CENTER_WINDOW_MS) {
                    continue;
                }
                int line = Math.abs(item.text.hashCode()) % topLines;
                float x = (width - textWidth) * 0.5f;
                float base = item.type == 4
                        ? height - dp(92) - line * textSizePx * 1.45f
                        : dp(84) + line * textSizePx * 1.45f;
                drawText(canvas, item.text, x, base);
            } else {
                long delta = position - item.timeMs;
                if (delta < 0 || delta > SCROLL_WINDOW_MS) {
                    continue;
                }
                float progress = delta * 1f / SCROLL_WINDOW_MS;
                float x = width - progress * (width + textWidth);
                int line = Math.abs((int) (item.timeMs / 1000 + item.text.hashCode())) % scrollLines;
                float y = dp(68) + line * textSizePx * 1.45f;
                drawText(canvas, item.text, x, y);
            }

            drawn++;
            if (drawn > 80) {
                break;
            }
        }

        if (positionProvider.isPlaying()) {
            postInvalidateDelayed(16);
        }
    }

    private void drawText(Canvas canvas, String text, float x, float y) {
        canvas.drawText(text, x, y, strokePaint);
        canvas.drawText(text, x, y, fillPaint);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
