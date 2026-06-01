package com.bazarlink.wholesaler;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

public final class WholesalerUiUtils {
    private WholesalerUiUtils() {}

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

    public static TextView toastLikeTitle(Context ctx, @Nullable String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text == null ? "" : text);
        tv.setTextSize(18);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setGravity(Gravity.START);
        return tv;
    }

    public static LinearLayout newColumn(Context ctx) {
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        return ll;
    }
}

