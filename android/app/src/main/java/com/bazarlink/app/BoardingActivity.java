package com.bazarlink.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * Intro slides shown before role selection on first launch.
 */
public class BoardingActivity extends AppCompatActivity {
    static final String EXTRA_FORCE_SHOW = "force_show_boarding";

    private static final String PREFS_NAME = "bazarlink_onboarding";
    private static final String KEY_BOARDING_COMPLETE = "boarding_complete";

    private static final String[] TITLES = {
            "Welcome to BazarLink",
            "Shop local, order fast",
            "Built for every role",
    };
    private static final String[] BODIES = {
            "Your grocery marketplace for retail shopping, shop inventory, and wholesale supply.",
            "Browse products, compare prices, place orders, and track deliveries in one app.",
            "Next, pick how you use BazarLink — customer, shopkeeper, or wholesaler.",
    };
    private static final String[] ICONS = {"🛒", "📦", "🤝"};

    private int pageIndex;
    private TextView emojiView;
    private TextView titleView;
    private TextView bodyView;
    private TextView pageIndicator;
    private LinearLayout dotsRow;
    private MaterialButton nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean forceShow = getIntent().getBooleanExtra(EXTRA_FORCE_SHOW, false);
        if (prefs.getBoolean(KEY_BOARDING_COMPLETE, false) && !forceShow) {
            openRoleSelection();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0E1C3D);
        root.setPadding(dp(24), dp(36), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView brand = new TextView(this);
        brand.setText("BazarLink");
        brand.setTextColor(0xFF5BCFEA);
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        brand.setTypeface(brand.getTypeface(), android.graphics.Typeface.BOLD);
        brand.setPadding(0, 0, 0, dp(28));
        root.addView(brand);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);

        emojiView = new TextView(this);
        emojiView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
        emojiView.setGravity(Gravity.CENTER);
        emojiView.setPadding(0, 0, 0, dp(24));

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setLineSpacing(0f, 1.08f);

        bodyView = new TextView(this);
        bodyView.setTextColor(0xFFCBD5E1);
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        bodyView.setGravity(Gravity.CENTER);
        bodyView.setLineSpacing(0f, 1.15f);
        bodyView.setPadding(dp(8), dp(16), dp(8), 0);

        hero.addView(emojiView);
        hero.addView(titleView);
        hero.addView(bodyView);
        root.addView(hero, new LinearLayout.LayoutParams(-1, 0, 1f));

        dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        dotsRow.setPadding(0, dp(12), 0, dp(8));
        root.addView(dotsRow);

        pageIndicator = new TextView(this);
        pageIndicator.setTextColor(0xFF94A3B8);
        pageIndicator.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pageIndicator.setGravity(Gravity.CENTER);
        pageIndicator.setPadding(0, 0, 0, dp(16));
        root.addView(pageIndicator);

        nextButton = new MaterialButton(this);
        nextButton.setAllCaps(false);
        nextButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        nextButton.setMinHeight(dp(52));
        nextButton.setCornerRadius(dp(16));
        nextButton.setBackgroundColor(0xFF0E8A86);
        nextButton.setTextColor(Color.WHITE);
        nextButton.setOnClickListener(v -> onNextClicked(prefs));
        root.addView(nextButton, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        showPage(0);
    }

    private void onNextClicked(SharedPreferences prefs) {
        if (pageIndex < TITLES.length - 1) {
            showPage(pageIndex + 1);
            return;
        }
        prefs.edit().putBoolean(KEY_BOARDING_COMPLETE, true).apply();
        openRoleSelection();
    }

    private void showPage(int index) {
        pageIndex = index;
        emojiView.setText(ICONS[index]);
        titleView.setText(TITLES[index]);
        bodyView.setText(BODIES[index]);
        pageIndicator.setText((index + 1) + " / " + TITLES.length);
        nextButton.setText(index == TITLES.length - 1 ? "Get started" : "Next");

        dotsRow.removeAllViews();
        for (int i = 0; i < TITLES.length; i++) {
            View dot = new View(this);
            int size = dp(i == index ? 10 : 8);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(dp(4), 0, dp(4), 0);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(i == index ? 0xFF5BCFEA : 0xFF475569);
            dot.setBackground(shape);
            dotsRow.addView(dot, params);
        }
    }

    private void openRoleSelection() {
        startActivity(new Intent(this, OnboardingActivity.class));
        finish();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
