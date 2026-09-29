package com.example.humantypingime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.rewriting.Rewriter;
import com.google.mlkit.genai.rewriting.RewriterOptions;
import com.google.mlkit.genai.rewriting.Rewriting;
import com.google.mlkit.genai.rewriting.RewritingRequest;
import com.google.mlkit.genai.rewriting.RewritingResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A separate, review-and-copy-only workspace for on-device rewriting. */
public class SafeRewriteActivity extends AppCompatActivity {
    private static final String[] STYLES = {
            "Professional", "Friendly", "Shorter", "More detailed", "Rephrase"
    };

    private EditText input;
    private TextView status;
    private TextView output;
    private Button rewrite;
    private Button copy;
    private Spinner style;
    private ExecutorService executor;
    private Rewriter availabilityRewriter;
    private boolean available;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(ThemeManager.getMode(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_rewrite);

        input = findViewById(R.id.et_rewrite_input);
        status = findViewById(R.id.tv_rewrite_status);
        output = findViewById(R.id.tv_rewrite_output);
        rewrite = findViewById(R.id.btn_rewrite_draft);
        copy = findViewById(R.id.btn_copy_rewrite);
        style = findViewById(R.id.sp_rewrite_tone);
        style.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, STYLES));
        rewrite.setEnabled(false);

        rewrite.setOnClickListener(v -> createSuggestion());
        copy.setOnClickListener(v -> copySuggestion());
        executor = Executors.newSingleThreadExecutor();
        checkAvailability();
    }

    private void checkAvailability() {
        executor.execute(() -> {
            try {
                availabilityRewriter = Rewriting.getClient(optionsFor(0));
                int featureStatus = availabilityRewriter.checkFeatureStatus().get();
                if (featureStatus == FeatureStatus.AVAILABLE) {
                    markAvailable();
                } else if (featureStatus == FeatureStatus.DOWNLOADABLE) {
                    runOnUiThread(() -> status.setText("Downloading the on-device rewrite model…"));
                    availabilityRewriter.downloadFeature(new DownloadCallback() {
                        @Override public void onDownloadStarted(long bytesToDownload) { }
                        @Override public void onDownloadProgress(long totalBytesDownloaded) { }
                        @Override public void onDownloadCompleted() { markAvailable(); }
                        @Override public void onDownloadFailed(GenAiException e) {
                            showAvailabilityError(e);
                        }
                    });
                } else if (featureStatus == FeatureStatus.DOWNLOADING) {
                    runOnUiThread(() -> status.setText("The on-device rewrite model is still downloading. Try again shortly."));
                } else {
                    showAvailabilityError(null);
                }
            } catch (Exception e) {
                showAvailabilityError(e);
            }
        });
    }

    private void markAvailable() {
        available = true;
        runOnUiThread(() -> {
            status.setText("On-device rewriting is ready. Your draft stays on this device.");
            rewrite.setEnabled(true);
        });
    }

    private void showAvailabilityError(Throwable error) {
        available = false;
        String detail = error == null ? "This device or its AICore model does not support rewriting." :
                (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        runOnUiThread(() -> status.setText("Rewrite unavailable: " + detail));
    }

    private void createSuggestion() {
        String draft = input.getText().toString().trim();
        if (draft.isEmpty()) {
            input.setError("Enter a draft first.");
            return;
        }
        if (draft.length() > 1_000) {
            input.setError("Use a shorter draft (up to about 1,000 characters).");
            return;
        }
        if (!available) {
            Toast.makeText(this, "On-device rewriting is unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }
        rewrite.setEnabled(false);
        copy.setEnabled(false);
        status.setText("Creating an on-device suggestion…");
        int chosenStyle = style.getSelectedItemPosition();
        executor.execute(() -> {
            Rewriter rewriter = null;
            try {
                rewriter = Rewriting.getClient(optionsFor(chosenStyle));
                RewritingResult result = rewriter.runInference(
                        RewritingRequest.builder(draft).build()).get();
                String suggestion = result.getResults().isEmpty() ? ""
                        : result.getResults().get(0).getText();
                runOnUiThread(() -> showSuggestion(suggestion));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    status.setText("Rewrite failed: " + detail);
                    rewrite.setEnabled(available);
                });
            } finally {
                if (rewriter != null) rewriter.close();
            }
        });
    }

    private RewriterOptions optionsFor(int position) {
        return RewriterOptions.builder(this)
                .setOutputType(outputTypeFor(position))
                .setLanguage(RewriterOptions.Language.ENGLISH)
                .build();
    }

    private int outputTypeFor(int position) {
        switch (position) {
            case 1: return RewriterOptions.OutputType.FRIENDLY;
            case 2: return RewriterOptions.OutputType.SHORTEN;
            case 3: return RewriterOptions.OutputType.ELABORATE;
            case 4: return RewriterOptions.OutputType.REPHRASE;
            default: return RewriterOptions.OutputType.PROFESSIONAL;
        }
    }

    private void showSuggestion(String suggestion) {
        if (suggestion == null || suggestion.trim().isEmpty()) {
            status.setText("No suggestion was returned. Try another style.");
        } else {
            output.setText(suggestion);
            status.setText("Suggestion ready — review it before copying.");
            copy.setEnabled(true);
        }
        rewrite.setEnabled(available);
    }

    private void copySuggestion() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Rewritten draft", output.getText()));
            Toast.makeText(this, "Suggestion copied.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (availabilityRewriter != null) availabilityRewriter.close();
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }
}
