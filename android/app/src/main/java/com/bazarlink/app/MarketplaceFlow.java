package com.bazarlink.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bazarlink.shared.api.ApiClient;
import com.bazarlink.shared.inventory.InventoryController;
import com.bazarlink.shared.models.BulkRequest;
import com.bazarlink.shared.models.Category;
import com.bazarlink.shared.models.Order;
import com.bazarlink.shared.models.OrderItem;
import com.bazarlink.shared.models.Page;
import com.bazarlink.shared.models.Payment;
import com.bazarlink.shared.models.PaymentConfirmRequest;
import com.bazarlink.shared.models.PaymentInitRequest;
import com.bazarlink.shared.models.Product;
import com.bazarlink.shared.models.Quotation;
import com.bazarlink.shared.models.Shop;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

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

/**
 * Interactive marketplace UI: customer checkout, shopkeeper retail + wholesale, wholesaler quotes.
 */
public class MarketplaceFlow {

    private static final String PAYMENT_COD = "cash_on_delivery";
    private static final String PAYMENT_EASYPAISA = "easypaisa";
    private static final String PAYMENT_JAZZCASH = "jazzcash";

    private String selectedPaymentMethod = PAYMENT_COD;

    public interface Host {
        AppCompatActivity activity();
        LinearLayout content();
        ApiClient apiClient();
        CartManager cart();
        void setActiveScreen(String screen);
        String activeScreen();
        String activeRole();
        void reload();
        int dp(int value);
        MaterialCardView createCard(int backgroundColor, int radiusDp, int paddingDp);
        View spacer(int heightDp);
        String money(BigDecimal value);
        void showError(String message);
        MaterialCardView wholesalerHeaderCard();
        MaterialCardView shopkeeperHeaderCard();
        void openWholesalerDrawer();

        MaterialCardView customerHeaderCard();

        void openAppDrawer();
    }

    private final Host host;
    private Shop selectedBrowseShop;
    private Shop selectedWholesalePartner;
    private String marketplaceShopFilter = "all";

    public MarketplaceFlow(Host host) {
        this.host = host;
    }

    // ——— Customer ———

    public void loadCustomerHome() {
        clearContent();
        addCustomerHeader("BazarLink");
        hero("Shop groceries nearby", "Pick a retail shop, add items to cart, and place your order.");
        section("Browse");
        host.apiClient().api().categories().enqueue(callbackCategories(page -> {
            if (page.results != null) {
                for (Category c : page.results) {
                    host.content().addView(actionCard(c.name, "Category • tap Shops to buy", () -> {
                        host.setActiveScreen("Shops");
                        host.reload();
                    }));
                    host.content().addView(host.spacer(10));
                }
            }
            host.content().addView(actionCard("Browse marketplace", "Retail shops & wholesalers in one place", () -> {
                host.setActiveScreen("Shops");
                host.reload();
            }));
            host.content().addView(host.spacer(10));
            host.content().addView(actionCard("Bulk wholesale", "Order large quantities directly from wholesalers", () -> {
                host.setActiveScreen("Bulk");
                host.reload();
            }));
        }, "Could not load categories."));
    }

    public void loadCustomerBulk() {
        clearContent();
        addCustomerHeader("Bulk wholesale");
        hero("Order from wholesalers", "Request bulk pricing for large quantities. Wholesalers send quotations you can accept.");
        section("Wholesale catalog");
        Map<String, String> filters = new HashMap<>();
        filters.put("is_bulk_available", "true");
        host.apiClient().api().products(filters).enqueue(callbackProducts(page -> {
            List<Product> products = page.results == null ? new ArrayList<>() : page.results;
            if (products.isEmpty()) {
                host.content().addView(infoCard("No bulk products", "Wholesalers add bulk products to their catalog first."));
                return;
            }
            for (Product p : products) {
                host.content().addView(wholesaleProductCard(p, "Bulk order", this::loadCustomerBulk));
                host.content().addView(host.spacer(10));
            }
        }, "Could not load wholesale catalog."));
        host.content().addView(host.spacer(16));
        section("My bulk requests");
        host.apiClient().api().bulkRequests().enqueue(callbackBulkRequests(page -> {
            List<BulkRequest> requests = page.results == null ? new ArrayList<>() : page.results;
            if (requests.isEmpty()) {
                host.content().addView(infoCard("No requests yet", "Pick a product above to request a bulk quote."));
                return;
            }
            for (BulkRequest r : requests) {
                host.content().addView(bulkRequestCard(r));
                host.content().addView(host.spacer(10));
            }
        }, "Could not load bulk requests."));
        host.content().addView(host.spacer(12));
        section("Quotations");
        host.apiClient().api().quotations().enqueue(callbackQuotations(page -> {
            List<Quotation> quotes = page.results == null ? new ArrayList<>() : page.results;
            if (quotes.isEmpty()) {
                host.content().addView(infoCard("No quotations", "Wholesaler quotes appear here after you request bulk stock."));
                return;
            }
            for (Quotation q : quotes) {
                host.content().addView(quotationCard(q, this::loadCustomerBulk));
                host.content().addView(host.spacer(10));
            }
        }, "Could not load quotations."));
    }

