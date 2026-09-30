/*
 * VoiceInputManager.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: FIX(b) — mark recognizer/isListening volatile (cross-thread
 *             callback fields).
 * 2026-09-30: FIX(i) — destroy the SpeechRecognizer deterministically: start()
 *             now destroys any prior session via cancel() instead of leaking it;
 *             cancel() already nulls the recognizer so destroy() can never run
 *             twice on the same instance.
 * 2026-09-30: HARDEN — replace silent empty catch blocks in stop()/cancel()
 *             with Log.w diagnostics; null-guard the results bundle.
 */

package com.example.humantypingime;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VoiceInputManager {

    private static final String TAG = "VoiceInputManager";

    public interface Callback {
        void onPartial(String text);
        void onFinal(String processedText);
        void onError(String message);
        void onReady();
        void onEnd();
    }

    private final Context context;
    private final Callback callback;
    private volatile SpeechRecognizer recognizer;
    private volatile boolean isListening = false;

    // Pause detection via RMS dB levels
    private long lastAudioTime = 0L;
    private long silenceStartTime = 0L;
    private final List<Long> pauseMarks = new ArrayList<>();
    private static final float RMS_SILENCE_THRESHOLD = 2.0f; // dB floor
    private static final long MIN_PAUSE_MS = 350L;

    public VoiceInputManager(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public boolean isRunning() {
        return isListening;
    }

    public void start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onError("Speech recognition not available on this device");
            return;
        }

        cancel(); // destroy any existing session before recreating

        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(listener);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        // Prefer the on-device recognizer where available; audio stays on the phone.
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());

        pauseMarks.clear();
        silenceStartTime = 0L;
        lastAudioTime = System.currentTimeMillis();

        recognizer.startListening(intent);
        isListening = true;
    }

    public void stop() {
        SpeechRecognizer r = recognizer;
        if (r != null) {
            try {
                r.stopListening();
            } catch (Exception e) {
                Log.w(TAG, "stopListening failed", e);
            }
        }
        isListening = false;
    }

    public void cancel() {
        SpeechRecognizer r = recognizer;
        recognizer = null;
        isListening = false;
        if (r != null) {
            try {
                r.cancel();
            } catch (Exception e) {
                Log.w(TAG, "Cancel recognizer failed", e);
            }
            try {
                r.destroy();
            } catch (Exception e) {
                Log.w(TAG, "Destroy recognizer failed", e);
            }
        }
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { callback.onReady(); }
        @Override public void onBeginningOfSpeech() { lastAudioTime = System.currentTimeMillis(); }

        @Override
        public void onRmsChanged(float rmsdB) {
            // Track pauses: if RMS drops below threshold, start timing silence.
            // When voice resumes, record the duration if it exceeds MIN_PAUSE_MS.
            long now = System.currentTimeMillis();
            if (rmsdB < RMS_SILENCE_THRESHOLD) {
                if (silenceStartTime == 0L) silenceStartTime = now;
            } else {
                if (silenceStartTime > 0L) {
                    long silenceDur = now - silenceStartTime;
                    if (silenceDur >= MIN_PAUSE_MS) {
                        pauseMarks.add(silenceDur);
                    }
                    silenceStartTime = 0L;
                }
                lastAudioTime = now;
            }
        }

        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { callback.onEnd(); }

        @Override
        public void onError(int error) {
            isListening = false;
            String msg = "Voice error (" + error + ")";
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO: msg = "Audio recording error"; break;
                case SpeechRecognizer.ERROR_NO_MATCH: msg = "No speech recognized"; break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: msg = "Network error"; break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    msg = "Record audio permission missing"; break;
            }
            callback.onError(msg);
            cancel();
        }

        @Override
        public void onResults(Bundle results) {
            isListening = false;
            String raw = "";
            if (results != null) {
                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                raw = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";
            }

            // Run through post-processor with recorded pause durations
            String processed = TranscriptPostProcessor.process(raw, pauseMarks);
            callback.onFinal(processed);
            cancel();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            if (partialResults == null) return;
            ArrayList<String> matches = partialResults.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                callback.onPartial(matches.get(0));
            }
        }

        @Override public void onEvent(int eventType, Bundle params) { }
    };
}
//（注：内容由AI生成）
