package com.carmind.app;

import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

final class ScreenInsets {
    static void apply(View root) {
        int left = root.getPaddingLeft(), top = root.getPaddingTop();
        int right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Rect padding = systemInsets(insets);
            view.setPadding(left + padding.left, top + padding.top,
                    right + padding.right, bottom + padding.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private static Rect systemInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Insets padding = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            return new Rect(padding.left, padding.top, padding.right, padding.bottom);
        }
        return legacyInsets(insets);
    }

    // Android 7–10 require the older API; only this compatibility path uses it.
    @SuppressWarnings("deprecation")
    private static Rect legacyInsets(WindowInsets insets) {
        return new Rect(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
    }
}
