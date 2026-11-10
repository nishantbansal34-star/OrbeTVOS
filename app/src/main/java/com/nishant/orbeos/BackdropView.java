package com.nishant.orbeos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.LruCache;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Top-shelf backdrop: the focused app's artwork, blurred and filling the screen.
 * Two layers cross-fade with GPU alpha only, so changing apps never redraws.
 * Each blurred image is 64x36 px; a small LRU keeps the last few.
 */
final class BackdropView extends FrameLayout {
    private static final LruCache<Bitmap, Bitmap> BLURS = new LruCache<>(24);

    private final Layer a, b;
    private final View shade;
    private Layer front;
    private Bitmap current;

    BackdropView(Context c) {
        super(c);
        a = new Layer(c);
        b = new Layer(c);
        a.setAlpha(0f);
        b.setAlpha(0f);
        addView(a, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(b, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        shade = new Shade(c);
        addView(shade, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        front = a;
    }

    /** Show this artwork (null = plain ORBÉ wallpaper). */
    void show(Bitmap art) {
        Bitmap blur = null;
        if (art != null) {
            blur = BLURS.get(art);
            if (blur == null) {
                blur = Theme.blurred(art);
                if (blur != null) BLURS.put(art, blur);
            }
        }
        if (blur == current) return;
        current = blur;
        Layer next = front == a ? b : a;
        Layer prev = front;
        front = next;
        int dur = Theme.dur(320);
        next.setBitmap(blur);
        next.bringToFront();
        shade.bringToFront();
        next.animate().cancel();
        prev.animate().cancel();
        float target = blur == null ? 0f : (Theme.light ? 0.55f : 0.8f);
        if (dur == 0) {
            next.setAlpha(target);
            prev.setAlpha(0f);
        } else {
            next.setAlpha(0f);
            next.animate().alpha(target).setDuration(dur).start();
            prev.animate().alpha(0f).setDuration(dur).start();
        }
    }

    void clear() {
        BLURS.evictAll();
        current = null;
        a.setBitmap(null);
        b.setBitmap(null);
        a.setAlpha(0f);
        b.setAlpha(0f);
    }

    private static final class Layer extends View {
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Matrix m = new Matrix();
        private Bitmap bmp;

        Layer(Context c) {
            super(c);
        }

        void setBitmap(Bitmap b) {
            bmp = b;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (bmp == null) return;
            float s = Math.max(getWidth() / (float) bmp.getWidth(), getHeight() / (float) bmp.getHeight());
            m.setScale(s, s);
            m.postTranslate((getWidth() - bmp.getWidth() * s) / 2f, (getHeight() - bmp.getHeight() * s) / 2f);
            c.drawBitmap(bmp, m, p);
        }
    }

    /** Keeps text readable: darker on the left and towards the rows below. */
    private static final class Shade extends View {
        private final Paint side = new Paint();
        private final Paint bottom = new Paint();

        Shade(Context c) {
            super(c);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            int bg = Theme.bg & 0x00FFFFFF;
            side.setShader(new LinearGradient(0, 0, w * 0.7f, 0, bg | 0xB3000000, bg, Shader.TileMode.CLAMP));
            bottom.setShader(new LinearGradient(0, h * 0.35f, 0, h, bg, bg | 0xF2000000, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), side);
            c.drawRect(0, 0, getWidth(), getHeight(), bottom);
        }
    }
}
