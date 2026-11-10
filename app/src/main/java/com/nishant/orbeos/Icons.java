package com.nishant.orbeos;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Minimal line icons drawn with paths: no image assets, crisp at any size. */
final class Icons {
    static final int SEARCH = 1, SLIDERS = 2, GEAR = 3, WIFI = 4, OFFLINE = 5, ETHERNET = 6,
            INPUT = 7, BLUETOOTH = 8, DISPLAY = 9, SOUND = 10, GRID = 11, PLUS = 12, INFO = 13,
            TRASH = 14, HIDE = 15, MOVE = 16, CAST = 17, MOON = 18, ACCESS = 19, CLOCK = 20,
            STORAGE = 21, STAR = 22, OPEN = 23, SHIELD = 24, MUTE = 25, PALETTE = 26, LAYERS = 27,
            MOTION = 28, EYE = 29, GAME = 30, MUSIC = 31, CHIP = 32;

    private static final Path P = new Path();
    private static final RectF R = new RectF();

    private Icons() {}

    static Paint paint(float density) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(2f * density);
        return p;
    }

    /** Draws icon centred at (cx, cy) inside a square of side s. */
    static void draw(Canvas c, int icon, float cx, float cy, float s, Paint p) {
        float h = s / 2f;
        float l = cx - h, t = cy - h, r = cx + h, b = cy + h;
        P.reset();
        switch (icon) {
            case SEARCH:
                c.drawCircle(cx - s * 0.08f, cy - s * 0.08f, s * 0.30f, p);
                c.drawLine(cx + s * 0.14f, cy + s * 0.14f, r - s * 0.08f, b - s * 0.08f, p);
                break;
            case SLIDERS:
                c.drawLine(l + s * 0.1f, cy - s * 0.2f, r - s * 0.1f, cy - s * 0.2f, p);
                c.drawLine(l + s * 0.1f, cy + s * 0.2f, r - s * 0.1f, cy + s * 0.2f, p);
                dot(c, p, cx + s * 0.18f, cy - s * 0.2f, s * 0.1f);
                dot(c, p, cx - s * 0.18f, cy + s * 0.2f, s * 0.1f);
                break;
            case GEAR:
                c.drawCircle(cx, cy, s * 0.16f, p);
                c.drawCircle(cx, cy, s * 0.32f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                    c.drawLine(cx + ca * s * 0.32f, cy + sa * s * 0.32f, cx + ca * s * 0.44f, cy + sa * s * 0.44f, p);
                }
                break;
            case WIFI:
            case OFFLINE:
                for (int i = 1; i <= 3; i++) {
                    float rr = s * 0.15f * i;
                    R.set(cx - rr, cy + s * 0.25f - rr, cx + rr, cy + s * 0.25f + rr);
                    c.drawArc(R, 225, 90, false, p);
                }
                c.drawPoint(cx, cy + s * 0.25f, p);
                if (icon == OFFLINE) c.drawLine(l + s * 0.12f, t + s * 0.12f, r - s * 0.12f, b - s * 0.12f, p);
                break;
            case ETHERNET:
                R.set(cx - s * 0.3f, cy - s * 0.3f, cx + s * 0.3f, cy + s * 0.2f);
                c.drawRoundRect(R, s * 0.06f, s * 0.06f, p);
                c.drawLine(cx, cy + s * 0.2f, cx, b - s * 0.05f, p);
                c.drawLine(cx - s * 0.12f, cy - s * 0.1f, cx - s * 0.12f, cy + s * 0.05f, p);
                c.drawLine(cx + s * 0.12f, cy - s * 0.1f, cx + s * 0.12f, cy + s * 0.05f, p);
                break;
            case INPUT:
                R.set(l + s * 0.12f, t + s * 0.2f, r - s * 0.12f, b - s * 0.28f);
                c.drawRoundRect(R, s * 0.06f, s * 0.06f, p);
                c.drawLine(cx - s * 0.18f, b - s * 0.12f, cx + s * 0.18f, b - s * 0.12f, p);
                c.drawLine(cx - s * 0.12f, cy - s * 0.04f, cx + s * 0.08f, cy - s * 0.04f, p);
                P.moveTo(cx, cy - s * 0.14f);
                P.lineTo(cx + s * 0.1f, cy - s * 0.04f);
                P.lineTo(cx, cy + s * 0.06f);
                c.drawPath(P, p);
                break;
            case BLUETOOTH:
                P.moveTo(cx - s * 0.2f, cy - s * 0.18f);
                P.lineTo(cx + s * 0.2f, cy + s * 0.16f);
                P.lineTo(cx, cy + s * 0.36f);
                P.lineTo(cx, cy - s * 0.36f);
                P.lineTo(cx + s * 0.2f, cy - s * 0.16f);
                P.lineTo(cx - s * 0.2f, cy + s * 0.18f);
                c.drawPath(P, p);
                break;
            case DISPLAY:
                R.set(l + s * 0.08f, t + s * 0.18f, r - s * 0.08f, b - s * 0.26f);
                c.drawRoundRect(R, s * 0.05f, s * 0.05f, p);
                c.drawLine(cx - s * 0.16f, b - s * 0.1f, cx + s * 0.16f, b - s * 0.1f, p);
                break;
            case SOUND:
            case MUTE:
                P.moveTo(l + s * 0.12f, cy - s * 0.12f);
                P.lineTo(l + s * 0.28f, cy - s * 0.12f);
                P.lineTo(cx, t + s * 0.16f);
                P.lineTo(cx, b - s * 0.16f);
                P.lineTo(l + s * 0.28f, cy + s * 0.12f);
                P.lineTo(l + s * 0.12f, cy + s * 0.12f);
                P.close();
                c.drawPath(P, p);
                if (icon == MUTE) {
                    c.drawLine(cx + s * 0.14f, cy - s * 0.12f, r - s * 0.1f, cy + s * 0.12f, p);
                    c.drawLine(cx + s * 0.14f, cy + s * 0.12f, r - s * 0.1f, cy - s * 0.12f, p);
                } else {
                    R.set(cx - s * 0.12f, cy - s * 0.2f, cx + s * 0.28f, cy + s * 0.2f);
                    c.drawArc(R, -50, 100, false, p);
                }
                break;
            case GRID:
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        float x = l + s * (0.14f + 0.4f * i), y = t + s * (0.14f + 0.4f * j);
                        R.set(x, y, x + s * 0.3f, y + s * 0.3f);
                        c.drawRoundRect(R, s * 0.06f, s * 0.06f, p);
                    }
                }
                break;
            case PLUS:
                c.drawLine(cx, t + s * 0.18f, cx, b - s * 0.18f, p);
                c.drawLine(l + s * 0.18f, cy, r - s * 0.18f, cy, p);
                break;
            case INFO:
                c.drawCircle(cx, cy, s * 0.36f, p);
                c.drawLine(cx, cy - s * 0.02f, cx, cy + s * 0.18f, p);
                c.drawPoint(cx, cy - s * 0.17f, p);
                break;
            case TRASH:
                c.drawLine(l + s * 0.16f, t + s * 0.26f, r - s * 0.16f, t + s * 0.26f, p);
                c.drawLine(cx - s * 0.1f, t + s * 0.14f, cx + s * 0.1f, t + s * 0.14f, p);
                P.moveTo(l + s * 0.24f, t + s * 0.26f);
                P.lineTo(l + s * 0.3f, b - s * 0.12f);
                P.lineTo(r - s * 0.3f, b - s * 0.12f);
                P.lineTo(r - s * 0.24f, t + s * 0.26f);
                c.drawPath(P, p);
                break;
            case HIDE:
            case EYE:
                P.moveTo(l + s * 0.08f, cy);
                P.quadTo(cx, t - s * 0.02f, r - s * 0.08f, cy);
                P.quadTo(cx, b + s * 0.02f, l + s * 0.08f, cy);
                c.drawPath(P, p);
                c.drawCircle(cx, cy, s * 0.11f, p);
                if (icon == HIDE) c.drawLine(l + s * 0.14f, t + s * 0.14f, r - s * 0.14f, b - s * 0.14f, p);
                break;
            case MOVE:
                c.drawLine(l + s * 0.12f, cy, r - s * 0.12f, cy, p);
                P.moveTo(l + s * 0.28f, cy - s * 0.15f);
                P.lineTo(l + s * 0.12f, cy);
                P.lineTo(l + s * 0.28f, cy + s * 0.15f);
                P.moveTo(r - s * 0.28f, cy - s * 0.15f);
                P.lineTo(r - s * 0.12f, cy);
                P.lineTo(r - s * 0.28f, cy + s * 0.15f);
                c.drawPath(P, p);
                break;
            case CAST:
                P.moveTo(l + s * 0.1f, t + s * 0.36f);
                P.lineTo(l + s * 0.1f, t + s * 0.2f);
                P.lineTo(r - s * 0.1f, t + s * 0.2f);
                P.lineTo(r - s * 0.1f, b - s * 0.2f);
                P.lineTo(cx + s * 0.04f, b - s * 0.2f);
                c.drawPath(P, p);
                for (int i = 1; i <= 2; i++) {
                    float rr = s * 0.13f * i;
                    R.set(l + s * 0.1f - rr, b - s * 0.2f - rr, l + s * 0.1f + rr, b - s * 0.2f + rr);
                    c.drawArc(R, 270, 90, false, p);
                }
                break;
            case MOON:
                R.set(cx - s * 0.32f, cy - s * 0.32f, cx + s * 0.32f, cy + s * 0.32f);
                c.drawArc(R, 70, 250, false, p);
                P.moveTo(cx + s * 0.1f, cy - s * 0.3f);
                P.quadTo(cx - s * 0.12f, cy + s * 0.06f, cx + s * 0.32f, cy + s * 0.08f);
                c.drawPath(P, p);
                break;
            case ACCESS:
                c.drawCircle(cx, t + s * 0.18f, s * 0.07f, p);
                c.drawLine(l + s * 0.16f, cy - s * 0.12f, r - s * 0.16f, cy - s * 0.12f, p);
                c.drawLine(cx, cy - s * 0.12f, cx, cy + s * 0.1f, p);
                c.drawLine(cx, cy + s * 0.1f, cx - s * 0.14f, b - s * 0.1f, p);
                c.drawLine(cx, cy + s * 0.1f, cx + s * 0.14f, b - s * 0.1f, p);
                break;
            case CLOCK:
                c.drawCircle(cx, cy, s * 0.36f, p);
                c.drawLine(cx, cy, cx, cy - s * 0.2f, p);
                c.drawLine(cx, cy, cx + s * 0.14f, cy + s * 0.08f, p);
                break;
            case STORAGE:
                for (int i = 0; i < 3; i++) {
                    float y = t + s * (0.16f + 0.24f * i);
                    R.set(l + s * 0.14f, y, r - s * 0.14f, y + s * 0.18f);
                    c.drawRoundRect(R, s * 0.04f, s * 0.04f, p);
                }
                break;
            case STAR:
                for (int i = 0; i < 5; i++) {
                    double a = -Math.PI / 2 + i * 4 * Math.PI / 5;
                    float x = cx + (float) Math.cos(a) * s * 0.38f, y = cy + 0.03f * s + (float) Math.sin(a) * s * 0.38f;
                    if (i == 0) P.moveTo(x, y); else P.lineTo(x, y);
                }
                P.close();
                c.drawPath(P, p);
                break;
            case OPEN:
                P.moveTo(cx - s * 0.16f, cy - s * 0.24f);
                P.lineTo(cx + s * 0.24f, cy);
                P.lineTo(cx - s * 0.16f, cy + s * 0.24f);
                P.close();
                c.drawPath(P, p);
                break;
            case SHIELD:
                P.moveTo(cx, t + s * 0.1f);
                P.lineTo(r - s * 0.16f, t + s * 0.22f);
                P.quadTo(r - s * 0.16f, b - s * 0.2f, cx, b - s * 0.08f);
                P.quadTo(l + s * 0.16f, b - s * 0.2f, l + s * 0.16f, t + s * 0.22f);
                P.close();
                c.drawPath(P, p);
                break;
            case PALETTE:
                c.drawCircle(cx, cy, s * 0.36f, p);
                R.set(cx - s * 0.36f, cy - s * 0.36f, cx + s * 0.36f, cy + s * 0.36f);
                P.moveTo(cx, cy - s * 0.36f);
                P.lineTo(cx, cy + s * 0.36f);
                c.drawPath(P, p);
                break;
            case LAYERS:
                for (int i = 0; i < 3; i++) {
                    float y = cy - s * 0.18f + i * s * 0.16f;
                    P.moveTo(l + s * 0.12f, y);
                    P.lineTo(cx, y - s * 0.12f);
                    P.lineTo(r - s * 0.12f, y);
                    P.lineTo(cx, y + s * 0.12f);
                    P.close();
                }
                c.drawPath(P, p);
                break;
            case MOTION:
                c.drawLine(l + s * 0.1f, cy - s * 0.16f, cx + s * 0.04f, cy - s * 0.16f, p);
                c.drawLine(l + s * 0.2f, cy + s * 0.16f, cx + s * 0.04f, cy + s * 0.16f, p);
                c.drawCircle(cx + s * 0.2f, cy, s * 0.14f, p);
                break;
            case GAME:
                R.set(l + s * 0.08f, cy - s * 0.2f, r - s * 0.08f, cy + s * 0.22f);
                c.drawRoundRect(R, s * 0.18f, s * 0.18f, p);
                c.drawLine(cx - s * 0.28f, cy, cx - s * 0.12f, cy, p);
                c.drawLine(cx - s * 0.2f, cy - s * 0.08f, cx - s * 0.2f, cy + s * 0.08f, p);
                c.drawPoint(cx + s * 0.16f, cy - s * 0.04f, p);
                c.drawPoint(cx + s * 0.24f, cy + s * 0.05f, p);
                break;
            case MUSIC:
                c.drawCircle(cx - s * 0.14f, cy + s * 0.2f, s * 0.1f, p);
                c.drawCircle(cx + s * 0.22f, cy + s * 0.12f, s * 0.1f, p);
                c.drawLine(cx - s * 0.04f, cy + s * 0.2f, cx - s * 0.04f, t + s * 0.18f, p);
                c.drawLine(cx + s * 0.32f, cy + s * 0.12f, cx + s * 0.32f, t + s * 0.1f, p);
                c.drawLine(cx - s * 0.04f, t + s * 0.18f, cx + s * 0.32f, t + s * 0.1f, p);
                break;
            case CHIP:
                R.set(l + s * 0.24f, t + s * 0.24f, r - s * 0.24f, b - s * 0.24f);
                c.drawRoundRect(R, s * 0.05f, s * 0.05f, p);
                for (int i = 0; i < 3; i++) {
                    float o = s * (0.34f + 0.16f * i);
                    c.drawLine(l + o, t + s * 0.1f, l + o, t + s * 0.24f, p);
                    c.drawLine(l + o, b - s * 0.24f, l + o, b - s * 0.1f, p);
                }
                break;
            default:
                c.drawCircle(cx, cy, s * 0.3f, p);
                break;
        }
    }

    private static void dot(Canvas c, Paint p, float x, float y, float r) {
        Paint.Style st = p.getStyle();
        int col = p.getColor();
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(x, y, r, p);
        p.setStyle(st);
        p.setColor(col);
    }
}
