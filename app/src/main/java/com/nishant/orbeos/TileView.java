package com.nishant.orbeos;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * App icon card, TV-shelf style: 5:3 artwork with rounded corners, name shown only
 * when focused. Focus lifts the card (scale + soft shadow), gives a small tilt in
 * the direction you moved, and sweeps a specular sheen across it once.
 *
 * Everything except the one-off sheen is a RenderNode property animated on the GPU:
 * no layout pass and no redraw while you move around.
 */
final class TileView extends View {
    static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);
    private static final OvershootInterpolator SETTLE = new OvershootInterpolator(1.6f);

    final int tileW, tileH, labelH, iconPx;
    private final float d;
    final Theme.Surface surface;
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint monoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint;
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix sheenMatrix = new Matrix();
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF artRect = new RectF();
    private final RectF tileRect;
    private final Rect tileBounds;
    private float sheenT = -1f;
    private ValueAnimator sheenAnim;

    AppRepo.AppEntry app;
    boolean dim;
    /** Called when artwork arrives while this tile is focused (to refresh the backdrop). */
    Runnable artReady;
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
        this.d = density;
        tileBounds = new Rect(0, 0, tileW, tileH);
        tileRect = new RectF(tileBounds);
        final float radius = Math.min(tileH * 0.16f, 20 * density);
        surface = new Theme.Surface(Theme.ROLE_CARD, radius, density);
        surface.setBounds(tileBounds);
        iconPaint = Icons.paint(density * 1.5f);

        monoPaint.setTextAlign(Paint.Align.CENTER);
        monoPaint.setTextSize(tileH * 0.40f);
        monoPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(labelH * 0.52f);
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(1.5f * density);
        rimPaint.setShader(new LinearGradient(0, 0, 0, tileH, 0xB3FFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP));

        float band = tileW * 0.35f;
        sheenPaint.setShader(new LinearGradient(-band, 0, band, 0,
                new int[]{0x00FFFFFF, 0x47FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));

        // Soft shadow under the lifted card, drawn by the RenderThread.
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, TileView.this.tileW, TileView.this.tileH, radius);
                outline.setAlpha(0.55f);
            }
        });
        setCameraDistance(8000 * density);

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

    /** The artwork currently shown, for the top-shelf backdrop. */
    Bitmap art() {
        return art;
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
        if (look && artReady != null) artReady.run();
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

    boolean isLook() {
        return look;
    }

    void setLook(boolean on, boolean animate) {
        setLook(on, animate, 0);
    }

    private void setLook(boolean on, boolean animate, int direction) {
        boolean changed = on != look;
        look = on;
        surface.setFocused(on && icon != 0);
        float s = moving ? 1.14f : on ? 1.10f : 1f;
        float z = on ? 18 * d : 0f;
        animate().cancel();
        int dur = Theme.dur(200);
        if (animate && dur > 0) {
            if (on && changed) {
                // A small tilt toward where focus came from, settling flat.
                if (direction == View.FOCUS_RIGHT) setRotationY(-7f);
                else if (direction == View.FOCUS_LEFT) setRotationY(7f);
                else if (direction == View.FOCUS_DOWN) setRotationX(6f);
                else if (direction == View.FOCUS_UP) setRotationX(-6f);
            }
            animate().scaleX(s).scaleY(s).translationZ(z).rotationX(0f).rotationY(0f)
                    .setDuration(on ? 420 : dur).setInterpolator(on ? SETTLE : EASE).start();
            if (on && changed) startSheen();
        } else {
            setScaleX(s);
            setScaleY(s);
            setTranslationZ(z);
            setRotationX(0f);
            setRotationY(0f);
        }
        if (!on) stopSheen();
        invalidate();
    }

    private void startSheen() {
        stopSheen();
        sheenAnim = ValueAnimator.ofFloat(0f, 1f);
        sheenAnim.setDuration(750);
        sheenAnim.setStartDelay(90);
        sheenAnim.setInterpolator(EASE);
        sheenAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                sheenT = (float) a.getAnimatedValue();
                if (sheenT >= 1f) sheenT = -1f;
                invalidate();
            }
        });
        sheenAnim.start();
    }

    private void stopSheen() {
        if (sheenAnim != null) {
            sheenAnim.cancel();
            sheenAnim = null;
        }
        sheenT = -1f;
    }

    void setMoving(boolean mv) {
        moving = mv;
        surface.setMoving(mv);
        setLook(look, true);
    }

    @Override
    protected void onFocusChanged(boolean gain, int direction, Rect previous) {
        super.onFocusChanged(gain, direction, previous);
        setLook(gain, true, direction);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopSheen();
    }

    @Override
    protected void onDraw(Canvas c) {
        float r = surface.radius;
        int alpha = dim ? 110 : 255;
        if (art != null && artBanner) {
            artPaint.setAlpha(alpha);
            c.drawRoundRect(tileRect, r, r, artPaint);
            if (moving) surface.drawStroke(c);
        } else {
            surface.draw(c);
            int fg = (look && icon != 0) ? Theme.onFocus : Theme.text;
            if (art != null) {
                float l = (tileW - iconPx) / 2f, t = (tileH - iconPx) / 2f;
                artRect.set(l, t, l + iconPx, t + iconPx);
                artPaint.setAlpha(alpha);
                c.drawBitmap(art, null, artRect, artPaint);
            } else if (icon != 0) {
                iconPaint.setColor(fg);
                iconPaint.setAlpha(alpha);
                Icons.draw(c, icon, tileW / 2f, tileH / 2f, tileH * 0.42f, iconPaint);
            } else {
                monoPaint.setColor(fg);
                monoPaint.setAlpha(alpha);
                float y = tileH / 2f - (monoPaint.descent() + monoPaint.ascent()) / 2f;
                c.drawText(mono, tileW / 2f, y, monoPaint);
            }
        }
        if (look && !Theme.light) c.drawRoundRect(tileRect, r, r, rimPaint);     // glassy edge
        if (sheenT >= 0f) {
            float x = -tileW * 0.4f + sheenT * tileW * 1.8f;
            sheenMatrix.setRotate(20f);
            sheenMatrix.postTranslate(x, 0);
            sheenPaint.getShader().setLocalMatrix(sheenMatrix);
            c.drawRoundRect(tileRect, r, r, sheenPaint);
        }
        if (look || moving) {
            if (shownLabel == null) {
                shownLabel = TextUtils.ellipsize(label, labelPaint, tileW * 1.1f, TextUtils.TruncateAt.END).toString();
            }
            labelPaint.setColor(Theme.text);
            labelPaint.setAlpha(dim ? 140 : 255);
            c.drawText(shownLabel, tileW / 2f, tileH + labelH * 0.80f, labelPaint);
        }
    }
}
