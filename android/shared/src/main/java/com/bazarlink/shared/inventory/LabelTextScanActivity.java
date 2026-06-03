package com.bazarlink.shared.inventory;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.bazarlink.shared.R;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scan product label text with ML Kit OCR and return parsed name, unit, price, and quantity hints.
 */
public class LabelTextScanActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView previewText;
    private ImageCapture imageCapture;
    private TextRecognizer textRecognizer;
    private ExecutorService cameraExecutor;
    private ActivityResultLauncher<String> requestCameraPermission;
    private boolean processing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_BazarLink_Scanner);
        super.onCreate(savedInstanceState);

        requestCameraPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
        );

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        cameraExecutor = Executors.newSingleThreadExecutor();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0E2236);
        int pad = dp(16);
        root.setPadding(pad, dp(40), pad, pad);

        TextView title = new TextView(this);
        title.setText("Scan product label");
        title.setTextColor(0xFF64D6F7);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Point at the product name, unit, and price on the package.");
        hint.setTextColor(0xFFE0EAF5);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(8), 0, dp(12));
        root.addView(hint);

        int camHeight = (int) (280 * getResources().getDisplayMetrics().density);
        previewView = new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new LinearLayout.LayoutParams(-1, camHeight));

        previewText = new TextView(this);
        previewText.setText("Recognized text will appear here");
        previewText.setTextColor(0xFFD5E3F1);
        previewText.setTextSize(13);
        previewText.setPadding(0, dp(12), 0, dp(8));
        root.addView(previewText);

        Button scanBtn = new Button(this);
        scanBtn.setText("Scan & fill add product");
        scanBtn.setAllCaps(false);
        scanBtn.setTextColor(0xFF0A223D);
        scanBtn.setBackgroundColor(0xFF67E8F9);
        scanBtn.setOnClickListener(v -> captureAndRecognize());
        root.addView(scanBtn, new LinearLayout.LayoutParams(-1, dp(52)));

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setAllCaps(false);
        cancel.setTextColor(0xFFFFFFFF);
        cancel.setBackgroundColor(0x55102A49);
        cancel.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, dp(48));
        cancelParams.topMargin = dp(10);
        root.addView(cancel, cancelParams);

        setContentView(root);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void captureAndRecognize() {
        if (processing || imageCapture == null) {
            Toast.makeText(this, "Camera not ready yet", Toast.LENGTH_SHORT).show();
            return;
        }
        processing = true;
        previewText.setText("Reading label…");
        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                runOnUiThread(() -> recognizeImage(image));
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                processing = false;
                runOnUiThread(() -> {
                    previewText.setText("Capture failed. Try again.");
                    Toast.makeText(LabelTextScanActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void recognizeImage(@NonNull ImageProxy image) {
        android.media.Image mediaImage = image.getImage();
        if (mediaImage == null) {
            processing = false;
            image.close();
            previewText.setText("No image captured");
            return;
        }
        InputImage input = InputImage.fromMediaImage(mediaImage, image.getImageInfo().getRotationDegrees());
        textRecognizer.process(input)
                .addOnSuccessListener(this::onTextRecognized)
                .addOnFailureListener(e -> {
                    processing = false;
                    image.close();
                    previewText.setText("Could not read text");
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                })
                .addOnCompleteListener(task -> image.close());
    }

    private void onTextRecognized(Text visionText) {
        processing = false;
        String raw = visionText.getText();
        if (raw == null || raw.trim().isEmpty()) {
            previewText.setText("No text found. Move closer to the label.");
            Toast.makeText(this, "No text detected", Toast.LENGTH_SHORT).show();
            return;
        }
        ScannedLabel label = LabelTextParser.parse(raw);
        previewText.setText(raw.length() > 200 ? raw.substring(0, 200) + "…" : raw);

        Intent result = new Intent();
        label.toIntent(result);
        setResult(RESULT_OK, result);
        finish();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                provider.unbindAll();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                );
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onDestroy() {
        if (textRecognizer != null) {
            textRecognizer.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
