package com.nishant.orbeos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * One tile = one hand-drawn View (card + art + label). Focus changes never trigger
 * a layout pass; the lift is a RenderNode scale handled on the GPU.
 */
final class TileView extends View {
    static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    final int tileW, tileH, labelH, iconPx;
    final Theme.Surface surface;
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint monoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint;
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF artRect = new RectF();
    private final Rect tileBounds;

    AppRepo.AppEntry app;
    Object payload;
    boolean dim;
    private String label = "";
    private String mono = "";
    private int icon;
    private String shownLabel;
    private Bitmap art;
    private boolean artBanner;
    private boolean look;
    private boolean moving;

    TileView(Context c, int tileW, int tileH, int labelH, int iconPx, float density, boolean focusable) {
        super(c);
        this.tileW = tileW;
        this.tileH = tileH;
        this.labelH = labelH;
        this.iconPx = iconPx;
        tileBounds = new Rect(0, 0, tileW, tileH);
        surface = new Theme.Surface(Theme.ROLE_CARD, Math.min(tileH * 0.14f, 18 * density), density);
        surface.setBounds(tileBounds);
        iconPaint = Icons.paint(density * 1.4f);

        monoPaint.setTextAlign(Paint.Align.CENTER);
        monoPaint.setTextSize(tileH * 0.38f);
        monoPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(labelH * 0.48f);
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        setFocusable(focusable);
        setClickable(focusable);
        setLongClickable(focusable);
        setPivotX(tileW / 2f);
        setPivotY(tileH / 2f);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(tileW, tileH + labelH);
    }

    void bind(final AppRepo.AppEntry a) {
        app = a;
        icon = 0;
        setText(a.label, a.label.isEmpty() ? "?" : a.label.substring(0, 1).toUpperCase());
        art = null;
        artPaint.setShader(null);
        Bitmap b = AppRepo.cached(a);
        if (b != null) {
            setArt(b, a.banner);
        } else {
            AppRepo.request(getContext(), a, tileW, tileH, iconPx, new Runnable() {
                @Override
                public void run() {
                    if (app != a) return;
                    Bitmap bb = AppRepo.cached(a);
                    if (bb != null) setArt(bb, a.banner);
                }
            });
        }
        invalidate();
    }

    void bindIcon(String text, int iconId) {
        app = null;
        art = null;
        artPaint.setShader(null);
        icon = iconId;
        setText(text, "");
        invalidate();
    }

    private void setText(String text, String monoText) {
        label = text;
        mono = monoText;
        shownLabel = null;
        setContentDescription(text);
    }

    private void setArt(Bitmap b, boolean banner) {
        art = b;
        artBanner = banner;
        if (banner) {
            BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(tileW / (float) b.getWidth(), tileH / (float) b.getHeight());
            s.setLocalMatrix(m);
            artPaint.setShader(s);
        } else {
            artPaint.setShader(null);
        }
        invalidate();
    }

    void dropArt() {
        if (app == null) return;
        art = null;
        artPaint.setShader(null);
        invalidate();
    }

    void ensureArt() {
        if (app != null && art == null) bind(app);
    }

    void setLook(boolean on, boolean animate) {
        look = on;
        surface.setFocused(on);
        float s = moving ? 1.08f : on ? Theme.focusScale() : 1f;
        animate().cancel();
        int d = Theme.dur(180);
        if (animate && d > 0) {
            animate().scaleX(s).scaleY(s).setDuration(d).setInterpolator(EASE).start();
        } else {
            setScaleX(s);
            setScaleY(s);
        }
        invalidate();
    }

    void setMoving(boolean mv) {
        moving = mv;
        surface.setMoving(mv);
        setLook(look, true);
    }

    @Override
    protected void onFocusChanged(boolean gain, int direction, Rect previous) {
        super.onFocusChanged(gain, direction, previous);
        setLook(gain, true);
    }

    @Override
    protected void onDraw(Canvas c) {
        float r = surface.radius;
        int alpha = dim ? 110 : 255;
        if (art != null && artBanner) {
            artRect.set(tileBounds);
            artPaint.setAlpha(alpha);
            c.drawRoundRect(artRect, r, r, artPaint);
            surface.drawStroke(c);
        } else {
            surface.draw(c);
            if (art != null) {
                float l = (tileW - iconPx) / 2f, t = (tileH - iconPx) / 2f;
                artRect.set(l, t, l + iconPx, t + iconPx);
                artPaint.setAlpha(alpha);
                c.drawBitmap(art, null, artRect, artPaint);
            } else if (icon != 0) {
                iconPaint.setColor(Theme.text);
                iconPaint.setAlpha(alpha);
                Icons.draw(c, icon, tileW / 2f, tileH / 2f, tileH * 0.42f, iconPaint);
            } else {
                monoPaint.setColor(Theme.text);
                monoPaint.setAlpha(alpha);
                float y = tileH / 2f - (monoPaint.descent() + monoPaint.ascent()) / 2f;
                c.drawText(mono, tileW / 2f, y, monoPaint);
            }
        }
        if (shownLabel == null) {
            shownLabel = TextUtils.ellipsize(label, labelPaint, tileW, TextUtils.TruncateAt.END).toString();
        }
        labelPaint.setColor(look ? Theme.text : Theme.text2);
        if (dim) labelPaint.setAlpha(140);
        c.drawText(shownLabel, tileW / 2f, tileH + labelH * 0.74f, labelPaint);
    }
}
