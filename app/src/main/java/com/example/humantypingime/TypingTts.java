/*
 * TypingTts.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: FIX(b) — make engine/ready volatile (written on the TTS init
 *             callback thread, read from the background typing thread).
 * 2026-09-30: HARDEN — null-guard engine in speak(); log init failure instead
 *             of silently ignoring a non-SUCCESS status; reset ready in
 *             shutdown() so release is deterministic and idempotent.
 */

package com.example.humantypingime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.Locale;

public class TypingTts {
    private static final String TAG = "TypingTts";
    private volatile TextToSpeech engine;
    private volatile boolean ready;
    private boolean closed;
    public TypingTts(Context context) {
        synchronized (this) {
            engine = new TextToSpeech(context.getApplicationContext(), status ->
                    new Handler(Looper.getMainLooper()).post(() -> {
                synchronized (TypingTts.this) {
                    if (closed || engine == null) return;
                    if (status == TextToSpeech.SUCCESS) {
                        engine.setLanguage(Locale.getDefault());
                        engine.setSpeechRate(1.4f);
                        ready = true;
                    } else Log.w(TAG, "TTS init failed with status " + status);
                }
            }));
        }
    }
    public synchronized void speak(char c) {
        TextToSpeech e = engine;
        if (ready && e != null && !Character.isWhitespace(c)) e.speak(String.valueOf(c), TextToSpeech.QUEUE_ADD, null, "key_" + System.nanoTime());
    }
    public synchronized void shutdown() {
        closed = true;
        ready = false;
        TextToSpeech e = engine;
        engine = null;
        if (e != null) {
            e.stop();
            e.shutdown();
        }
    }
}
//（注：内容由AI生成）
