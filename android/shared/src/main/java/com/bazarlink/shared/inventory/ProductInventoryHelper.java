package com.bazarlink.shared.inventory;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bazarlink.shared.api.ApiClient;
import com.bazarlink.shared.api.ApiMessages;
import com.bazarlink.shared.models.Category;
import com.bazarlink.shared.models.Page;
import com.bazarlink.shared.models.Product;
import com.bazarlink.shared.models.Shop;
import com.bazarlink.shared.models.User;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Add-product form, shop profile resolution, and inventory scanner launcher. */
public final class ProductInventoryHelper {

    public interface UserSink {
        void apply(User user);
    }

    private ProductInventoryHelper() {
    }

    public interface ShopReadyCallback {
        void onReady(User user);
    }

    public static void ensureShopReady(Context context, ApiClient api, User cached, UserSink sink, ShopReadyCallback onReady) {
        if (cached != null && cached.shop_id != null && cached.shop_id > 0) {
            if (sink != null) {
                sink.apply(cached);
            }
            onReady.onReady(cached);
            return;
        }
        api.api().me().enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                User user = response.body();
                if (!response.isSuccessful() || user == null) {
                    toast(context, "Could not load your profile. Check your connection.");
                    return;
                }
                finishShopReady(context, api, user, sink, onReady);
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                toast(context, ApiMessages.fromFailure(t, "Profile load failed"));
            }
        });
    }

    private static void finishShopReady(Context context, ApiClient api, User user, UserSink sink, ShopReadyCallback onReady) {
        if (user.shop_id != null && user.shop_id > 0) {
            if (sink != null) {
                sink.apply(user);
            }
            onReady.onReady(user);
            return;
        }
        api.api().myShop().enqueue(new Callback<Shop>() {
            @Override
            public void onResponse(Call<Shop> call, Response<Shop> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Shop shop = response.body();
                    user.shop_id = shop.id;
                    user.shop_name = shop.name;
                    user.shop_kind = shop.kind;
                    user.is_shop_approved = shop.is_approved;
                }
                if (sink != null) {
                    sink.apply(user);
                }
                onReady.onReady(user);
            }

            @Override
            public void onFailure(Call<Shop> call, Throwable t) {
                if (sink != null) {
                    sink.apply(user);
                }
                onReady.onReady(user);
            }
        });
    }

    public static void showAddProductForm(
            AppCompatActivity activity,
            ApiClient api,
            String role,
            User cachedUser,
            UserSink sink,
            InventoryController.RefreshListener refresh
    ) {
        showAddProductForm(activity, api, role, cachedUser, sink, refresh, null);
    }

    public static void showAddProductForm(
            AppCompatActivity activity,
            ApiClient api,
            String role,
            User cachedUser,
            UserSink sink,
            InventoryController.RefreshListener refresh,
            ScannedLabel scannedLabel
    ) {
        ensureShopReady(activity, api, cachedUser, sink, user ->
                loadCategoriesAndShowDialog(activity, api, role, user, refresh, scannedLabel));
    }

    private static void loadCategoriesAndShowDialog(
            AppCompatActivity activity,
            ApiClient api,
            String role,
            User user,
            InventoryController.RefreshListener refresh,
            ScannedLabel scannedLabel
    ) {
        api.api().categories().enqueue(new Callback<Page<Category>>() {
            @Override
            public void onResponse(Call<Page<Category>> call, Response<Page<Category>> response) {
                Page<Category> page = response.body();
                if (!response.isSuccessful() || page == null || page.results == null || page.results.isEmpty()) {
                    toast(activity, "Could not load categories.");
                    return;
                }
                Category category = page.results.get(0);
                boolean wholesaler = "wholesaler".equalsIgnoreCase(role);

                LinearLayout dialog = new LinearLayout(activity);
                dialog.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
                dialog.setPadding(pad, pad, pad, pad);

                EditText name = field(activity, "Product name", InputType.TYPE_CLASS_TEXT);
                EditText unit = field(activity, "Unit (kg, bag, crate)", InputType.TYPE_CLASS_TEXT);
                unit.setText("kg");
                EditText price = field(activity, "Price (Rs)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                EditText stock = field(activity, "Opening stock", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                stock.setText("0");
                EditText low = field(activity, "Low-stock alert at", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                low.setText("5");
                EditText minBulk = null;
                dialog.addView(name);
                dialog.addView(unit);
                dialog.addView(price);
                dialog.addView(stock);
                dialog.addView(low);
                if (wholesaler) {
                    minBulk = field(activity, "Min bulk quantity", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    minBulk.setText("10");
                    dialog.addView(minBulk);
                }

                applyScannedLabel(scannedLabel, name, unit, price, stock);

                EditText finalMinBulk = minBulk;
                String scanNote = scannedLabel != null ? "\nFilled from label scan — review before saving." : "";
                new AlertDialog.Builder(activity)
                        .setTitle("Add product")
                        .setMessage("Category: " + category.name + "\nShop: " + safe(user.shop_name) + scanNote)
                        .setView(dialog)
                        .setPositiveButton("Save", (d, w) -> saveProduct(
                                activity,
                                api,
                                user,
                                category,
                                wholesaler,
                                name,
                                unit,
                                price,
                                stock,
                                low,
                                finalMinBulk,
                                refresh
                        ))
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onFailure(Call<Page<Category>> call, Throwable t) {
                toast(activity, "Could not load categories: " + t.getMessage());
            }
        });
    }

    private static void saveProduct(
            Context context,
            ApiClient api,
            User user,
            Category category,
            boolean wholesaler,
            EditText name,
            EditText unit,
            EditText price,
            EditText stock,
            EditText low,
            EditText minBulk,
            InventoryController.RefreshListener refresh
    ) {
        try {
            String productName = name.getText().toString().trim();
            if (productName.isEmpty()) {
                toast(context, "Product name is required");
                return;
            }
            Product body = new Product();
            if (user.shop_id != null && user.shop_id > 0) {
                body.shop = user.shop_id;
            }
            body.category = category.id;
            body.name = productName;
            body.description = productName;
            body.unit = unit.getText().toString().trim();
            body.price = new BigDecimal(price.getText().toString().trim());
            body.stock_quantity = new BigDecimal(stock.getText().toString().trim());
            body.low_stock_threshold = new BigDecimal(low.getText().toString().trim());
            body.is_bulk_available = wholesaler;
            if (wholesaler && minBulk != null) {
                body.min_bulk_quantity = new BigDecimal(minBulk.getText().toString().trim());
            }
            api.api().createProduct(body).enqueue(new Callback<Product>() {
                @Override
                public void onResponse(Call<Product> call, Response<Product> response) {
                    if (response.isSuccessful()) {
                        toast(context, "Product added");
                        if (refresh != null) {
                            refresh.onInventoryChanged();
                        }
                    } else {
                        toast(context, ApiMessages.fromResponse(response, "Could not add product"));
                    }
                }

                @Override
                public void onFailure(Call<Product> call, Throwable t) {
                    toast(context, ApiMessages.fromFailure(t, "Could not add product"));
                }
            });
        } catch (Exception e) {
            toast(context, "Check your inputs");
        }
    }

    public static void launchInventoryScanner(AppCompatActivity activity, ApiClient api, InventoryController.RefreshListener refresh) {
        launchInventoryScanner(activity, api, "shopkeeper", null, null, refresh);
    }

    public static void launchInventoryScanner(
            AppCompatActivity activity,
            ApiClient api,
            String role,
            User cachedUser,
            UserSink sink,
            InventoryController.RefreshListener refresh
    ) {
        if (api == null) {
            toast(activity, "Sign in again to use the scanner");
            return;
        }
        try {
            InventoryScannerActivity.PendingSession.prepare(role, cachedUser, sink, refresh);
            Intent intent = new Intent(activity, InventoryScannerActivity.class);
            activity.startActivity(intent);
        } catch (Exception e) {
            toast(activity, "Could not open scanner: " + e.getMessage());
        }
    }

    private static void applyScannedLabel(ScannedLabel scanned, EditText name, EditText unit, EditText price, EditText stock) {
        if (scanned == null) {
            return;
        }
        if (scanned.name != null && !scanned.name.trim().isEmpty()) {
            name.setText(scanned.name.trim());
        }
        if (scanned.unit != null && !scanned.unit.trim().isEmpty()) {
            unit.setText(scanned.unit.trim());
        }
        if (scanned.price != null && !scanned.price.trim().isEmpty()) {
            price.setText(scanned.price.trim());
        }
        if (scanned.stock != null && !scanned.stock.trim().isEmpty()) {
            stock.setText(scanned.stock.trim());
        }
    }

    private static EditText field(Context context, String hint, int inputType) {
        EditText edit = new EditText(context);
        edit.setHint(hint);
        edit.setInputType(inputType);
        return edit;
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Your shop" : value.trim();
    }

    private static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
