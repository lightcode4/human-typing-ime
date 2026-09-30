/*
 * AboutActivity.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: HARDEN — log the previously silent package-info failure (fallback text kept);
 *             guard openUrl against ActivityNotFoundException with a short friendly toast.
 */

package com.example.humantypingime;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    private static final String TAG = "AboutActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        TextView version = findViewById(R.id.tv_version);
        try {
            version.setText("Version " + getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception e) {
            Log.w(TAG, "Could not read package version", e);
            version.setText("Version —");
        }
        findViewById(R.id.btn_github).setOnClickListener(v ->
                openUrl("https://github.com/lightcode4"));
        findViewById(R.id.btn_x).setOnClickListener(v ->
                openUrl("https://x.com/blaqattitude"));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No app available to open " + url, e);
            Toast.makeText(this, "No app available to open that link.", Toast.LENGTH_SHORT).show();
        }
    }
}
//（注：内容由AI生成）
