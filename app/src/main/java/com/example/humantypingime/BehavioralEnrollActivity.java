package com.example.humantypingime;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Explicit, one-time enrollment for a local rhythm profile. This activity
 * never stores the passage or raw timestamps after it is closed.
 */
public class BehavioralEnrollActivity extends AppCompatActivity {
    private static final int TARGET_INTERVALS = 60;
    private final List<Long> intervals = new ArrayList<>();
    private long lastKeyNanos;
    private TextView progressText;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(ThemeManager.getMode(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_behavioral_enroll);

        EditText passage = findViewById(R.id.et_behavioral_passage);
        progressText = findViewById(R.id.tv_behavioral_progress);
        progressBar = findViewById(R.id.pb_behavioral_enroll);
        progressBar.setMax(TARGET_INTERVALS);
        updateProgress();

        passage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Count direct single-character entry only; pastes and deletes do not form timing data.
                if (before != 0 || count != 1 || intervals.size() >= TARGET_INTERVALS) return;
                long now = System.nanoTime();
                if (lastKeyNanos != 0L) {
                    long intervalMs = (now - lastKeyNanos) / 1_000_000L;
                    if (intervalMs > 0 && intervalMs < 2_000L) intervals.add(intervalMs);
                }
                lastKeyNanos = now;
                updateProgress();
            }

            @Override public void afterTextChanged(Editable s) { }
        });

        ((Button) findViewById(R.id.btn_behavioral_cancel)).setOnClickListener(v -> finish());
        ((Button) findViewById(R.id.btn_behavioral_save)).setOnClickListener(v -> saveProfile());
    }

    private void updateProgress() {
        progressBar.setProgress(intervals.size());
        progressText.setText(intervals.size() + " / " + TARGET_INTERVALS + " intervals captured");
    }

    private void saveProfile() {
        if (intervals.size() < 40) {
            Toast.makeText(this, "Type a little more to collect at least 40 intervals.", Toast.LENGTH_LONG).show();
            return;
        }
        FingerprintBuilder.Fingerprint fp = FingerprintBuilder.build(intervals);
        BehavioralProfile profile = new BehavioralProfile();
        profile.mean = fp.mean;
        profile.stddev = fp.stddev;
        profile.median = fp.median;
        profile.p10 = fp.p10;
        profile.p90 = fp.p90;
        profile.samples = fp.samples;
        if (!profile.isValid()) {
            Toast.makeText(this, "That sample was not usable. Please try again.", Toast.LENGTH_LONG).show();
            return;
        }
        new BehavioralProfileStore(this).save(profile);
        intervals.clear();
        Toast.makeText(this, "Local rhythm profile saved. No text was stored.", Toast.LENGTH_LONG).show();
        finish();
    }
}
