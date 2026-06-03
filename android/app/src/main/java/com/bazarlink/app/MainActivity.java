package com.bazarlink.app;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import com.bazarlink.shared.api.ApiClient;
import com.bazarlink.shared.api.ApiConfig;
import com.bazarlink.shared.api.TokenStore;
import com.bazarlink.shared.inventory.InventoryController;
import com.bazarlink.shared.inventory.ProductInventoryHelper;
import com.bazarlink.shared.models.AuthResponse;
import com.bazarlink.shared.models.BulkRequest;
import com.bazarlink.shared.models.Category;
import com.bazarlink.shared.models.Order;
import com.bazarlink.shared.models.Page;
import com.bazarlink.shared.models.Product;
import com.bazarlink.shared.models.Quotation;
import com.bazarlink.shared.models.Shop;
import com.bazarlink.shared.models.WholesalerDashboard;
import com.bazarlink.shared.models.User;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MultipartBody;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
// Use fully-qualified retrofit2 types in method signatures to avoid name conflicts with OkHttp

public class MainActivity extends AppCompatActivity implements MarketplaceFlow.Host {
    private static final int REQUEST_PICK_AVATAR = 9101;
    private static final int DRAWER_WIDTH_DP = 292;
    static final String EXTRA_OPEN_HOME_ONLY = "open_home_only";
    private static final String ROLE_CUSTOMER = "customer";
    private static final String ROLE_SHOPKEEPER = "shopkeeper";
    private static final String ROLE_WHOLESALER = "wholesaler";
    private static final String ROLE_ADMIN = "admin";
    private static final String DEFAULT_BASE_URL = ApiConfig.BASE_URL;
    private static final String EXTRA_SELECTED_ROLE = "selected_role";
    private static final String DEMO_PASSWORD = "DemoPass123!";

    private LinearLayout content;
    private BottomNavigationView navigation;
    private MaterialButton loginButton;
    private EditText serverUrlInput;
    private MaterialButton checkServerButton;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView authStatus;
    private TokenStore tokenStore;
    private ApiClient apiClient;
    private FrameLayout rootContainer;
    private View drawerScrim;
    private LinearLayout mainContainer;
    private MaterialCardView drawerPanel;
    private ImageView drawerAvatarImage;
    private TextView drawerNameView;
    private TextView drawerRoleView;
    private String activeRole = ROLE_CUSTOMER;
    private String activeScreen = "Home";
    private String authenticatedRole = "";
    private String signedInLabel = "";
    private User currentUser;
    private String wholesalerOrdersMode = "live";
    private List<BulkRequest> wholesalerOrdersCache = new ArrayList<>();
    private final CartManager cart = new CartManager();
    private MarketplaceFlow marketplaceFlow;
    private List<Product> inventoryProductsCache = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean openHomeOnly = getIntent().getBooleanExtra(EXTRA_OPEN_HOME_ONLY, false);

        String requestedRole = getIntent().getStringExtra(EXTRA_SELECTED_ROLE);
        if (ROLE_SHOPKEEPER.equals(requestedRole) || ROLE_WHOLESALER.equals(requestedRole)) {
            activeRole = requestedRole;
        }
        signedInLabel = getIntent().getStringExtra(LoginActivity.EXTRA_DISPLAY_NAME);
        if (signedInLabel == null || signedInLabel.trim().isEmpty()) {
            signedInLabel = roleLabel(activeRole);
        }

        tokenStore = new TokenStore(this);
        if (!openHomeOnly) {
            tokenStore.clear();
        }
        apiClient = new ApiClient(this, resolveBaseUrl());
        marketplaceFlow = new MarketplaceFlow(this);

        rootContainer = new FrameLayout(this);
        rootContainer.setBackground(createBackground());

        mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout.LayoutParams mainParams = new FrameLayout.LayoutParams(-1, -1);
        rootContainer.addView(mainContainer, mainParams);

