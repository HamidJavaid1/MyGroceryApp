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
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class OnboardingActivity extends AppCompatActivity {
    static final String EXTRA_SELECTED_ROLE = "selected_role";
    private static final String PREFS_NAME = "bazarlink_onboarding";
    private static final String KEY_SELECTED_ROLE = "selected_role";
    private static final String ROLE_CUSTOMER = "customer";
    private static final String ROLE_SHOPKEEPER = "shopkeeper";
    private static final String ROLE_WHOLESALER = "wholesaler";

    private SharedPreferences prefs;
    private String selectedRole = ROLE_CUSTOMER;
    private MaterialButton primaryButton;
    private TextView headline;
    private TextView subtitle;
    private RoleCard customerCard;
    private RoleCard shopkeeperCard;
    private RoleCard wholesalerCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedRole = prefs.getString(KEY_SELECTED_ROLE, ROLE_CUSTOMER);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0E1C3D);
        root.setPadding(dp(20), dp(14), dp(20), dp(18));

        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.addView(scrollContent);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(14), 0, dp(14));

        TextView chip = new TextView(this);
        chip.setText("Choose Your Journey");
        chip.setTextColor(Color.WHITE);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable chipBackground = new GradientDrawable();
        chipBackground.setCornerRadius(dp(24));
        chipBackground.setColor(0x22FFFFFF);
        chipBackground.setStroke(dp(1), 0x44FFFFFF);
        chip.setBackground(chipBackground);

        headline = new TextView(this);
        headline.setText("How will you use\nBazarLinkApp?");
        headline.setTextColor(Color.WHITE);
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        headline.setTypeface(headline.getTypeface(), android.graphics.Typeface.BOLD);
        headline.setLineSpacing(0f, 1.05f);
        headline.setPadding(0, dp(20), 0, dp(10));

        subtitle = new TextView(this);
        subtitle.setText("Select one role to personalize your home feed, tools, and recommendations.");
        subtitle.setTextColor(0xFFCBD5E1);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitle.setLineSpacing(0f, 1.12f);

        header.addView(chip);
        header.addView(headline);
        header.addView(subtitle);
        scrollContent.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardsParams = new LinearLayout.LayoutParams(-1, -2);
        cardsParams.topMargin = dp(8);

        customerCard = createRoleCard(
                ROLE_CUSTOMER,
                "Customer",
                "Discover products, compare prices, and place fast orders.",
                android.R.drawable.ic_menu_myplaces,
                0xFF0EA5C7
        );
        shopkeeperCard = createRoleCard(
                ROLE_SHOPKEEPER,
                "Shopkeeper",
                "Manage inventory, reach nearby buyers, and grow daily sales.",
                android.R.drawable.ic_menu_manage,
                0xFF7C3AED
        );
        wholesalerCard = createRoleCard(
                ROLE_WHOLESALER,
                "Wholesaler",
                "Sell in bulk, unlock B2B demand, and handle large shipments.",
                android.R.drawable.ic_menu_sort_by_size,
                0xFFF57C00
        );

        cards.addView(customerCard.card);
        cards.addView(shopkeeperCard.card);
        cards.addView(wholesalerCard.card);
        scrollContent.addView(cards, cardsParams);

        primaryButton = new MaterialButton(this);
        primaryButton.setAllCaps(false);
        primaryButton.setText("Continue");
        primaryButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        primaryButton.setCornerRadius(dp(18));
        primaryButton.setMinHeight(dp(56));
        primaryButton.setOnClickListener(v -> finishSelection(selectedRole));
        primaryButton.setBackgroundColor(0xFF0E8A86);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(14);
        root.addView(primaryButton, buttonParams);

        setContentView(root);
        selectRole(selectedRole);
    }

    private RoleCard createRoleCard(String role, String title, String description, int iconRes, int accentColor) {
        MaterialCardView card = new MaterialCardView(this);
        card.setUseCompatPadding(true);
        card.setRadius(dp(18));
        card.setCardElevation(dp(4));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout iconCircle = new LinearLayout(this);
        iconCircle.setGravity(Gravity.CENTER);
        iconCircle.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(accentColor);
        iconCircle.setBackground(circle);

        TextView icon = new TextView(this);
        icon.setText(getRoleIcon(role));
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        icon.setGravity(Gravity.CENTER);
        iconCircle.addView(icon, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout textWrap = new LinearLayout(this);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = dp(14);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setTextColor(0xFF0F172A);

        TextView bodyView = new TextView(this);
        bodyView.setText(description);
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        bodyView.setTextColor(0xFF475569);
        bodyView.setLineSpacing(0f, 1.12f);
        bodyView.setPadding(0, dp(4), 0, 0);

        textWrap.addView(titleView);
        textWrap.addView(bodyView);

        container.addView(iconCircle);
        container.addView(textWrap, textParams);
        card.addView(container, new LinearLayout.LayoutParams(-1, -2));
        card.setOnClickListener(v -> selectRole(role));

        return new RoleCard(role, card, iconCircle, accentColor);
    }

    private void selectRole(String role) {
        selectedRole = role;
        prefs.edit().putString(KEY_SELECTED_ROLE, role).apply();
        updateCard(customerCard, ROLE_CUSTOMER.equals(role));
        updateCard(shopkeeperCard, ROLE_SHOPKEEPER.equals(role));
        updateCard(wholesalerCard, ROLE_WHOLESALER.equals(role));
        primaryButton.setEnabled(true);
        primaryButton.setAlpha(1f);
    }

    private void updateCard(RoleCard roleCard, boolean selected) {
        roleCard.card.setCardBackgroundColor(selected ? Color.WHITE : 0xFFF2F4F8);
        roleCard.card.setStrokeWidth(dp(2));
        roleCard.card.setStrokeColor(selected ? roleCard.accentColor : 0x00000000);
        roleCard.card.setCardElevation(selected ? dp(8) : dp(3));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(selected ? roleCard.accentColor : 0xFFCBD5E1);
        roleCard.iconCircle.setBackground(circle);
    }

    private String getRoleIcon(String role) {
        if (ROLE_SHOPKEEPER.equals(role)) {
            return "\u25A3";
        }
        if (ROLE_WHOLESALER.equals(role)) {
            return "\u25A6";
        }
        return "\u263A";
    }

    private void finishSelection(String role) {
        startActivity(new Intent(this, LoginActivity.class).putExtra(EXTRA_SELECTED_ROLE, role));
        finish();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private static class RoleCard {
        final String role;
        final MaterialCardView card;
        final LinearLayout iconCircle;
        final int accentColor;

        RoleCard(String role, MaterialCardView card, LinearLayout iconCircle, int accentColor) {
            this.role = role;
            this.card = card;
            this.iconCircle = iconCircle;
            this.accentColor = accentColor;
        }
    }
}
