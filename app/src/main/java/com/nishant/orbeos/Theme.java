package com.nishant.orbeos;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * ORBÉ design tokens + surfaces.
 *
 * Liquid glass, cheaply: everything behind the glass (the top-shelf backdrop) is
 * already a pre-blurred image, so a glass surface only needs a translucent fill,
 * an inner sheen and a specular rim. No live blur on any frame.
 */
final class Theme {
    static boolean light;
    static boolean reduceMotion;

    static int bg, bg2, card, elevated, text, text2, accent, accent2, ring, hairline, scrim, onFocus;

    static Bitmap wallpaper;
    static int screenW = 1920, screenH = 1080;

    static final int ROLE_CARD = 0, ROLE_ELEVATED = 1, ROLE_GLASS = 2, ROLE_NONE = 3;

    private Theme() {}

    static void apply(boolean isLight) {
        light = isLight;
        if (isLight) {
            bg = 0xFFF3F4F7; bg2 = 0xFFE8EAF0; card = 0xFFFFFFFF; elevated = 0xFFFFFFFF;
            text = 0xFF0E1015; text2 = 0xFF555C6A; accent = 0xFF6A4FF0; accent2 = 0xFF3D6FE8;
            ring = 0xFF0E1015; hairline = 0x1A0E1015; scrim = 0x660E1015; onFocus = 0xFFFFFFFF;
        } else {
            bg = 0xFF090A0D; bg2 = 0xFF12141A; card = 0xFF191C24; elevated = 0xFF222633;
            text = 0xFFFFFFFF; text2 = 0xFF9298A5; accent = 0xFF8B72FF; accent2 = 0xFF5C8DFF;
            ring = 0xFFFFFFFF; hairline = 0x14FFFFFF; scrim = 0x99000000; onFocus = 0xFF090A0D;
        }
        buildWallpaper();
    }

    static int dur(int ms) {
        return reduceMotion ? 0 : ms;
    }

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
    }

    /** Tiny, heavily blurred copy of an image for the top-shelf backdrop (~14 KB). */
    static Bitmap blurred(Bitmap src) {
        if (src == null) return null;
        int w = 64, h = 36;
        Bitmap s = Bitmap.createScaledBitmap(src, w, h, true);
        if (s.getConfig() != Bitmap.Config.ARGB_8888 || !s.isMutable()) {
            Bitmap c = s.copy(Bitmap.Config.ARGB_8888, true);
            if (s != src) s.recycle();
            s = c;
        }
        boxBlur(s, 3);
        boxBlur(s, 3);
        boxBlur(s, 2);
        // Deepen and enrich the colours so the shelf stays dark and text stays readable.
        int[] px = new int[w * h];
        s.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            float rr = (p >> 16) & 255, gg = (p >> 8) & 255, bb = p & 255;
            float l = 0.3f * rr + 0.59f * gg + 0.11f * bb;
            rr = l + (rr - l) * 1.5f;
            gg = l + (gg - l) * 1.5f;
            bb = l + (bb - l) * 1.5f;
            float k = light ? 0.95f : 0.55f;
            px[i] = 0xFF000000 | (clamp(rr * k) << 16) | (clamp(gg * k) << 8) | clamp(bb * k);
        }
        s.setPixels(px, 0, w, 0, 0, w, h);
        return s;
    }

    private static int clamp(float v) {
        return v < 0 ? 0 : v > 255 ? 255 : (int) v;
    }

    static void boxBlur(Bitmap bmp, int r) {
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

    /**
     * Rounded surface. Focus follows the TV convention: the focused control turns
     * solid (white in dark mode) and its content flips to dark.
     */
    static final class Surface extends Drawable {
        final float radius;
        private final float d;
        private final int role;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        boolean focused, moving, selected, active;

        Surface(int role, float radius, float density) {
            this.role = role;
            this.radius = radius;
            this.d = density;
            stroke.setStyle(Paint.Style.STROKE);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1.5f * density);
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

        void setActive(boolean a) {
            active = a;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect b) {
            rect.set(b);
            rect.inset(d * 0.75f, d * 0.75f);
            float top = b.top, h = b.height();
            sheen.setShader(new LinearGradient(0, top, 0, top + h * 0.55f,
                    light ? 0x66FFFFFF : 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rim.setShader(new LinearGradient(0, top, 0, top + h,
                    light ? 0xCCFFFFFF : 0x80FFFFFF, light ? 0x330E1015 : 0x0FFFFFFF, Shader.TileMode.CLAMP));
        }

        void drawFill(Canvas c) {
            if (focused && role != ROLE_ELEVATED) {
                fill.setColor(ring);
                c.drawRoundRect(rect, radius, radius, fill);
                return;
            }
            switch (role) {
                case ROLE_CARD:
                    fill.setColor(active ? (accent & 0x00FFFFFF) | 0x59000000 : card);
                    c.drawRoundRect(rect, radius, radius, fill);
                    break;
                case ROLE_ELEVATED:
                    fill.setColor(elevated);
                    c.drawRoundRect(rect, radius, radius, fill);
                    c.drawRoundRect(rect, radius, radius, sheen);
                    break;
                case ROLE_GLASS:
                    fill.setColor(light ? 0xB3FFFFFF : 0x2E9AA3B8);
                    c.drawRoundRect(rect, radius, radius, fill);
                    fill.setColor(light ? 0x14FFFFFF : 0x40090A0D);
                    c.drawRoundRect(rect, radius, radius, fill);
                    c.drawRoundRect(rect, radius, radius, sheen);
                    break;
                default:
                    if (active || selected) {
                        fill.setColor(active ? (accent & 0x00FFFFFF) | 0x59000000 : (text & 0x00FFFFFF) | 0x1A000000);
                        c.drawRoundRect(rect, radius, radius, fill);
                    }
                    break;
            }
        }

        void drawStroke(Canvas c) {
            if (moving) {
                stroke.setStrokeWidth(3f * d);
                stroke.setColor(accent);
                c.drawRoundRect(rect, radius, radius, stroke);
                return;
            }
            if (focused && role != ROLE_ELEVATED) return;
            if (role == ROLE_GLASS || role == ROLE_ELEVATED) {
                c.drawRoundRect(rect, radius, radius, rim);    // specular edge
            } else if (role == ROLE_CARD) {
                stroke.setStrokeWidth(d);
                stroke.setColor(hairline);
                c.drawRoundRect(rect, radius, radius, stroke);
            }
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
