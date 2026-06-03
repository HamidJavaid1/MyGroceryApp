package com.bazarlink.shared.inventory;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bazarlink.shared.R;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Camera barcode scanner powered by ML Kit. */
public class BarcodeScannerActivity extends AppCompatActivity {

    public static final String EXTRA_BARCODE = "scanned_barcode";

    private PreviewView previewView;
    private final AtomicBoolean delivered = new AtomicBoolean(false);
    private final AtomicBoolean analyzing = new AtomicBoolean(false);
    private ExecutorService analysisExecutor;
    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;

    private final ActivityResultLauncher<String> requestCameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required to scan barcodes", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_BazarLink_Scanner);
        super.onCreate(savedInstanceState);
        setTitle("Scan barcode");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0E2236);

        previewView = new PreviewView(this);
        previewView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        root.addView(previewView);

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(-1, -2);
        overlayParams.gravity = Gravity.TOP;
        int top = dp(48);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int status = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            overlay.setPadding(dp(20), status + dp(12), dp(20), dp(12));
            return insets;
        });
        overlay.setPadding(dp(20), top, dp(20), dp(12));

        TextView title = new TextView(this);
        title.setText("Point at product barcode");
        title.setTextColor(0xFF64D6F7);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        overlay.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Product ID barcodes work best. Hold steady until it beeps.");
        hint.setTextColor(0xFFE0EAF5);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(8), 0, 0);
        overlay.addView(hint);

        root.addView(overlay, overlayParams);

        TextView cancel = new TextView(this);
        cancel.setText("Cancel");
        cancel.setTextColor(0xFFFFFFFF);
        cancel.setTextSize(16);
        cancel.setPadding(dp(24), dp(16), dp(24), dp(16));
        cancel.setBackgroundColor(0xCC102A49);
        cancel.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(-1, -2);
        cancelParams.gravity = Gravity.BOTTOM;
        cancelParams.bottomMargin = dp(32);
        cancelParams.leftMargin = dp(24);
        cancelParams.rightMargin = dp(24);
        root.addView(cancel, cancelParams);

        setContentView(root);

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_ALL_FORMATS
                )
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Could not start camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
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
            cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
            );
        } catch (Exception e) {
            Toast.makeText(this, "Camera bind failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        if (delivered.get() || !analyzing.compareAndSet(false, true)) {
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
                    if (!delivered.get()) {
                        for (Barcode barcode : barcodes) {
                            String raw = barcode.getRawValue();
                            if (raw != null && !raw.trim().isEmpty()) {
                                deliverResult(raw.trim());
                                break;
                            }
                        }
                    }
                })
                .addOnCompleteListener(task -> {
                    analyzing.set(false);
                    imageProxy.close();
                });
    }

    private void deliverResult(String code) {
        if (!delivered.compareAndSet(false, true)) {
            return;
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_BARCODE, code);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdownNow();
            analysisExecutor = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
