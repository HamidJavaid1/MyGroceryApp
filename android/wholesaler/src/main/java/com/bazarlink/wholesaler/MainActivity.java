package com.bazarlink.wholesaler;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import com.bazarlink.shared.api.TokenStore;
import com.bazarlink.shared.api.BazarLinkApi;
import com.bazarlink.shared.models.Order;
import com.bazarlink.shared.models.Page;
import com.bazarlink.shared.models.WholesalerDashboard;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {
    private LinearLayout content;
    private BottomNavigationView nav;
    private String profileName;
    private WholesalerRepository repository;
    private BazarLinkApi api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profileName = getIntent().getStringExtra("display_name");
        if (profileName == null || profileName.trim().isEmpty()) {
            profileName = "Noor Din";
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0E2236"));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28, 28, 28, 28);
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = new BottomNavigationView(this);
        nav.getMenu().add(0, 1, 0, "Dashboard").setIcon(android.R.drawable.ic_menu_view);
        nav.getMenu().add(0, 2, 1, "Orders").setIcon(android.R.drawable.ic_menu_agenda);
        nav.getMenu().add(0, 3, 2, "Inventory").setIcon(android.R.drawable.ic_menu_sort_by_size);
        nav.getMenu().add(0, 4, 3, "Settings").setIcon(android.R.drawable.ic_menu_manage);
        String baseUrl = repositoryDefaultBaseUrl();
        repository = new WholesalerRepository(this, baseUrl);

        nav.setOnItemSelectedListener(item -> {
            render(item.getTitle().toString());
            return true;
        });
        root.addView(nav);
        setContentView(root);
        nav.setSelectedItemId(4);
        render("Settings");
    }

    private void render(String screen) {
        content.removeAllViews();
        if ("Settings".equals(screen)) {
            renderSettings();
            return;
        }

        if ("Dashboard".equals(screen)) {
            renderDashboard();
            return;
        }

        if ("Orders".equals(screen)) {
            renderOrders();
            return;
        }

        if ("Inventory".equals(screen)) {
            renderInventory();
            return;
        }

        addTitle(screen, "Wholesale distribution dashboard");
        card("Bulk marketplace", "Bulk product listings, incoming requests, quotation builder, accepted quotation notifications, and history are backed by /bulk-requests/ and /quotations/.");
    }

    private String repositoryDefaultBaseUrl() {
        // TokenStore may already have server url from login/signup flow.
        try {
            TokenStore store = new TokenStore(this);
            String saved = store.serverUrl();
            if (saved != null && !saved.isEmpty()) {
                if (!saved.endsWith("/")) saved = saved + "/";
                return saved;
            }
        } catch (Exception ignored) {}
        // Fallback for dev environments; adjust in your login flow or token store.
        return "https://mygroceryapp-4ryn.onrender.com/";
    }

    private void renderDashboard() {
        addTitle("Dashboard", "Loading wholesale metrics...");
        repository.loadDashboard(new WholesalerRepository.Callback1<WholesalerDashboard>() {
            @Override
            public void onSuccess(WholesalerDashboard data) {
                WholesalerScreenRenderer renderer = new WholesalerScreenRenderer(MainActivity.this, content, Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT));
                renderer.renderDashboard(data);
            }

            @Override
            public void onError(String message) {
                toast(message);
                card("Dashboard error", message);
            }
        });
    }

    private void renderOrders() {
        addTitle("Orders", "Loading orders...");
        repository.loadOrders(null, new WholesalerRepository.Callback1<Page<Order>>() {
            @Override
            public void onSuccess(Page<Order> page) {
                WholesalerScreenRenderer renderer = new WholesalerScreenRenderer(MainActivity.this, content, Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT));
                renderer.renderOrders(page, (order, newStatus) -> {
                    toast("Updating order #" + order.id + "...");
                    repository.transitionOrder(order.id, newStatus, new WholesalerRepository.Callback1<Order>() {
                        @Override
                        public void onSuccess(Order updated) {
                            toast("Order updated: " + updated.status);
                            renderOrders();
                        }

                        @Override
                        public void onError(String message) {
                            toast(message);
                        }
                    });
                });
            }

            @Override
            public void onError(String message) {
                toast(message);
                card("Orders error", message);
            }
        });
    }

    private void renderInventory() {
        addTitle("Inventory", "Loading low stock alerts...");
        // For now, inventory alerts are surfaced through wholesaler dashboard.
        repository.loadDashboard(new WholesalerRepository.Callback1<WholesalerDashboard>() {
            @Override
            public void onSuccess(WholesalerDashboard data) {
                if (data.low_stock_alerts == null || data.low_stock_alerts.isEmpty()) {
                    WholesalerScreenRenderer renderer = new WholesalerScreenRenderer(MainActivity.this, content, Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT));
                    renderer.renderDashboard(data);
                    toast("No low stock alerts");
                } else {
                    // Reuse dashboard renderer low-stock section.
                    WholesalerScreenRenderer renderer = new WholesalerScreenRenderer(MainActivity.this, content, Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT));
                    renderer.renderDashboard(data);
                    toast("Low stock alerts loaded");
                }
            }

            @Override
            public void onError(String message) {
                toast(message);
                card("Inventory error", message);
            }
        });
    }


    private void renderSettings() {
        addTitle("Settings", "Manage your wholesale distribution preferences");
        content.addView(spacer(10));

        content.addView(topProfileCard());
        content.addView(spacer(16));
        content.addView(systemStatusCard());
        content.addView(spacer(18));

        addSectionLabel("ACCOUNT");
        content.addView(sectionCard(
                rowAction(android.R.drawable.ic_menu_myplaces, "Personal Information", true, null),
                rowAction(android.R.drawable.ic_menu_send, "Payment Methods", true, null)
        ));
        content.addView(spacer(10));

        addSectionLabel("OPERATIONS");
        content.addView(sectionCard(
                rowSwitch(android.R.drawable.ic_menu_info_details, "Push Notifications", true, checked -> toast("Notification preference saved")),
                rowAction(android.R.drawable.ic_menu_upload, "Inventory Alerts", true, null)
        ));
        content.addView(spacer(10));

        addSectionLabel("SECURITY");
        content.addView(sectionCard(
                rowBadge(android.R.drawable.ic_lock_lock, "Two-Factor Authentication", "ON"),
                rowAction(android.R.drawable.ic_menu_compass, "Biometric Access", true, null)
        ));
        content.addView(spacer(18));
        content.addView(logoutButton());
    }

    private void addTitle(String headerTitle, String subtitle) {
        TextView titleView = new TextView(this);
        titleView.setText(headerTitle);
        titleView.setTextSize(34);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setTextColor(Color.parseColor("#22D3EE"));
        titleView.setPadding(0, 0, 0, 4);
        content.addView(titleView);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextSize(16);
        sub.setTextColor(Color.parseColor("#B7C8D8"));
        content.addView(sub);
    }

    private void card(String title, String body) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(28);
        card.setCardBackgroundColor(Color.parseColor("#1A3147"));
        card.setStrokeColor(Color.parseColor("#28435D"));
        card.setStrokeWidth(2);
        card.setCardElevation(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = 22;
        card.setLayoutParams(params);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 20, 24, 20);
        TextView h = new TextView(this);
        h.setText(title);
        h.setTextSize(18);
        h.setTextColor(Color.WHITE);
        TextView p = new TextView(this);
        p.setText(body);
        p.setTextSize(14);
        p.setTextColor(Color.parseColor("#B8C7D8"));
        box.addView(h);
        box.addView(p);
        card.addView(box);
        content.addView(card);
    }

    private void addSectionLabel(String label) {
        TextView section = new TextView(this);
        section.setText(label);
        section.setTextSize(14);
        section.setLetterSpacing(0.22f);
        section.setTextColor(Color.parseColor("#A8B9C8"));
        section.setPadding(12, 0, 0, 10);
        content.addView(section);
    }

    private MaterialCardView topProfileCard() {
        MaterialCardView card = settingsCard();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(24, 24, 24, 24);

        TextView avatar = circleIcon();
        row.addView(avatar);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, -2, 1);
        infoParams.leftMargin = 24;
        info.setLayoutParams(infoParams);

        TextView name = new TextView(this);
        name.setText(profileName);
        name.setTextSize(24);
        name.setTextColor(Color.WHITE);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);

        TextView role = new TextView(this);
        role.setText("WHOLESALE MANAGER");
        role.setTextSize(13);
        role.setLetterSpacing(0.12f);
        role.setTextColor(Color.parseColor("#C2D0DE"));

        TextView adminId = new TextView(this);
        adminId.setText("Admin ID: 8842");
        adminId.setTextSize(16);
        adminId.setTextColor(Color.parseColor("#D8E6F2"));

        info.addView(name);
        info.addView(role);
        info.addView(adminId);

        TextView edit = new TextView(this);
        edit.setText("✎");
        edit.setTextSize(28);
        edit.setTextColor(Color.parseColor("#22D3EE"));
        edit.setPadding(12, 0, 0, 0);

        row.addView(info);
        row.addView(edit);
        card.addView(row);
        return card;
    }

    private MaterialCardView systemStatusCard() {
        MaterialCardView card = settingsCard();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 24, 24, 24);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("System Status");
        title.setTextSize(14);
        title.setTextColor(Color.parseColor("#C1D0DE"));
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));

        TextView dot = new TextView(this);
        dot.setText("•");
        dot.setTextSize(30);
        dot.setTextColor(Color.parseColor("#22D3EE"));

        header.addView(title);
        header.addView(dot);

        TextView status = new TextView(this);
        status.setText("All Operational");
        status.setTextSize(28);
        status.setTextColor(Color.parseColor("#DDF3FF"));
        status.setTypeface(status.getTypeface(), android.graphics.Typeface.BOLD);

        TextView version = new TextView(this);
        version.setText("Version 2.4.1 Build-Stable");
        version.setTextSize(15);
        version.setTextColor(Color.parseColor("#AEC2D3"));

        box.addView(header);
        box.addView(spacer(10));
        box.addView(status);
        box.addView(spacer(8));
        box.addView(version);
        card.addView(box);
        return card;
    }

    private MaterialCardView sectionCard(View... rows) {
        MaterialCardView card = settingsCard();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(4, 4, 4, 4);
        for (int i = 0; i < rows.length; i++) {
            box.addView(rows[i]);
            if (i < rows.length - 1) {
                box.addView(divider());
            }
        }
        card.addView(box);
        return card;
    }

    private View rowAction(int iconRes, String text, boolean showArrow, Runnable onClick) {
        LinearLayout row = baseRow();
        row.setOnClickListener(v -> {
            if (onClick != null) {
                onClick.run();
            } else {
                toast(text + " opened");
            }
        });
        row.addView(rowIcon(iconRes));
        TextView label = rowLabel(text);
        row.addView(label);
        TextView arrow = new TextView(this);
        arrow.setText(showArrow ? ">" : "");
        arrow.setTextSize(24);
        arrow.setTextColor(Color.parseColor("#B6C6D5"));
        row.addView(arrow);
        return row;
    }

    private View rowBadge(int iconRes, String text, String badge) {
        LinearLayout row = baseRow();
        row.addView(rowIcon(iconRes));
        TextView label = rowLabel(text);
        row.addView(label);

        TextView pill = new TextView(this);
        pill.setText(badge);
        pill.setTextSize(12);
        pill.setTextColor(Color.parseColor("#DDEAF4"));
        pill.setPadding(18, 8, 18, 8);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(22);
        bg.setColor(Color.parseColor("#2E475E"));
        pill.setBackground(bg);
        row.addView(pill);
        return row;
    }

    private View rowSwitch(int iconRes, String text, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = baseRow();
        row.addView(rowIcon(iconRes));
        TextView label = rowLabel(text);
        row.addView(label);

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle);
        return row;
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(22, 26, 22, 26);
        return row;
    }

    private View rowIcon(int iconRes) {
        ImageView icon = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(72, 72);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(2, Color.parseColor("#2FC8E7"));
        bg.setColor(Color.parseColor("#153447"));
        icon.setBackground(bg);
        icon.setLayoutParams(params);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setColorFilter(Color.parseColor("#22D3EE"));
        return icon;
    }

    private TextView rowLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(18);
        label.setTextColor(Color.parseColor("#DCE8F3"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = 18;
        params.rightMargin = 12;
        label.setLayoutParams(params);
        return label;
    }

    private MaterialCardView settingsCard() {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(28);
        card.setCardBackgroundColor(Color.parseColor("#1A3147"));
        card.setStrokeColor(Color.parseColor("#28435D"));
        card.setStrokeWidth(2);
        card.setCardElevation(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        card.setLayoutParams(params);
        return card;
    }

    private View divider() {
        View line = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 1);
        params.leftMargin = 22;
        params.rightMargin = 22;
        line.setLayoutParams(params);
        line.setBackgroundColor(Color.parseColor("#264359"));
        return line;
    }

    private View spacer(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, height));
        return view;
    }

    private View logoutButton() {
        Button button = new Button(this);
        button.setText("Log Out System");
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setTextColor(Color.parseColor("#FFB0AA"));
        button.setPadding(0, 20, 0, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(28);
        bg.setStroke(2, Color.parseColor("#8E5D61"));
        bg.setColor(Color.parseColor("#102A3C"));
        button.setBackground(bg);
        button.setOnClickListener(v -> {
            toast("Logged out");
            finishAffinity();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = 8;
        button.setLayoutParams(params);
        return button;
    }

    private TextView circleIcon() {
        TextView icon = new TextView(this);
        icon.setText("◉");
        icon.setTextSize(28);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Color.parseColor("#BDEFFF"));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(2, Color.parseColor("#2FC8E7"));
        bg.setColor(Color.parseColor("#153447"));
        icon.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(124, 124);
        icon.setLayoutParams(params);
        return icon;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
