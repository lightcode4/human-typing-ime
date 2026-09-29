package com.example.humantypingime;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the interrupted clipboard typing job for a short, safe-to-resume window. */
public class TypingResumeStore {

    private static final String PREFS = "typing_resume_prefs";
    private static final String KEY_PACKAGE = "pkg";
    private static final String KEY_TEXT = "text";
    private static final String KEY_INDEX = "index";
    private static final String KEY_TIMESTAMP = "ts";
    private static final long MAX_AGE_MS = 10 * 60 * 1000L;

    public static class State {
        public String text;
        public String packageName;
        public int index;
        public long timestamp;
    }

    private final SharedPreferences prefs;

    public TypingResumeStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void save(String packageName, String text, int index) {
        if (packageName == null || text == null || index <= 0 || index >= text.length()) return;
        prefs.edit()
                .putString(KEY_PACKAGE, packageName)
                .putString(KEY_TEXT, text)
                .putInt(KEY_INDEX, index)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .apply();
    }

    /** Returns null when the state is missing, stale, or belongs to different content or app. */
    public synchronized State getFor(String packageName, String text) {
        String savedPackage = prefs.getString(KEY_PACKAGE, null);
        String savedText = prefs.getString(KEY_TEXT, null);
        int savedIndex = prefs.getInt(KEY_INDEX, 0);
        long savedTimestamp = prefs.getLong(KEY_TIMESTAMP, 0L);

        if (savedPackage == null || savedText == null) return null;
        if (System.currentTimeMillis() - savedTimestamp > MAX_AGE_MS) {
            clear();
            return null;
        }
        if (!savedPackage.equals(packageName) || !savedText.equals(text)
                || savedIndex <= 0 || savedIndex >= savedText.length()) {
            return null;
        }

        State state = new State();
        state.packageName = savedPackage;
        state.text = savedText;
        state.index = savedIndex;
        state.timestamp = savedTimestamp;
        return state;
    }

    /** Invalidates the saved state as soon as the clipboard contains different content. */
    public synchronized void clearIfTextDiffers(String text) {
        String savedText = prefs.getString(KEY_TEXT, null);
        if (savedText != null && !savedText.equals(text)) clear();
    }

    public synchronized void clear() {
        prefs.edit().clear().apply();
    }
}