        if (!openHomeOnly) {
            MaterialButtonToggleGroup roleSwitcher = new MaterialButtonToggleGroup(this);
            roleSwitcher.setSingleSelection(true);
            roleSwitcher.setSelectionRequired(true);
            roleSwitcher.setPadding(18, 18, 18, 0);
            addRoleButton(roleSwitcher, 101, "Customer", ROLE_CUSTOMER);
            addRoleButton(roleSwitcher, 102, "Shopkeeper", ROLE_SHOPKEEPER);
            addRoleButton(roleSwitcher, 103, "Wholesaler", ROLE_WHOLESALER);
            roleSwitcher.check(roleButtonIdForRole(activeRole));
            roleSwitcher.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                View button = findViewById(checkedId);
                activeRole = String.valueOf(button.getTag());
                signOut();
                updateLoginHints();
            });
            mainContainer.addView(roleSwitcher);

            MaterialCardView loginCard = new MaterialCardView(this);
            loginCard.setRadius(18);
            loginCard.setUseCompatPadding(true);
            LinearLayout loginBox = new LinearLayout(this);
            loginBox.setOrientation(LinearLayout.VERTICAL);
            loginBox.setPadding(24, 20, 24, 20);

            authStatus = new TextView(this);
            authStatus.setTextSize(15);
            authStatus.setText("Select a role and sign in with the seeded demo account.");

            serverUrlInput = new EditText(this);
            serverUrlInput.setHint("Backend URL (fixed)");
            serverUrlInput.setText(DEFAULT_BASE_URL);
            serverUrlInput.setSingleLine();
            serverUrlInput.setFocusable(false);
            serverUrlInput.setClickable(false);
            serverUrlInput.setLongClickable(false);
            serverUrlInput.setKeyListener(null);

            checkServerButton = new MaterialButton(this);
            checkServerButton.setText("Check server");
            checkServerButton.setOnClickListener(v -> checkServer());

            usernameInput = new EditText(this);
            usernameInput.setHint("Username");

            passwordInput = new EditText(this);
            passwordInput.setHint("Password");
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            loginButton = new MaterialButton(this);
            loginButton.setText("Sign in");
            loginButton.setOnClickListener(v -> authenticate());

            TextView helper = new TextView(this);
            helper.setText("Demo accounts: demo_customer, demo_shopkeeper, demo_wholesaler. Password: DemoPass123!");
            helper.setTextSize(13);

            loginBox.addView(authStatus);
            loginBox.addView(serverUrlInput);
            loginBox.addView(checkServerButton);
            loginBox.addView(usernameInput);
            loginBox.addView(passwordInput);
            loginBox.addView(loginButton);
            loginBox.addView(helper);
            loginCard.addView(loginBox);
            mainContainer.addView(loginCard);
        }

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28, 28, 28, 28);
        content.setBackgroundColor(Color.TRANSPARENT);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setFillViewport(true);
        scrollView.addView(content);
        mainContainer.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        navigation = new BottomNavigationView(this);
        navigation.setVisibility(View.GONE);
        navigation.setBackgroundColor(0xCC102B52);
        navigation.setOnItemSelectedListener(item -> {
            activeScreen = item.getTitle().toString();
            loadActiveScreen();
            return true;
        });
        mainContainer.addView(navigation);

        drawerScrim = new View(this);
        drawerScrim.setBackgroundColor(0x99000000);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(v -> closeDrawer());
        rootContainer.addView(drawerScrim, new FrameLayout.LayoutParams(-1, -1));

        setContentView(rootContainer);
        if (openHomeOnly) {
            authenticatedRole = activeRole;
            navigation.setVisibility(View.VISIBLE);
            configureNavigation();
        } else {
            updateLoginHints();
            showLoginLanding();
        }
    }

    private void checkServer() {
        String baseUrl = DEFAULT_BASE_URL;
        authStatus.setText("Checking " + baseUrl + "api/v1/schema/ ...");
        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(8, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(baseUrl + "api/v1/schema/").get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> authStatus.setText("Server check failed: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                response.close();
                runOnUiThread(() -> authStatus.setText("Server responded: HTTP " + code));
            }
        });
    }

    private void addRoleButton(MaterialButtonToggleGroup group, int id, String label, String role) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setId(id);
        button.setText(label);
        button.setTag(role);
        group.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private int roleButtonIdForRole(String role) {
        if (ROLE_SHOPKEEPER.equals(role)) {
            return 102;
        }
        if (ROLE_WHOLESALER.equals(role)) {
            return 103;
        }
        return 101;
    }

    private void updateLoginHints() {
        usernameInput.setText(demoUsernameForRole(activeRole));
        passwordInput.setText(DEMO_PASSWORD);
        authStatus.setText("Sign in as " + roleLabel(activeRole) + " to load the seeded backend data.");
    }

    private void signOut() {
        tokenStore.clear();
        authenticatedRole = "";
        navigation.setVisibility(View.GONE);
        // After sign-out, return to role selection (boarding slides already completed).
        Intent intent = new Intent(this, OnboardingActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void authenticate() {
        String baseUrl = DEFAULT_BASE_URL;
        apiClient = new ApiClient(this, baseUrl);
        Map<String, String> body = new HashMap<>();
        body.put("username", usernameInput.getText().toString().trim());
        body.put("password", passwordInput.getText().toString());
        loginButton.setEnabled(false);
        authStatus.setText("Signing in...");
        apiClient.api().login(body).enqueue(new retrofit2.Callback<AuthResponse>() {
            @Override
            public void onResponse(retrofit2.Call<AuthResponse> call, retrofit2.Response<AuthResponse> response) {
                loginButton.setEnabled(true);
                AuthResponse auth = response.body();
                if (!response.isSuccessful() || auth == null) {
                    int code = response.code();
                    String err = "";
                    try {
                        if (response.errorBody() != null) err = response.errorBody().string();
                    } catch (IOException e) {
                        err = e.getMessage();
                    }
                    String msg = "Sign in failed: HTTP " + code + (err == null || err.isEmpty() ? "" : " - " + err);
                    authStatus.setText(msg);
                    Toast.makeText(MainActivity.this, "Login failed: HTTP " + code, Toast.LENGTH_SHORT).show();
                    return;
                }
                tokenStore.save(auth.access, auth.refresh);
                authenticatedRole = auth.user != null && auth.user.role != null ? auth.user.role : activeRole;
                activeRole = authenticatedRole;
                currentUser = auth.user;
                signedInLabel = displayName(auth.user);
                authStatus.setText("Signed in as " + roleLabel(activeRole) + ". Loading demo data...");
                navigation.setVisibility(View.VISIBLE);
                refreshSellerProfile();
                configureNavigation();
            }

            @Override
            public void onFailure(retrofit2.Call<AuthResponse> call, Throwable t) {
                loginButton.setEnabled(true);
                String message = t.getMessage() == null ? "Unknown network error" : t.getMessage();
                if (ApiConfig.isEmulatorUrl(baseUrl)) {
                    authStatus.setText("Sign in failed at " + baseUrl + ". Make sure the backend is running on port 8000 in Docker, then retry.");
                } else {
                    authStatus.setText("Sign in failed at " + baseUrl + ". Phone and PC must be on the same Wi‑Fi; allow port 8000 in Windows Firewall.");
                }
                Toast.makeText(MainActivity.this, "Login failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshSellerProfile() {
        if (apiClient == null) {
            return;
        }
        if (!ROLE_WHOLESALER.equals(activeRole) && !ROLE_SHOPKEEPER.equals(activeRole)) {
            return;
        }
        ProductInventoryHelper.ensureShopReady(this, apiClient, currentUser, user -> currentUser = user, user -> {
        });
    }

    private void reloadInventoryScreen() {
        if (ROLE_WHOLESALER.equals(activeRole)) {
            loadWholesalerInventoryScreen();
        } else if (ROLE_ADMIN.equals(activeRole)) {
            loadAdminInventoryScreen();
        } else {
            loadShopkeeperInventoryScreen();
        }
    }

    private void configureNavigation() {
        navigation.getMenu().clear();
        List<Screen> screens = screensForRole(activeRole);
        for (Screen screen : screens) {
            navigation.getMenu().add(0, screen.id, screen.id, screen.title).setIcon(screen.icon);
        }
        activeScreen = screens.get(0).title;
        navigation.setSelectedItemId(screens.get(0).id);
        installDrawerForRole();
    }

    private List<Screen> screensForRole(String role) {
        if (ROLE_ADMIN.equals(role)) {
            return Arrays.asList(
                    new Screen(1, "Inventory", android.R.drawable.ic_menu_sort_by_size),
                    new Screen(2, "Shops", android.R.drawable.ic_dialog_map),
                    new Screen(3, "Orders", android.R.drawable.ic_menu_agenda),
                    new Screen(4, "Bulk Orders", android.R.drawable.ic_menu_agenda),
                    new Screen(5, "Settings", android.R.drawable.ic_menu_preferences)
            );
        }
        if (ROLE_SHOPKEEPER.equals(role)) {
            return Arrays.asList(
                    new Screen(1, "Dashboard", android.R.drawable.ic_menu_view),
                    new Screen(2, "Orders", android.R.drawable.ic_menu_agenda),
                    new Screen(3, "Wholesale", android.R.drawable.ic_menu_upload),
                    new Screen(4, "Inventory", android.R.drawable.ic_menu_sort_by_size),
                    new Screen(5, "Settings", android.R.drawable.ic_menu_preferences)
            );
        }
        if (ROLE_WHOLESALER.equals(role)) {
            return Arrays.asList(
                    new Screen(1, "Dashboard", android.R.drawable.ic_menu_view),
                    new Screen(2, "Inventory Management", android.R.drawable.ic_menu_sort_by_size),
                    new Screen(3, "Bulk Orders", android.R.drawable.ic_menu_agenda),
                    new Screen(4, "Settings", android.R.drawable.ic_menu_preferences)
            );
        }
        // BottomNavigationView supports at most 5 items; Bulk is in the drawer + Home.
        return Arrays.asList(
                new Screen(1, "Home", android.R.drawable.ic_menu_view),
                new Screen(2, "Shops", android.R.drawable.ic_dialog_map),
                new Screen(3, "Cart", android.R.drawable.ic_menu_add),
                new Screen(4, "Orders", android.R.drawable.ic_menu_agenda),
                new Screen(5, "Settings", android.R.drawable.ic_menu_preferences)
        );
    }

    private void loadActiveScreen() {
        if (tokenStore.accessToken().isEmpty() || authenticatedRole.isEmpty()) {
            showLoginLanding();
            return;
        }
        content.removeAllViews();
        if ("Dashboard".equals(activeScreen) && ROLE_SHOPKEEPER.equals(activeRole)) {
            loadShopkeeperDashboard();
            return;
        }
        if ("Dashboard".equals(activeScreen) && ROLE_WHOLESALER.equals(activeRole)) {
            loadWholesalerDashboard();
            return;
        }
        if ("Settings".equals(activeScreen) && ROLE_SHOPKEEPER.equals(activeRole)) {
            loadProfile();
            return;
        }
        if ("Settings".equals(activeScreen) && ROLE_CUSTOMER.equals(activeRole)) {
            loadProfile();
            return;
        }
        if (ROLE_ADMIN.equals(activeRole)) {
            loadAdminScreen();
            return;
        }
        if (ROLE_CUSTOMER.equals(activeRole)) {
            loadCustomerScreen();
            return;
        }
        if (ROLE_SHOPKEEPER.equals(activeRole)) {
            loadShopkeeperScreen();
            return;
        }
        if (ROLE_WHOLESALER.equals(activeRole)) {
            loadWholesalerScreen();
            return;
        }
        title(activeScreen);
    }

    @Override
    public void reload() {
        loadActiveScreen();
    }

    @Override
    public AppCompatActivity activity() {
        return this;
    }

    @Override
    public LinearLayout content() {
        return content;
    }

    @Override
    public ApiClient apiClient() {
        return apiClient;
    }

    @Override
    public CartManager cart() {
        return cart;
    }

    @Override
    public void setActiveScreen(String screen) {
        activeScreen = screen;
    }

    @Override
    public String activeScreen() {
        return activeScreen;
    }

    @Override
    public String activeRole() {
        return activeRole;
    }

    private void loadCustomerScreen() {
        if ("Home".equals(activeScreen)) {
            marketplaceFlow.loadCustomerHome();
        } else if ("Shops".equals(activeScreen)) {
            marketplaceFlow.loadCustomerShops();
        } else if ("ShopCatalog".equals(activeScreen)) {
            marketplaceFlow.loadCustomerShopCatalog();
        } else if ("Bulk".equals(activeScreen)) {
            marketplaceFlow.loadCustomerBulk();
        } else if ("Cart".equals(activeScreen)) {
            marketplaceFlow.loadCustomerCart();
        } else if ("Orders".equals(activeScreen)) {
            marketplaceFlow.loadCustomerOrders();
        } else {
            loadProfile();
        }
    }

    private void loadAdminScreen() {
        if ("Inventory".equals(activeScreen)) {
            loadAdminInventoryScreen();
        } else if ("Shops".equals(activeScreen)) {
            marketplaceFlow.loadCustomerShops();
        } else if ("Orders".equals(activeScreen)) {
            marketplaceFlow.loadShopkeeperOrders();
        } else if ("Bulk Orders".equals(activeScreen)) {
            loadWholesalerOrdersScreen();
        } else if ("Settings".equals(activeScreen)) {
            loadProfile();
        } else {
            loadAdminInventoryScreen();
        }
    }

    private void loadShopkeeperScreen() {
        if ("Orders".equals(activeScreen)) {
            marketplaceFlow.loadShopkeeperOrders();
        } else if ("Wholesale".equals(activeScreen)) {
            marketplaceFlow.loadShopkeeperWholesale();
        } else if ("WholesalerCatalog".equals(activeScreen)) {
            marketplaceFlow.loadShopkeeperWholesalerCatalog();
        } else if ("Inventory".equals(activeScreen)) {
            loadShopkeeperInventoryScreen();
        } else if ("Settings".equals(activeScreen)) {
            loadProfile();
        } else {
            loadShopkeeperDashboard();
        }
    }

    private void loadShopkeeperDashboard() {
        apiClient.api().shopkeeperDashboard().enqueue(new retrofit2.Callback<WholesalerDashboard>() {
            @Override
            public void onResponse(retrofit2.Call<WholesalerDashboard> call, retrofit2.Response<WholesalerDashboard> response) {
                WholesalerDashboard dashboard = response.body();
                if (!response.isSuccessful() || dashboard == null) {
                    showError("Could not load shopkeeper dashboard from backend.");
                    return;
                }
                content.setPadding(dp(14), dp(14), dp(14), dp(24));
                content.addView(shopkeeperHeaderCard());
                content.addView(spacer(18));
                content.addView(shopkeeperHeroCard());
                content.addView(spacer(18));
                content.addView(sectionHeader("Sales Snapshot", "Track retail sales and profit at a glance."));
                content.addView(metricRow(
                        "Total Sales",
                        money(dashboard.total_sales),
                        "Delivered orders",
                        "Total Profit",
                        money(dashboard.total_profit),
                        "Estimated profit"));
                content.addView(spacer(16));
                content.addView(sectionHeader("Low Stock Alerts", null));
                if (dashboard.low_stock_alerts == null || dashboard.low_stock_alerts.isEmpty()) {
                    content.addView(alertCard("No low stock items", "Everything is healthy right now", null));
                } else {
                    for (int i = 0; i < dashboard.low_stock_alerts.size(); i++) {
                        WholesalerDashboard.LowStockAlert alert = dashboard.low_stock_alerts.get(i);
                        content.addView(alertCard(alert.name, money(alert.stock_left) + " left", alert.name));
                        if (i < dashboard.low_stock_alerts.size() - 1) {
                            content.addView(spacer(12));
                        }
                    }
                }
                content.addView(spacer(16));
                content.addView(sectionHeader("Order Overview", null));
                content.addView(metricRow(
                        "Live",
                        String.valueOf(dashboard.active_shipments),
                        "Active Orders",
                        "Awaiting",
                        String.format(Locale.US, "%02d", dashboard.pending_orders),
                        "Pending Orders"));
            }

            @Override
            public void onFailure(retrofit2.Call<WholesalerDashboard> call, Throwable t) {
                showError("Could not load shopkeeper dashboard: " + t.getMessage());
            }
        });
    }

    private void loadWholesalerScreen() {
        if ("Bulk Orders".equals(activeScreen)) {
            loadWholesalerOrdersScreen();
        } else if ("Inventory Management".equals(activeScreen)) {
            loadWholesalerInventoryScreen();
        } else if ("Settings".equals(activeScreen)) {
            loadProfile();
        } else {
            loadWholesalerDashboard();
        }
    }

    private void loadWholesalerDashboard() {
        apiClient.api().wholesalerDashboard().enqueue(new retrofit2.Callback<WholesalerDashboard>() {
            @Override
            public void onResponse(retrofit2.Call<WholesalerDashboard> call, retrofit2.Response<WholesalerDashboard> response) {
                WholesalerDashboard dashboard = response.body();
                if (!response.isSuccessful() || dashboard == null) {
                    showError("Could not load wholesaler dashboard from backend.");
                    return;
                }
                content.setPadding(dp(14), dp(14), dp(14), dp(24));
                content.addView(wholesalerHeaderCard());
                content.addView(spacer(18));
                content.addView(wholesalerHeroCard());
                content.addView(spacer(18));
                content.addView(sectionHeader("Sales Snapshot", "Track total sales and profit at a glance."));
                content.addView(metricRow(
                        "Total Sales",
                        money(dashboard.total_sales),
                        "This month",
                        "Total Profit",
                        money(dashboard.total_profit),
                        "This month"));
                content.addView(spacer(16));
                content.addView(sectionHeader("Low Stock Alerts", null));
                if (dashboard.low_stock_alerts == null || dashboard.low_stock_alerts.isEmpty()) {
                    content.addView(alertCard("No low stock items", "Everything is healthy right now", null));
                } else {
                    for (int i = 0; i < dashboard.low_stock_alerts.size(); i++) {
                        WholesalerDashboard.LowStockAlert alert = dashboard.low_stock_alerts.get(i);
                        content.addView(alertCard(alert.name, money(alert.stock_left) + " left", alert.name));
                        if (i < dashboard.low_stock_alerts.size() - 1) {
                            content.addView(spacer(12));
                        }
                    }
                }
                content.addView(spacer(16));
                content.addView(sectionHeader("Logistics Overview", null));
                content.addView(metricRow(
                        "Live",
                        String.valueOf(dashboard.active_shipments),
                        "Active Shipments",
                        "Awaiting",
                        String.format(Locale.US, "%02d", dashboard.pending_orders),
                        "Pending Orders"));
            }

            @Override
            public void onFailure(retrofit2.Call<WholesalerDashboard> call, Throwable t) {
                showError("Could not load wholesaler dashboard: " + t.getMessage());
            }
        });
    }

    private void loadWholesalerOrdersScreen() {
        apiClient.api().bulkRequests().enqueue(new retrofit2.Callback<Page<BulkRequest>>() {
            @Override
            public void onResponse(retrofit2.Call<Page<BulkRequest>> call, retrofit2.Response<Page<BulkRequest>> response) {
                Page<BulkRequest> page = response.body();
                if (!response.isSuccessful() || page == null) {
                    showError("Could not load orders queue from backend.");
                    return;
                }
                wholesalerOrdersCache = page.results == null ? new ArrayList<>() : new ArrayList<>(page.results);
                renderWholesalerOrdersScreen();
            }

            @Override
            public void onFailure(retrofit2.Call<Page<BulkRequest>> call, Throwable t) {
                showError("Could not load orders queue: " + t.getMessage());
            }
        });
    }

    private void loadWholesalerInventoryScreen() {
        refreshSellerProfile();
        Map<String, String> filters = new HashMap<>();
        filters.put("mine", "true");
        apiClient.api().products(filters).enqueue(new retrofit2.Callback<Page<Product>>() {
            @Override
            public void onResponse(retrofit2.Call<Page<Product>> call, retrofit2.Response<Page<Product>> response) {
                Page<Product> page = response.body();
                if (!response.isSuccessful() || page == null) {
                    showError("Could not load inventory from backend.");
                    return;
                }
                content.removeAllViews();
                content.setPadding(dp(14), dp(14), dp(14), dp(24));
                content.addView(wholesalerHeaderCard());
                content.addView(spacer(18));
                content.addView(inventoryHeroCard());
                content.addView(spacer(18));
                inventoryProductsCache = page.results == null ? new ArrayList<>() : new ArrayList<>(page.results);
                content.addView(inventoryContainerCard(inventoryProductsCache));
            }

            @Override
            public void onFailure(retrofit2.Call<Page<Product>> call, Throwable t) {
                showError("Could not load inventory: " + t.getMessage());
            }
        });
    }

    private void loadShopkeeperInventoryScreen() {
        refreshSellerProfile();
        Map<String, String> filters = new HashMap<>();
        filters.put("mine", "true");
        apiClient.api().products(filters).enqueue(new retrofit2.Callback<Page<Product>>() {
            @Override
            public void onResponse(retrofit2.Call<Page<Product>> call, retrofit2.Response<Page<Product>> response) {
                Page<Product> page = response.body();
                if (!response.isSuccessful() || page == null) {
                    showError("Could not load inventory from backend.");
                    return;
                }
                content.removeAllViews();
                content.setPadding(dp(14), dp(14), dp(14), dp(24));
                content.addView(shopkeeperHeaderCard());
                content.addView(spacer(18));
                content.addView(inventoryHeroCard());
                content.addView(spacer(18));
                inventoryProductsCache = page.results == null ? new ArrayList<>() : new ArrayList<>(page.results);
                content.addView(inventoryContainerCard(inventoryProductsCache));
            }

            @Override
            public void onFailure(retrofit2.Call<Page<Product>> call, Throwable t) {
                showError("Could not load inventory: " + t.getMessage());
            }
        });
    }

    private void loadAdminInventoryScreen() {
        Map<String, String> filters = new HashMap<>();
        filters.put("is_active", "true");

        apiClient.api().products(filters).enqueue(new retrofit2.Callback<Page<Product>>() {
            @Override
            public void onResponse(retrofit2.Call<Page<Product>> call, retrofit2.Response<Page<Product>> response) {
                Page<Product> page = response.body();
                if (!response.isSuccessful() || page == null) {
                    showError("Could not load inventory from backend.");
                    return;
                }

                content.removeAllViews();
                content.setPadding(dp(14), dp(14), dp(14), dp(24));
                content.addView(adminHeaderCard());
                content.addView(spacer(18));
                content.addView(inventoryHeroCard());
                content.addView(spacer(18));
                inventoryProductsCache = page.results == null ? new ArrayList<>() : new ArrayList<>(page.results);
                content.addView(inventoryContainerCard(inventoryProductsCache));
            }

            @Override
            public void onFailure(retrofit2.Call<Page<Product>> call, Throwable t) {
                showError("Could not load inventory: " + t.getMessage());
            }
        });
    }

    private void renderWholesalerOrdersQueue() {
        renderWholesalerOrdersScreen();
    }

    private void renderWholesalerOrdersScreen() {
        content.removeAllViews();
        content.setPadding(dp(14), dp(14), dp(14), dp(24));
        content.addView(wholesalerHeaderCard());
        content.addView(spacer(18));
        content.addView(wholesalerOrdersHeroCard());
        content.addView(spacer(18));
        content.addView(ordersQueueCard(filteredWholesalerOrders()));
    }

    private List<BulkRequest> filteredWholesalerOrders() {
        List<BulkRequest> filtered = new ArrayList<>();
        for (BulkRequest request : wholesalerOrdersCache) {
            if (request == null) {
                continue;
            }
            boolean isClosed = "closed".equalsIgnoreCase(request.status);
            if ("history".equals(wholesalerOrdersMode) && isClosed) {
                filtered.add(request);
            } else if ("live".equals(wholesalerOrdersMode) && !isClosed) {
                filtered.add(request);
            }
        }
        return filtered;
    }

    private void loadCategories(String sectionTitle) {
        apiClient.api().categories().enqueue(new retrofit2.Callback<Page<Category>>() {
            @Override
            public void onResponse(retrofit2.Call<Page<Category>> call, retrofit2.Response<Page<Category>> response) {
                Page<Category> page = response.body();
                if (!response.isSuccessful() || page == null) {
                    showError("Could not load categories from backend.");
                    return;
                }
                renderRecords(sectionTitle, categoriesToCards(page.results));
            }

            @Override
            public void onFailure(retrofit2.Call<Page<Category>> call, Throwable t) {
                showError("Could not load categories: " + t.getMessage());
            }
        });
    }

    private void loadProducts(String sectionTitle, Map<String, String> filters) {
        apiClient.api().products(filters).enqueue(new retrofit2.Callback<Page<Product>>() {
            @Override
            public void onResponse(retrofit2.Call<Page<Product>> call, retrofit2.Response<Page<Product>> response) {
                Page<Product> page = response.body();
                if (!response.isSuccessful() || page == null) {
                    showError("Could not load products from backend.");
                    return;
                }
                renderRecords(sectionTitle, productsToCards(page.results));
            }

            @Override
            public void onFailure(retrofit2.Call<Page<Product>> call, Throwable t) {
                showError("Could not load products: " + t.getMessage());
            }
        });
    }

    private void loadNearbyShops() {
        apiClient.api().nearbyShops(24.9133, 67.0971, 25.0, null).enqueue(new retrofit2.Callback<List<Shop>>() {
            @Override
            public void onResponse(retrofit2.Call<List<Shop>> call, retrofit2.Response<List<Shop>> response) {
                List<Shop> shops = response.body();
                if (!response.isSuccessful() || shops == null) {
                    showError("Could not load nearby shops from backend.");
                    return;
                }
                renderRecords("Nearby shops from backend", shopsToCards(shops));
            }

            @Override
            public void onFailure(retrofit2.Call<List<Shop>> call, Throwable t) {
                showError("Could not load shops: " + t.getMessage());
            }
        });
    }

    private void loadOrders(String sectionTitle) {
        apiClient.api().orders(new HashMap<>()).enqueue(new retrofit2.Callback<Page<Order>>() {
            @Override
            public void onResponse(retrofit2.Call<Page<Order>> call, retrofit2.Response<Page<Order>> response) {
                Page<Order> page = response.body();
                if (!response.isSuccessful() || page == null) {
                    showError("Could not load orders from backend.");
                    return;
                }
                renderRecords(sectionTitle, ordersToCards(page.results));
            }

            @Override
            public void onFailure(retrofit2.Call<Page<Order>> call, Throwable t) {
                showError("Could not load orders: " + t.getMessage());
            }
        });
    }

    private void loadBulkRequests() {
        apiClient.api().bulkRequests().enqueue(new retrofit2.Callback<Page<BulkRequest>>() {
            @Override
            public void onResponse(retrofit2.Call<Page<BulkRequest>> call, retrofit2.Response<Page<BulkRequest>> response) {
                Page<BulkRequest> page = response.body();
                if (!response.isSuccessful() || page == null) {
                    showError("Could not load bulk requests from backend.");
                    return;
                }
                renderRecords("Bulk requests from backend", bulkRequestsToCards(page.results));
            }

            @Override
            public void onFailure(retrofit2.Call<Page<BulkRequest>> call, Throwable t) {
                showError("Could not load bulk requests: " + t.getMessage());
            }
        });
    }

    private void loadQuotations() {
        apiClient.api().quotations().enqueue(new retrofit2.Callback<Page<Quotation>>() {
            @Override
            public void onResponse(retrofit2.Call<Page<Quotation>> call, retrofit2.Response<Page<Quotation>> response) {
                Page<Quotation> page = response.body();
                if (!response.isSuccessful() || page == null) {
                    showError("Could not load quotations from backend.");
                    return;
                }
                renderRecords("Quotations from backend", quotationsToCards(page.results));
            }

            @Override
            public void onFailure(retrofit2.Call<Page<Quotation>> call, Throwable t) {
                showError("Could not load quotations: " + t.getMessage());
            }
        });
    }

    private void loadProfile() {
        apiClient.api().me().enqueue(new retrofit2.Callback<User>() {
            @Override
            public void onResponse(retrofit2.Call<User> call, retrofit2.Response<User> response) {
                User user = response.body();
                if (!response.isSuccessful() || user == null) {
                    showError("Could not load profile from backend.");
                    return;
                }
                currentUser = user;
                refreshWholesalerChrome(user);
                if (ROLE_WHOLESALER.equals(activeRole) && "Settings".equals(activeScreen)) {
                    renderWholesalerSettings(user);
                    return;
                }
                if (ROLE_SHOPKEEPER.equals(activeRole) && "Settings".equals(activeScreen)) {
                    renderShopkeeperSettings(user);
                    return;
                }
                if (ROLE_CUSTOMER.equals(activeRole) && "Settings".equals(activeScreen)) {
                    renderCustomerSettings(user);
                    return;
                }
                List<DemoCard> cards = new ArrayList<>();
                cards.add(new DemoCard(user.username, user.email + " - " + roleLabel(user.role)));
                cards.add(new DemoCard("Phone", user.phone_number == null || user.phone_number.isEmpty() ? "No phone number set" : user.phone_number));
                cards.add(new DemoCard("Access token", "Demo login active for this role"));
                renderRecords("Signed-in user from backend", cards);
            }

            @Override
            public void onFailure(retrofit2.Call<User> call, Throwable t) {
                showError("Could not load profile: " + t.getMessage());
            }
        });
    }

    private void renderShopkeeperSettings(User user) {
        content.setPadding(dp(14), dp(12), dp(14), dp(22));
        content.addView(shopkeeperHeaderCard());
        content.addView(spacer(18));
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(0xFF5BDCF7);
        title.setTextSize(28);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Manage your retail shop preferences.");
        subtitle.setTextColor(0xFFD6E5F2);
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        content.addView(subtitle);
        content.addView(settingsProfileCard(user));
        content.addView(spacer(16));
        content.addView(settingsStatusCard());
        content.addView(spacer(18));
        content.addView(settingsLogoutButton());
    }

    private void renderWholesalerSettings(User user) {
        content.setPadding(dp(14), dp(12), dp(14), dp(22));

        content.addView(topHeroBar(user));
        content.addView(spacer(18));

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(0xFF5BDCF7);
        title.setTextSize(28);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Compare margins, demand patterns, and delivery performance.");
        subtitle.setTextColor(0xFFD6E5F2);
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        content.addView(subtitle);

        content.addView(performanceInsightsCard());
        content.addView(spacer(18));

        content.addView(sectionHeaderLabel("Settings"));
        content.addView(settingsProfileCard(user));
        content.addView(spacer(16));
        content.addView(settingsStatusCard());
        content.addView(spacer(18));

        content.addView(sectionHeaderLabel("Account"));
        content.addView(settingsGroupCard(
            settingsRow(android.R.drawable.ic_menu_myplaces, "Personal Information", true, false, null),
            settingsRow(android.R.drawable.ic_menu_send, "Cash on Delivery", true, false, null)
        ));
        content.addView(spacer(12));

        content.addView(sectionHeaderLabel("Operations"));
        content.addView(settingsGroupCard(
            settingsRow(android.R.drawable.ic_lock_idle_alarm, "Push Notifications", false, user.notification_enabled, null),
            settingsRow(android.R.drawable.ic_menu_agenda, "Inventory Alerts", true, user.inventory_alerts_enabled, null)
        ));
        content.addView(spacer(12));

        content.addView(sectionHeaderLabel("Security"));
        content.addView(settingsGroupCard(
            settingsRow(android.R.drawable.ic_lock_lock, "Two-Factor Authentication", false, user.two_factor_enabled, user.two_factor_enabled ? "ON" : "OFF"),
            settingsRow(android.R.drawable.ic_menu_compass, "Biometric Access", true, user.biometric_access_enabled, null)
        ));
        content.addView(spacer(18));
        content.addView(settingsLogoutButton());
    }

        private MaterialCardView topHeroBar(User user) {
        MaterialCardView card = createCard(0xFF14355B, 28, 18);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView menu = new TextView(this);
        menu.setText("≡");
        menu.setTextSize(26);
        menu.setTextColor(0xFFFFFFFF);
        menu.setPadding(0, 0, dp(12), 0);
        menu.setOnClickListener(v -> openAppDrawer());
        row.addView(menu);

        TextView name = new TextView(this);
        name.setText(safeText(displayName(user), "Noor Din"));
        name.setTextSize(20);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        name.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        row.addView(name, nameParams);

        ImageView avatar = settingsCircleIcon(android.R.drawable.ic_menu_myplaces);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        avatar.setOnClickListener(v -> showProfileUploadMenu(v));
        row.addView(avatar, avatarParams);

        box.addView(row);
        return card;
        }

        private MaterialCardView performanceInsightsCard() {
        MaterialCardView card = createCard(0xFF123253, 24, 18);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        TextView title = new TextView(this);
        title.setText("Performance Insights");
        title.setTextColor(0xFF5BDCF7);
        title.setTextSize(21);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Review margins, demand, and delivery health at a glance.");
        subtitle.setTextColor(0xFFD7E5F1);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(8), 0, 0);
        box.addView(subtitle);
        return card;
        }

        private TextView sectionHeaderLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14);
        label.setLetterSpacing(0.14f);
        label.setTextColor(0xFFB9C8D7);
        label.setPadding(dp(10), 0, 0, dp(10));
        return label;
    }

    private MaterialCardView settingsProfileCard(User user) {
        MaterialCardView card = createCard(0xFF123552, 22, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView avatar = settingsCircleIcon(android.R.drawable.ic_menu_myplaces);
        avatar.setOnClickListener(v -> showProfileUploadMenu(v));
        row.addView(avatar);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, -2, 1f);
        infoParams.leftMargin = dp(16);
        info.setLayoutParams(infoParams);

        TextView name = new TextView(this);
        name.setText(displayName(user));
        name.setTextSize(19);
        name.setTextColor(0xFFFFFFFF);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        info.addView(name);

        TextView roleLine = new TextView(this);
        roleLine.setText("WHOLESALE MANAGER");
        roleLine.setTextSize(12);
        roleLine.setLetterSpacing(0.18f);
        roleLine.setTextColor(0xFFB5C6D6);
        info.addView(roleLine);

        TextView idLine = new TextView(this);
        String adminId = user.id > 0 ? String.valueOf(user.id) : "8842";
        idLine.setText(safeText(user.email, "wholesaler@gmail.com"));
        idLine.setTextSize(14);
        idLine.setTextColor(0xFFDCE7F2);
        info.addView(idLine);

        ImageView edit = new ImageView(this);
        edit.setImageResource(android.R.drawable.ic_menu_edit);
        edit.setColorFilter(0xFF5BDCF7);
        edit.setOnClickListener(v -> showProfileUploadMenu(v));
        row.addView(info);
        row.addView(edit, new LinearLayout.LayoutParams(dp(28), dp(28)));
        box.addView(row);
        return card;
    }

    private MaterialCardView settingsStatusCard() {
        MaterialCardView card = createCard(0xFF123552, 22, 18);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("System Status");
        title.setTextSize(13);
        title.setTextColor(0xFFB8C8D8);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(title);

        View dot = new View(this);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(10), dp(10)));
        dot.setBackground(circleDrawable(0xFF2FD9EF));
        header.addView(dot);

        TextView status = new TextView(this);
        status.setText("All Operational");
        status.setTextSize(22);
        status.setTextColor(0xFFFFFFFF);
        status.setTypeface(status.getTypeface(), android.graphics.Typeface.BOLD);

        TextView version = new TextView(this);
        version.setText("Version 2.4.1 Build-Stable");
        version.setTextSize(14);
        version.setTextColor(0xFFB8C8D8);

        box.addView(header);
        box.addView(spacer(10));
        box.addView(status);
        box.addView(spacer(6));
        box.addView(version);
        return card;
    }

    private MaterialCardView settingsGroupCard(View... rows) {
        MaterialCardView card = createCard(0xFF123552, 22, 4);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        box.setPadding(0, 0, 0, 0);
        for (int i = 0; i < rows.length; i++) {
            box.addView(rows[i]);
            if (i < rows.length - 1) {
                box.addView(settingsDivider());
            }
        }
        return card;
    }

    private View settingsRow(int iconRes, String text, boolean arrow, boolean checked, String badge) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));

        ImageView icon = settingsCircleIcon(iconRes);
        row.addView(icon);

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(16);
        label.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = dp(14);
        label.setLayoutParams(labelParams);
        row.addView(label);

        if ("Push Notifications".equals(text)) {
            Switch toggle = new Switch(this);
            toggle.setChecked(checked);
            toggle.setOnCheckedChangeListener((buttonView, isChecked) -> Toast.makeText(MainActivity.this, text + " " + (isChecked ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show());
            row.addView(toggle);
        } else if (badge != null) {
            TextView pill = new TextView(this);
            pill.setText(badge);
            pill.setTextSize(12);
            pill.setTextColor(0xFFE9EEF3);
            pill.setTypeface(pill.getTypeface(), android.graphics.Typeface.BOLD);
            pill.setPadding(dp(14), dp(6), dp(14), dp(6));
            pill.setBackground(circleDrawable(0xFF6E7A85));
            row.addView(pill);
        } else if (arrow) {
            TextView caret = new TextView(this);
            caret.setText(">");
            caret.setTextSize(28);
            caret.setTextColor(0xFF8CCFE2);
            row.addView(caret);
        }

        return row;
    }

    private View settingsDivider() {
        View divider = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.leftMargin = dp(18);
        params.rightMargin = dp(18);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(0xFFE2E6EA);
        return divider;
    }

    private ImageView settingsCircleIcon(int iconRes) {
        ImageView icon = new ImageView(this);
        int size = dp(34);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        icon.setLayoutParams(params);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setColorFilter(0xFF27D5EE);
        icon.setBackground(circleDrawable(0xFFE7F2F5));
        return icon;
    }

    private View settingsLogoutButton() {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText("Logout");
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setTextColor(0xFFFFA9A9);
        button.setStrokeWidth(dp(1));
        button.setStrokeColor(ColorStateList.valueOf(0xFF9F7070));
        button.setCornerRadius(dp(24));
        button.setBackgroundColor(0xFF142B3C);
        button.setOnClickListener(v -> signOut());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(56));
        button.setLayoutParams(params);
        return button;
    }

    private MaterialCardView simpleSettingCard(String titleText, String subtitleText, int iconRes, boolean showToggle, String badge) {
        MaterialCardView card = createCard(0xFFF7F0F6, 14, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(0xFF55606C);
        icon.setBackground(circleDrawable(0xFFE8EDF1));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        iconParams.rightMargin = dp(14);
        row.addView(icon, iconParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(18);
        title.setTextColor(0xFF24313D);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF5D6874);
        subtitle.setPadding(0, dp(2), 0, 0);

        texts.addView(title);
        texts.addView(subtitle);
        row.addView(texts, textsParams);

        if (showToggle) {
            Switch toggle = new Switch(this);
            row.addView(toggle);
        } else if (badge != null && !badge.isEmpty()) {
            TextView pill = new TextView(this);
            pill.setText(badge);
            pill.setTextColor(0xFFFFFFFF);
            pill.setPadding(dp(10), dp(5), dp(10), dp(5));
            pill.setBackground(circleDrawable(0xFF607080));
            row.addView(pill);
        }

        box.addView(row);
        return card;
    }

    private android.graphics.drawable.GradientDrawable circleDrawable(int fillColor) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        drawable.setColor(fillColor);
        return drawable;
    }

    private void renderRecords(String sectionTitle, List<DemoCard> items) {
        content.removeAllViews();
        title(activeScreen);
        cards(sectionTitle, items);
    }

    private void showLoginLanding() {
        content.removeAllViews();
    }

    @Override
    public void showError(String message) {
        content.removeAllViews();
        title(activeScreen);
        card("Load failed", message);
    }

    private List<DemoCard> categoriesToCards(List<Category> categories) {
        List<DemoCard> items = new ArrayList<>();
        if (categories != null) {
            for (Category category : categories) {
                items.add(new DemoCard(category.name, "Slug: " + category.slug));
            }
        }
        return items;
    }

    private List<DemoCard> productsToCards(List<Product> products) {
        List<DemoCard> items = new ArrayList<>();
        if (products != null) {
            for (Product product : products) {
                items.add(new DemoCard(
                        product.name,
                        product.shop_name + " - " + product.category_name + " - Rs " + money(product.price) + " / " + product.unit + " - stock " + money(product.stock_quantity)
                ));
            }
        }
        return items;
    }

    private List<DemoCard> shopsToCards(List<Shop> shops) {
        List<DemoCard> items = new ArrayList<>();
        if (shops != null) {
            for (Shop shop : shops) {
                items.add(new DemoCard(
                        shop.name,
                        shop.kind + " - " + shop.address + (shop.distance_km == null ? "" : " - " + String.format(Locale.US, "%.1f km away", shop.distance_km))
                ));
            }
        }
        return items;
    }

    private List<DemoCard> ordersToCards(List<Order> orders) {
        List<DemoCard> items = new ArrayList<>();
        if (orders != null) {
            for (Order order : orders) {
                String firstItem = order.items == null || order.items.isEmpty() ? "No items" : "First item product #" + order.items.get(0).product;
                items.add(new DemoCard(
                        "Order #" + order.id,
                        order.status + " - " + order.shop_name + " - total Rs " + money(order.total) + " - " + firstItem
                ));
            }
        }
        return items;
    }

    private List<DemoCard> bulkRequestsToCards(List<BulkRequest> requests) {
        List<DemoCard> items = new ArrayList<>();
        if (requests != null) {
            for (BulkRequest request : requests) {
                items.add(new DemoCard(
                        "Bulk request #" + request.id,
                        request.product_name + " - qty " + money(request.quantity) + " - " + request.status
                ));
            }
        }
        return items;
    }

    private List<DemoCard> quotationsToCards(List<Quotation> quotations) {
        List<DemoCard> items = new ArrayList<>();
        if (quotations != null) {
            for (Quotation quotation : quotations) {
                items.add(new DemoCard(
                        "Quotation #" + quotation.id,
                        quotation.bulk_request_product_name + " - " + quotation.wholesaler_name + " - total Rs " + money(quotation.total)
                ));
            }
        }
        return items;
    }

    private void title(String text) {
        TextView view = new TextView(this);
        view.setText(text + " - " + roleLabel(activeRole));
        view.setTextSize(28);
        view.setGravity(Gravity.START);
        view.setTextColor(Color.WHITE);
        content.addView(view);
    }

    private String roleLabel(String role) {
        if (ROLE_SHOPKEEPER.equals(role)) {
            return "Shopkeeper";
        }
        if (ROLE_WHOLESALER.equals(role)) {
            return "Wholesaler";
        }
        return "Customer";
    }

    private String demoUsernameForRole(String role) {
        if (ROLE_SHOPKEEPER.equals(role)) {
            return "demo_shopkeeper";
        }
        if (ROLE_WHOLESALER.equals(role)) {
            return "demo_wholesaler";
        }
        return "demo_customer";
    }

    @Override
    public String money(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private android.graphics.drawable.GradientDrawable createBackground() {
        return new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0xFF08203A, 0xFF0D2E57, 0xFF061725 }
        );
    }

    private String displayName(User user) {
        if (user == null) {
            return roleLabel(activeRole);
        }
        if (user.first_name != null || user.last_name != null) {
            String first = user.first_name == null ? "" : user.first_name.trim();
            String last = user.last_name == null ? "" : user.last_name.trim();
            String fullName = (first + " " + last).trim();
            if (!fullName.isEmpty()) {
                return fullName;
            }
        }
        if (user.username != null && !user.username.trim().isEmpty()) {
            return user.username.trim();
        }
        return roleLabel(activeRole);
    }

    private String resolveBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        String normalized = baseUrl.trim();
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        return normalized;
    }

    private void card(String title, String body) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(16);
        card.setUseCompatPadding(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 20, 24, 20);
        TextView h = new TextView(this);
        h.setText(title);
        h.setTextSize(18);
        TextView p = new TextView(this);
        p.setText(body);
        p.setTextSize(14);
        box.addView(h);
        box.addView(p);
        card.addView(box);
        content.addView(card);
    }

    @Override
    public MaterialCardView createCard(int backgroundColor, int radiusDp, int paddingDp) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(backgroundColor);
        card.setRadius(dp(radiusDp));
        card.setUseCompatPadding(true);
        card.setCardElevation(dp(3));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(0x22FFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        card.setLayoutParams(params);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        card.addView(box);
        return card;
    }

    @Override
    public View spacer(int heightDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(heightDp)));
        return spacer;
    }

    private LinearLayout sectionHeader(String titleText, String subtitleText) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);
        if (subtitleText != null && !subtitleText.isEmpty()) {
            TextView subtitle = new TextView(this);
            subtitle.setText(subtitleText);
            subtitle.setTextColor(0xFFDCE8F6);
            subtitle.setTextSize(15);
            subtitle.setPadding(0, dp(6), 0, 0);
            box.addView(subtitle);
        }
        return box;
    }

    @Override
    public MaterialCardView wholesalerHeaderCard() {
        MaterialCardView card = createCard(0xFF173B63, 24, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView menu = new ImageView(this);
        menu.setImageResource(android.R.drawable.ic_menu_sort_by_size);
        menu.setColorFilter(0xFFFFFFFF);
        menu.setOnClickListener(v -> openAppDrawer());
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        menuParams.rightMargin = dp(14);
        row.addView(menu, menuParams);

        TextView name = new TextView(this);
        name.setText(signedInLabel == null || signedInLabel.trim().isEmpty() ? roleLabel(activeRole) : signedInLabel);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(20);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        name.setGravity(Gravity.CENTER);
        row.addView(name, nameParams);

        MaterialCardView avatar = new MaterialCardView(this);
        avatar.setCardBackgroundColor(0xFF4F7BA7);
        avatar.setRadius(dp(22));
        avatar.setUseCompatPadding(false);
        avatar.setCardElevation(0);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        ImageView avatarIcon = new ImageView(this);
        avatarIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarIcon.setPadding(dp(6), dp(6), dp(6), dp(6));
        avatar.setOnClickListener(v -> showProfileUploadMenu(v));
        bindAvatarImage(avatarIcon, currentUser);
        avatar.addView(avatarIcon, new LinearLayout.LayoutParams(-1, -1));
        row.addView(avatar, avatarParams);

        box.addView(row);
        return card;
    }

    @Override
    public MaterialCardView shopkeeperHeaderCard() {
        MaterialCardView card = createCard(0xFF173B63, 24, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView menu = new ImageView(this);
        menu.setImageResource(android.R.drawable.ic_menu_sort_by_size);
        menu.setColorFilter(0xFFFFFFFF);
        menu.setOnClickListener(v -> openAppDrawer());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        iconParams.rightMargin = dp(14);
        row.addView(menu, iconParams);

        TextView name = new TextView(this);
        name.setText(signedInLabel == null || signedInLabel.trim().isEmpty() ? roleLabel(activeRole) : signedInLabel);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(20);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialCardView avatar = new MaterialCardView(this);
        avatar.setCardBackgroundColor(0xFF4F7BA7);
        avatar.setRadius(dp(22));
        avatar.setUseCompatPadding(false);
        avatar.setCardElevation(0);
        avatar.setOnClickListener(v -> showProfileUploadMenu(v));

        ImageView avatarIcon = new ImageView(this);
        avatarIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarIcon.setPadding(dp(6), dp(6), dp(6), dp(6));
        bindAvatarImage(avatarIcon, currentUser);
        avatar.addView(avatarIcon, new LinearLayout.LayoutParams(-1, -1));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));

        box.addView(row);
        return card;
    }

    @Override
    public void openWholesalerDrawer() {
        openAppDrawer();
    }

    public void openAppDrawer() {
        if (drawerPanel == null || drawerScrim == null) {
            return;
        }
        drawerScrim.setVisibility(View.VISIBLE);
        drawerPanel.animate().translationX(0f).setDuration(220L).start();
    }

    private void closeDrawer() {
        if (drawerPanel == null || drawerScrim == null) {
            return;
        }
        drawerPanel.animate().translationX(-dp(DRAWER_WIDTH_DP)).setDuration(220L).withEndAction(() -> drawerScrim.setVisibility(View.GONE)).start();
    }

    private void installDrawerForRole() {
        if (drawerPanel != null) {
            rootContainer.removeView(drawerPanel);
        }
        drawerPanel = buildDrawerPanelForRole(activeRole);
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(dp(DRAWER_WIDTH_DP), -1);
        drawerParams.gravity = Gravity.START;
        rootContainer.addView(drawerPanel, drawerParams);
        drawerPanel.setTranslationX(-dp(DRAWER_WIDTH_DP));
    }

    private MaterialCardView buildDrawerPanelForRole(String role) {
        MaterialCardView panel = new MaterialCardView(this);
        panel.setCardBackgroundColor(0xFF081A2C);
        panel.setRadius(dp(24));
        panel.setCardElevation(dp(10));
        panel.setUseCompatPadding(false);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(18));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        MaterialCardView avatarWrap = new MaterialCardView(this);
        avatarWrap.setCardBackgroundColor(0xFF173B63);
        avatarWrap.setRadius(dp(18));
        avatarWrap.setCardElevation(0);
        LinearLayout.LayoutParams avatarWrapParams = new LinearLayout.LayoutParams(dp(38), dp(38));

        drawerAvatarImage = new ImageView(this);
        drawerAvatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bindAvatarImage(drawerAvatarImage, currentUser);
        avatarWrap.addView(drawerAvatarImage, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, -2, 1f);
        infoParams.leftMargin = dp(12);
        info.setLayoutParams(infoParams);

        drawerNameView = new TextView(this);
        drawerNameView.setText(signedInLabel == null || signedInLabel.trim().isEmpty() ? roleLabel(role) : signedInLabel);
        drawerNameView.setTextColor(0xFFFFFFFF);
        drawerNameView.setTextSize(18);
        drawerNameView.setTypeface(drawerNameView.getTypeface(), android.graphics.Typeface.BOLD);

        drawerRoleView = new TextView(this);
        drawerRoleView.setText(roleLabel(role));
        drawerRoleView.setTextColor(0xFFB3C7D8);
        drawerRoleView.setTextSize(12);

        info.addView(drawerNameView);
        info.addView(drawerRoleView);

        TextView close = new TextView(this);
        close.setText("X");
        close.setTextColor(0xFFE5EEF7);
        close.setTextSize(18);
        close.setPadding(dp(10), dp(4), dp(6), dp(4));
        close.setOnClickListener(v -> closeDrawer());

        header.addView(avatarWrap, avatarWrapParams);
        header.addView(info);
        header.addView(close);

        box.addView(header);
        box.addView(spacer(18));

        if (ROLE_CUSTOMER.equals(role)) {
            addDrawerNavItem(box, "Home");
            addDrawerNavItem(box, "Shops");
            addDrawerNavItem(box, "Bulk");
            addDrawerNavItem(box, "Cart");
            addDrawerNavItem(box, "Orders");
            addDrawerNavItem(box, "Settings");
        } else if (ROLE_SHOPKEEPER.equals(role)) {
            addDrawerNavItem(box, "Dashboard");
            addDrawerNavItem(box, "Orders");
            addDrawerNavItem(box, "Wholesale");
            addDrawerNavItem(box, "Inventory");
            addDrawerNavItem(box, "Settings");
        } else if (ROLE_ADMIN.equals(role)) {
            addDrawerNavItem(box, "Inventory");
            addDrawerNavItem(box, "Shops");
            addDrawerNavItem(box, "Orders");
            addDrawerNavItem(box, "Bulk Orders");
            addDrawerNavItem(box, "Settings");
        } else {
            addDrawerNavItem(box, "Dashboard");
            addDrawerNavItem(box, "Inventory Management");
            addDrawerNavItem(box, "Bulk Orders");
            addDrawerNavItem(box, "Settings");
        }

        View flexibleSpacer = new View(this);
        flexibleSpacer.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        box.addView(flexibleSpacer);
        box.addView(drawerItem(android.R.drawable.ic_lock_power_off, "Logout", false));

        panel.addView(box, new LinearLayout.LayoutParams(-1, -1));
        return panel;
    }

    private void addDrawerNavItem(LinearLayout box, String label) {
        int icon = android.R.drawable.ic_menu_preferences;
        if ("Home".equals(label) || "Dashboard".equals(label)) {
            icon = android.R.drawable.ic_menu_view;
        } else if ("Shops".equals(label)) {
            icon = android.R.drawable.ic_dialog_map;
        } else if ("Cart".equals(label)) {
            icon = android.R.drawable.ic_menu_add;
        } else if ("Orders".equals(label) || "Bulk Orders".equals(label)) {
            icon = android.R.drawable.ic_menu_agenda;
        } else if ("Inventory".equals(label) || "Inventory Management".equals(label)) {
            icon = android.R.drawable.ic_menu_sort_by_size;
        } else if ("Wholesale".equals(label) || "Bulk".equals(label)) {
            icon = android.R.drawable.ic_menu_upload;
        } else if ("Settings".equals(label)) {
            icon = android.R.drawable.ic_menu_preferences;
        }
        box.addView(drawerItem(icon, label, label.equals(activeScreen)));
        box.addView(spacer(10));
    }

    private MaterialCardView drawerItem(int iconRes, String label, boolean selected) {
        MaterialCardView item = new MaterialCardView(this);
        item.setCardBackgroundColor(selected ? 0xFF46567B : 0x00000000);
        item.setRadius(dp(14));
        item.setCardElevation(0);
        item.setUseCompatPadding(false);
        item.setClickable(true);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(selected ? 0xFFE6F2FF : 0xFFC5D3DF);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconParams.rightMargin = dp(12);
        row.addView(icon, iconParams);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(selected ? 0xFFF4FAFF : 0xFFD3E1EE);
        text.setTextSize(15);
        row.addView(text, new LinearLayout.LayoutParams(-2, -2));

        item.addView(row, new LinearLayout.LayoutParams(-1, -2));
        item.setOnClickListener(v -> {
            closeDrawer();
            if ("Logout".equals(label)) {
                signOut();
                return;
            }
            activeScreen = label;
            selectBottomNavForScreen(activeScreen);
            loadActiveScreen();
        });
        return item;
    }

    private int screenIdForTitle(String title) {
        if (ROLE_ADMIN.equals(activeRole)) {
            if ("Inventory".equals(title)) return 1;
            if ("Shops".equals(title)) return 2;
            if ("Orders".equals(title)) return 3;
            if ("Bulk Orders".equals(title)) return 4;
            if ("Settings".equals(title)) return 5;
            return 1;
        }
        if (ROLE_WHOLESALER.equals(activeRole)) {
            if ("Dashboard".equals(title)) return 1;
            if ("Inventory Management".equals(title)) return 2;
            if ("Bulk Orders".equals(title)) return 3;
            if ("Settings".equals(title)) return 4;
            return 1;
        }
        if (ROLE_SHOPKEEPER.equals(activeRole)) {
            if ("Dashboard".equals(title)) return 1;
            if ("Orders".equals(title)) return 2;
            if ("Wholesale".equals(title)) return 3;
            if ("Inventory".equals(title)) return 4;
            if ("Settings".equals(title)) return 5;
            return 1;
        }
        if ("Home".equals(title)) return 1;
        if ("Shops".equals(title)) return 2;
        if ("Cart".equals(title)) return 3;
        if ("Orders".equals(title)) return 4;
        if ("Settings".equals(title)) return 5;
        return 1;
    }

    private void selectBottomNavForScreen(String screenTitle) {
        int navId = screenIdForTitle(screenTitle);
        if (navigation.getMenu().findItem(navId) != null) {
            navigation.setSelectedItemId(navId);
        } else {
            for (int i = 0; i < navigation.getMenu().size(); i++) {
                navigation.getMenu().getItem(i).setChecked(false);
            }
        }
    }

    public MaterialCardView customerHeaderCard() {
        MaterialCardView card = createCard(0xFF173B63, 24, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView menu = new ImageView(this);
        menu.setImageResource(android.R.drawable.ic_menu_sort_by_size);
        menu.setColorFilter(0xFFFFFFFF);
        menu.setOnClickListener(v -> openAppDrawer());
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        menuParams.rightMargin = dp(14);
        row.addView(menu, menuParams);

        TextView name = new TextView(this);
        name.setText(signedInLabel == null || signedInLabel.trim().isEmpty() ? roleLabel(ROLE_CUSTOMER) : signedInLabel);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(20);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialCardView avatar = new MaterialCardView(this);
        avatar.setCardBackgroundColor(0xFF4F7BA7);
        avatar.setRadius(dp(22));
        avatar.setCardElevation(0);
        ImageView avatarIcon = new ImageView(this);
        avatarIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bindAvatarImage(avatarIcon, currentUser);
        avatar.addView(avatarIcon, new LinearLayout.LayoutParams(-1, -1));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));

        box.addView(row);
        return card;
    }

    private void renderCustomerSettings(User user) {
        content.setPadding(dp(14), dp(12), dp(14), dp(22));
        content.addView(customerHeaderCard());
        content.addView(spacer(18));
        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor(0xFF5BDCF7);
        title.setTextSize(28);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title);
        content.addView(settingsProfileCard(user));
        content.addView(spacer(16));
        content.addView(settingsStatusCard());
        content.addView(spacer(18));
        content.addView(settingsLogoutButton());
    }

    private void showProfileUploadMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor, Gravity.END);
        popupMenu.getMenu().add(0, 1, 0, "Upload profile picture");
        popupMenu.getMenu().add(0, 2, 1, "Cancel");
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openAvatarPicker();
            }
            return true;
        });
        popupMenu.show();
    }

    private void refreshWholesalerChrome(User user) {
        if (drawerNameView != null) {
            drawerNameView.setText(safeText(displayName(user), "Noor Din"));
        }
        if (drawerRoleView != null) {
            drawerRoleView.setText("Wholesale Manager");
        }
        if (drawerAvatarImage != null) {
            bindAvatarImage(drawerAvatarImage, user);
        }
    }

    private void bindAvatarImage(ImageView imageView, @Nullable User user) {
        imageView.setImageResource(android.R.drawable.ic_menu_myplaces);
        imageView.setColorFilter(0xFFFFFFFF);
        if (user == null || user.avatar == null || user.avatar.trim().isEmpty()) {
            return;
        }
        String avatarUrl = resolveAvatarUrl(user.avatar);
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(avatarUrl).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setDoInput(true);
                connection.connect();
                if (connection.getResponseCode() >= 200 && connection.getResponseCode() < 300) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 4;
                    Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream(), null, opts);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            imageView.setColorFilter(null);
                            imageView.setImageBitmap(bitmap);
                        });
                    }
                }
            } catch (Exception ignored) {
                // Keep placeholder icon when the avatar cannot be loaded.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private String resolveAvatarUrl(String avatar) {
        String value = avatar.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return DEFAULT_BASE_URL + value;
    }

    private void openAvatarPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select profile picture"), REQUEST_PICK_AVATAR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (InventoryController.handleScanResult(requestCode, resultCode, data)) {
            return;
        }
        if (requestCode == REQUEST_PICK_AVATAR && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadAvatar(data.getData());
        }
    }

    private static final int MAX_AVATAR_UPLOAD_BYTES = 2 * 1024 * 1024;

    private void uploadAvatar(Uri uri) {
        if (apiClient == null) {
            Toast.makeText(this, "Sign in again to update your profile photo.", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                if (inputStream == null) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Could not open the selected image.", Toast.LENGTH_SHORT).show());
                    return;
                }
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    if (outputStream.size() + read > MAX_AVATAR_UPLOAD_BYTES) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Image is too large. Choose a photo under 2 MB.", Toast.LENGTH_LONG).show());
                        return;
                    }
                    outputStream.write(buffer, 0, read);
                }
                RequestBody requestBody = RequestBody.create(MediaType.parse("image/*"), outputStream.toByteArray());
                MultipartBody.Part avatarPart = MultipartBody.Part.createFormData("avatar", "avatar.jpg", requestBody);
                apiClient.api().updateMeAvatar(avatarPart).enqueue(new retrofit2.Callback<User>() {
                    @Override
                    public void onResponse(retrofit2.Call<User> call, retrofit2.Response<User> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Profile picture upload failed.", Toast.LENGTH_SHORT).show());
                            return;
                        }
                        currentUser = response.body();
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Profile picture updated.", Toast.LENGTH_SHORT).show();
                            loadProfile();
                        });
                    }

                    @Override
                    public void onFailure(retrofit2.Call<User> call, Throwable t) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Upload failed: " + t.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private MaterialCardView wholesalerHeroCard() {
        MaterialCardView card = createCard(0xFF0F2E56, 22, 16);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        TextView title = new TextView(this);
        title.setText("Wholesaler Hub");
        title.setTextColor(0xFF64D6F7);
        title.setTextSize(24);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Track bulk leads, inventory, and shipments from one polished dashboard.");
        subtitle.setTextColor(0xFFE0EAF5);
        subtitle.setTextSize(14);
        subtitle.setLineSpacing(0f, 1.1f);
        subtitle.setPadding(0, dp(10), 0, 0);
        box.addView(subtitle);

        return card;
    }

    private MaterialCardView shopkeeperHeroCard() {
        MaterialCardView card = createCard(0xFF0F2E56, 22, 16);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        TextView title = new TextView(this);
        title.setText("Shopkeeper Hub");
        title.setTextColor(0xFF64D6F7);
        title.setTextSize(24);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Track retail orders, inventory alerts, and daily shop performance from one dashboard.");
        subtitle.setTextColor(0xFFE0EAF5);
        subtitle.setTextSize(14);
        subtitle.setLineSpacing(0f, 1.1f);
        subtitle.setPadding(0, dp(10), 0, 0);
        box.addView(subtitle);

        return card;
    }

    private MaterialCardView adminHeaderCard() {
        MaterialCardView card = createCard(0xFF0F2E56, 22, 16);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        TextView title = new TextView(this);
        title.setText("Admin Panel");
        title.setTextColor(0xFF64D6F7);
        title.setTextSize(24);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Review all shops, inventories, and dispatch actions across the marketplace.");
        subtitle.setTextColor(0xFFE0EAF5);
        subtitle.setTextSize(14);
        subtitle.setLineSpacing(0f, 1.1f);
        subtitle.setPadding(0, dp(10), 0, 0);
        box.addView(subtitle);

        return card;
    }

    private MaterialCardView wholesalerOrdersHeroCard() {
        MaterialCardView card = createCard(0xFF0F2E56, 22, 16);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        TextView title = new TextView(this);
        title.setText("Order Track");
        title.setTextColor(0xFF64D6F7);
        title.setTextSize(24);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Review bulk orders and prioritize the fastest-moving requests.");
        subtitle.setTextColor(0xFFE0EAF5);
        subtitle.setTextSize(14);
        subtitle.setLineSpacing(0f, 1.1f);
        subtitle.setPadding(0, dp(10), 0, 0);
        box.addView(subtitle);

        return card;
    }

    private MaterialCardView ordersQueueCard(List<BulkRequest> requests) {
        MaterialCardView card = createCard(0xFF102A49, 22, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        TextView title = new TextView(this);
        title.setText("Orders Queue");
        title.setTextColor(0xFF64D6F7);
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Sort active requests and move the highest priority orders first.");
        subtitle.setTextColor(0xFFD6E3F1);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(8), 0, 0);
        box.addView(subtitle);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(16), 0, dp(8));
        tabs.addView(queueTab("Live", "live", "live".equals(wholesalerOrdersMode)), new LinearLayout.LayoutParams(0, -2, 1f));
        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 1));
        tabs.addView(gap);
        tabs.addView(queueTab("History", "history", "history".equals(wholesalerOrdersMode)), new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(tabs);

        List<BulkRequest> filtered = filteredWholesalerOrders();
        if (filtered.isEmpty()) {
            box.addView(orderPipelineCard(null, "No " + wholesalerOrdersMode + " orders", "No requests in this view.", "", "Queue is clear"));
            return card;
        }
        int limit = Math.min(filtered.size(), 6);
        for (int i = 0; i < limit; i++) {
            BulkRequest request = filtered.get(i);
            String dueText = i == 0 ? "Due today" : (i == 1 ? "Tomorrow" : "This week");
            String rightText = priorityLabelForStatus(request.status, i);
            MaterialCardView pipeline = orderPipelineCard(
                    request,
                    safeText(request.product_name, "Bulk request"),
                    "Qty " + money(request.quantity) + " • " + safeText(request.status, "open"),
                    dueText,
                    rightText
            );
            box.addView(pipeline);
            if (i < limit - 1) {
                box.addView(spacer(12));
            }
        }
        return card;
    }

    private MaterialCardView queueTab(String label, String mode, boolean selected) {
        MaterialCardView chip = new MaterialCardView(this);
        chip.setCardBackgroundColor(selected ? 0xFF64D6F7 : 0xFF2A4264);
        chip.setRadius(dp(20));
        chip.setCardElevation(0);
        chip.setUseCompatPadding(false);
        chip.setClickable(true);
        chip.setForeground(null);
        chip.setOnClickListener(v -> {
            wholesalerOrdersMode = mode;
            renderWholesalerOrdersScreen();
        });

        TextView text = new TextView(this);
        text.setText(label);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(18), dp(10), dp(18), dp(10));
        text.setTextColor(selected ? 0xFF0A223D : 0xFFF0F6FF);
        text.setTextSize(16);
        text.setTypeface(text.getTypeface(), android.graphics.Typeface.BOLD);
        chip.addView(text, new LinearLayout.LayoutParams(-1, -2));
        return chip;
    }

    private MaterialCardView orderPipelineCard(@Nullable BulkRequest request, String titleText, String subText, String dueText, String rightText) {
        MaterialCardView card = createCard(0xFF132E50, 20, 12);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        TextView pipeline = new TextView(this);
        pipeline.setText("Bulk request");
        pipeline.setTextColor(0xFFB8D1E7);
        pipeline.setTextSize(14);
        pipeline.setTypeface(pipeline.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(pipeline);

        long id = request == null ? 0 : request.id;
        TextView title = new TextView(this);
        title.setText(id > 0 ? "Request #" + id : titleText);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(17);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(8), 0, 0);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subText);
        subtitle.setTextColor(0xFFD5E3F1);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), 0, 0);
        box.addView(subtitle);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.HORIZONTAL);
        details.setPadding(0, dp(12), 0, dp(12));

        TextView due = new TextView(this);
        due.setText(dueText);
        due.setTextColor(0xFFEAF3FF);
        due.setTextSize(14);
        details.addView(due, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView right = new TextView(this);
        right.setText(rightText);
        right.setTextColor(0xFFD3E1EF);
        right.setTextSize(14);
        right.setGravity(Gravity.END);
        details.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(details);

        if (request != null) {
            MaterialButton action = new MaterialButton(this);
            action.setAllCaps(false);
            action.setTextColor(0xFFE7F5FF);
            action.setTextSize(14);
            action.setCornerRadius(dp(22));
            action.setBackgroundColor(0xFF5BCFEA);

            boolean isOpen = "open".equalsIgnoreCase(request.status);
            boolean canDispatch = request.can_dispatch;
            if (!canDispatch && (ROLE_WHOLESALER.equals(activeRole) || ROLE_ADMIN.equals(activeRole))) {
                canDispatch = "accepted".equalsIgnoreCase(request.status);
            }

            if (isOpen) {
                action.setText("Send quotation");
                action.setOnClickListener(v -> marketplaceFlow.openQuoteDialog(request));
            } else if (canDispatch && (ROLE_WHOLESALER.equals(activeRole) || ROLE_ADMIN.equals(activeRole))) {
                action.setText("Dispatch order");
                action.setOnClickListener(v -> dispatchBulkRequest(request.id));
            } else {
                action.setText("View details");
                action.setOnClickListener(v -> InventoryController.showBulkRequestDetails(this, request, null));
            }
            box.addView(action, new LinearLayout.LayoutParams(-1, dp(46)));
        }

        return card;
    }

    private String priorityLabelForStatus(String status, int index) {
        if (status != null) {
            if ("accepted".equalsIgnoreCase(status)) {
                return "High priority";
            }
            if ("quoted".equalsIgnoreCase(status)) {
                return "18% margin";
            }
            if ("open".equalsIgnoreCase(status)) {
                return "New request";
            }
        }
        return index == 0 ? "High priority" : "Open";
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private LinearLayout metricRow(String label1, String value1, String footer1, String label2, String value2, String footer2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        row.addView(metricCard(label1, value1, footer1), new LinearLayout.LayoutParams(0, -2, 1f));
        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 1));
        row.addView(gap);
        row.addView(metricCard(label2, value2, footer2), new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private MaterialCardView metricCard(String label, String value, String footer) {
        MaterialCardView card = createCard(0xFF132F54, 20, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xFFD3E7F7);
        labelView.setTextSize(13);
        labelView.setTypeface(labelView.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(0xFFFFFFFF);
        valueView.setTextSize(22);
        valueView.setTypeface(valueView.getTypeface(), android.graphics.Typeface.BOLD);
        valueView.setPadding(0, dp(10), 0, 0);
        box.addView(valueView);

        TextView footerView = new TextView(this);
        footerView.setText(footer);
        footerView.setTextColor(0xFFB4C9DD);
        footerView.setTextSize(12);
        footerView.setPadding(0, dp(10), 0, 0);
        box.addView(footerView);

        return card;
    }

    private void restockLowStockItem(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            Toast.makeText(this, "Invalid product name", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> filters = new HashMap<>();
        filters.put("mine", "true");

        apiClient.api().products(filters).enqueue(new retrofit2.Callback<Page<Product>>() {
            @Override
            public void onResponse(retrofit2.Call<Page<Product>> call, retrofit2.Response<Page<Product>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(MainActivity.this, "Could not load products to restock", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<Product> products = response.body().results == null ? new ArrayList<>() : response.body().results;
                Product match = null;
                for (Product p : products) {
                    if (p != null && p.name != null && p.name.trim().equalsIgnoreCase(productName.trim())) {
                        match = p;
                        break;
                    }
                }

                if (match == null) {
                    Toast.makeText(MainActivity.this, "Product not found in your catalog: " + productName, Toast.LENGTH_SHORT).show();
                    return;
                }

                InventoryController.showRestockDialog(MainActivity.this, apiClient, match, () -> {
                    // Refresh the dashboard after restock.
                    if (ROLE_WHOLESALER.equals(activeRole)) {
                        loadWholesalerDashboard();
                    } else if (ROLE_SHOPKEEPER.equals(activeRole)) {
                        loadShopkeeperDashboard();
                    } else {
                        loadProfile();
                    }
                });
            }

            @Override
            public void onFailure(retrofit2.Call<Page<Product>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Restock failed: " + (t.getMessage() == null ? "Unknown error" : t.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void dispatchBulkRequest(long bulkRequestId) {
        apiClient.api().dispatchBulkRequest(bulkRequestId).enqueue(new retrofit2.Callback<BulkRequest>() {
            @Override
            public void onResponse(retrofit2.Call<BulkRequest> call, retrofit2.Response<BulkRequest> response) {
                if (!response.isSuccessful()) {
                    String message = "Dispatch failed";
                    try {
                        if (response.errorBody() != null) {
                            String body = response.errorBody().string().trim();
                            if (!body.isEmpty()) {
                                message = body.length() > 120 ? body.substring(0, 120) + "…" : body;
                            }
                        }
                    } catch (IOException ignored) {
                    }
                    if (message.equals("Dispatch failed")) {
                        message = "Dispatch failed (HTTP " + response.code() + ")";
                    }
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(MainActivity.this, "Dispatched. Moved to History.", Toast.LENGTH_SHORT).show();
                wholesalerOrdersMode = "history";
                loadWholesalerOrdersScreen();
            }

            @Override
            public void onFailure(retrofit2.Call<BulkRequest> call, Throwable t) {
                Toast.makeText(
                        MainActivity.this,
                        "Dispatch failed: " + (t.getMessage() == null ? "Unknown error" : t.getMessage()),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private MaterialCardView alertCard(String titleText, String subtitleText, @Nullable String restockProductName) {
        MaterialCardView card = createCard(0xFF132B49, 20, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(android.R.drawable.ic_menu_agenda);
        icon.setColorFilter(0xFFFFFFFF);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.rightMargin = dp(16);
        row.addView(icon, iconParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(17);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(0xFFB8CCE2);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), 0, 0);
        texts.addView(title);
        texts.addView(subtitle);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialButton restock = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        restock.setText("Restock");
        restock.setAllCaps(false);
        restock.setTextColor(0xFFFFFFFF);
        restock.setTextSize(13);
        restock.setCornerRadius(dp(20));
        restock.setBackgroundColor(0xFFE11D48);
        if (restockProductName == null) {
            restock.setVisibility(View.GONE);
        } else {
            restock.setOnClickListener(v -> restockLowStockItem(restockProductName));
        }
        row.addView(restock, new LinearLayout.LayoutParams(dp(96), dp(44)));

        box.addView(row);
        return card;
    }

    private MaterialCardView inventoryHeroCard() {
        MaterialCardView card = createCard(0xFF0F2E56, 22, 16);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        TextView title = new TextView(this);
        title.setText("Inventory Control");
        title.setTextColor(0xFF64D6F7);
        title.setTextSize(24);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Watch stock thresholds, fast movers, and restock timing.");
        subtitle.setTextColor(0xFFE0EAF5);
        subtitle.setTextSize(14);
        subtitle.setLineSpacing(0f, 1.1f);
        subtitle.setPadding(0, dp(10), 0, 0);
        box.addView(subtitle);

        return card;
    }

    private MaterialCardView inventoryContainerCard(List<Product> products) {
        MaterialCardView card = createCard(0xFF102A49, 22, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        List<Product> inventoryItems = new ArrayList<>();
        if (products != null) {
            inventoryItems.addAll(products);
        }

        if (inventoryItems.isEmpty()) {
            box.addView(inventoryItemCard(null, "Your catalog is empty", "Add your first product — stock starts at zero until you restock.", "", "", false));
            box.addView(spacer(18));
            box.addView(inventoryActionsRow());
            return card;
        }

        for (int i = 0; i < inventoryItems.size(); i++) {
            Product product = inventoryItems.get(i);
            box.addView(inventoryItemCard(
                    product,
                    safeText(product.name, "Inventory item"),
                    money(product.stock_quantity) + " " + safeText(product.unit, "units") + " left",
                    statusBadgeForStock(product),
                    stockHintForProduct(product),
                    product.stock_quantity != null && product.low_stock_threshold != null && product.stock_quantity.compareTo(product.low_stock_threshold) <= 0
            ));
            if (i < inventoryItems.size() - 1) {
                box.addView(spacer(12));
            }
        }

        box.addView(spacer(18));
        box.addView(inventoryActionsRow());

        return card;
    }

    private MaterialCardView inventoryItemCard(@Nullable Product product, String titleText, String subtitleText, String badgeText, String hintText, boolean lowSupply) {
        MaterialCardView card = createCard(0xFF132E50, 20, 14);
        LinearLayout box = (LinearLayout) card.getChildAt(0);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(android.R.drawable.ic_menu_myplaces);
        icon.setColorFilter(0xFFFFFFFF);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.rightMargin = dp(12);
        topRow.addView(icon, iconParams);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(17);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(0xFFD5E3F1);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), 0, 0);
        left.addView(title);
        left.addView(subtitle);
        topRow.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(topRow);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, dp(12), 0, 0);
        MaterialCardView badge = pillBadge(badgeText, lowSupply ? 0xFFFBBF24 : 0xFF67E8F9, lowSupply ? 0xFF1F2937 : 0xFF0A223D);
        actionRow.addView(badge, new LinearLayout.LayoutParams(-2, -2));

        TextView hint = new TextView(this);
        hint.setText(hintText);
        hint.setTextColor(0xFFD5E3F1);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(0, -2, 1f);
        hintParams.leftMargin = dp(10);
        hintParams.rightMargin = dp(10);
        actionRow.addView(hint, hintParams);

        MaterialButton restock = new MaterialButton(this);
        restock.setText("Restock");
        restock.setAllCaps(false);
        restock.setTextColor(0xFFFFFFFF);
        restock.setTextSize(13);
        restock.setCornerRadius(dp(18));
        restock.setBackgroundColor(0xFFE11D48);
        if (product != null) {
            restock.setOnClickListener(v -> InventoryController.showRestockDialog(this, apiClient, product, () -> {
                if (ROLE_WHOLESALER.equals(activeRole)) {
                    loadWholesalerInventoryScreen();
                } else if (ROLE_SHOPKEEPER.equals(activeRole)) {
                    loadShopkeeperInventoryScreen();
                } else if (ROLE_ADMIN.equals(activeRole)) {
                    loadAdminInventoryScreen();
                } else {
                    loadProfile();
                }
            }));
        }
        actionRow.addView(restock, new LinearLayout.LayoutParams(dp(98), dp(42)));

        box.addView(actionRow);
        return card;
    }

    private void showAddProductDialog() {
        ProductInventoryHelper.showAddProductForm(
                this,
                apiClient,
                activeRole,
                currentUser,
                user -> currentUser = user,
                this::reloadInventoryScreen
        );
    }

    private LinearLayout inventoryActionsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        MaterialButton addProduct = new MaterialButton(this);
        addProduct.setText("Add product");
        addProduct.setAllCaps(false);
        addProduct.setTextColor(0xFF0A223D);
        addProduct.setBackgroundColor(0xFF67E8F9);
        addProduct.setCornerRadius(dp(22));
        addProduct.setOnClickListener(v -> showAddProductDialog());
        row.addView(addProduct, new LinearLayout.LayoutParams(-1, dp(48)));
        row.addView(spacer(10));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton scanner = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        scanner.setText("Scanner");
        scanner.setAllCaps(false);
        scanner.setTextSize(14);
        scanner.setTextColor(0xFFF3F7FF);
        scanner.setCornerRadius(dp(22));
        scanner.setStrokeColorResource(android.R.color.holo_blue_light);
        scanner.setStrokeWidth(dp(2));
        scanner.setBackgroundColor(0xFF102A49);
        LinearLayout.LayoutParams scannerParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        tools.addView(scanner, scannerParams);

        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(14), 1));
        tools.addView(gap);

        MaterialButton manual = new MaterialButton(this);
        manual.setText("Restock");
        manual.setAllCaps(false);
        manual.setTextSize(14);
        manual.setTextColor(0xFF0A223D);
        manual.setCornerRadius(dp(22));
        manual.setBackgroundColor(0xFF67E8F9);
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(0, dp(48), 1.2f);
        manual.setOnClickListener(v -> InventoryController.showScannerDialog(
                this,
                apiClient,
                inventoryProductsCache,
                this::reloadInventoryScreen
        ));
        tools.addView(manual, manualParams);

        scanner.setOnClickListener(v -> ProductInventoryHelper.launchInventoryScanner(
                this,
                apiClient,
                activeRole,
                currentUser,
                user -> currentUser = user,
                this::reloadInventoryScreen
        ));

        row.addView(tools);
        return row;
    }

    private MaterialCardView pillBadge(String text, int backgroundColor, int textColor) {
        MaterialCardView pill = new MaterialCardView(this);
        pill.setCardBackgroundColor(backgroundColor);
        pill.setRadius(dp(18));
        pill.setCardElevation(0);
        pill.setUseCompatPadding(false);
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(textColor);
        label.setTextSize(12);
        label.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.addView(label);
        return pill;
    }

    private String statusBadgeForStock(Product product) {
        if (product == null || product.stock_quantity == null || product.low_stock_threshold == null) {
            return "Healthy buffer";
        }
        int comparison = product.stock_quantity.compareTo(product.low_stock_threshold);
        if (comparison <= 0) {
            return "Reorder soon";
        }
        if (comparison <= 1) {
            return "Restock today";
        }
        return "Healthy buffer";
    }

    private String stockHintForProduct(Product product) {
        if (product == null || product.stock_quantity == null || product.low_stock_threshold == null) {
            return "Stable";
        }
        if (product.stock_quantity.compareTo(product.low_stock_threshold) <= 0) {
            return "Low supply";
        }
        if (product.stock_quantity.compareTo(product.low_stock_threshold.multiply(new java.math.BigDecimal("2"))) <= 0) {
            return "Fast moving";
        }
        return "Balanced";
    }

    @Override
    public int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void cards(String sectionTitle, List<DemoCard> items) {
        TextView label = new TextView(this);
        label.setText(sectionTitle);
        label.setTextSize(18);
        label.setPadding(0, 18, 0, 12);
        content.addView(label);
        for (DemoCard item : items) {
            card(item.title, item.body);
        }
    }

    private static class Screen {
        final int id;
        final String title;
        final int icon;

        Screen(int id, String title, int icon) {
            this.id = id;
            this.title = title;
            this.icon = icon;
        }
    }

    private static class DemoCard {
        final String title;
        final String body;

        DemoCard(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }
}
