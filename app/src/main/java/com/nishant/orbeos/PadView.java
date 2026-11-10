package com.nishant.orbeos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** Control Center toggle tile: glass square, icon on top, label + state below. */
final class PadView extends View {
    private final float d;
    final Theme.Surface surface;
    private final Paint iconPaint;
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint statePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final int height;
    final int icon;
    final String label;
    String state;

    PadView(Context c, int icon, String label, String state, float density) {
        super(c);
        d = density;
        this.icon = icon;
        this.label = label;
        this.state = state;
        height = Math.round(104 * density);
        surface = new Theme.Surface(Theme.ROLE_GLASS, 22 * density, density);
        setBackground(surface);
        iconPaint = Icons.paint(density * 1.1f);
        labelPaint.setTextSize(15 * density);
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statePaint.setTextSize(13 * density);
        setFocusable(true);
        setClickable(true);
        setContentDescription(label);
    }

    void setState(String s, boolean on) {
        state = s;
        surface.setActive(on);
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), height);
    }

    @Override
    protected void onFocusChanged(boolean gain, int dir, Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        surface.setFocused(gain);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        boolean f = isFocused();
        int fg = f ? Theme.onFocus : Theme.text;
        float pad = 16 * d;
        iconPaint.setColor(surface.active && !f ? Theme.accent : fg);
        Icons.draw(c, icon, pad + 14 * d, pad + 14 * d, 28 * d, iconPaint);
        labelPaint.setColor(fg);
        float w = getWidth() - 2 * pad;
        CharSequence l = TextUtils.ellipsize(label, labelPaint, w, TextUtils.TruncateAt.END);
        c.drawText(l, 0, l.length(), pad, getHeight() - pad - 18 * d, labelPaint);
        if (state != null) {
            statePaint.setColor(f ? (Theme.onFocus & 0x00FFFFFF) | 0xA6000000 : Theme.text2);
            CharSequence s = TextUtils.ellipsize(state, statePaint, w, TextUtils.TruncateAt.END);
            c.drawText(s, 0, s.length(), pad, getHeight() - pad, statePaint);
        }
    }
}
