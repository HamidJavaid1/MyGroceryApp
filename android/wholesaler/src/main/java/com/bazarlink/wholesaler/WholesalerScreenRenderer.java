package com.bazarlink.wholesaler;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bazarlink.shared.models.Order;
import com.bazarlink.shared.models.Page;
import com.bazarlink.shared.models.WholesalerDashboard;
import com.google.android.material.card.MaterialCardView;

import java.math.BigDecimal;

public class WholesalerScreenRenderer {

    public interface Navigator {
        void onOpenSettings();
    }

    private final Context ctx;
    private final LinearLayout content;
    private final Toast toast;

    public WholesalerScreenRenderer(Context ctx, LinearLayout content, Toast toast) {
        this.ctx = ctx;
        this.content = content;
        this.toast = toast;
    }

    public void renderLoading(String title) {
        content.removeAllViews();
        addTitle(title, "Loading...");
        content.addView(WholesalerUiUtils.spacer(ctx, 10));
    }

    public void renderDashboard(WholesalerDashboard dashboard) {
        content.removeAllViews();
        addTitle("Dashboard", "Wholesale distribution dashboard");

        cardMetric("Total Sales", formatMoney(dashboard.total_sales));
        cardMetric("Total Profit", formatMoney(dashboard.total_profit));
        cardMetric("Active Shipments", String.valueOf(dashboard.active_shipments));
        cardMetric("Pending Orders", String.valueOf(dashboard.pending_orders));

        content.addView(WholesalerUiUtils.spacer(ctx, 14));
        addSectionLabel("LOW STOCK ALERTS");

        if (dashboard.low_stock_alerts == null || dashboard.low_stock_alerts.isEmpty()) {
            card("No Alerts", "Inventory levels are healthy.");
        } else {
            for (WholesalerDashboard.LowStockAlert a : dashboard.low_stock_alerts) {
                card(a.name, "Stock left: " + a.stock_left);
            }
        }
    }

    public void renderOrders(Page<Order> page, OrderActionHandler handler) {
        content.removeAllViews();
        addTitle("Orders", "Review and update order status");
        content.addView(WholesalerUiUtils.spacer(ctx, 10));

        addSectionLabel("ORDER QUEUE");

        if (page == null || page.results == null || page.results.isEmpty()) {
            card("No Orders", "No orders found.");
            return;
        }

        for (Order o : page.results) {
            content.addView(orderCard(o, handler));
            content.addView(WholesalerUiUtils.spacer(ctx, 10));
        }
    }

    public interface OrderActionHandler {
        void onTransition(Order order, String newStatus);
    }

    private View orderCard(Order o, OrderActionHandler handler) {
        MaterialCardView card = settingsCard();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 20, 24, 20);

        TextView h = new TextView(ctx);
        h.setText("Order #" + o.id);
        h.setTextSize(18);
        h.setTextColor(Color.WHITE);
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(h);

        TextView meta = new TextView(ctx);
        meta.setText("Customer: " + (o.customer_name == null ? "" : o.customer_name) + "\nShop: " + o.shop_name + "\nStatus: " + o.status);
        meta.setTextSize(14);
        meta.setTextColor(Color.parseColor("#B8C7D8"));
        box.addView(meta);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, 12, 0, 0);

        Button accept = new Button(ctx);
        accept.setText("Dispatch");
        accept.setAllCaps(false);
        accept.setTextSize(14);
        accept.setTextColor(Color.parseColor("#DDF3FF"));
        accept.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#22D3EE")));
        accept.setOnClickListener(v -> handler.onTransition(o, "dispatched"));

        Button cancel = new Button(ctx);
        cancel.setText("Reject");
        cancel.setAllCaps(false);
        cancel.setTextSize(14);
        cancel.setTextColor(Color.parseColor("#FFB0AA"));
        cancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#8E5D61")));
        cancel.setOnClickListener(v -> handler.onTransition(o, "rejected"));

        actions.addView(accept);
        actions.addView(cancel);

        box.addView(actions);
        card.addView(box);
        return card;
    }

    public void renderSettingsPlaceholder() {
        content.removeAllViews();
        addTitle("Settings", "Manage your wholesale distribution preferences");
        content.addView(card("Pending", "Settings actions will be wired in MainActivity.") );
    }

    private void addTitle(String headerTitle, String subtitle) {
        TextView titleView = new TextView(ctx);
        titleView.setText(headerTitle);
        titleView.setTextSize(34);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setTextColor(Color.parseColor("#22D3EE"));
        titleView.setPadding(0, 0, 0, 4);
        content.addView(titleView);

        TextView sub = new TextView(ctx);
        sub.setText(subtitle);
        sub.setTextSize(16);
        sub.setTextColor(Color.parseColor("#B7C8D8"));
        content.addView(sub);
    }

    private void addSectionLabel(String label) {
        content.addView(WholesalerUiUtils.sectionLabel(ctx, label));
    }

    private void cardMetric(String title, String value) {
        MaterialCardView card = settingsCard();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 20, 24, 20);

        TextView h = new TextView(ctx);
        h.setText(title);
        h.setTextSize(14);
        h.setTextColor(Color.parseColor("#C1D0DE"));
        box.addView(h);

        TextView v = new TextView(ctx);
        v.setText(value);
        v.setTextSize(30);
        v.setTextColor(Color.parseColor("#DDF3FF"));
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(v);

        card.addView(box);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = 14;
        card.setLayoutParams(params);
        content.addView(card);
    }

    private void card(String title, String body) {
        MaterialCardView card = new MaterialCardView(ctx);
        card.setRadius(28);
        card.setCardBackgroundColor(Color.parseColor("#1A3147"));
        card.setStrokeColor(Color.parseColor("#28435D"));
        card.setStrokeWidth(2);
        card.setCardElevation(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = 22;
        card.setLayoutParams(params);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 20, 24, 20);
        TextView h = new TextView(ctx);
        h.setText(title);
        h.setTextSize(18);
        h.setTextColor(Color.WHITE);
        TextView p = new TextView(ctx);
        p.setText(body);
        p.setTextSize(14);
        p.setTextColor(Color.parseColor("#B8C7D8"));
        box.addView(h);
        box.addView(p);
        card.addView(box);
        content.addView(card);
    }

    private MaterialCardView settingsCard() {
        MaterialCardView card = new MaterialCardView(ctx);
        card.setRadius(28);
        card.setCardBackgroundColor(Color.parseColor("#1A3147"));
        card.setStrokeColor(Color.parseColor("#28435D"));
        card.setStrokeWidth(2);
        card.setCardElevation(0);
        return card;
    }

    private String formatMoney(BigDecimal v) {
        if (v == null) return "0";
        return v.toPlainString();
    }
}

