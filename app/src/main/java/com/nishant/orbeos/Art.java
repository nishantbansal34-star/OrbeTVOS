package com.nishant.orbeos;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

/**
 * Turns whatever artwork an app ships (a full 16:9 banner, a small logo floating in a
 * transparent banner, or a square icon) into a full, edge-to-edge 5:3 card.
 *
 *  - Full banner: cover-fit as is.
 *  - Logo with a solid background (Netflix, Prime, YouTube...): trimmed of empty space,
 *    scaled to the card's height, and the sides filled with the logo's own edge colour,
 *    so the card looks like one seamless piece.
 *  - Shaped logo/icon with no background: centred on its own average colour.
 */
final class Art {
    static final String TAG = "ORBE";
    private static final int MAX_SRC = 720;

    private Art() {}

    static Bitmap card(Drawable d, int tw, int th, String pkg, String src) {
        // 1. Render the drawable at its own size (so a centred bitmap isn't left tiny).
        Bitmap a;
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            a = b.copy(Bitmap.Config.ARGB_8888, false);
        } else {
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) {
                iw = tw;
                ih = th;
            }
            float k = Math.min(1f, MAX_SRC / (float) Math.max(iw, ih));
            iw = Math.max(1, Math.round(iw * k));
            ih = Math.max(1, Math.round(ih * k));
            a = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(a);
            d.setBounds(0, 0, iw, ih);
            d.draw(c);
        }
        if (a == null) return null;
        int w = a.getWidth(), h = a.getHeight();
        int[] px = new int[w * h];
        a.getPixels(px, 0, w, 0, 0, w, h);

        // 2. Find the real content (non-transparent pixels).
        int minX = w, minY = h, maxX = -1, maxY = -1, opaque = 0;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = px[y * w + x];
                if ((p >>> 24) < 24) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                if ((p >>> 24) > 200) {
                    opaque++;
                    r += (p >> 16) & 255;
                    g += (p >> 8) & 255;
                    b += p & 255;
                }
            }
        }
        if (maxX < 0) {
            a.recycle();
            Log.d(TAG, pkg + " src=" + src + " empty");
            return null;
        }
        int bw = maxX - minX + 1, bh = maxY - minY + 1;
        float coverage = (bw * bh) / (float) (w * h);
        float density = opaque / (float) (bw * bh);

        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(out);
        Paint fp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        String mode;

        if (coverage > 0.88f && density > 0.9f && w > h) {
            // A real full-width banner.
            mode = "banner";
            c.drawColor(0xFF191C24);
            coverFit(c, a, new Rect(0, 0, w, h), tw, th, fp);
        } else {
            // 3. Edge colour of the content box: if solid, the logo has its own background.
            long er = 0, eg = 0, eb = 0;
            int en = 0, et = 0;
            for (int x = minX; x <= maxX; x++) {
                int[] ys = {minY, maxY};
                for (int y : ys) {
                    int p = px[y * w + x];
                    et++;
                    if ((p >>> 24) > 200) {
                        er += (p >> 16) & 255;
                        eg += (p >> 8) & 255;
                        eb += p & 255;
                        en++;
                    }
                }
            }
            for (int y = minY; y <= maxY; y++) {
                int[] xs = {minX, maxX};
                for (int x : xs) {
                    int p = px[y * w + x];
                    et++;
                    if ((p >>> 24) > 200) {
                        er += (p >> 16) & 255;
                        eg += (p >> 8) & 255;
                        eb += p & 255;
                        en++;
                    }
                }
            }
            Rect crop = new Rect(minX, minY, maxX + 1, maxY + 1);
            if (en > et * 0.7f) {
                // Solid logo tile: fill the card with its edge colour and make the logo full height.
                mode = "solidLogo";
                int bg = 0xFF000000 | ((int) (er / en) << 16) | ((int) (eg / en) << 8) | (int) (eb / en);
                c.drawColor(bg);
                containFit(c, a, crop, tw, th, 1f, fp);
            } else {
                // Shaped logo: own average colour (deepened) behind it, logo centred.
                mode = "shapedLogo";
                int bg = opaque == 0 ? 0xFF2A2F3D : 0xFF000000
                        | ((int) (r / opaque * 0.62) << 16) | ((int) (g / opaque * 0.62) << 8) | (int) (b / opaque * 0.62);
                c.drawColor(bg);
                Paint light = new Paint();
                light.setShader(new LinearGradient(0, 0, 0, th, 0x2EFFFFFF, 0x26000000, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, tw, th, light);
                containFit(c, a, crop, tw, th, 0.68f, fp);
            }
        }
        Log.d(TAG, pkg + " src=" + src + " size=" + w + "x" + h + " coverage=" + coverage
                + " density=" + density + " mode=" + mode);
        a.recycle();
        return out;
    }

    private static void coverFit(Canvas c, Bitmap a, Rect src, int tw, int th, Paint p) {
        float s = Math.max(tw / (float) src.width(), th / (float) src.height());
        int sw = Math.round(tw / s), sh = Math.round(th / s);
        int sx = src.left + (src.width() - sw) / 2, sy = src.top + (src.height() - sh) / 2;
        c.drawBitmap(a, new Rect(sx, sy, sx + sw, sy + sh), new Rect(0, 0, tw, th), p);
    }

    private static void containFit(Canvas c, Bitmap a, Rect src, int tw, int th, float fill, Paint p) {
        float s = Math.min(tw * fill / src.width(), th * fill / src.height());
        int dw = Math.round(src.width() * s), dh = Math.round(src.height() * s);
        int dx = (tw - dw) / 2, dy = (th - dh) / 2;
        c.drawBitmap(a, src, new Rect(dx, dy, dx + dw, dy + dh), p);
    }
}
