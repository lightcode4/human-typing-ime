package com.example.humantypingime;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VoiceInputManager {

    public interface Callback {
        void onPartial(String text);
        void onFinal(String processedText);
        void onError(String message);
        void onReady();
        void onEnd();
    }

    private final Context context;
    private final Callback callback;
    private SpeechRecognizer recognizer;
    private boolean isListening = false;

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

        stop(); // clean up any existing session

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
        if (recognizer != null) {
            try {
                recognizer.stopListening();
            } catch (Exception ignored) { }
        }
        isListening = false;
    }

    public void cancel() {
        if (recognizer != null) {
            try {
                recognizer.cancel();
                recognizer.destroy();
            } catch (Exception ignored) { }
                recognizer = null;
        }
        isListening = false;
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
            ArrayList<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            String raw = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";

            // Run through post-processor with recorded pause durations
            String processed = TranscriptPostProcessor.process(raw, pauseMarks);
            callback.onFinal(processed);
            cancel();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                callback.onPartial(matches.get(0));
            }
        }

        @Override public void onEvent(int eventType, Bundle params) { }
    };
}