    public void loadCustomerShops() {
        clearContent();
        addCustomerHeader("Marketplace");
        hero("Browse shops", "Retail stores for cart checkout • Wholesalers for bulk quotes.");
        host.content().addView(shopFilterBar(() -> loadCustomerShops()));
        host.content().addView(host.spacer(14));
        section("Approved sellers");
        fetchApprovedShops(marketplaceShopFilter, shops -> renderShopList(shops, shop -> openBrowseShop(shop, false)));
    }

    private void openBrowseShop(Shop shop, boolean shopkeeperWholesale) {
        if (shopkeeperWholesale) {
            selectedWholesalePartner = shop;
            host.setActiveScreen("WholesalerCatalog");
        } else {
            selectedBrowseShop = shop;
            if (!isWholesaleShop(shop)) {
                host.cart().bindShop(shop.id, shop.name);
            }
            host.setActiveScreen("ShopCatalog");
        }
        host.reload();
    }

    public void loadCustomerShopCatalog() {
        if (selectedBrowseShop == null) {
            host.setActiveScreen("Shops");
            host.reload();
            return;
        }
        loadShopCatalogScreen(selectedBrowseShop, false, () -> {
            host.setActiveScreen("Shops");
            host.reload();
        }, this::loadCustomerShopCatalog);
    }

    public void loadShopkeeperWholesalerCatalog() {
        if (selectedWholesalePartner == null) {
            host.setActiveScreen("Wholesale");
            host.reload();
            return;
        }
        loadShopCatalogScreen(selectedWholesalePartner, true, () -> {
            host.setActiveScreen("Wholesale");
            host.reload();
        }, this::loadShopkeeperWholesalerCatalog);
    }

    private void loadShopCatalogScreen(Shop shop, boolean shopkeeperView, Runnable onBack, Runnable reloadAfterBulk) {
        clearContent();
        if (shopkeeperView) {
            host.content().setPadding(host.dp(14), host.dp(14), host.dp(14), host.dp(24));
            host.content().addView(host.shopkeeperHeaderCard());
            host.content().addView(host.spacer(14));
        } else {
            addCustomerHeader(shop.name);
        }
        boolean wholesale = isWholesaleShop(shop);
        String subtitle = wholesale
                ? "Bulk-ready catalog • request quotes per product"
                : "Add items to cart • checkout from one retail shop";
        hero(wholesale ? "Wholesaler" : "Retail shop", subtitle + "\n" + safeAddress(shop));
        host.content().addView(actionCard("← Back to shops", "Browse other sellers", onBack));
        host.content().addView(host.spacer(12));
        section("Inventory");
        Map<String, String> filters = new HashMap<>();
        filters.put("shop", String.valueOf(shop.id));
        filters.put("is_active", "true");
        host.apiClient().api().products(filters).enqueue(callbackProducts(page -> {
            List<Product> products = page.results == null ? new ArrayList<>() : page.results;
            if (products.isEmpty()) {
                host.content().addView(infoCard("No products listed", "This seller has not added inventory yet."));
                return;
            }
            Runnable bulkReload = reloadAfterBulk;
            for (Product p : products) {
                if (wholesale && p.is_bulk_available) {
                    host.content().addView(wholesaleProductCard(p, "Request bulk", bulkReload));
                } else if (!wholesale) {
                    host.content().addView(productCard(p));
                } else {
                    host.content().addView(infoCard(p.name, "Not available for bulk • contact seller"));
                }
                host.content().addView(host.spacer(10));
            }
        }, "Could not load inventory."));
    }

    public void loadCustomerCart() {
        clearContent();
        addCustomerHeader("Cart");
        CartManager cart = host.cart();
        if (cart.isEmpty()) {
            hero("Your cart is empty", "Browse shops and add products.");
            host.content().addView(actionCard("Browse shops", "Find a retail store", () -> {
                host.setActiveScreen("Shops");
                host.reload();
            }));
            return;
        }
        hero(cart.getShopName(), cart.itemCount() + " item(s) in cart");
        for (CartManager.Line line : cart.getLines()) {
            host.content().addView(cartLineCard(line));
            host.content().addView(host.spacer(8));
        }
        host.content().addView(summaryCard("Subtotal", host.money(cart.subtotal())));
        host.content().addView(host.spacer(12));

        EditText address = input("Delivery address", "Block 5, Gulshan-e-Iqbal, Karachi");
        host.content().addView(address);
        host.content().addView(host.spacer(12));

        host.content().addView(sectionLabel("Payment method"));
        host.content().addView(host.spacer(8));
        host.content().addView(paymentMethodRow());
        host.content().addView(host.spacer(12));

        MaterialButton checkout = primaryButton(checkoutLabel());
        checkout.setOnClickListener(v -> placeOrder(address.getText().toString().trim()));
        host.content().addView(checkout, fullWidth());
        host.content().addView(host.spacer(8));
        MaterialButton clear = outlineButton("Clear cart");
        clear.setOnClickListener(v -> {
            cart.clear();
            toast("Cart cleared");
            host.reload();
        });
        host.content().addView(clear, fullWidth());
    }

