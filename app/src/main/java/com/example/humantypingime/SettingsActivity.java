package com.example.humantypingime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    private Spinner spProfile;
    private SeekBar sbMin, sbMax, sbPunct, sbTypo;
    private TextView tvMin, tvMax, tvPunct, tvTypo;
    private ImageButton btnThemeToggle;
    private TextView tvThemeMode;
    private TextView behavioralStatus;

    // Common app packages for quick configuration
    private static final String[] PACKAGE_LABELS = {
            "Default (all apps)",
            "Chrome / WebViews",
            "WhatsApp",
            "Gmail",
            "Slack / Discord",
            "Code Editor (Acode / Termux)",
            "Samsung Internet"
    };

    private static final String[] PACKAGE_NAMES = {
            "", // empty string = default fallback
            "com.android.chrome",
            "com.whatsapp",
            "com.google.android.gm",
            "com.discord",
            "com.foxdebug.acode",
            "com.sec.android.app.sbrowser"
    };

    private String currentPackage = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(ThemeManager.getMode(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(HumanTypingIME.PREFS_NAME, MODE_PRIVATE);

        spProfile = findViewById(R.id.sp_profile);
        sbMin = findViewById(R.id.sb_min);
        sbMax = findViewById(R.id.sb_max);
        sbPunct = findViewById(R.id.sb_punct);
        sbTypo = findViewById(R.id.sb_typo);
        tvMin = findViewById(R.id.tv_min);
        tvMax = findViewById(R.id.tv_max);
        tvPunct = findViewById(R.id.tv_punct);
        tvTypo = findViewById(R.id.tv_typo);
        btnThemeToggle = findViewById(R.id.btn_theme_toggle);
        tvThemeMode = findViewById(R.id.tv_theme_mode);
        Button btnSave = findViewById(R.id.btn_save);

        if (btnThemeToggle != null) {
            updateThemeToggle();
            btnThemeToggle.setOnClickListener(v -> {
                int newMode = isDarkTheme() ? ThemeManager.MODE_LIGHT : ThemeManager.MODE_DARK;
                selectTheme(newMode);
                updateThemeToggle();
            });
        }

        sbMin.setMax(300);
        sbMax.setMax(600);
        sbPunct.setMax(600);
        sbTypo.setMax(100);

        // Populate profile spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, PACKAGE_LABELS);
        spProfile.setAdapter(adapter);

        spProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentPackage = PACKAGE_NAMES[position];
                loadProfile(currentPackage);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        sbMin.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvMin.setText("Min delay: " + progress + " ms");
            }
        });

        sbMax.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvMax.setText("Max delay: " + progress + " ms");
            }
        });

        sbPunct.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvPunct.setText("Punctuation pause: " + progress + " ms");
            }
        });

        sbTypo.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvTypo.setText("Typo chance: " + progress + "%");
            }
        });

        btnSave.setOnClickListener(v -> {
            saveProfile();
            String label = PACKAGE_LABELS[spProfile.getSelectedItemPosition()];
            Toast.makeText(this, "Saved profile: " + label, Toast.LENGTH_SHORT).show();
        });

        Switch swProof = findViewById(R.id.sw_proof);
        if (swProof != null) {
            swProof.setChecked(prefs.getBoolean(HumanTypingIME.KEY_PROOF_ENABLED, true));
            swProof.setOnCheckedChangeListener((btn, checked) -> {
                prefs.edit().putBoolean(HumanTypingIME.KEY_PROOF_ENABLED, checked).apply();
            });
        }

        Button btnExportPubkey = findViewById(R.id.btn_export_pubkey);
        if (btnExportPubkey != null) {
            btnExportPubkey.setOnClickListener(v -> {
                try {
                    String pubKeyB64 = SignatureCrypto.exportPublicKeyBase64();
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("Human Typing Public Key", pubKeyB64));
                    }
                    Toast.makeText(this, "Public key copied to clipboard", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        Switch swFocus = findViewById(R.id.sw_focus);
        Switch swTts = findViewById(R.id.sw_tts);
        Switch swSlow = findViewById(R.id.sw_slow);
        Switch swBounce = findViewById(R.id.sw_bounce);
        swFocus.setChecked(prefs.getBoolean(HumanTypingIME.KEY_FOCUS_MODE, false));
        swTts.setChecked(prefs.getBoolean(HumanTypingIME.KEY_TTS_ON_TAP, false));
        swSlow.setChecked(prefs.getBoolean(HumanTypingIME.KEY_SLOW_KEYS, false));
        swBounce.setChecked(prefs.getBoolean(HumanTypingIME.KEY_BOUNCE_KEYS, false));
        swFocus.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(HumanTypingIME.KEY_FOCUS_MODE, checked).apply();
            sendBroadcast(new android.content.Intent(ThemeManager.ACTION_THEME_CHANGED)
                    .setPackage(getPackageName()));
        });
        swTts.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(HumanTypingIME.KEY_TTS_ON_TAP, checked).apply());
        swSlow.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(HumanTypingIME.KEY_SLOW_KEYS, checked).apply());
        swBounce.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(HumanTypingIME.KEY_BOUNCE_KEYS, checked).apply());

        behavioralStatus = findViewById(R.id.tv_behavioral_status);
        findViewById(R.id.btn_enroll_behavioral).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, BehavioralEnrollActivity.class)));
        findViewById(R.id.btn_clear_behavioral).setOnClickListener(v -> {
            BehavioralProfile profile = new BehavioralProfileStore(this).load();
            if (!profile.isValid()) {
                Toast.makeText(this, "There is no rhythm profile to delete.", Toast.LENGTH_SHORT).show();
                return;
            }
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Delete rhythm profile?")
                    .setMessage("This removes the locally stored aggregate timing statistics. The typed passage was never stored.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        new BehavioralProfileStore(this).clear();
                        updateBehavioralStatus();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        findViewById(R.id.btn_safe_rewrite).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, SafeRewriteActivity.class)));

        Switch swRewrite = findViewById(R.id.sw_rewrite);
        TextView tvAiStatus = findViewById(R.id.tv_ai_status);
        if (swRewrite != null) {
            swRewrite.setChecked(prefs.getBoolean(HumanTypingIME.KEY_AI_REWRITE, true));
            swRewrite.setOnCheckedChangeListener((button, checked) ->
                    prefs.edit().putBoolean(HumanTypingIME.KEY_AI_REWRITE, checked).apply());
            AiRewriteManager ai = new AiRewriteManager(this);
            ai.checkAvailability(available -> {
                tvAiStatus.setText(available
                        ? "Available on this device (Gemini Nano, on-device)."
                        : "Not available on this device — needs AICore (Pixel 8+, Galaxy S24+ and newer flagships).");
                if (!available) {
                    swRewrite.setChecked(false);
                    swRewrite.setEnabled(false);
                }
                ai.shutdown();
            });
        }
        updateBehavioralStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (behavioralStatus != null) updateBehavioralStatus();
    }

    private void updateBehavioralStatus() {
        BehavioralProfile profile = new BehavioralProfileStore(this).load();
        if (!profile.isValid()) {
            behavioralStatus.setText("No local typing-rhythm profile saved.");
            return;
        }
        behavioralStatus.setText("Local profile saved from " + profile.samples
                + " intervals. It is not used as a login or vault unlock method.");
    }

    private void loadProfile(String pkg) {
        String prefix = pkg.isEmpty() ? "" : pkg + "_";
        int min = prefs.getInt(prefix + HumanTypingIME.KEY_MIN_DELAY,
                prefs.getInt(HumanTypingIME.KEY_MIN_DELAY, HumanTypingIME.DEF_MIN_DELAY));
        int max = prefs.getInt(prefix + HumanTypingIME.KEY_MAX_DELAY,
                prefs.getInt(HumanTypingIME.KEY_MAX_DELAY, HumanTypingIME.DEF_MAX_DELAY));
        int punct = prefs.getInt(prefix + HumanTypingIME.KEY_PUNCT_DELAY,
                prefs.getInt(HumanTypingIME.KEY_PUNCT_DELAY, HumanTypingIME.DEF_PUNCT_DELAY));
        float typo = prefs.getFloat(prefix + HumanTypingIME.KEY_TYPO_PROB,
                prefs.getFloat(HumanTypingIME.KEY_TYPO_PROB, HumanTypingIME.DEF_TYPO_PROB));

        sbMin.setProgress(min);
        sbMax.setProgress(max);
        sbPunct.setProgress(punct);
        sbTypo.setProgress((int) (typo * 100));

        tvMin.setText("Min delay: " + min + " ms");
        tvMax.setText("Max delay: " + max + " ms");
        tvPunct.setText("Punctuation pause: " + punct + " ms");
        tvTypo.setText("Typo chance: " + (int) (typo * 100) + "%");
    }

    private void saveProfile() {
        String prefix = currentPackage.isEmpty() ? "" : currentPackage + "_";
        prefs.edit()
                .putInt(prefix + HumanTypingIME.KEY_MIN_DELAY, sbMin.getProgress())
                .putInt(prefix + HumanTypingIME.KEY_MAX_DELAY, sbMax.getProgress())
                .putInt(prefix + HumanTypingIME.KEY_PUNCT_DELAY, sbPunct.getProgress())
                .putFloat(prefix + HumanTypingIME.KEY_TYPO_PROB, sbTypo.getProgress() / 100f)
                .apply();
    }

    private void selectTheme(int mode) {
        ThemeManager.setMode(this, mode);
    }

    private boolean isDarkTheme() {
        int uiMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void updateThemeToggle() {
        boolean dark = isDarkTheme();
        btnThemeToggle.setImageResource(dark ? R.drawable.ic_half_moon : R.drawable.ic_sun);
        btnThemeToggle.setContentDescription(dark ? "Switch to light theme" : "Switch to dark theme");
        if (tvThemeMode != null) tvThemeMode.setText(dark ? "Dark theme" : "Light theme");
    }

    // Helper to avoid implementing all SeekBar methods
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
