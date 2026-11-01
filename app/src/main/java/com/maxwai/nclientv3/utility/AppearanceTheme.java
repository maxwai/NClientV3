package com.maxwai.nclientv3.utility;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.*;
import com.maxwai.nclientv3.R;

public final class AppearanceTheme {
    private AppearanceTheme() { }
    public static String selected(Context context) { return context.getSharedPreferences("Settings", 0).getString("theme_select", "dark"); }
    public static void apply(Resources.Theme theme, String value) {
        int style;
        switch (value) {
            case "midnight": style=R.style.Appearance_Midnight; break;
            case "forest": style=R.style.Appearance_Forest; break;
            case "amethyst": style=R.style.Appearance_Amethyst; break;
            case "glass": style=R.style.Appearance_Glass; break;
            case "black": style=R.style.Appearance_Oled; break;
            default: return;
        }
        theme.applyStyle(style, true);
    }
    public static int color(Context context, int attribute) {
        TypedValue value = new TypedValue(); context.getTheme().resolveAttribute(attribute, value, true); return value.data;
    }
    public static void decorate(Activity activity) {
        if (!"glass".equals(selected(activity))) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View root = content.getChildAt(0);
        clearCanvas(root, color(activity, android.R.attr.colorBackground));
        root.setBackgroundResource(R.drawable.glass_backdrop);
    }
    private static void clearCanvas(View view, int canvas) {
        if (view instanceof com.google.android.material.appbar.AppBarLayout)
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if (view.getBackground() instanceof ColorDrawable && ((ColorDrawable) view.getBackground()).getColor() == canvas)
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        if (view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) clearCanvas(group.getChildAt(i),canvas);
        }
    }
}
