package com.example.humantypingime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/** A separate review workspace. Typing in another app needs an explicit action. */
public class SafeRewriteActivity extends AppCompatActivity {
    private static final String[] STYLES = {
            "Professional", "Friendly", "Shorter", "More detailed", "Rephrase"
    };
    private EditText input;
    private TextView status, output, charCount;
    private Button rewrite, copy, typeThis, retry;
    private Spinner style;
    private AiRewriteManager manager;
    private boolean destroyed, busy;
    private String suggestion = "";

    @Override protected void onCreate(Bundle state) {
        ThemeManager.apply(ThemeManager.getMode(this));
        super.onCreate(state);
        setContentView(R.layout.activity_safe_rewrite);
        input = findViewById(R.id.et_rewrite_input);
        status = findViewById(R.id.tv_rewrite_status);
        output = findViewById(R.id.tv_rewrite_output);
        charCount = findViewById(R.id.tv_char_count);
        rewrite = findViewById(R.id.btn_rewrite_draft);
        copy = findViewById(R.id.btn_copy_rewrite);
        typeThis = findViewById(R.id.btn_type_rewrite);
        retry = findViewById(R.id.btn_retry_model);
        style = findViewById(R.id.sp_rewrite_tone);
        style.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, STYLES));
        manager = new AiRewriteManager(this);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                charCount.setText(s.length() + " / " + AiRewriteManager.MAX_REWRITE_CHARS);
                input.setError(s.length() > AiRewriteManager.MAX_REWRITE_CHARS
                        ? "Use a draft up to " + AiRewriteManager.MAX_REWRITE_CHARS + " characters."
                        : null);
            }
        });
        rewrite.setOnClickListener(v -> createSuggestion());
        retry.setOnClickListener(v -> checkAvailability());
        copy.setOnClickListener(v -> copySuggestion());
        typeThis.setOnClickListener(v -> typeSuggestion());
        checkAvailability();
    }

    private void updateControls() {
        rewrite.setEnabled(!busy && manager.isAvailable());
        retry.setEnabled(!busy);
        style.setEnabled(!busy);
        input.setEnabled(!busy);
        copy.setEnabled(!busy && !suggestion.isEmpty());
        typeThis.setEnabled(!busy && !suggestion.isEmpty());
    }

    private void checkAvailability() {
        busy = true;
        status.setText("Checking on-device availability…");
        updateControls();
        manager.checkAvailability(available -> {
            if (destroyed) return;
            busy = false;
            updateControls();
        }, message -> { if (!destroyed) status.setText(message); });
    }

    private void createSuggestion() {
        String draft = input.getText().toString().trim();
        if (draft.isEmpty() || draft.length() > AiRewriteManager.MAX_REWRITE_CHARS) {
            input.setError("Enter a draft up to " + AiRewriteManager.MAX_REWRITE_CHARS + " characters.");
            return;
        }
        busy = true;
        suggestion = "";
        output.setText("Your rewritten draft will appear here.");
        status.setText("Creating an on-device suggestion…");
        updateControls();
        AiRewriteManager.Tone tone = AiRewriteManager.Tone.values()[style.getSelectedItemPosition()];
        manager.rewrite(draft, tone, new AiRewriteManager.Callback() {
            @Override public void onResult(String text) {
                if (destroyed) return;
                busy = false;
                suggestion = text;
                output.setText(text);
                status.setText("Suggestion ready — review it before copying or typing.");
                updateControls();
            }
            @Override public void onError(String message) {
                if (destroyed) return;
                busy = false;
                status.setText(message);
                updateControls();
            }
        });
    }

    private void typeSuggestion() {
        if (suggestion.isEmpty()) return;
        boolean queued = ClipboardActions.requestTyping(this, "Rewritten draft", suggestion, false, true);
        Toast.makeText(this, queued
                ? "Open a text field in another app within 30 s to type this suggestion."
                : "Could not copy the suggestion.", Toast.LENGTH_LONG).show();
    }

    private void copySuggestion() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && !suggestion.isEmpty()) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Rewritten draft", suggestion));
            Toast.makeText(this, "Suggestion copied.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (manager != null) manager.shutdown();
        super.onDestroy();
    }
}
