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

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bazarlink.shared.api.ApiClient;
import com.bazarlink.shared.inventory.InventoryController;
import com.bazarlink.shared.inventory.ProductInventoryHelper;
import com.bazarlink.shared.models.BulkRequest;
import com.bazarlink.shared.models.Order;
import com.bazarlink.shared.models.Page;
import com.bazarlink.shared.models.Product;
import com.bazarlink.shared.models.Shop;
import com.bazarlink.shared.models.WholesalerDashboard;
import com.google.android.material.card.MaterialCardView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WholesalerScreenRenderer {

    public interface Navigator {
        void onOpenSettings();
    }

    private final Context ctx;
    private final LinearLayout content;
    private final Toast toast;
    private ApiClient apiClient;
    private Runnable inventoryRefresh;
    private List<Product> lastInventoryProducts = new ArrayList<>();

    public WholesalerScreenRenderer(Context ctx, LinearLayout content, Toast toast) {
        this.ctx = ctx;
        this.content = content;
        this.toast = toast;
    }

    public void bindInventoryActions(ApiClient api, Runnable refresh) {
        this.apiClient = api;
        this.inventoryRefresh = refresh;
    }

    public void renderLoading(String title) {
        content.removeAllViews();
        addTitle(title, "Loading...");
        content.addView(WholesalerUiUtils.spacer(ctx, 10));
    }

    public void renderDashboard(WholesalerDashboard dashboard) {
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

    public void renderInventory(List<Product> products) {
        lastInventoryProducts = products == null ? new ArrayList<>() : new ArrayList<>(products);
        content.setPadding(WholesalerUiUtils.dp(ctx, 14), WholesalerUiUtils.dp(ctx, 14), WholesalerUiUtils.dp(ctx, 14), WholesalerUiUtils.dp(ctx, 24));
        content.addView(inventoryHeroCard());
        content.addView(WholesalerUiUtils.spacer(ctx, 18));
        MaterialCardView listCard = inventoryContainerCard(lastInventoryProducts);
        content.addView(listCard);
    }

    public void renderShops(List<Shop> shops) {
        content.setPadding(WholesalerUiUtils.dp(ctx, 14), WholesalerUiUtils.dp(ctx, 14), WholesalerUiUtils.dp(ctx, 14), WholesalerUiUtils.dp(ctx, 24));
        addTitle("Shops", "Partner retail locations in your network");
        content.addView(WholesalerUiUtils.spacer(ctx, 14));
        addSectionLabel("NEARBY SHOPS");

        if (shops == null || shops.isEmpty()) {
            card("No shops", "No partner shops were returned from the backend.");
            return;
        }

        for (Shop shop : shops) {
            String distance = shop.distance_km == null ? "" : String.format(Locale.US, " • %.1f km", shop.distance_km);
            card(safe(shop.name, "Shop"), safe(shop.kind, "retail") + distance + "\n" + safe(shop.address, "Address unavailable"));
            content.addView(WholesalerUiUtils.spacer(ctx, 10));
        }
    }

    public void renderBulkOrders(Page<BulkRequest> page) {
        content.setPadding(WholesalerUiUtils.dp(ctx, 14), WholesalerUiUtils.dp(ctx, 14), WholesalerUiUtils.dp(ctx, 14), WholesalerUiUtils.dp(ctx, 24));
        addTitle("Bulk Orders", "Incoming bulk requests and quotation workflow");
        content.addView(WholesalerUiUtils.spacer(ctx, 10));
        addSectionLabel("ORDER QUEUE");

        if (page == null || page.results == null || page.results.isEmpty()) {
            card("No bulk orders", "Bulk requests from shopkeepers will appear here.");
            return;
        }

        for (BulkRequest request : page.results) {
            content.addView(bulkRequestCard(request));
            content.addView(WholesalerUiUtils.spacer(ctx, 10));
        }
    }

    public interface BulkQuoteHandler {
        void onSendQuote(BulkRequest request);
    }

    private BulkQuoteHandler bulkQuoteHandler;
    private BulkDispatchHandler bulkDispatchHandler;

    public void bindBulkQuoteHandler(BulkQuoteHandler handler) {
        this.bulkQuoteHandler = handler;
    }

    public void bindBulkDispatchHandler(BulkDispatchHandler handler) {
        this.bulkDispatchHandler = handler;
    }

    public interface BulkDispatchHandler {
        void onDispatch(BulkRequest request);
    }

    private View bulkRequestCard(BulkRequest request) {
        MaterialCardView card = settingsCard();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(WholesalerUiUtils.dp(ctx, 20), WholesalerUiUtils.dp(ctx, 16), WholesalerUiUtils.dp(ctx, 20), WholesalerUiUtils.dp(ctx, 16));
        String qty = request.quantity == null ? "" : request.quantity.toPlainString();
        TextView h = new TextView(ctx);
        h.setText("Request #" + request.id);
        h.setTextColor(Color.WHITE);
        h.setTextSize(17);
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(h);
        TextView p = new TextView(ctx);
        p.setText("Product: " + safe(request.product_name, "—") + "\nQty: " + qty + "\nStatus: " + safe(request.status, "open"));
        p.setTextColor(Color.parseColor("#D5E3F1"));
        p.setTextSize(14);
        p.setPadding(0, WholesalerUiUtils.dp(ctx, 8), 0, WholesalerUiUtils.dp(ctx, 10));
        box.addView(p);
        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button details = new Button(ctx);
        details.setText("View details");
        details.setAllCaps(false);
        details.setOnClickListener(v -> InventoryController.showBulkRequestDetails(ctx,
                request,
                bulkQuoteHandler == null ? null : () -> bulkQuoteHandler.onSendQuote(request)));
        actions.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
        if ("open".equalsIgnoreCase(request.status) && bulkQuoteHandler != null) {
            Button quote = new Button(ctx);
            quote.setText("Send quote");
            quote.setAllCaps(false);
            quote.setOnClickListener(v -> bulkQuoteHandler.onSendQuote(request));
            actions.addView(quote, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        boolean canDispatch = request.can_dispatch || "accepted".equalsIgnoreCase(request.status);
        if (canDispatch && bulkDispatchHandler != null) {
            Button dispatch = new Button(ctx);
            dispatch.setText("Dispatch");
            dispatch.setAllCaps(false);
            dispatch.setOnClickListener(v -> bulkDispatchHandler.onDispatch(request));
            actions.addView(dispatch, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        box.addView(actions);
        card.addView(box);
        return card;
    }

    private MaterialCardView inventoryHeroCard() {
        MaterialCardView card = settingsCard();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(WholesalerUiUtils.dp(ctx, 24), WholesalerUiUtils.dp(ctx, 20), WholesalerUiUtils.dp(ctx, 24), WholesalerUiUtils.dp(ctx, 20));

        TextView title = new TextView(ctx);
        title.setText("Inventory Control");
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#64D6F7"));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(ctx);
        subtitle.setText("Products and stock levels in one place — thresholds, fast movers, and restock timing.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#E0EAF5"));
        subtitle.setPadding(0, WholesalerUiUtils.dp(ctx, 10), 0, 0);
        box.addView(subtitle);

        card.addView(box);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = 0;
        card.setLayoutParams(params);
        return card;
    }

    private MaterialCardView inventoryContainerCard(List<Product> products) {
        MaterialCardView card = settingsCard();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(WholesalerUiUtils.dp(ctx, 20), WholesalerUiUtils.dp(ctx, 18), WholesalerUiUtils.dp(ctx, 20), WholesalerUiUtils.dp(ctx, 18));

        if (products == null || products.isEmpty()) {
            box.addView(inventoryItemCard(null, "No inventory items", "Create bulk products to see them here.", "Healthy buffer", "Stable", false));
        } else {
            for (int i = 0; i < products.size(); i++) {
                Product product = products.get(i);
                boolean low = product.stock_quantity != null && product.low_stock_threshold != null
                        && product.stock_quantity.compareTo(product.low_stock_threshold) <= 0;
                box.addView(inventoryItemCard(
                        product,
                        safe(product.name, "Product"),
                        money(product.stock_quantity) + " " + safe(product.unit, "units") + " left • Rs " + money(product.price),
                        statusBadgeForStock(product),
                        stockHintForProduct(product),
                        low
                ));
                if (i < products.size() - 1) {
                    box.addView(WholesalerUiUtils.spacer(ctx, 12));
                }
            }
        }
        box.addView(WholesalerUiUtils.spacer(ctx, 16));
        box.addView(inventoryActionsRow(products));

        card.addView(box);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        card.setLayoutParams(params);
        return card;
    }

    private View inventoryActionsRow(List<Product> products) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);

        Button addProduct = new Button(ctx);
        addProduct.setText("Add product");
        addProduct.setAllCaps(false);
        addProduct.setOnClickListener(v -> {
            if (apiClient == null || !(ctx instanceof AppCompatActivity)) {
                Toast.makeText(ctx, "API not ready", Toast.LENGTH_SHORT).show();
                return;
            }
            ProductInventoryHelper.showAddProductForm(
                    (AppCompatActivity) ctx,
                    apiClient,
                    "wholesaler",
                    null,
                    null,
                    () -> {
                        if (inventoryRefresh != null) {
                            inventoryRefresh.run();
                        }
                    }
            );
        });
        col.addView(addProduct, new LinearLayout.LayoutParams(-1, -2));
        col.addView(WholesalerUiUtils.spacer(ctx, 10));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button scanner = new Button(ctx);
        scanner.setText("Scanner");
        scanner.setAllCaps(false);
        scanner.setOnClickListener(v -> openScanner(products));
        Button restock = new Button(ctx);
        restock.setText("Quick restock");
        restock.setAllCaps(false);
        restock.setOnClickListener(v -> InventoryController.showScannerDialog(ctx, apiClient, products, refreshListener()));
        row.addView(scanner, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(restock, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(row);
        return col;
    }

    private View inventoryItemCard(@Nullable Product product, String titleText, String subtitleText, String badgeText, String hintText, boolean lowSupply) {
        MaterialCardView card = settingsCard();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(WholesalerUiUtils.dp(ctx, 18), WholesalerUiUtils.dp(ctx, 16), WholesalerUiUtils.dp(ctx, 18), WholesalerUiUtils.dp(ctx, 16));

        TextView title = new TextView(ctx);
        title.setText(titleText);
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(ctx);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#D5E3F1"));
        subtitle.setPadding(0, WholesalerUiUtils.dp(ctx, 6), 0, 0);
        box.addView(subtitle);

        LinearLayout actionRow = new LinearLayout(ctx);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, WholesalerUiUtils.dp(ctx, 12), 0, 0);

        TextView badge = new TextView(ctx);
        badge.setText(badgeText);
        badge.setTextSize(12);
        badge.setTextColor(lowSupply ? Color.parseColor("#1F2937") : Color.parseColor("#0A223D"));
        badge.setPadding(WholesalerUiUtils.dp(ctx, 12), WholesalerUiUtils.dp(ctx, 7), WholesalerUiUtils.dp(ctx, 12), WholesalerUiUtils.dp(ctx, 7));
        badge.setBackgroundColor(lowSupply ? Color.parseColor("#FBBF24") : Color.parseColor("#67E8F9"));
        actionRow.addView(badge);

        TextView hint = new TextView(ctx);
        hint.setText(hintText);
        hint.setTextSize(14);
        hint.setTextColor(Color.parseColor("#D5E3F1"));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(0, -2, 1f);
        hintParams.leftMargin = WholesalerUiUtils.dp(ctx, 10);
        hint.setLayoutParams(hintParams);
        actionRow.addView(hint);

        if (product != null && apiClient != null) {
            Button restock = new Button(ctx);
            restock.setText("Restock");
            restock.setAllCaps(false);
            restock.setTextColor(Color.parseColor("#FFFFFF"));
            restock.setOnClickListener(v -> InventoryController.showRestockDialog(ctx, apiClient, product, refreshListener()));
            actionRow.addView(restock, new LinearLayout.LayoutParams(WholesalerUiUtils.dp(ctx, 96), -2));
        }

        box.addView(actionRow);
        card.addView(box);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        card.setLayoutParams(params);
        return card;
    }

    private String statusBadgeForStock(Product product) {
        if (product == null || product.stock_quantity == null || product.low_stock_threshold == null) {
            return "Healthy buffer";
        }
        int comparison = product.stock_quantity.compareTo(product.low_stock_threshold);
        if (comparison <= 0) return "Reorder soon";
        if (comparison <= 1) return "Restock today";
        return "Healthy buffer";
    }

    private String stockHintForProduct(Product product) {
        if (product == null || product.stock_quantity == null || product.low_stock_threshold == null) {
            return "Stable";
        }
        if (product.stock_quantity.compareTo(product.low_stock_threshold) <= 0) return "Low supply";
        if (product.stock_quantity.compareTo(product.low_stock_threshold.multiply(new BigDecimal("2"))) <= 0) {
            return "Fast moving";
        }
        return "Balanced";
    }

    private String money(BigDecimal value) {
        if (value == null) return "0";
        return value.toPlainString();
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
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
                    "wholesaler",
                    null,
                    null,
                    refreshListener()
            );
        } else {
            InventoryController.showScannerDialog(ctx, apiClient, products, refreshListener());
        }
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

