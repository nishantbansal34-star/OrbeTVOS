package com.nishant.orbeos;

import android.content.Context;
import android.graphics.Rect;
import android.widget.HorizontalScrollView;

/**
 * A horizontal row that respects its side margins when following focus.
 * The stock HorizontalScrollView ignores padding, so moving back to the first card
 * scrolled it past the margin (outside the glass dock). Here a focused card is only
 * scrolled until it sits inside the padded area, and never past the row's ends.
 */
final class RowScroller extends HorizontalScrollView {
    RowScroller(Context c) {
        super(c);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        if (getChildCount() == 0) return 0;
        int left = getScrollX() + getPaddingLeft();
        int right = getScrollX() + getWidth() - getPaddingRight();
        int delta = 0;
        if (rect.left < left) delta = rect.left - left;
        else if (rect.right > right) delta = rect.right - right;
        int max = Math.max(0, getChildAt(0).getWidth() + getPaddingLeft() + getPaddingRight() - getWidth());
        int target = Math.max(0, Math.min(max, getScrollX() + delta));
        return target - getScrollX();
    }
}
