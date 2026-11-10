package com.nishant.orbeos;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/** ORBÉ design tokens + the two surface types (solid card, sparing glass). */
final class Theme {
    static boolean light;
    static boolean reduceMotion;

    static int bg, bg2, card, elevated, text, text2, accent, accent2, ring, hairline, scrim;

    static Bitmap wallpaper;   // 480x270, rendered once
    static Bitmap frosted;     // 120x68 pre-blurred copy for glass surfaces
    static int screenW = 1920, screenH = 1080;

    static final int ROLE_CARD = 0, ROLE_ELEVATED = 1, ROLE_GLASS = 2, ROLE_NONE = 3;

    private Theme() {}

    static void apply(boolean isLight) {
        light = isLight;
        if (isLight) {
            bg = 0xFFF3F4F7; bg2 = 0xFFE8EAF0; card = 0xFFFFFFFF; elevated = 0xFFFFFFFF;
            text = 0xFF0E1015; text2 = 0xFF555C6A; accent = 0xFF6A4FF0; accent2 = 0xFF3D6FE8;
            ring = 0xFF0E1015; hairline = 0x1A0E1015; scrim = 0x660E1015;
        } else {
            bg = 0xFF090A0D; bg2 = 0xFF12141A; card = 0xFF191C24; elevated = 0xFF222633;
            text = 0xFFFFFFFF; text2 = 0xFF9298A5; accent = 0xFF8B72FF; accent2 = 0xFF5C8DFF;
            ring = 0xFFFFFFFF; hairline = 0x14FFFFFF; scrim = 0x99000000;
        }
        buildWallpaper();
    }

    static int dur(int ms) {
        return reduceMotion ? 0 : ms;
    }

    static float focusScale() {
        return reduceMotion ? 1f : 1.04f;
    }

    /** A flat background with one faint accent glow: subtle depth, no busy gradients. */
    private static void buildWallpaper() {
        int w = 480, h = 270;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        int glow = (accent & 0x00FFFFFF) | (light ? 0x18000000 : 0x26000000);
        p.setShader(new RadialGradient(w * 0.85f, -h * 0.15f, h * 1.1f,
                new int[]{glow, accent & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        int glow2 = (accent2 & 0x00FFFFFF) | (light ? 0x10000000 : 0x1A000000);
        p.setShader(new RadialGradient(0, h * 1.1f, h * 0.9f,
                new int[]{glow2, accent2 & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        wallpaper = b;

        Bitmap s = Bitmap.createScaledBitmap(b, w / 4, h / 4, true).copy(Bitmap.Config.ARGB_8888, true);
        boxBlur(s, 3);
        boxBlur(s, 3);
        frosted = s;
    }

    private static void boxBlur(Bitmap bmp, int r) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] a = new int[w * h];
        int[] t = new int[w * h];
        bmp.getPixels(a, 0, w, 0, 0, w, h);
        pass(a, t, w, h, r, true);
        pass(t, a, w, h, r, false);
        bmp.setPixels(a, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] src, int[] dst, int w, int h, int r, boolean horiz) {
        int lines = horiz ? h : w;
        int len = horiz ? w : h;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) {
                int rs = 0, gs = 0, bs = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = i + k;
                    if (j < 0 || j >= len) continue;
                    int px = horiz ? src[l * w + j] : src[j * w + l];
                    rs += (px >> 16) & 255;
                    gs += (px >> 8) & 255;
                    bs += px & 255;
                    n++;
                }
                dst[horiz ? l * w + i : i * w + l] = 0xFF000000 | ((rs / n) << 16) | ((gs / n) << 8) | (bs / n);
            }
        }
    }

    /** Keeps a glass surface's frosted sample aligned with where it sits on screen. */
    static void track(final View v, final Surface s) {
        v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            final int[] p = new int[2];

            @Override
            public void onLayoutChange(View view, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                view.getLocationOnScreen(p);
                if (s.setOffset(p[0], p[1])) view.invalidate();
            }
        });
    }

    /**
     * Rounded surface. Cards are a solid fill (cheapest possible). Glass is used
     * sparingly (nav bar, panels): a pre-blurred wallpaper sample + translucent tint,
     * so there is no live blur cost on any frame.
     */
    static final class Surface extends Drawable {
        final float radius;
        private final float d;
        private int role;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frost = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Matrix m = new Matrix();
        private Bitmap shaderSrc;
        private int sx, sy;
        boolean focused, moving, selected;

        Surface(int role, float radius, float density) {
            this.role = role;
            this.radius = radius;
            this.d = density;
            stroke.setStyle(Paint.Style.STROKE);
        }

        void setRole(int r) {
            role = r;
            invalidateSelf();
        }

        boolean setOffset(int x, int y) {
            if (x == sx && y == sy) return false;
            sx = x;
            sy = y;
            shaderSrc = null;
            invalidateSelf();
            return true;
        }

        void setFocused(boolean f) {
            if (focused == f) return;
            focused = f;
            invalidateSelf();
        }

        void setMoving(boolean mv) {
            moving = mv;
            invalidateSelf();
        }

        void setSelected(boolean s) {
            selected = s;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect b) {
            rect.set(b);
            rect.inset(d, d);
        }

        private void ensureShader() {
            Bitmap f = frosted;
            if (f == null || f == shaderSrc) return;
            shaderSrc = f;
            BitmapShader s = new BitmapShader(f, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            m.setScale(screenW / (float) f.getWidth(), screenH / (float) f.getHeight());
            m.postTranslate(-sx, -sy);
            s.setLocalMatrix(m);
            frost.setShader(s);
        }

        void drawFill(Canvas c) {
            switch (role) {
                case ROLE_CARD:
                    fill.setColor(focused ? elevated : card);
                    c.drawRoundRect(rect, radius, radius, fill);
                    break;
                case ROLE_ELEVATED:
                    fill.setColor(elevated);
                    c.drawRoundRect(rect, radius, radius, fill);
                    break;
                case ROLE_GLASS:
                    ensureShader();
                    c.drawRoundRect(rect, radius, radius, frost);
                    fill.setColor((card & 0x00FFFFFF) | (light ? 0xB3000000 : 0x99000000));
                    c.drawRoundRect(rect, radius, radius, fill);
                    break;
                default:
                    if (focused || selected) {
                        fill.setColor(focused ? elevated : (text & 0x00FFFFFF) | 0x14000000);
                        c.drawRoundRect(rect, radius, radius, fill);
                    }
                    break;
            }
        }

        void drawStroke(Canvas c) {
            if (moving) {
                stroke.setStrokeWidth(2.5f * d);
                stroke.setColor(accent);
            } else if (focused) {
                stroke.setStrokeWidth(2.5f * d);
                stroke.setColor(ring);
            } else if (role == ROLE_NONE) {
                return;
            } else {
                stroke.setStrokeWidth(d);
                stroke.setColor(hairline);
            }
            c.drawRoundRect(rect, radius, radius, stroke);
        }

        @Override
        public void draw(Canvas c) {
            drawFill(c);
            drawStroke(c);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
