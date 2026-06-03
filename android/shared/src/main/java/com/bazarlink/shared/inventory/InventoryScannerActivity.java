package com.bazarlink.shared.inventory;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bazarlink.shared.R;
import com.bazarlink.shared.api.ApiClient;
import com.bazarlink.shared.api.ApiConfig;
import com.bazarlink.shared.api.ApiMessages;
import com.bazarlink.shared.models.Page;
import com.bazarlink.shared.models.Product;
import com.bazarlink.shared.models.User;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Full-screen inventory scanner: live camera, detected items list, rescan / manual / add-all.
 */
public class InventoryScannerActivity extends AppCompatActivity {

    public static final class PendingSession {
        private static String role = "shopkeeper";
        private static User cachedUser;
        private static ProductInventoryHelper.UserSink userSink;
        private static InventoryController.RefreshListener refresh;

        private PendingSession() {
        }

        public static void prepare(
                String sellerRole,
                User user,
                ProductInventoryHelper.UserSink sink,
                InventoryController.RefreshListener listener
        ) {
            role = sellerRole == null ? "shopkeeper" : sellerRole;
            cachedUser = user;
            userSink = sink;
            refresh = listener;
        }

        static String role() {
            return role;
        }

        static User user() {
            return cachedUser;
        }

        static ProductInventoryHelper.UserSink userSink() {
            return userSink;
        }

        static InventoryController.RefreshListener refresh() {
            return refresh;
        }

        static void clear() {
            role = "shopkeeper";
            cachedUser = null;
            userSink = null;
            refresh = null;
        }
    }

    private static final class DetectedLine {
        final String barcode;
        final Product product;

        DetectedLine(String barcode, Product product) {
            this.barcode = barcode;
            this.product = product;
        }

        String displayName() {
            if (product != null && product.name != null && !product.name.isEmpty()) {
                return product.name;
            }
            return "Code " + barcode;
        }

        String unitLabel() {
            if (product != null && product.unit != null && !product.unit.isEmpty()) {
                return "1 " + product.unit;
            }
            return "1 unit";
        }
    }

    private ApiClient apiClient;
    private final List<Product> catalog = new ArrayList<>();
    private final List<DetectedLine> detected = new ArrayList<>();
    private final Set<String> seenBarcodes = new HashSet<>();

    private TextView subtitleView;
    private LinearLayout listContainer;
    private PreviewView previewView;
    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService analysisExecutor;
    private final AtomicBoolean analyzing = new AtomicBoolean(false);

