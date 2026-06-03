package com.bazarlink.shared.inventory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;

import androidx.appcompat.app.AppCompatActivity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.bazarlink.shared.api.ApiClient;
import com.bazarlink.shared.models.BulkRequest;
import com.bazarlink.shared.models.Order;
import com.bazarlink.shared.models.OrderItem;
import com.bazarlink.shared.models.Product;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Restock dialogs, scanner lookup, and order/bulk detail popups. */
public final class InventoryController {

    public interface RefreshListener {
        void onInventoryChanged();
    }

    public static final int REQUEST_CODE_BARCODE_SCAN = 9203;

    private static PendingScan pendingScan;

    private static final class PendingScan {
        final ApiClient api;
        final List<Product> products;
        final RefreshListener refresh;
        final Context context;

        PendingScan(Context context, ApiClient api, List<Product> products, RefreshListener refresh) {
            this.context = context;
            this.api = api;
            this.products = products;
            this.refresh = refresh;
        }
    }

    private InventoryController() {}

    /** Opens ML Kit camera scanner (requests CAMERA permission in scanner activity). */
    public static void launchBarcodeScanner(AppCompatActivity activity, ApiClient api, List<Product> products, RefreshListener refresh) {
        pendingScan = new PendingScan(activity, api, products, refresh);
        activity.startActivityForResult(new Intent(activity, BarcodeScannerActivity.class), REQUEST_CODE_BARCODE_SCAN);
    }

