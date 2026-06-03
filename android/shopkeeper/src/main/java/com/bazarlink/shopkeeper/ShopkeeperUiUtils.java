package com.bazarlink.shopkeeper;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class ShopkeeperUiUtils {
    private ShopkeeperUiUtils() {}

    public static int dp(Context ctx, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                ctx.getResources().getDisplayMetrics()));
    }

    public static TextView sectionLabel(Context ctx, String label) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setLetterSpacing(0.22f);
        tv.setTextColor(Color.parseColor("#A8B9C8"));
        tv.setPadding(12, 0, 0, 10);
        return tv;
    }

    public static View spacer(Context ctx, int height) {
        View view = new View(ctx);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, height));
        return view;
    }
}
