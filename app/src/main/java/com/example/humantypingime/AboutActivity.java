package com.example.humantypingime;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        TextView version = findViewById(R.id.tv_version);
        try {
            version.setText("Version " + getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception ignored) {
            version.setText("Version —");
        }
        findViewById(R.id.btn_github).setOnClickListener(v ->
                openUrl("https://github.com/lightcode4"));
        findViewById(R.id.btn_x).setOnClickListener(v ->
                openUrl("https://x.com/blaqattitude"));
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
