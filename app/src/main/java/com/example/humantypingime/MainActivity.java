/*
 * MainActivity.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: UI — live setup checklist (keyboard enabled / mic granted /
 *             vault & clipboard ready) with deep-link actions; status line
 *             becomes "Setup progress: n/3".
 */

package com.example.humantypingime;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvCheckIme;
    private TextView tvCheckMic;
    private TextView tvCheckTools;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvCheckIme = findViewById(R.id.tv_check_ime);
        tvCheckMic = findViewById(R.id.tv_check_mic);
        tvCheckTools = findViewById(R.id.tv_check_tools);

        findViewById(R.id.card_ime).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        findViewById(R.id.card_mic).setOnClickListener(v ->
                startActivity(new Intent(this, PermissionActivity.class)));
        findViewById(R.id.card_vault).setOnClickListener(v ->
                startActivity(new Intent(this, VaultActivity.class)));
        findViewById(R.id.card_clipboard).setOnClickListener(v ->
                startActivity(new Intent(this, ClipboardManagerActivity.class)));
        findViewById(R.id.card_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.card_about).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        findViewById(R.id.btn_pick_ime).setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });

        // Checklist rows deep-link to the matching setup screen.
        tvCheckIme.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        tvCheckMic.setOnClickListener(v ->
                startActivity(new Intent(this, PermissionActivity.class)));
        tvCheckTools.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshChecklist();
    }

    private void refreshChecklist() {
        boolean micGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean imeEnabled = isImeEnabled();
        // Empty history and an empty vault are valid; setup never requires saving a secret.
        boolean toolsReady = true;

        setCheck(tvCheckIme, imeEnabled,
                "Keyboard enabled — tap to change",
                "Keyboard not enabled — tap to enable");
        setCheck(tvCheckMic, micGranted,
                "Microphone granted",
                "Microphone not granted — tap to allow");
        setCheck(tvCheckTools, toolsReady,
                "Vault & clipboard ready",
                "Set up vault & clipboard — tap to open settings");

        int done = (imeEnabled ? 1 : 0) + (micGranted ? 1 : 0) + (toolsReady ? 1 : 0);
        if (done == 3) {
            tvStatus.setText("All set — ready to type");
        } else {
            tvStatus.setText("Setup progress: " + done + "/3");
        }
    }

    private void setCheck(TextView view, boolean ok, String okText, String todoText) {
        view.setText((ok ? "✓ " : "○ ") + (ok ? okText : todoText));
        view.setTextColor(ContextCompat.getColor(this,
                ok ? R.color.success : R.color.text_primary));
    }

    /** True when this package has at least one enabled input method. */
    private boolean isImeEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        List<InputMethodInfo> enabled = imm.getEnabledInputMethodList();
        for (InputMethodInfo info : enabled) {
            if (getPackageName().equals(info.getPackageName())) return true;
        }
        return false;
    }
}
//（注：内容由AI生成）