    /** Call from Activity.onActivityResult (e.g. after avatar picker). */
    public static boolean handleScanResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_BARCODE_SCAN || pendingScan == null) {
            return false;
        }
        PendingScan pending = pendingScan;
        pendingScan = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
            String code = data.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE);
            if (code == null || code.trim().isEmpty()) {
                toast(pending.context, "No barcode detected");
                return true;
            }
            Product match = findProduct(pending.products, code.trim());
            if (match == null) {
                toast(pending.context, "No product matches: " + code);
                showManualScannerFallback(pending.context, pending.api, pending.products, pending.refresh, code);
                return true;
            }
            showRestockDialog(pending.context, pending.api, match, pending.refresh);
        }
        return true;
    }

    public static void showRestockDialog(Context context, ApiClient api, Product product, RefreshListener refresh) {
        if (product == null) {
            return;
        }
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        EditText qty = new EditText(context);
        qty.setHint("Add stock quantity");
        qty.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        BigDecimal current = product.stock_quantity == null ? BigDecimal.ZERO : product.stock_quantity;
        qty.setText("10");
        box.addView(qty);

        new AlertDialog.Builder(context)
                .setTitle("Restock: " + product.name)
                .setMessage("Current stock: " + current.toPlainString() + " " + safeUnit(product.unit))
                .setView(box)
                .setPositiveButton("Add stock", (d, w) -> {
                    try {
                        BigDecimal add = new BigDecimal(qty.getText().toString().trim());
                        if (add.compareTo(BigDecimal.ZERO) <= 0) {
                            toast(context, "Enter a positive amount");
                            return;
                        }
                        BigDecimal updated = current.add(add);
                        patchStock(context, api, product.id, updated, refresh);
                    } catch (Exception e) {
                        toast(context, "Invalid quantity");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showScannerDialog(Context context, ApiClient api, List<Product> products, RefreshListener refresh) {
        if (context instanceof AppCompatActivity) {
            launchBarcodeScanner((AppCompatActivity) context, api, products, refresh);
            return;
        }
        showManualScannerFallback(context, api, products, refresh, null);
    }

    private static void showManualScannerFallback(Context context, ApiClient api, List<Product> products, RefreshListener refresh, String prefilled) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        EditText scan = new EditText(context);
        scan.setHint("Product ID or name");
        scan.setInputType(InputType.TYPE_CLASS_TEXT);
        if (prefilled != null) {
            scan.setText(prefilled);
        }
        box.addView(scan);

        new AlertDialog.Builder(context)
                .setTitle("Find product")
                .setMessage("Enter product ID or name if the barcode did not match.")
                .setView(box)
                .setPositiveButton("Find & restock", (d, w) -> {
                    String query = scan.getText().toString().trim();
                    if (query.isEmpty()) {
                        toast(context, "Enter a product ID or name");
                        return;
                    }
                    Product match = findProduct(products, query);
                    if (match == null) {
                        toast(context, "No matching product in this list");
                        return;
                    }
                    showRestockDialog(context, api, match, refresh);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showBulkRequestDetails(Context context, BulkRequest request, Runnable onSendQuote) {
        if (request == null) {
            return;
        }
        StringBuilder body = new StringBuilder();
        body.append("Product: ").append(safe(request.product_name)).append("\n");
        body.append("Quantity: ").append(request.quantity == null ? "—" : request.quantity.toPlainString()).append("\n");
        body.append("Status: ").append(safe(request.status)).append("\n");
        body.append("Address: ").append(safe(request.delivery_address)).append("\n");
        if (request.notes != null && !request.notes.trim().isEmpty()) {
            body.append("Notes: ").append(request.notes.trim());
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle("Bulk request #" + request.id)
                .setMessage(body.toString())
                .setNegativeButton("Close", null);
        if (onSendQuote != null && "open".equalsIgnoreCase(request.status)) {
            builder.setPositiveButton("Send quotation", (d, w) -> onSendQuote.run());
        }
        builder.show();
    }

    public static void showOrderDetails(Context context, Order order) {
        if (order == null) {
            return;
        }
        StringBuilder body = new StringBuilder();
        body.append("Shop: ").append(safe(order.shop_name)).append("\n");
        body.append("Status: ").append(safe(order.status)).append("\n");
        body.append("Payment: ").append(safe(order.payment_method)).append("\n");
        body.append("Address: ").append(safe(order.address)).append("\n");
        body.append("Subtotal: Rs ").append(money(order.subtotal)).append("\n");
        body.append("Delivery: Rs ").append(money(order.delivery_fee)).append("\n");
        body.append("Total: Rs ").append(money(order.total)).append("\n\n");
        if (order.items != null && !order.items.isEmpty()) {
            body.append("Items:\n");
            for (int i = 0; i < order.items.size(); i++) {
                OrderItem item = order.items.get(i);
                body.append("• Product #").append(item.product)
                        .append(" × ").append(money(item.quantity))
                        .append(" @ Rs ").append(money(item.unit_price)).append("\n");
            }
        } else {
            body.append("No line items returned.");
        }
        new AlertDialog.Builder(context)
                .setTitle("Order #" + order.id)
                .setMessage(body.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private static void patchStock(Context context, ApiClient api, long productId, BigDecimal newQty, RefreshListener refresh) {
        Map<String, String> body = new HashMap<>();
        body.put("stock_quantity", newQty.toPlainString());
        api.api().updateProduct(productId, body).enqueue(new Callback<Product>() {
            @Override
            public void onResponse(Call<Product> call, Response<Product> response) {
                if (response.isSuccessful()) {
                    toast(context, "Stock updated to " + newQty.toPlainString());
                    if (refresh != null) {
                        refresh.onInventoryChanged();
                    }
                } else {
                    toast(context, "Restock failed");
                }
            }

            @Override
            public void onFailure(Call<Product> call, Throwable t) {
                toast(context, "Restock failed: " + t.getMessage());
            }
        });
    }

    public static Product findProductInList(List<Product> products, String query) {
        return findProduct(products, query);
    }

    private static Product findProduct(List<Product> products, String query) {
        if (products == null || query == null) {
            return null;
        }
        String normalized = query.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            long id = Long.parseLong(normalized);
            for (Product p : products) {
                if (p.id == id) {
                    return p;
                }
            }
        } catch (NumberFormatException ignored) {
            // not a numeric id
        }
        for (Product p : products) {
            if (p.name != null && p.name.trim().equalsIgnoreCase(query.trim())) {
                return p;
            }
        }
        for (Product p : products) {
            if (p.name != null && p.name.toLowerCase(Locale.US).contains(normalized)) {
                return p;
            }
        }
        return null;
    }

    private static String safe(String v) {
        return v == null || v.trim().isEmpty() ? "—" : v.trim();
    }

    private static String safeUnit(String unit) {
        return unit == null || unit.isEmpty() ? "units" : unit;
    }

    private static String money(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    private static void toast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }
}
