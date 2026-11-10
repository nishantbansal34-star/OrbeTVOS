package com.nishant.orbeos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/** Round icon-only button for the nav bar (also used, non-focusable, as a status icon). */
final class IconButton extends View {
    private final int size;
    private final Paint paint;
    final Theme.Surface surface;
    int icon;

    IconButton(Context c, int icon, String description, float density, boolean focusable) {
        super(c);
        this.icon = icon;
        size = Math.round(44 * density);
        paint = Icons.paint(density);
        surface = new Theme.Surface(Theme.ROLE_NONE, size / 2f, density);
        setBackground(surface);
        setFocusable(focusable);
        setClickable(focusable);
        setContentDescription(description);
    }

    void setIcon(int i) {
        if (icon == i) return;
        icon = i;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onFocusChanged(boolean gain, int dir, Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        surface.setFocused(gain);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        paint.setColor(isFocused() ? Theme.onFocus : isFocusable() ? Theme.text : Theme.text2);
        Icons.draw(c, icon, size / 2f, size / 2f, size * 0.5f, paint);
    }
}
