package com.example.humantypingime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.rewriting.Rewriter;
import com.google.mlkit.genai.rewriting.RewriterOptions;
import com.google.mlkit.genai.rewriting.Rewriting;
import com.google.mlkit.genai.rewriting.RewritingRequest;
import com.google.mlkit.genai.rewriting.RewritingResult;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** Owns model clients and waits only on its background executor. */
public class AiRewriteManager {
    private static final String TAG = "AiRewriteManager";
    private static final int CHECK_TIMEOUT_SECONDS = 15;
    private static final int DOWNLOAD_TIMEOUT_SECONDS = 180;
    private static final int PREPARE_TIMEOUT_SECONDS = 30;
    private static final int INFER_TIMEOUT_SECONDS = 60;
    private static final int MAX_POLL_ATTEMPTS = 6;
    public static final int MAX_REWRITE_CHARS = 600;

    public enum Tone { PROFESSIONAL, FRIENDLY, SHORTER, ELABORATE, REPHRASE }
    public interface Callback {
        void onResult(String rewritten);
        void onError(String message);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final Map<String, Rewriter> clients = new HashMap<>();
    private ListenableFuture<?> activeFuture;
    private volatile boolean shutDown, available, checked;
    private volatile int language = RewriterOptions.Language.ENGLISH;
    private volatile String statusMessage = "Checking on-device AI availability…";

    public AiRewriteManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setLanguage(@RewriterOptions.Language int language) {
        synchronized (lock) {
            if (this.language != language) {
                this.language = language;
                checked = available = false;
            }
        }
    }

    public void checkAvailability(Consumer<Boolean> result) {
        checkAvailability(result, null);
    }

    public void checkAvailability(Consumer<Boolean> result, Consumer<String> progress) {
        final int requestedLanguage = language;
        submit(() -> {
            Rewriter client = null;
            boolean ready = false;
            String message;
            try {
                client = clientFor(Tone.PROFESSIONAL, requestedLanguage);
                prepare(client, progress);
                ready = true;
                message = "On-device rewriting is ready. Your draft stays on this device.";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                message = "Model preparation was interrupted. Tap Retry model.";
            } catch (Exception e) {
                Log.w(TAG, "Model preparation failed", e);
                message = friendlyError(e);
            }
            if (!ready) discard(Tone.PROFESSIONAL, requestedLanguage, client);
            final boolean prepared = ready;
            final String finalMessage = message;
            synchronized (lock) {
                if (shutDown || language != requestedLanguage) return;
                available = prepared;
                checked = true;
                statusMessage = finalMessage;
            }
            post(() -> {
                if (progress != null) progress.accept(finalMessage);
                if (result != null) result.accept(prepared);
            });
        });
    }

    public boolean isAvailable() { return checked && available && !shutDown; }
    public String getStatusMessage() { return statusMessage; }

    public void rewrite(String text, Tone tone, Callback callback) {
        if (callback == null) return;
        if (text == null || text.trim().isEmpty()) {
            post(() -> callback.onError("Nothing to rewrite."));
            return;
        }
        if (text.length() > MAX_REWRITE_CHARS) {
            post(() -> callback.onError("Use a draft up to " + MAX_REWRITE_CHARS + " characters."));
            return;
        }
        final Tone chosenTone = tone == null ? Tone.PROFESSIONAL : tone;
        final int requestedLanguage = language;
        submit(() -> {
            if (!isAvailable() || requestedLanguage != language) {
                post(() -> callback.onError(statusMessage));
                return;
            }
            Rewriter client = null;
            try {
                client = clientFor(chosenTone, requestedLanguage);
                // Each style has its own client; check and prepare the selected style.
                prepare(client, null);
                RewritingResult result = await(client.runInference(
                        RewritingRequest.builder(text).build()), INFER_TIMEOUT_SECONDS);
                String suggestion = result.getResults().isEmpty()
                        ? "" : result.getResults().get(0).getText();
                if (suggestion == null || suggestion.trim().isEmpty())
                    post(() -> callback.onError("The model returned nothing. Try another style."));
                else post(() -> callback.onResult(suggestion));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                discard(chosenTone, requestedLanguage, client);
                post(() -> callback.onError("Rewrite was interrupted. Try again."));
            } catch (Exception e) {
                Log.w(TAG, "Rewrite failed", e);
                discard(chosenTone, requestedLanguage, client);
                post(() -> callback.onError(friendlyError(e)));
            }
        });
    }

