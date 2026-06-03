package com.bazarlink.wholesaler;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import android.annotation.SuppressLint;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bazarlink.shared.api.ApiClient;
import com.bazarlink.shared.api.ApiConfig;
import com.bazarlink.shared.api.TokenStore;
import com.bazarlink.shared.inventory.InventoryController;
import com.bazarlink.shared.models.BulkRequest;
import com.bazarlink.shared.models.Quotation;
import com.bazarlink.shared.models.Order;
import com.bazarlink.shared.models.Page;
import com.bazarlink.shared.models.Product;
import com.bazarlink.shared.models.Shop;
import com.bazarlink.shared.models.WholesalerDashboard;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private static final int DRAWER_WIDTH_DP = 292;

    private LinearLayout content;
    private BottomNavigationView nav;
    private String profileName;
    private WholesalerRepository repository;
    private String activeScreen = "Dashboard";

    private FrameLayout rootContainer;
    private View drawerScrim;
    private MaterialCardView drawerPanel;

    @SuppressLint("ResourceType")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profileName = getIntent().getStringExtra("display_name");
        if (profileName == null || profileName.trim().isEmpty()) {
            profileName = "Noor Din";
        }
        // Important: profileName must never block inventory operations.
        // If caller passes an empty name (or an async load fails), we still allow product creation/restock flows.
        if (profileName != null) {
            profileName = profileName.trim();
            if (profileName.isEmpty()) profileName = "Noor Din";
        }

        repository = new WholesalerRepository(this, repositoryDefaultBaseUrl());

        rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(Color.parseColor("#0E2236"));

        LinearLayout mainColumn = new LinearLayout(this);
        mainColumn.setOrientation(LinearLayout.VERTICAL);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28, 28, 28, 28);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        mainColumn.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        nav = new BottomNavigationView(this);
        nav.getMenu().add(0, 1, 0, "Dashboard").setIcon(android.R.drawable.ic_menu_view);
        nav.getMenu().add(0, 2, 1, "Inventory").setIcon(android.R.drawable.ic_menu_sort_by_size);
        nav.getMenu().add(0, 3, 2, "Bulk Orders").setIcon(android.R.drawable.ic_menu_agenda);
        nav.getMenu().add(0, 4, 3, "Shops").setIcon(android.R.drawable.ic_menu_myplaces);
        nav.getMenu().add(0, 5, 4, "Settings").setIcon(android.R.drawable.ic_menu_preferences);
        nav.setOnItemSelectedListener(item -> {
            activeScreen = item.getTitle().toString();
            render(activeScreen);
            return true;
        });
        mainColumn.addView(nav);

        rootContainer.addView(mainColumn, new FrameLayout.LayoutParams(-1, -1));

        drawerScrim = new View(this);
        drawerScrim.setBackgroundColor(0x99000000);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(v -> closeDrawer());
        rootContainer.addView(drawerScrim, new FrameLayout.LayoutParams(-1, -1));

        drawerPanel = buildDrawerPanel();
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(dp(DRAWER_WIDTH_DP), -1);
        drawerParams.gravity = Gravity.START;
        rootContainer.addView(drawerPanel, drawerParams);
        drawerPanel.setTranslationX(-dp(DRAWER_WIDTH_DP));

        setContentView(rootContainer);
        nav.setSelectedItemId(1);
        render("Dashboard");
    }

    private void render(String screen) {
        activeScreen = screen;
        syncDrawerSelection();
        content.removeAllViews();
        content.setPadding(28, 28, 28, 28);

        if ("Settings".equals(screen)) {
            renderSettings();
            return;
        }

        content.addView(headerCard());
        content.addView(spacer(12));

        if ("Dashboard".equals(screen)) {
            renderDashboard();
        } else if ("Inventory".equals(screen)) {
            renderInventory();
        } else if ("Bulk Orders".equals(screen)) {
            renderBulkOrders();
        } else if ("Shops".equals(screen)) {
            renderShops();
        }
    }

    private void navigateTo(String screen) {
        activeScreen = screen;
        int navId = 1;
        if ("Inventory".equals(screen)) navId = 2;
        else if ("Bulk Orders".equals(screen)) navId = 3;
        else if ("Shops".equals(screen)) navId = 4;
        else if ("Settings".equals(screen)) navId = 5;
        nav.setSelectedItemId(navId);
        render(screen);
    }

    private void syncDrawerSelection() {
        // Drawer highlights are rebuilt when opened; selection is applied on navigate.
    }

    private String repositoryDefaultBaseUrl() {
        try {
            TokenStore store = new TokenStore(this);
            String saved = store.serverUrl();
            if (saved != null && !saved.isEmpty()) {
                if (!saved.endsWith("/")) saved = saved + "/";
                return saved;
            }
        } catch (Exception ignored) {}
        return ApiConfig.BASE_URL;
    }

    private void renderDashboard() {
        TextView loading = new TextView(this);
        loading.setText("Loading wholesale metrics...");
        loading.setTextColor(Color.parseColor("#B7C8D8"));
        content.addView(loading);

        repository.loadDashboard(new WholesalerRepository.Callback1<WholesalerDashboard>() {
            @Override
            public void onSuccess(WholesalerDashboard data) {
                WholesalerScreenRenderer renderer = renderer();
                content.removeAllViews();
                content.setPadding(28, 28, 28, 28);
                content.addView(headerCard());
                content.addView(spacer(12));
                renderer.renderDashboard(data);
            }

            @Override
            public void onError(String message) {
                toast(message);
                card("Dashboard error", message);
            }
        });
    }

    private void renderInventory() {
        TextView loading = new TextView(this);
        loading.setText("Loading inventory...");
        loading.setTextColor(Color.parseColor("#B7C8D8"));
        content.addView(loading);

        Map<String, String> filters = new HashMap<>();
        filters.put("mine", "true");
        repository.loadProducts(filters, new WholesalerRepository.Callback1<Page<Product>>() {
            @Override
            public void onSuccess(Page<Product> page) {
                List<Product> items = page.results == null ? new ArrayList<>() : page.results;
                content.removeAllViews();
                content.setPadding(0, 0, 0, 0);
                content.addView(headerCard());
                WholesalerScreenRenderer r = renderer();
                r.renderInventory(items);
            }

            @Override
            public void onError(String message) {
                toast(message);
                card("Inventory error", message);
            }
        });
    }

    private void renderBulkOrders() {
        TextView loading = new TextView(this);
        loading.setText("Loading bulk orders...");
        loading.setTextColor(Color.parseColor("#B7C8D8"));
        content.addView(loading);

        repository.loadBulkRequests(new WholesalerRepository.Callback1<Page<BulkRequest>>() {
            @Override
            public void onSuccess(Page<BulkRequest> page) {
                content.removeAllViews();
                content.setPadding(0, 0, 0, 0);
                content.addView(headerCard());
                WholesalerScreenRenderer r = renderer();
                r.renderBulkOrders(page);
            }

            @Override
            public void onError(String message) {
                toast(message);
                card("Bulk orders error", message);
            }
        });
    }

    private void renderShops() {
        TextView loading = new TextView(this);
        loading.setText("Loading shops...");
        loading.setTextColor(Color.parseColor("#B7C8D8"));
        content.addView(loading);

        repository.loadShops(new WholesalerRepository.Callback1<List<Shop>>() {
            @Override
            public void onSuccess(List<Shop> shops) {
                content.removeAllViews();
                content.setPadding(0, 0, 0, 0);
                content.addView(headerCard());
                renderer().renderShops(shops);
            }

            @Override
            public void onError(String message) {
                toast(message);
                card("Shops error", message);
            }
        });
    }

    private void renderSettings() {
        content.addView(headerCard());
        content.addView(spacer(12));
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
                rowSwitch(android.R.drawable.ic_menu_info_details, "Push Notifications", true, (button, checked) -> toast("Notification preference saved")),
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

    private MaterialCardView headerCard() {
        MaterialCardView card = settingsCard();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(20, 18, 20, 18);

        ImageView menu = new ImageView(this);
        menu.setImageResource(android.R.drawable.ic_menu_sort_by_size);
        menu.setColorFilter(Color.WHITE);
        menu.setOnClickListener(v -> openDrawer());
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        menuParams.rightMargin = dp(14);
        row.addView(menu, menuParams);

        TextView name = new TextView(this);
        name.setText(profileName);
        name.setTextColor(Color.WHITE);
        name.setTextSize(20);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView avatar = circleIcon();
        row.addView(avatar);

        card.addView(row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(4);
        card.setLayoutParams(params);
        return card;
    }

    private MaterialCardView buildDrawerPanel() {
        MaterialCardView panel = new MaterialCardView(this);
        panel.setCardBackgroundColor(Color.parseColor("#081A2C"));
        panel.setRadius(dp(24));
        panel.setCardElevation(dp(10));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(18));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = circleIcon();
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        header.addView(avatar, avatarParams);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, -2, 1f);
        infoParams.leftMargin = dp(12);
        info.setLayoutParams(infoParams);

        TextView name = new TextView(this);
        name.setText(profileName);
        name.setTextColor(Color.WHITE);
        name.setTextSize(18);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);

        TextView role = new TextView(this);
        role.setText("Wholesale Manager");
        role.setTextColor(Color.parseColor("#B3C7D8"));
        role.setTextSize(12);

        info.addView(name);
        info.addView(role);
        header.addView(info);

        TextView close = new TextView(this);
        close.setText("X");
        close.setTextColor(Color.parseColor("#E5EEF7"));
        close.setTextSize(18);
        close.setPadding(dp(10), dp(4), dp(6), dp(4));
        close.setOnClickListener(v -> closeDrawer());
        header.addView(close);

        box.addView(header);
        box.addView(spacer(18));
        box.addView(drawerItem("Dashboard"));
        box.addView(spacer(10));
        box.addView(drawerItem("Inventory"));
        box.addView(spacer(10));
        box.addView(drawerItem("Bulk Orders"));
        box.addView(spacer(10));
        box.addView(drawerItem("Shops"));
        box.addView(spacer(10));
        box.addView(drawerItem("Settings"));

        View flex = new View(this);
        flex.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        box.addView(flex);

        MaterialCardView logout = drawerItem("Logout");
        box.addView(logout);

        panel.addView(box, new LinearLayout.LayoutParams(-1, -1));
        return panel;
    }

    private MaterialCardView drawerItem(String label) {
        boolean selected = label.equals(activeScreen);
        MaterialCardView item = new MaterialCardView(this);
        item.setCardBackgroundColor(selected ? Color.parseColor("#46567B") : Color.TRANSPARENT);
        item.setRadius(dp(14));
        item.setCardElevation(0);
        item.setClickable(true);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(selected ? Color.parseColor("#F4FAFF") : Color.parseColor("#D3E1EE"));
        text.setTextSize(15);
        row.addView(text);

        item.addView(row);
        item.setOnClickListener(v -> {
            closeDrawer();
            if ("Logout".equals(label)) {
                toast("Logged out");
                finishAffinity();
                return;
            }
            navigateTo(label);
        });
        return item;
    }

    private void openDrawer() {
        drawerScrim.setVisibility(View.VISIBLE);
        drawerPanel.animate().translationX(0f).setDuration(220L).start();
    }

    private void closeDrawer() {
        drawerPanel.animate().translationX(-dp(DRAWER_WIDTH_DP)).setDuration(220L)
                .withEndAction(() -> drawerScrim.setVisibility(View.GONE)).start();
    }

    private WholesalerScreenRenderer renderer() {
        WholesalerScreenRenderer r = new WholesalerScreenRenderer(this, content, Toast.makeText(this, "", Toast.LENGTH_SHORT));
        ApiClient client = new ApiClient(this, repositoryDefaultBaseUrl());
        r.bindInventoryActions(client, this::renderInventory);
        r.bindBulkQuoteHandler(this::showBulkQuoteDialog);
        r.bindBulkDispatchHandler(this::dispatchBulkRequest);
        return r;
    }

    private void dispatchBulkRequest(BulkRequest request) {
        new ApiClient(this, repositoryDefaultBaseUrl()).api().dispatchBulkRequest(request.id).enqueue(new Callback<BulkRequest>() {
            @Override
            public void onResponse(Call<BulkRequest> call, Response<BulkRequest> response) {
                if (response.isSuccessful()) {
                    toast("Dispatched");
                    renderBulkOrders();
                } else {
                    String message = "Dispatch failed";
                    try {
                        if (response.errorBody() != null) {
                            String body = response.errorBody().string().trim();
                            if (!body.isEmpty()) {
                                message = body;
                            }
                        }
                    } catch (IOException ignored) {
                    }
                    toast(message);
                }
            }

            @Override
            public void onFailure(Call<BulkRequest> call, Throwable t) {
                toast("Dispatch failed: " + (t.getMessage() == null ? "network error" : t.getMessage()));
            }
        });
    }

    private void showBulkQuoteDialog(BulkRequest request) {
        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(dp(20), dp(16), dp(20), dp(8));
        EditText price = new EditText(this);
        price.setHint("Price per unit");
        price.setText("1200");
        EditText fee = new EditText(this);
        fee.setHint("Delivery fee");
        fee.setText("500");
        EditText message = new EditText(this);
        message.setHint("Message");
        message.setText("Bulk quote for your request");
        dialog.addView(price);
        dialog.addView(fee);
        dialog.addView(message);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Quote for request #" + request.id)
                .setView(dialog)
                .setPositiveButton("Send", (d, w) -> {
                    Quotation body = new Quotation();
                    body.bulk_request = request.id;
                    try {
                        body.price_per_unit = new BigDecimal(price.getText().toString().trim());
                        body.delivery_fee = new BigDecimal(fee.getText().toString().trim());
                    } catch (Exception e) {
                        toast("Invalid price");
                        return;
                    }
                    body.message = message.getText().toString().trim();
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_MONTH, 7);
                    body.valid_until = String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d:%02dZ",
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
                    new ApiClient(this, repositoryDefaultBaseUrl()).api().createQuotation(body).enqueue(new Callback<Quotation>() {
                        @Override
                        public void onResponse(Call<Quotation> call, Response<Quotation> response) {
                            if (response.isSuccessful()) {
                                toast("Quotation sent");
                                renderBulkOrders();
                            } else {
                                toast("Failed to send quotation");
                            }
                        }

                        @Override
                        public void onFailure(Call<Quotation> call, Throwable t) {
                            toast("Failed: " + t.getMessage());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int value) {
        return WholesalerUiUtils.dp(this, value);
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
        MaterialCardView card = settingsCard();
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
        content.addView(WholesalerUiUtils.sectionLabel(this, label));
    }

    private MaterialCardView topProfileCard() {
        MaterialCardView card = settingsCard();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(24, 24, 24, 24);

        row.addView(circleIcon());

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
        row.addView(info);

        TextView edit = new TextView(this);
        edit.setText("✎");
        edit.setTextSize(28);
        edit.setTextColor(Color.parseColor("#22D3EE"));
        edit.setPadding(12, 0, 0, 0);
        row.addView(edit);

        card.addView(row);
        return card;
    }

    private MaterialCardView systemStatusCard() {
        MaterialCardView card = settingsCard();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 24, 24, 24);

        TextView status = new TextView(this);
        status.setText("All Operational");
        status.setTextSize(28);
        status.setTextColor(Color.parseColor("#DDF3FF"));
        status.setTypeface(status.getTypeface(), android.graphics.Typeface.BOLD);

        TextView version = new TextView(this);
        version.setText("Version 2.4.1 Build-Stable");
        version.setTextSize(15);
        version.setTextColor(Color.parseColor("#AEC2D3"));

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
            if (i < rows.length - 1) box.addView(divider());
        }
        card.addView(box);
        return card;
    }

    private View rowAction(int iconRes, String text, boolean showArrow, Runnable onClick) {
        LinearLayout row = baseRow();
        row.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
            else toast(text + " opened");
        });
        row.addView(rowIcon(iconRes));
        row.addView(rowLabel(text));
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
        row.addView(rowLabel(text));
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
        row.addView(rowLabel(text));
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
        card.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
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
        return WholesalerUiUtils.spacer(this, height);
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
        icon.setLayoutParams(params);
        return icon;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        InventoryController.handleScanResult(requestCode, resultCode, data);
    }
}