    private ActivityResultLauncher<String> requestCameraPermission;
    private ActivityResultLauncher<Intent> labelScanLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_BazarLink_Scanner);
        super.onCreate(savedInstanceState);
        labelScanLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    ScannedLabel label = ScannedLabel.fromIntent(result.getData());
                    openAddProductForm(label);
                }
        );
        requestCameraPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
                    }
                }
        );
        try {
            buildScannerUi();
        } catch (Exception e) {
            Toast.makeText(this, "Scanner error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void buildScannerUi() {
        apiClient = new ApiClient(this, ApiConfig.BASE_URL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFD8EEFC, 0xFFC8D8F8, 0xFFB8C8F0}
        );
        root.setBackground(bg);

        int pad = dp(20);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            root.setPadding(pad, top + dp(12), pad, bottom + dp(12));
            return insets;
        });
        root.setPadding(pad, dp(48), pad, dp(20));

        TextView title = new TextView(this);
        title.setText("Scanner");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        subtitleView = new TextView(this);
        subtitleView.setText("0 items detected");
        subtitleView.setTextColor(0xE6FFFFFF);
        subtitleView.setTextSize(14);
        subtitleView.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitleView);

        FrameLayout cameraCard = roundedContainer(0xFF0E2236, 20);
        int camHeight = (int) (220 * getResources().getDisplayMetrics().density);
        previewView = new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        cameraCard.addView(previewView, new FrameLayout.LayoutParams(-1, camHeight));
        root.addView(cameraCard, new LinearLayout.LayoutParams(-1, camHeight + dp(8)));

        TextView listTitle = new TextView(this);
        listTitle.setText("Detected Items");
        listTitle.setTextColor(0xFFFFFFFF);
        listTitle.setTextSize(18);
        listTitle.setTypeface(Typeface.DEFAULT_BOLD);
        listTitle.setPadding(0, dp(16), 0, dp(8));
        root.addView(listTitle);

        ScrollView scroll = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollParams.topMargin = dp(4);
        root.addView(scroll, scrollParams);

        root.addView(bottomBar(), new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        loadCatalog();
        rebuildList();

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Camera unavailable: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private FrameLayout roundedContainer(int color, int cornerDp) {
        FrameLayout frame = new FrameLayout(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(cornerDp));
        frame.setBackground(shape);
        return frame;
    }

    private void loadCatalog() {
        Map<String, String> filters = new HashMap<>();
        filters.put("mine", "true");
        filters.put("page_size", "200");
        apiClient.api().products(filters).enqueue(new Callback<Page<Product>>() {
            @Override
            public void onResponse(Call<Page<Product>> call, Response<Page<Product>> response) {
                catalog.clear();
                Page<Product> page = response.body();
                if (response.isSuccessful() && page != null && page.results != null) {
                    catalog.addAll(page.results);
                }
            }

            @Override
            public void onFailure(Call<Page<Product>> call, Throwable t) {
                Toast.makeText(
                        InventoryScannerActivity.this,
                        ApiMessages.fromFailure(t, "Could not load products"),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private LinearLayout bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, dp(12), 0, 0);

        Button rescan = actionButton("Rescan", 0xFF102A49, 0xFFFFFFFF);
        rescan.setOnClickListener(v -> clearDetections());
        bar.addView(rescan, buttonParams(1f));

        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 1));
        bar.addView(gap);

        Button manual = actionButton("Add product", 0xFF67E8F9, 0xFF0A223D);
        manual.setOnClickListener(v -> labelScanLauncher.launch(new Intent(this, LabelTextScanActivity.class)));
        bar.addView(manual, buttonParams(1f));

        View gap2 = new View(this);
        gap2.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 1));
        bar.addView(gap2);

        Button addAll = actionButton("Add All", 0xFFE53935, 0xFFFFFFFF);
        addAll.setOnClickListener(v -> restockAllMatched());
        bar.addView(addAll, buttonParams(1.1f));

        return bar;
    }

    private Button actionButton(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setBackgroundColor(bg);
        b.setPadding(dp(8), dp(14), dp(8), dp(14));
        return b;
    }

    private LinearLayout.LayoutParams buttonParams(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, weight);
        p.height = dp(52);
        return p;
    }

    private void clearDetections() {
        detected.clear();
        seenBarcodes.clear();
        rebuildList();
    }

    private void rebuildList() {
        listContainer.removeAllViews();
        subtitleView.setText(detected.size() + (detected.size() == 1 ? " item detected" : " items detected"));
        if (detected.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Scan product barcodes or IDs. Each code is listed once per session.");
            empty.setTextColor(0xCCFFFFFF);
            empty.setTextSize(14);
            empty.setPadding(0, dp(8), 0, dp(8));
            listContainer.addView(empty);
            return;
        }
        for (int i = 0; i < detected.size(); i++) {
            listContainer.addView(itemCard(detected.get(i)));
            if (i < detected.size() - 1) {
                listContainer.addView(spacer(dp(10)));
            }
        }
    }

    private View itemCard(DetectedLine line) {
        FrameLayout card = roundedContainer(0xFF1A3A5C, 16);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = new TextView(this);
        name.setText(line.displayName());
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(name);

        TextView units = new TextView(this);
        units.setText(line.unitLabel());
        units.setTextColor(0xFFD5E3F1);
        units.setTextSize(13);
        units.setPadding(0, dp(4), 0, 0);
        info.addView(units);

        TextView badge = new TextView(this);
        badge.setText(line.product != null ? "Scanned" : "Unmatched");
        badge.setTextColor(0xFF64D6F7);
        badge.setTextSize(12);
        badge.setPadding(0, dp(6), 0, 0);
        info.addView(badge);

        row.addView(info);

        Button restock = new Button(this);
        restock.setText("Restock");
        restock.setAllCaps(false);
        restock.setTextColor(0xFFFFFFFF);
        restock.setBackgroundColor(0xFFE53935);
        restock.setOnClickListener(v -> {
            if (line.product != null) {
                InventoryController.showRestockDialog(this, apiClient, line.product, pendingRefresh());
            } else {
                InventoryController.showScannerDialog(this, apiClient, catalog, pendingRefresh());
            }
        });
        row.addView(restock, new LinearLayout.LayoutParams(dp(100), dp(44)));

        card.addView(row);
        return card;
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, h));
        return v;
    }

    private void openAddProductForm(ScannedLabel label) {
        ProductInventoryHelper.showAddProductForm(
                this,
                apiClient,
                PendingSession.role(),
                PendingSession.user(),
                PendingSession.userSink(),
                pendingRefresh(),
                label
        );
    }

    private InventoryController.RefreshListener pendingRefresh() {
        return () -> {
            loadCatalog();
            InventoryController.RefreshListener outer = PendingSession.refresh();
            if (outer != null) {
                outer.onInventoryChanged();
            }
        };
    }

    private void restockAllMatched() {
        List<Product> matched = new ArrayList<>();
        for (DetectedLine line : detected) {
            if (line.product != null) {
                matched.add(line.product);
            }
        }
        if (matched.isEmpty()) {
            Toast.makeText(this, "No matched products to restock", Toast.LENGTH_SHORT).show();
            return;
        }
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Add stock to all");
        builder.setMessage("Apply the same quantity to " + matched.size() + " matched item(s).");
        android.widget.EditText qty = new android.widget.EditText(this);
        qty.setHint("Quantity to add");
        qty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        qty.setText("10");
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(pad, pad, pad, pad);
        box.addView(qty);
        builder.setView(box);
        builder.setPositiveButton("Apply", (d, w) -> {
            try {
                BigDecimal add = new BigDecimal(qty.getText().toString().trim());
                if (add.compareTo(BigDecimal.ZERO) <= 0) {
                    Toast.makeText(this, "Enter a positive amount", Toast.LENGTH_SHORT).show();
                    return;
                }
                applyBulkRestock(matched, add, 0);
            } catch (Exception e) {
                Toast.makeText(this, "Invalid quantity", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void applyBulkRestock(List<Product> items, BigDecimal add, int index) {
        if (index >= items.size()) {
            Toast.makeText(this, "Stock updated for " + items.size() + " products", Toast.LENGTH_SHORT).show();
            pendingRefresh().onInventoryChanged();
            return;
        }
        Product product = items.get(index);
        BigDecimal current = product.stock_quantity == null ? BigDecimal.ZERO : product.stock_quantity;
        BigDecimal updated = current.add(add);
        Map<String, String> body = new HashMap<>();
        body.put("stock_quantity", updated.toPlainString());
        apiClient.api().updateProduct(product.id, body).enqueue(new Callback<Product>() {
            @Override
            public void onResponse(Call<Product> call, Response<Product> response) {
                if (response.isSuccessful()) {
                    product.stock_quantity = updated;
                }
                applyBulkRestock(items, add, index + 1);
            }

            @Override
            public void onFailure(Call<Product> call, Throwable t) {
                applyBulkRestock(items, add, index + 1);
            }
        });
    }

    private void onBarcodeDetected(String raw) {
        String code = raw.trim();
        if (code.isEmpty()) {
            return;
        }
        String key = code.toLowerCase(Locale.US);
        if (!seenBarcodes.add(key)) {
            return;
        }
        Product match = InventoryController.findProductInList(catalog, code);
        detected.add(0, new DetectedLine(code, match));
        runOnUiThread(this::rebuildList);
    }

    private void startCamera() {
        if (previewView == null) {
            return;
        }
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (cameraProvider == null) {
            return;
        }
        cameraProvider.unbindAll();
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        if (analysisExecutor == null) {
            analysisExecutor = Executors.newSingleThreadExecutor();
        }
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(analysisExecutor, this::analyzeFrame);
        try {
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
        } catch (Exception e) {
            Toast.makeText(this, "Camera bind failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        if (!analyzing.compareAndSet(false, true)) {
            imageProxy.close();
            return;
        }
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            analyzing.set(false);
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String raw = barcode.getRawValue();
                        if (raw != null && !raw.trim().isEmpty()) {
                            onBarcodeDetected(raw.trim());
                            break;
                        }
                    }
                })
                .addOnCompleteListener(task -> {
                    analyzing.set(false);
                    imageProxy.close();
                });
    }

    @Override
    protected void onDestroy() {
        PendingSession.clear();
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
