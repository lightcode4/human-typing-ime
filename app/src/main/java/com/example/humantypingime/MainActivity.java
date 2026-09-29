package com.example.humantypingime;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean granted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        ((TextView) findViewById(R.id.tv_status)).setText(
                granted ? "Microphone ready" : "Microphone not granted");
        ((TextView) findViewById(R.id.tv_mic_sub)).setText(
                granted ? "Granted" : "Required for voice input");
    }
}
