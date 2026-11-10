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

/** A focusable list row (icon, label, value, optional level bar) drawn in one View. */
final class RowView extends View {
    private final float d;
    final Theme.Surface surface;
    private final Paint iconPaint;
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint valuePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final int height;

    int icon;
    String label;
    String value;
    float level = -1f;   // 0..1 shows a level bar; <0 hides it
    boolean accentValue;
    Object payload;

    RowView(Context c, int icon, String label, float density, int heightDp) {
        super(c);
        d = density;
        height = Math.round(heightDp * density);
        this.icon = icon;
        this.label = label;
        surface = new Theme.Surface(Theme.ROLE_NONE, 16 * density, density);
        setBackground(surface);
        iconPaint = Icons.paint(density);
        labelPaint.setTextSize(17 * density);
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        valuePaint.setTextSize(15 * density);
        valuePaint.setTextAlign(Paint.Align.RIGHT);
        valuePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(label);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), height);
    }

    void setValue(String v) {
        value = v;
        invalidate();
    }

    void setLevel(float l) {
        level = l;
        invalidate();
    }

    @Override
    protected void onFocusChanged(boolean gain, int dir, Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        surface.setFocused(gain);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float pad = 16 * d;
        float x = pad;
        if (icon != 0) {
            iconPaint.setColor(isFocused() ? Theme.onFocus : Theme.text);
            Icons.draw(c, icon, x + 12 * d, h / 2f, 24 * d, iconPaint);
            x += 40 * d;
        }
        float right = w - pad;
        if (value != null) {
            valuePaint.setColor(isFocused() ? (Theme.onFocus & 0x00FFFFFF) | 0xB3000000
                    : accentValue ? Theme.accent : Theme.text2);
            float vy = h / 2f - (valuePaint.descent() + valuePaint.ascent()) / 2f;
            c.drawText(value, right, vy, valuePaint);
            right -= valuePaint.measureText(value) + 16 * d;
        }
        if (level >= 0f) {
            float bw = Math.min(160 * d, right - x - 120 * d);
            if (bw > 20 * d) {
                float bl = right - bw;
                r.set(bl, h / 2f - 3 * d, right, h / 2f + 3 * d);
                bar.setColor(Theme.hairline | 0x1A000000);
                c.drawRoundRect(r, 3 * d, 3 * d, bar);
                r.right = bl + bw * Math.max(0f, Math.min(1f, level));
                bar.setColor(Theme.accent);
                c.drawRoundRect(r, 3 * d, 3 * d, bar);
                right = bl - 16 * d;
            }
        }
        labelPaint.setColor(isFocused() ? Theme.onFocus : Theme.text);
        CharSequence shown = TextUtils.ellipsize(label, labelPaint, Math.max(0, right - x), TextUtils.TruncateAt.END);
        float ly = h / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f;
        c.drawText(shown, 0, shown.length(), x, ly, labelPaint);
    }
}
