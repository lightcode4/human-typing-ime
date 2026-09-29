package com.example.humantypingime;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class TypingTts {
    private TextToSpeech engine;
    private boolean ready;
    public TypingTts(Context context) {
        engine = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) { engine.setLanguage(Locale.getDefault()); engine.setSpeechRate(1.4f); ready = true; }
        });
    }
    public void speak(char c) { if (ready && !Character.isWhitespace(c)) engine.speak(String.valueOf(c), TextToSpeech.QUEUE_ADD, null, "key_" + System.nanoTime()); }
    public void shutdown() { if (engine != null) { engine.stop(); engine.shutdown(); engine = null; } }
}
