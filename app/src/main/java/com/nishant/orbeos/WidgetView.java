package com.nishant.orbeos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * Glass widget card for the top shelf: a small caps heading, one big value, a line
 * of detail, an optional large icon and an optional row of usage bars.
 * Drawn in one View; updates only redraw this card.
 */
final class WidgetView extends View {
    private final float d;
    private final int w, h;
    final Theme.Surface surface;
    private final Paint iconPaint, bigIconPaint;
    private final TextPaint head = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint sub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    int headIcon;
    String heading = "";
    String value = "";
    String detail = "";
    int sideIcon;
    float[] bars;

    WidgetView(Context c, int widthDp, int heightDp, float density) {
        super(c);
        d = density;
        w = Math.round(widthDp * density);
        h = Math.round(heightDp * density);
        surface = new Theme.Surface(Theme.ROLE_GLASS, 24 * density, density);
        setBackground(surface);
        iconPaint = Icons.paint(density * 0.8f);
        bigIconPaint = Icons.paint(density * 1.3f);
        head.setTextSize(11.5f * density);
        head.setLetterSpacing(0.14f);
        head.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        big.setTextSize(34 * density);
        big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        sub.setTextSize(13.5f * density);
        sub.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        setFocusable(true);
        setClickable(true);
    }

    void set(String heading, String value, String detail) {
        this.heading = heading == null ? "" : heading;
        this.value = value == null ? "" : value;
        this.detail = detail == null ? "" : detail;
        setContentDescription(this.heading + " " + this.value + " " + this.detail);
        invalidate();
    }

    @Override
    protected void onMeasure(int a, int b) {
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onFocusChanged(boolean gain, int dir, Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        surface.setFocused(gain);
        float s = gain ? 1.05f : 1f;
        animate().scaleX(s).scaleY(s).setDuration(Theme.dur(180)).setInterpolator(TileView.EASE).start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        boolean f = isFocused();
        int fg = f ? Theme.onFocus : Theme.text;
        int fg2 = f ? (Theme.onFocus & 0x00FFFFFF) | 0xA6000000 : Theme.text2;
        float pad = 16 * d;
        float x = pad;

        if (headIcon != 0) {
            iconPaint.setColor(fg2);
            Icons.draw(c, headIcon, x + 7 * d, pad + 7 * d, 15 * d, iconPaint);
            x += 20 * d;
        }
        head.setColor(fg2);
        CharSequence hd = TextUtils.ellipsize(heading.toUpperCase(), head, w - x - pad, TextUtils.TruncateAt.END);
        c.drawText(hd, 0, hd.length(), x, pad + 11 * d, head);

        big.setColor(fg);
        float by = pad + 54 * d;
        c.drawText(value, pad, by, big);

        if (sideIcon != 0) {
            bigIconPaint.setColor(fg);
            Icons.draw(c, sideIcon, w - pad - 22 * d, pad + 40 * d, 40 * d, bigIconPaint);
        }

        sub.setColor(fg2);
        float subY = h - pad;
        if (bars != null && bars.length > 0) {
            float bw = w - 2 * pad, bx = pad, bh = 5 * d, top = by + 12 * d;
            r.set(bx, top, bx + bw, top + bh);
            bar.setColor(f ? 0x1F000000 : 0x1FFFFFFF);
            c.drawRoundRect(r, bh / 2, bh / 2, bar);
            float cx = bx;
            int[] cols = {Theme.accent, Theme.accent2, 0xFF4FD1C5};
            for (int i = 0; i < bars.length && i < 3; i++) {
                float seg = bw * Math.max(0f, Math.min(1f, bars[i]));
                if (seg < 1) continue;
                r.set(cx, top, cx + seg, top + bh);
                bar.setColor(cols[i]);
                c.drawRoundRect(r, bh / 2, bh / 2, bar);
                cx += seg;
            }
        }
        CharSequence dt = TextUtils.ellipsize(detail, sub, w - 2 * pad, TextUtils.TruncateAt.END);
        c.drawText(dt, 0, dt.length(), pad, subY, sub);
    }
}
