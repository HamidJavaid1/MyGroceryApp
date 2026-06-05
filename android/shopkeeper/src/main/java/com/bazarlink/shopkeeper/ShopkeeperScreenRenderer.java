package com.bazarlink.shopkeeper;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bazarlink.shared.api.ApiClient;
import com.bazarlink.shared.inventory.InventoryController;
import com.bazarlink.shared.inventory.ProductInventoryHelper;
import com.bazarlink.shared.models.Order;
import com.bazarlink.shared.models.Page;
import com.bazarlink.shared.models.Product;
import com.bazarlink.shared.models.WholesalerDashboard;
import com.google.android.material.card.MaterialCardView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ShopkeeperScreenRenderer {

    public interface OrderActionHandler {
        void onTransition(Order order, String newStatus);
    }

    private final Context ctx;
    private final LinearLayout content;
    private ApiClient apiClient;
    private Runnable inventoryRefresh;

    public ShopkeeperScreenRenderer(Context ctx, LinearLayout content) {
        this.ctx = ctx;
        this.content = content;
    }

    public void bindInventoryActions(ApiClient api, Runnable refresh) {
        this.apiClient = api;
        this.inventoryRefresh = refresh;
    }

    public void renderDashboard(WholesalerDashboard dashboard) {
        addTitle("Dashboard", "Retail shop operations dashboard");

        cardMetric("Total Sales", formatMoney(dashboard.total_sales));
        cardMetric("Total Profit", formatMoney(dashboard.total_profit));
        cardMetric("Active Shipments", String.valueOf(dashboard.active_shipments));
        cardMetric("Pending Orders", String.valueOf(dashboard.pending_orders));

        content.addView(ShopkeeperUiUtils.spacer(ctx, 14));
        addSectionLabel("LOW STOCK ALERTS");

        if (dashboard.low_stock_alerts == null || dashboard.low_stock_alerts.isEmpty()) {
            card("No Alerts", "Inventory levels are healthy.");
            return;
        }

        for (WholesalerDashboard.LowStockAlert alert : dashboard.low_stock_alerts) {
            card(alert.name, "Stock left: " + alert.stock_left);
        }
    }

    public void renderInventory(List<Product> products) {
        content.setPadding(ShopkeeperUiUtils.dp(ctx, 14), ShopkeeperUiUtils.dp(ctx, 14), ShopkeeperUiUtils.dp(ctx, 14), ShopkeeperUiUtils.dp(ctx, 24));
        addTitle("Inventory", "Products and stock in one place");
        content.addView(ShopkeeperUiUtils.spacer(ctx, 14));
        addSectionLabel("STOCK LIST");

        if (products == null || products.isEmpty()) {
            card("No inventory items", "Add products to your shop to manage stock here.");
            return;
        }

        List<Product> list = products == null ? new ArrayList<>() : products;
        for (int i = 0; i < list.size(); i++) {
            Product product = list.get(i);
            content.addView(inventoryProductCard(product));
            if (i < list.size() - 1) {
                content.addView(ShopkeeperUiUtils.spacer(ctx, 10));
            }
        }
        content.addView(ShopkeeperUiUtils.spacer(ctx, 14));
        content.addView(inventoryActionsRow(list));
    }

    private View inventoryProductCard(Product product) {
        MaterialCardView card = settingsCard();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20, 16, 20, 16);
        boolean low = product.stock_quantity != null && product.low_stock_threshold != null
                && product.stock_quantity.compareTo(product.low_stock_threshold) <= 0;
        TextView h = new TextView(ctx);
        h.setText(safe(product.name, "Product"));
        h.setTextColor(Color.WHITE);
        h.setTextSize(17);
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(h);
        TextView meta = new TextView(ctx);
        meta.setText(money(product.stock_quantity) + " " + safe(product.unit, "units") + " • Rs " + money(product.price)
                + " • " + (low ? "Reorder soon" : "In stock"));
        meta.setTextColor(Color.parseColor("#D5E3F1"));
        meta.setTextSize(14);
        meta.setPadding(0, 6, 0, 10);
        box.addView(meta);
        card.addView(box);
        return card;
    }

    private View inventoryActionsRow(List<Product> products) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button addProduct = new Button(ctx);
        addProduct.setText("Add product");
        addProduct.setAllCaps(false);
        addProduct.setOnClickListener(v -> {
            if (apiClient == null || !(ctx instanceof AppCompatActivity)) {
                Toast.makeText(ctx, "API not ready", Toast.LENGTH_SHORT).show();
                return;
            }
            ProductInventoryHelper.launchInventoryScanner(
                    (AppCompatActivity) ctx,
                    apiClient,
                    "shopkeeper",
                    null,
                    null,
                    refreshListener()
            );
        });
        row.addView(addProduct, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(row);
        return col;
    }

    private InventoryController.RefreshListener refreshListener() {
        return () -> {
            if (inventoryRefresh != null) {
                inventoryRefresh.run();
            }
        };
    }

    private void openScanner(List<Product> products) {
        if (apiClient == null) {
            Toast.makeText(ctx, "API not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ctx instanceof AppCompatActivity) {
            ProductInventoryHelper.launchInventoryScanner(
                    (AppCompatActivity) ctx,
                    apiClient,
                    "shopkeeper",
                    null,
                    null,
                    refreshListener()
            );
        } else {
            InventoryController.showScannerDialog(ctx, apiClient, products, refreshListener());
        }
    }

    public void renderOrders(Page<Order> page, OrderActionHandler handler) {
        addTitle("Orders", "Review and update order status");
        content.addView(ShopkeeperUiUtils.spacer(ctx, 10));
        addSectionLabel("ORDER QUEUE");

        if (page == null || page.results == null || page.results.isEmpty()) {
            card("No Orders", "No orders found.");
            return;
        }

        for (Order order : page.results) {
            content.addView(orderCard(order, handler));
            content.addView(ShopkeeperUiUtils.spacer(ctx, 10));
        }
    }

    private View orderCard(Order order, OrderActionHandler handler) {
        MaterialCardView card = settingsCard();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 20, 24, 20);

        TextView title = new TextView(ctx);
        title.setText("Order #" + order.id);
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView meta = new TextView(ctx);
        meta.setText("Customer: " + (order.customer_name == null ? "" : order.customer_name) + "\nShop: " + order.shop_name + "\nStatus: " + order.status);
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
        accept.setOnClickListener(v -> handler.onTransition(order, "dispatched"));

        Button reject = new Button(ctx);
        reject.setText("Reject");
        reject.setAllCaps(false);
        reject.setTextSize(14);
        reject.setTextColor(Color.parseColor("#FFB0AA"));
        reject.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#8E5D61")));
        reject.setOnClickListener(v -> handler.onTransition(order, "rejected"));

        Button details = new Button(ctx);
        details.setText("Details");
        details.setAllCaps(false);
        details.setTextSize(13);
        details.setOnClickListener(v -> InventoryController.showOrderDetails(ctx, order));

        actions.addView(details);
        actions.addView(accept);
        actions.addView(reject);
        box.addView(actions);
        card.addView(box);
        return card;
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
        content.addView(ShopkeeperUiUtils.sectionLabel(ctx, label));
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
        MaterialCardView card = settingsCard();
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

    private String formatMoney(BigDecimal value) {
        if (value == null) return "0";
        return value.toPlainString();
    }

    private String money(BigDecimal value) {
        return formatMoney(value);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
