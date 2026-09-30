/*
 * VaultActivity.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: FIX(i) — keep the unlock and reveal BiometricPrompts as fields and cancel them
 *             deterministically in onDestroy, guarded against double release; add an
 *             onAuthenticationError to the reveal prompt so its reference is cleared on cancel.
 * 2026-09-30: UI — window is FLAG_SECURE (no screenshots/recents preview); revealed values
 *             are masked by default with an explicit Reveal/Hide toggle; copying a value
 *             auto-clears the clipboard after 30 s; "Type this" hands the value to the IME
 *             (clipboard + broadcast) with honest feedback.
 */

package com.example.humantypingime;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
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

    // Live biometric prompts; cancelled deterministically in onDestroy.
    private BiometricPrompt unlockPrompt;
    private BiometricPrompt revealPrompt;
    private boolean promptsReleased;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vault);

        // Sensitive content: block screenshots, screen recordings, and recents previews.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

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
            Toast.makeText(this, "Biometrics unavailable. Enroll a fingerprint or face.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Executor exec = ContextCompat.getMainExecutor(this);
        unlockPrompt = new BiometricPrompt(this, exec,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        unlockPrompt = null;
                        unlocked = true;
                        refresh();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        unlockPrompt = null;
                        finish();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vault")
                .setSubtitle("Authenticate to view saved sensitive items")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        unlockPrompt.authenticate(info);
    }

    private static String prettyLabel(String kind) {
        switch (kind) {
            case "CARD":     return "💳";
            case "OTP":      return "🔢";
            case "API_KEY":  return "🔑";
            case "JWT":      return "🎫";
            case "PASSWORD": return "🔑";
            default:         return "🔒";
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
        revealPrompt = new BiometricPrompt(this, exec,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        revealPrompt = null;
                        String plain = store.reveal(it);
                        if (plain == null) {
                            Toast.makeText(VaultActivity.this, "Decryption failed", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showRevealDialog(it.kind, plain, it);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        revealPrompt = null;
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Reveal Item")
                .setSubtitle("Authenticate to decrypt")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        revealPrompt.authenticate(info);
    }

    /** Masked-by-default reveal dialog: no shoulder-surfing, no clipboard leftovers. */
    private void showRevealDialog(final String kind, final String plain, final VaultStore.VaultItem item) {
        final boolean[] revealed = {false};

        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);

        final TextView value = new TextView(this);
        value.setTextSize(16f);
        value.setTypeface(Typeface.MONOSPACE);
        value.setText(mask(plain));

        final Button toggle = new Button(this);
        toggle.setText("Reveal");
        toggle.setOnClickListener(v -> {
            revealed[0] = !revealed[0];
            value.setText(revealed[0] ? plain : mask(plain));
            toggle.setText(revealed[0] ? "Hide" : "Reveal");
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, pad, 0, pad);

        Button typeBtn = new Button(this);
        typeBtn.setText("Type this");
        Button copyBtn = new Button(this);
        copyBtn.setText("Copy");
        Button delBtn = new Button(this);
        delBtn.setText("Delete");

        typeBtn.setOnClickListener(v -> {
            boolean queued = ClipboardActions.requestTyping(this, "Vault item", plain, true, true);
            Toast.makeText(this, queued
                    ? "Open a text field in another app within 30 s to type this item."
                    : "Could not copy vault item.", Toast.LENGTH_LONG).show();
        });
        copyBtn.setOnClickListener(v -> {
            boolean copied = ClipboardActions.copy(this, "Vault item", plain, true, true);
            Toast.makeText(this, copied ? "Copied — cleanup scheduled in 30 s."
                    : "Could not copy vault item.", Toast.LENGTH_LONG).show();
        });

        actions.addView(typeBtn);
        actions.addView(copyBtn);
        actions.addView(delBtn);
        box.addView(value);
        box.addView(toggle);
        box.addView(actions);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(kind)
                .setView(box)
                .setNegativeButton("Close", null)
                .create();
        delBtn.setOnClickListener(v -> {
            store.delete(item);
            refresh();
            dialog.dismiss();
        });
        dialog.setOnDismissListener(d -> {
            revealed[0] = false;
            value.setText(mask(plain));
        });
        dialog.show();
    }

    private static String mask(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        int n = Math.min(plain.length(), 16);
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append('•');
        return sb.toString();
    }

    private void releasePrompts() {
        if (promptsReleased) return;
        promptsReleased = true;
        if (unlockPrompt != null) { unlockPrompt.cancelAuthentication(); unlockPrompt = null; }
        if (revealPrompt != null) { revealPrompt.cancelAuthentication(); revealPrompt = null; }
    }

    @Override
    protected void onDestroy() {
        releasePrompts();
        super.onDestroy();
    }
}
//（注：内容由AI生成）