    private LinearLayout paymentMethodRow() {
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.VERTICAL);
        row.addView(paymentMethodChip("Cash on delivery", PAYMENT_COD, 0xFF0E8A86));
        row.addView(host.spacer(8));
        row.addView(paymentMethodChip("Easypaisa", PAYMENT_EASYPAISA, 0xFF1B8A4C));
        row.addView(host.spacer(8));
        row.addView(paymentMethodChip("JazzCash", PAYMENT_JAZZCASH, 0xFFC62828));
        return row;
    }

    private MaterialCardView paymentMethodChip(String label, String methodId, int accent) {
        boolean selected = methodId.equals(selectedPaymentMethod);
        MaterialCardView card = host.createCard(selected ? 0xFFFFFFFF : 0xFF1A2E4F, 16, 14);
        card.setStrokeWidth(host.dp(selected ? 2 : 0));
        card.setStrokeColor(accent);
        card.setOnClickListener(v -> {
            selectedPaymentMethod = methodId;
            host.reload();
        });
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        TextView text = new TextView(host.activity());
        text.setText(label + (selected ? "  ✓" : ""));
        text.setTextColor(selected ? 0xFF0F172A : 0xFFE2E8F0);
        text.setTextSize(16);
        box.addView(text);
        return card;
    }

    private String checkoutLabel() {
        if (PAYMENT_EASYPAISA.equals(selectedPaymentMethod)) {
            return "Place order & pay with Easypaisa";
        }
        if (PAYMENT_JAZZCASH.equals(selectedPaymentMethod)) {
            return "Place order & pay with JazzCash";
        }
        return "Place order • Cash on delivery";
    }

    private void placeOrder(String address) {
        CartManager cart = host.cart();
        if (address.isEmpty()) {
            toast("Enter a delivery address");
            return;
        }
        Order body = new Order();
        body.shop = cart.getShopId();
        body.address = address;
        body.payment_method = selectedPaymentMethod;
        body.delivery_fee = new BigDecimal("50");
        body.items = new ArrayList<>();
        for (CartManager.Line line : cart.getLines()) {
            OrderItem item = new OrderItem();
            item.product = line.productId;
            item.quantity = line.quantity;
            body.items.add(item);
        }
        host.apiClient().api().createOrder(body).enqueue(new Callback<Order>() {
            @Override
            public void onResponse(Call<Order> call, Response<Order> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    host.showError("Checkout failed. Check stock and try again.");
                    return;
                }
                Order order = response.body();
                if (PAYMENT_EASYPAISA.equals(selectedPaymentMethod) || PAYMENT_JAZZCASH.equals(selectedPaymentMethod)) {
                    showWalletPaymentDialog(order, selectedPaymentMethod, cart);
                    return;
                }
                cart.clear();
                toast("Order #" + order.id + " placed!");
                host.setActiveScreen("Orders");
                host.reload();
            }

            @Override
            public void onFailure(Call<Order> call, Throwable t) {
                host.showError("Checkout failed: " + t.getMessage());
            }
        });
    }

    private void showWalletPaymentDialog(Order order, String provider, CartManager cart) {
        AppCompatActivity activity = host.activity();
        LinearLayout dialog = new LinearLayout(activity);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(host.dp(20), host.dp(12), host.dp(20), host.dp(4));

        TextView hint = new TextView(activity);
        hint.setText("Order #" + order.id + " • Total " + host.money(order.total)
                + "\nSandbox: use any 4+ digit OTP.");
        hint.setTextColor(0xFF475569);
        hint.setPadding(0, 0, 0, host.dp(12));
        dialog.addView(hint);

        EditText mobile = input("Mobile wallet number", "03001234567");
        mobile.setInputType(InputType.TYPE_CLASS_PHONE);
        dialog.addView(mobile);

        EditText otp = input("OTP from SMS / app", "1234");
        otp.setInputType(InputType.TYPE_CLASS_NUMBER);
        dialog.addView(otp);

        AlertDialog alert = new AlertDialog.Builder(activity)
                .setTitle(providerLabel(provider))
                .setView(dialog)
                .setNegativeButton("Cancel", (d, w) -> toast("Complete payment from Orders when ready"))
                .setPositiveButton("Pay now", null)
                .create();
        alert.setOnShowListener(d -> alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String mobileNumber = mobile.getText().toString().trim();
            if (mobileNumber.isEmpty()) {
                toast("Enter your wallet mobile number");
                return;
            }
            PaymentInitRequest init = new PaymentInitRequest();
            init.order_id = order.id;
            init.provider = provider;
            init.mobile_account = mobileNumber;
            host.apiClient().api().initiatePayment(init).enqueue(new Callback<Payment>() {
                @Override
                public void onResponse(Call<Payment> call, Response<Payment> response) {
                    Payment payment = response.body();
                    if (!response.isSuccessful() || payment == null) {
                        toast(paymentError(response));
                        return;
                    }
                    String code = otp.getText().toString().trim();
                    if (code.length() < 4) {
                        toast("Enter the OTP sent to your phone");
                        return;
                    }
                    PaymentConfirmRequest confirm = new PaymentConfirmRequest();
                    confirm.otp = code;
                    host.apiClient().api().confirmPayment(payment.id, confirm).enqueue(new Callback<Payment>() {
                        @Override
                        public void onResponse(Call<Payment> c, Response<Payment> r) {
                            if (!r.isSuccessful() || r.body() == null) {
                                toast(paymentError(r));
                                return;
                            }
                            cart.clear();
                            alert.dismiss();
                            toast("Payment successful! Order #" + order.id);
                            host.setActiveScreen("Orders");
                            host.reload();
                        }

                        @Override
                        public void onFailure(Call<Payment> c, Throwable t) {
                            toast("Payment failed: " + t.getMessage());
                        }
                    });
                }

                @Override
                public void onFailure(Call<Payment> call, Throwable t) {
                    toast("Could not start payment: " + t.getMessage());
                }
            });
        }));
        alert.show();
    }

    private String formatPaymentMethod(String method) {
        if (PAYMENT_EASYPAISA.equals(method)) {
            return "Easypaisa";
        }
        if (PAYMENT_JAZZCASH.equals(method)) {
            return "JazzCash";
        }
        if (PAYMENT_COD.equals(method)) {
            return "Cash on delivery";
        }
        return method;
    }

    private String providerLabel(String provider) {
        if (PAYMENT_EASYPAISA.equals(provider)) {
            return "Pay with Easypaisa";
        }
        if (PAYMENT_JAZZCASH.equals(provider)) {
            return "Pay with JazzCash";
        }
        return "Pay";
    }

    private String paymentError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String body = response.errorBody().string();
                if (body.contains("detail")) {
                    return body.replace("{\"detail\":\"", "").replace("\"}", "");
                }
                return body;
            }
        } catch (Exception ignored) {
        }
        return "Payment failed (HTTP " + response.code() + ")";
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(host.activity());
        label.setText(text);
        label.setTextColor(0xFF94A3B8);
        label.setTextSize(13);
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        return label;
    }

    public void loadCustomerOrders() {
        clearContent();
        addCustomerHeader("My orders");
        hero("Order history", "Track status from your shops.");
        host.apiClient().api().orders(new HashMap<>()).enqueue(callbackOrders(page -> {
            List<Order> orders = page.results == null ? new ArrayList<>() : page.results;
            if (orders.isEmpty()) {
                host.content().addView(infoCard("No orders yet", "Place an order from the Cart tab."));
                return;
            }
            for (Order o : orders) {
                host.content().addView(orderStatusCard(o, null, true));
                host.content().addView(host.spacer(10));
            }
        }, "Could not load orders."));
    }

    // ——— Shopkeeper ———

    public void loadShopkeeperOrders() {
        clearContent();
        host.content().setPadding(host.dp(14), host.dp(14), host.dp(14), host.dp(24));
        host.content().addView(host.shopkeeperHeaderCard());
        host.content().addView(host.spacer(14));
        hero("Retail orders", "Accept, dispatch, or reject customer orders.");
        host.apiClient().api().orders(new HashMap<>()).enqueue(callbackOrders(page -> {
            List<Order> orders = page.results == null ? new ArrayList<>() : page.results;
            if (orders.isEmpty()) {
                host.content().addView(infoCard("No orders", "Customer orders will appear here."));
                return;
            }
            for (Order o : orders) {
                host.content().addView(orderStatusCard(o, this::transitionRetailOrder, true));
                host.content().addView(host.spacer(10));
            }
        }, "Could not load orders."));
    }

    private void transitionRetailOrder(Order order, String status) {
        Map<String, String> body = new HashMap<>();
        body.put("status", status);
        host.apiClient().api().transitionOrder(order.id, body).enqueue(new Callback<Order>() {
            @Override
            public void onResponse(Call<Order> call, Response<Order> response) {
                if (response.isSuccessful()) {
                    toast("Order #" + order.id + " → " + status);
                    loadShopkeeperOrders();
                } else {
                    toast("Update failed");
                }
            }

            @Override
            public void onFailure(Call<Order> call, Throwable t) {
                toast("Update failed: " + t.getMessage());
            }
        });
    }

    public void loadShopkeeperWholesale() {
        clearContent();
        host.content().setPadding(host.dp(14), host.dp(14), host.dp(14), host.dp(24));
        host.content().addView(host.shopkeeperHeaderCard());
        host.content().addView(host.spacer(14));
        hero("Wholesale marketplace", "Browse wholesaler shops, open inventory, and request bulk restock.");
        section("Wholesaler shops");
        fetchApprovedShops("wholesale", shops -> renderShopList(shops, shop -> openBrowseShop(shop, true)));
        host.content().addView(host.spacer(16));
        section("My bulk requests");
        host.apiClient().api().bulkRequests().enqueue(callbackBulkRequests(page -> {
            List<BulkRequest> requests = page.results == null ? new ArrayList<>() : page.results;
            if (requests.isEmpty()) {
                host.content().addView(infoCard("No requests", "Request stock from the catalog above."));
                return;
            }
            for (BulkRequest r : requests) {
                host.content().addView(bulkRequestCard(r));
                host.content().addView(host.spacer(10));
            }
        }, "Could not load bulk requests."));
        host.content().addView(host.spacer(12));
        section("Quotations received");
        host.apiClient().api().quotations().enqueue(callbackQuotations(page -> {
            List<Quotation> quotes = page.results == null ? new ArrayList<>() : page.results;
            if (quotes.isEmpty()) {
                host.content().addView(infoCard("No quotations", "Wholesaler quotes will show here."));
                return;
            }
            for (Quotation q : quotes) {
                host.content().addView(quotationCard(q, this::loadShopkeeperWholesale));
                host.content().addView(host.spacer(10));
            }
        }, "Could not load quotations."));
    }


    private void acceptQuotation(Quotation q, Runnable onSuccess) {
        host.apiClient().api().acceptQuotation(q.id).enqueue(new Callback<Quotation>() {
            @Override
            public void onResponse(Call<Quotation> call, Response<Quotation> response) {
                if (response.isSuccessful()) {
                    toast("Quotation accepted");
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } else {
                    toast("Accept failed");
                }
            }

            @Override
            public void onFailure(Call<Quotation> call, Throwable t) {
                toast("Accept failed: " + t.getMessage());
            }
        });
    }

    // ——— Wholesaler bulk orders with quote action ———

    public void wireWholesalerBulkCard(MaterialCardView card, BulkRequest request) {
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        MaterialButton quote = primaryButton("Send quotation");
        quote.setOnClickListener(v -> openQuoteDialog(request));
        box.addView(quote, fullWidth());
    }

    public void openQuoteDialog(BulkRequest request) {
        showQuoteDialog(request);
    }

    private void showQuoteDialog(BulkRequest request) {
        AppCompatActivity activity = host.activity();
        LinearLayout dialog = new LinearLayout(activity);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(host.dp(20), host.dp(16), host.dp(20), host.dp(8));
        EditText price = input("Price per unit", "1200");
        EditText fee = input("Delivery fee", "500");
        EditText message = input("Message", "Bulk quote for your request");
        dialog.addView(price);
        dialog.addView(fee);
        dialog.addView(message);
        new AlertDialog.Builder(activity)
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
                    host.apiClient().api().createQuotation(body).enqueue(new Callback<Quotation>() {
                        @Override
                        public void onResponse(Call<Quotation> call, Response<Quotation> response) {
                            if (response.isSuccessful()) {
                                toast("Quotation sent");
                                host.reload();
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

    // ——— UI building blocks ———

    private void clearContent() {
        host.content().removeAllViews();
        host.content().setPadding(host.dp(14), host.dp(14), host.dp(14), host.dp(24));
    }

    private void addCustomerHeader(String title) {
        host.content().addView(host.customerHeaderCard());
        host.content().addView(host.spacer(12));
    }

    private void hero(String title, String subtitle) {
        MaterialCardView card = host.createCard(0xFF0F2E56, 22, 16);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        TextView h = new TextView(host.activity());
        h.setText(title);
        h.setTextColor(0xFF64D6F7);
        h.setTextSize(22);
        h.setTypeface(h.getTypeface(), Typeface.BOLD);
        box.addView(h);
        TextView s = new TextView(host.activity());
        s.setText(subtitle);
        s.setTextColor(0xFFE0EAF5);
        s.setTextSize(14);
        s.setPadding(0, host.dp(8), 0, 0);
        box.addView(s);
        host.content().addView(card);
        host.content().addView(host.spacer(14));
    }

    private void section(String label) {
        TextView t = new TextView(host.activity());
        t.setText(label.toUpperCase(Locale.US));
        t.setTextColor(0xFFA8B9C8);
        t.setTextSize(13);
        t.setLetterSpacing(0.15f);
        t.setPadding(host.dp(4), 0, 0, host.dp(8));
        host.content().addView(t);
    }

    private MaterialCardView infoCard(String title, String body) {
        MaterialCardView card = host.createCard(0xFF132E50, 20, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        TextView h = new TextView(host.activity());
        h.setText(title);
        h.setTextColor(Color.WHITE);
        h.setTextSize(17);
        h.setTypeface(h.getTypeface(), Typeface.BOLD);
        box.addView(h);
        TextView p = new TextView(host.activity());
        p.setText(body);
        p.setTextColor(0xFFD5E3F1);
        p.setTextSize(14);
        p.setPadding(0, host.dp(6), 0, 0);
        box.addView(p);
        return card;
    }

    private MaterialCardView actionCard(String title, String body, Runnable onClick) {
        MaterialCardView card = host.createCard(0xFF132E50, 20, 14);
        card.setClickable(true);
        card.setOnClickListener(v -> onClick.run());
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        TextView h = new TextView(host.activity());
        h.setText(title);
        h.setTextColor(Color.WHITE);
        h.setTextSize(17);
        h.setTypeface(h.getTypeface(), Typeface.BOLD);
        box.addView(h);
        TextView p = new TextView(host.activity());
        p.setText(body);
        p.setTextColor(0xFFD5E3F1);
        p.setTextSize(14);
        p.setPadding(0, host.dp(6), 0, 0);
        box.addView(p);
        TextView cta = new TextView(host.activity());
        cta.setText("Open →");
        cta.setTextColor(0xFF64D6F7);
        cta.setPadding(0, host.dp(10), 0, 0);
        box.addView(cta);
        return card;
    }

    private MaterialCardView productCard(Product p) {
        MaterialCardView card = host.createCard(0xFF132E50, 20, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        TextView h = new TextView(host.activity());
        h.setText(p.name);
        h.setTextColor(Color.WHITE);
        h.setTextSize(17);
        h.setTypeface(h.getTypeface(), Typeface.BOLD);
        box.addView(h);
        TextView meta = new TextView(host.activity());
        meta.setText("Rs " + host.money(p.price) + " / " + (p.unit == null ? "unit" : p.unit) + " • Stock " + host.money(p.stock_quantity));
        meta.setTextColor(0xFFD5E3F1);
        meta.setTextSize(14);
        meta.setPadding(0, host.dp(6), 0, host.dp(10));
        box.addView(meta);
        MaterialButton add = primaryButton("Add to cart");
        add.setOnClickListener(v -> {
            host.cart().addProduct(p, BigDecimal.ONE);
            toast("Added " + p.name);
        });
        box.addView(add, fullWidth());
        return card;
    }

    private MaterialCardView cartLineCard(CartManager.Line line) {
        MaterialCardView card = host.createCard(0xFF132E50, 18, 12);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        TextView h = new TextView(host.activity());
        h.setText(line.name);
        h.setTextColor(Color.WHITE);
        h.setTextSize(16);
        box.addView(h);
        TextView meta = new TextView(host.activity());
        meta.setText("Qty " + host.money(line.quantity) + " × Rs " + host.money(line.unitPrice) + " = Rs " + host.money(line.lineTotal()));
        meta.setTextColor(0xFFD5E3F1);
        meta.setTextSize(13);
        meta.setPadding(0, host.dp(4), 0, host.dp(8));
        box.addView(meta);
        LinearLayout actions = new LinearLayout(host.activity());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton minus = outlineButton("−");
        minus.setOnClickListener(v -> {
            host.cart().setQuantity(line.productId, line.quantity.subtract(BigDecimal.ONE));
            host.reload();
        });
        MaterialButton plus = outlineButton("+");
        plus.setOnClickListener(v -> {
            host.cart().setQuantity(line.productId, line.quantity.add(BigDecimal.ONE));
            host.reload();
        });
        actions.addView(minus, new LinearLayout.LayoutParams(0, host.dp(42), 1f));
        actions.addView(host.spacer(8), new LinearLayout.LayoutParams(host.dp(8), 1));
        actions.addView(plus, new LinearLayout.LayoutParams(0, host.dp(42), 1f));
        box.addView(actions);
        return card;
    }

    private MaterialCardView summaryCard(String label, String value) {
        MaterialCardView card = host.createCard(0xFF123253, 18, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        TextView l = new TextView(host.activity());
        l.setText(label);
        l.setTextColor(0xFFC1D0DE);
        l.setTextSize(13);
        box.addView(l);
        TextView v = new TextView(host.activity());
        v.setText("Rs " + value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(22);
        v.setTypeface(v.getTypeface(), Typeface.BOLD);
        v.setPadding(0, host.dp(6), 0, 0);
        box.addView(v);
        return card;
    }

    private interface OrderAction {
        void run(Order order, String status);
    }

    private MaterialCardView orderStatusCard(Order o, OrderAction action, boolean showDetails) {
        MaterialCardView card = host.createCard(0xFF132E50, 20, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        TextView h = new TextView(host.activity());
        h.setText("Order #" + o.id + " • " + o.status);
        h.setTextColor(Color.WHITE);
        h.setTextSize(17);
        h.setTypeface(h.getTypeface(), Typeface.BOLD);
        box.addView(h);
        TextView meta = new TextView(host.activity());
        String paymentLine = o.payment_method == null ? "" : "\nPay: " + formatPaymentMethod(o.payment_method)
                + (o.payment_status != null ? " (" + o.payment_status + ")" : "");
        meta.setText(o.shop_name + "\nTotal Rs " + host.money(o.total) + paymentLine);
        meta.setTextColor(0xFFD5E3F1);
        meta.setTextSize(14);
        meta.setPadding(0, host.dp(6), 0, 0);
        box.addView(meta);
        if (showDetails) {
            MaterialButton details = outlineButton("View details");
            details.setOnClickListener(v -> InventoryController.showOrderDetails(host.activity(), o));
            box.addView(details, fullWidth());
        }
        if (action != null) {
            LinearLayout row = new LinearLayout(host.activity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, host.dp(10), 0, 0);
            if ("pending".equalsIgnoreCase(o.status)) {
                row.addView(statusButton("Accept", "accepted", o, action), flex());
                row.addView(statusButton("Reject", "rejected", o, action), flex());
            } else if ("accepted".equalsIgnoreCase(o.status)) {
                row.addView(statusButton("Dispatch", "dispatched", o, action), flex());
            } else if ("dispatched".equalsIgnoreCase(o.status)) {
                row.addView(statusButton("Delivered", "delivered", o, action), flex());
            }
            box.addView(row);
        }
        return card;
    }

    private MaterialCardView wholesaleProductCard(Product p, String actionLabel, Runnable onSuccess) {
        MaterialCardView card = host.createCard(0xFF132E50, 20, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        TextView h = new TextView(host.activity());
        h.setText(p.name);
        h.setTextColor(Color.WHITE);
        h.setTextSize(17);
        box.addView(h);
        TextView meta = new TextView(host.activity());
        meta.setText(p.shop_name + " • min " + host.money(p.min_bulk_quantity) + " " + (p.unit == null ? "units" : p.unit));
        meta.setTextColor(0xFFD5E3F1);
        meta.setTextSize(14);
        meta.setPadding(0, host.dp(6), 0, host.dp(10));
        box.addView(meta);
        MaterialButton req = primaryButton(actionLabel);
        req.setOnClickListener(v -> showBulkRequestDialog(p, defaultBulkNotes(), onSuccess));
        box.addView(req, fullWidth());
        return card;
    }

    private String defaultBulkNotes() {
        if ("customer".equalsIgnoreCase(host.activeRole())) {
            return "Bulk order from customer app";
        }
        return "Restock from shopkeeper app";
    }

    private void showBulkRequestDialog(Product p, String notes, Runnable onSuccess) {
        AppCompatActivity activity = host.activity();
        LinearLayout dialog = new LinearLayout(activity);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(host.dp(20), host.dp(16), host.dp(20), host.dp(8));
        EditText qty = input("Quantity", p.min_bulk_quantity != null ? p.min_bulk_quantity.toPlainString() : "10");
        EditText address = input("Delivery address", "Daily Basket Market, Karachi");
        dialog.addView(qty);
        dialog.addView(address);
        new AlertDialog.Builder(activity)
                .setTitle("Bulk request: " + p.name)
                .setView(dialog)
                .setPositiveButton("Submit", (d, w) -> {
                    try {
                        BulkRequest body = new BulkRequest();
                        body.product = p.id;
                        body.quantity = new BigDecimal(qty.getText().toString().trim());
                        body.delivery_address = address.getText().toString().trim();
                        body.notes = notes;
                        host.apiClient().api().createBulkRequest(body).enqueue(new Callback<BulkRequest>() {
                            @Override
                            public void onResponse(Call<BulkRequest> call, Response<BulkRequest> response) {
                                if (response.isSuccessful()) {
                                    toast("Bulk request #" + (response.body() != null ? response.body().id : "") + " sent");
                                    if (onSuccess != null) {
                                        onSuccess.run();
                                    }
                                } else {
                                    toast("Request failed");
                                }
                            }

                            @Override
                            public void onFailure(Call<BulkRequest> call, Throwable t) {
                                toast("Request failed: " + t.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        toast("Invalid quantity");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private MaterialCardView bulkRequestCard(BulkRequest r) {
        return infoCard("Request #" + r.id, r.product_name + " • qty " + host.money(r.quantity) + " • " + r.status);
    }

    private MaterialCardView quotationCard(Quotation q, Runnable onSuccess) {
        MaterialCardView card = host.createCard(0xFF132E50, 20, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        TextView h = new TextView(host.activity());
        h.setText("Quotation #" + q.id);
        h.setTextColor(Color.WHITE);
        h.setTextSize(17);
        box.addView(h);
        TextView meta = new TextView(host.activity());
        meta.setText(q.bulk_request_product_name + "\nRs " + host.money(q.price_per_unit) + "/unit • Total Rs " + host.money(q.total)
                + (q.is_accepted ? "\nAccepted" : ""));
        meta.setTextColor(0xFFD5E3F1);
        meta.setTextSize(14);
        meta.setPadding(0, host.dp(6), 0, host.dp(10));
        box.addView(meta);
        if (!q.is_accepted) {
            MaterialButton accept = primaryButton("Accept quotation");
            accept.setOnClickListener(v -> acceptQuotation(q, onSuccess));
            box.addView(accept, fullWidth());
        }
        return card;
    }

    private MaterialButton statusButton(String label, String status, Order o, OrderAction action) {
        MaterialButton b = primaryButton(label);
        b.setOnClickListener(v -> action.run(o, status));
        return b;
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(host.activity());
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0x88FFFFFF);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = host.dp(10);
        e.setLayoutParams(p);
        return e;
    }

    private MaterialButton primaryButton(String text) {
        MaterialButton b = new MaterialButton(host.activity());
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(0xFF0A223D);
        b.setBackgroundColor(0xFF67E8F9);
        b.setCornerRadius(host.dp(20));
        return b;
    }

    private MaterialButton outlineButton(String text) {
        MaterialButton b = new MaterialButton(host.activity());
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(0xFFE7F5FF);
        b.setBackgroundColor(0xFF2A4264);
        b.setCornerRadius(host.dp(20));
        return b;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(-1, host.dp(46));
    }

    private LinearLayout.LayoutParams flex() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, host.dp(42), 1f);
        p.rightMargin = host.dp(6);
        return p;
    }

    private void toast(String msg) {
        Toast.makeText(host.activity(), msg, Toast.LENGTH_SHORT).show();
    }

    private <T> void onSuccess(Runnable r) {
        host.activity().runOnUiThread(r);
    }

    private interface PageConsumer<T> {
        void accept(T data);
    }

    private Callback<Page<Category>> callbackCategories(PageConsumer<Page<Category>> consumer, String error) {
        return callbackPage(consumer, error);
    }

    private Callback<Page<Product>> callbackProducts(PageConsumer<Page<Product>> consumer, String error) {
        return callbackPage(consumer, error);
    }

    private Callback<Page<Order>> callbackOrders(PageConsumer<Page<Order>> consumer, String error) {
        return callbackPage(consumer, error);
    }

    private Callback<Page<BulkRequest>> callbackBulkRequests(PageConsumer<Page<BulkRequest>> consumer, String error) {
        return callbackPage(consumer, error);
    }

    private Callback<Page<Quotation>> callbackQuotations(PageConsumer<Page<Quotation>> consumer, String error) {
        return callbackPage(consumer, error);
    }

    private void fetchApprovedShops(String filter, PageConsumer<List<Shop>> consumer) {
        Map<String, String> filters = new HashMap<>();
        filters.put("browse", "true");
        if ("retail".equals(filter)) {
            filters.put("kind", "retail");
        } else if ("wholesale".equals(filter)) {
            filters.put("kind", "wholesale");
        }
        host.apiClient().api().listShops(filters).enqueue(new Callback<Page<Shop>>() {
            @Override
            public void onResponse(Call<Page<Shop>> call, Response<Page<Shop>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    onSuccess(() -> host.showError("Could not load shops."));
                    return;
                }
                List<Shop> shops = response.body().results == null ? new ArrayList<>() : new ArrayList<>(response.body().results);
                String kindParam = "retail".equals(filter) ? "retail" : ("wholesale".equals(filter) ? "wholesale" : null);
                host.apiClient().api().nearbyShops(24.9133, 67.0971, 50.0, kindParam).enqueue(new Callback<List<Shop>>() {
                    @Override
                    public void onResponse(Call<List<Shop>> call, Response<List<Shop>> response) {
                        List<Shop> nearby = response.isSuccessful() && response.body() != null ? response.body() : new ArrayList<>();
                        List<Shop> merged = mergeShops(nearby, shops);
                        onSuccess(() -> consumer.accept(merged));
                    }

                    @Override
                    public void onFailure(Call<List<Shop>> call, Throwable t) {
                        onSuccess(() -> consumer.accept(shops));
                    }
                });
            }

            @Override
            public void onFailure(Call<Page<Shop>> call, Throwable t) {
                onSuccess(() -> host.showError("Could not load shops: " + t.getMessage()));
            }
        });
    }

    private List<Shop> mergeShops(List<Shop> nearby, List<Shop> listed) {
        List<Shop> merged = new ArrayList<>();
        if (nearby != null) {
            merged.addAll(nearby);
        }
        if (listed != null) {
            for (Shop shop : listed) {
                boolean exists = false;
                for (Shop existing : merged) {
                    if (existing.id == shop.id) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    merged.add(shop);
                }
            }
        }
        return merged;
    }

    private void renderShopList(List<Shop> shops, java.util.function.Consumer<Shop> onOpen) {
        if (shops == null || shops.isEmpty()) {
            host.content().addView(infoCard("No shops found", "Try another filter or ask sellers to register."));
            return;
        }
        for (Shop shop : shops) {
            host.content().addView(shopBrowseCard(shop, () -> onOpen.accept(shop)));
            host.content().addView(host.spacer(10));
        }
    }

    private LinearLayout shopFilterBar(Runnable onChanged) {
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        addFilterChip(row, "All", "all", onChanged);
        addFilterChip(row, "Retail", "retail", onChanged);
        addFilterChip(row, "Wholesale", "wholesale", onChanged);
        return row;
    }

    private void addFilterChip(LinearLayout row, String label, String value, Runnable onChanged) {
        MaterialButton chip = outlineButton(label);
        boolean selected = value.equals(marketplaceShopFilter);
        if (selected) {
            chip.setBackgroundColor(0xFF67E8F9);
            chip.setTextColor(0xFF0A223D);
        }
        chip.setOnClickListener(v -> {
            marketplaceShopFilter = value;
            onChanged.run();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, host.dp(40), 1f);
        params.rightMargin = host.dp(6);
        row.addView(chip, params);
    }

    private MaterialCardView shopBrowseCard(Shop shop, Runnable onOpen) {
        boolean wholesale = isWholesaleShop(shop);
        String kindLabel = wholesale ? "Wholesaler" : "Retail";
        String dist = shop.distance_km == null ? "" : String.format(Locale.US, " • %.1f km away", shop.distance_km);
        MaterialCardView card = actionCard(shop.name, kindLabel + dist + "\n" + safeAddress(shop), onOpen);
        return card;
    }

    private boolean isWholesaleShop(Shop shop) {
        return shop != null && shop.kind != null && "wholesale".equalsIgnoreCase(shop.kind);
    }

    private String safeAddress(Shop shop) {
        return shop.address == null || shop.address.trim().isEmpty() ? "Address not set" : shop.address.trim();
    }

    private Callback<List<Shop>> callbackShops(PageConsumer<List<Shop>> consumer, String error) {
        return new Callback<List<Shop>>() {
            @Override
            public void onResponse(Call<List<Shop>> call, Response<List<Shop>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    onSuccess(() -> consumer.accept(response.body()));
                } else {
                    onSuccess(() -> host.showError(error));
                }
            }

            @Override
            public void onFailure(Call<List<Shop>> call, Throwable t) {
                onSuccess(() -> host.showError(error + ": " + t.getMessage()));
            }
        };
    }

    private <T> Callback<Page<T>> callbackPage(PageConsumer<Page<T>> consumer, String error) {
        return new Callback<Page<T>>() {
            @Override
            public void onResponse(Call<Page<T>> call, Response<Page<T>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    onSuccess(() -> consumer.accept(response.body()));
                } else {
                    onSuccess(() -> host.showError(error));
                }
            }

            @Override
            public void onFailure(Call<Page<T>> call, Throwable t) {
                onSuccess(() -> host.showError(error + ": " + t.getMessage()));
            }
        };
    }
}