    private void prepare(Rewriter client, Consumer<String> progress) throws Exception {
        int status = await(client.checkFeatureStatus(), CHECK_TIMEOUT_SECONDS);
        for (int attempt = 0; status == FeatureStatus.DOWNLOADING
                && attempt < MAX_POLL_ATTEMPTS; attempt++) {
            report(progress, "The on-device model is downloading…");
            TimeUnit.SECONDS.sleep(3);
            status = await(client.checkFeatureStatus(), CHECK_TIMEOUT_SECONDS);
        }
        if (status == FeatureStatus.DOWNLOADING)
            throw new IllegalStateException("The model is still downloading. Tap Retry model shortly.");
        if (status == FeatureStatus.DOWNLOADABLE) {
            report(progress, "Downloading the on-device rewrite model…");
            // Download callbacks report progress only, and never wait on an engine.
            await(client.downloadFeature(new DownloadCallback() {
                @Override public void onDownloadStarted(long bytes) { }
                @Override public void onDownloadProgress(long bytes) { }
                @Override public void onDownloadCompleted() { }
                @Override public void onDownloadFailed(GenAiException error) {
                    Log.w(TAG, "Model download failed", error);
                }
            }), DOWNLOAD_TIMEOUT_SECONDS);
        } else if (status != FeatureStatus.AVAILABLE) {
            throw new IllegalStateException("Rewriting is not supported by this device's AICore service.");
        }
        report(progress, "Preparing the on-device rewrite model…");
        await(client.prepareInferenceEngine(), PREPARE_TIMEOUT_SECONDS);
    }

    private Rewriter clientFor(Tone tone, int language) throws InterruptedException {
        synchronized (lock) {
            if (shutDown) throw new InterruptedException("Manager closed");
            String key = key(tone, language);
            Rewriter client = clients.get(key);
            if (client == null) {
                client = Rewriting.getClient(RewriterOptions.builder(context)
                        .setOutputType(outputType(tone)).setLanguage(language).build());
                clients.put(key, client);
            }
            return client;
        }
    }

    private <T> T await(ListenableFuture<T> future, int seconds) throws Exception {
        synchronized (lock) {
            if (shutDown) {
                future.cancel(true);
                throw new InterruptedException("Manager closed");
            }
            activeFuture = future;
        }
        try { return BoundedFutureWait.get(future, seconds, TimeUnit.SECONDS); }
        finally {
            synchronized (lock) {
                if (activeFuture == future) activeFuture = null;
            }
        }
    }

    private void submit(Runnable work) {
        synchronized (lock) { if (!shutDown) executor.execute(work); }
    }

    private void post(Runnable callback) {
        synchronized (lock) {
            if (!shutDown) main.post(() -> { if (!shutDown) callback.run(); });
        }
    }

    private void report(Consumer<String> progress, String message) {
        if (progress != null) post(() -> progress.accept(message));
    }

    private void discard(Tone tone, int language, Rewriter client) {
        if (client == null) return;
        synchronized (lock) {
            if (clients.remove(key(tone, language), client)) closeQuietly(client);
        }
    }

    private static String key(Tone tone, int language) {
        return language + ":" + outputType(tone);
    }

    private static String friendlyError(Exception error) {
        if (error instanceof TimeoutException)
            return "The on-device AI service took too long. Retry with a short draft.";
        if (error instanceof IllegalStateException) return error.getMessage();
        return "AICore could not prepare or run rewriting. Update AICore and Android, "
                + "restart the phone, then retry with a short draft.";
    }

    private static int outputType(Tone tone) {
        switch (tone) {
            case FRIENDLY: return RewriterOptions.OutputType.FRIENDLY;
            case SHORTER: return RewriterOptions.OutputType.SHORTEN;
            case ELABORATE: return RewriterOptions.OutputType.ELABORATE;
            case REPHRASE: return RewriterOptions.OutputType.REPHRASE;
            default: return RewriterOptions.OutputType.PROFESSIONAL;
        }
    }

    private static void closeQuietly(Rewriter client) {
        try { client.close(); }
        catch (Exception e) { Log.w(TAG, "Rewriter.close failed", e); }
    }

    public void shutdown() {
        synchronized (lock) {
            if (shutDown) return;
            shutDown = true;
            available = false;
            main.removeCallbacksAndMessages(null);
            if (activeFuture != null) activeFuture.cancel(true);
            executor.shutdownNow();
            for (Rewriter client : clients.values()) closeQuietly(client);
            clients.clear();
        }
    }
}
