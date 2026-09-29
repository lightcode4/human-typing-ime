package com.example.humantypingime;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class VaultActivity extends AppCompatActivity {

    private VaultStore store;
    private List<VaultStore.VaultItem> items = new ArrayList<>();
    private ListView lv;
    private TextView tvEmpty;
    private boolean unlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vault);

        store = new VaultStore(this);
        lv = findViewById(R.id.lv_vault);
        tvEmpty = findViewById(R.id.tv_vault_empty);
        Button clear = findViewById(R.id.btn_vault_clear);

        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Clear vault?")
                .setMessage("This permanently deletes all vault items.")
                .setPositiveButton("Delete", (d, w) -> {
                    store.clear();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show());

        lv.setOnItemClickListener((parent, view, position, id) -> revealItem(items.get(position)));

        promptUnlock();
    }

    private void promptUnlock() {
        BiometricManager bm = BiometricManager.from(this);
        int can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometrics enrolled — fall back to device credential only, or refuse.
            Toast.makeText(this, "Biometrics unavailable. Enroll a fingerprint or face.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Executor exec = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, exec,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        unlocked = true;
                        refresh();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        finish();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vault")
                .setSubtitle("Authenticate to view saved sensitive items")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        prompt.authenticate(info);
    }

    private static String prettyLabel(String kind) {
        switch (kind) {
            case "CARD":     return "💳"; // 💳
            case "OTP":      return "🔢"; // 🔢
            case "API_KEY":  return "🔑"; // 🔑
            case "JWT":      return "🎫"; // 🎫
            case "PASSWORD": return "🔑"; // 🔑
            default:         return "🔒"; // 🔒
        }
    }

    private void refresh() {
        if (!unlocked) return;
        items = store.getAll();

        if (items.isEmpty()) {
            tvEmpty.setVisibility(TextView.VISIBLE);
            lv.setAdapter(null);
            return;
        }
        tvEmpty.setVisibility(TextView.GONE);

        List<String> display = new ArrayList<>();
        for (VaultStore.VaultItem it : items) {
            display.add(prettyLabel(it.kind) + " " + it.preview);
        }
        lv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, display));
    }

    private void revealItem(VaultStore.VaultItem it) {
        Executor exec = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, exec,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        String plain = store.reveal(it);
                        if (plain == null) {
                            Toast.makeText(VaultActivity.this, "Decryption failed", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        new AlertDialog.Builder(VaultActivity.this)
                                .setTitle(it.kind)
                                .setMessage(plain)
                                .setPositiveButton("Type this", (d, w) -> {
                                    // Hand back to the IME through a broadcast is complex;
                                    // simplest UX: copy to clipboard so the user pastes.
                                    android.content.ClipboardManager cm =
                                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    if (cm != null) {
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("vault", plain));
                                    }
                                    Toast.makeText(VaultActivity.this, "Copied. Clear after use.", Toast.LENGTH_LONG).show();
                                })
                                .setNeutralButton("Delete", (d, w) -> {
                                    store.delete(it);
                                    refresh();
                                })
                                .setNegativeButton("Close", null)
                                .show();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Reveal Item")
                .setSubtitle("Authenticate to decrypt")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        prompt.authenticate(info);
    }
}
