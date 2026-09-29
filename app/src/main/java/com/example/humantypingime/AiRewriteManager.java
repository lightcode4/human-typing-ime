package com.example.humantypingime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.rewriting.Rewriter;
import com.google.mlkit.genai.rewriting.RewriterOptions;
import com.google.mlkit.genai.rewriting.Rewriting;
import com.google.mlkit.genai.rewriting.RewritingRequest;
import com.google.mlkit.genai.rewriting.RewritingResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * On-device rewriting through ML Kit GenAI (Gemini Nano via AICore).
 * All ML Kit calls are blocking and therefore run on a private executor;
 * callbacks are posted back to the main thread.
 */
public class AiRewriteManager {

    /** The five output styles the beta Rewriter supports. */
    public enum Tone { PROFESSIONAL, FRIENDLY, SHORTER, ELABORATE, REPHRASE }

    public interface Callback {
        void onResult(String rewritten);
        void onError(String message);
    }

    private final Context ctx;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean available = false;
    private volatile boolean checked = false;

    public AiRewriteManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Probes AICore once; cheap to call again. onResult fires on the main thread. */
    public void checkAvailability(Consumer<Boolean> onResult) {
        executor.execute(() -> {
            Rewriter rewriter = null;
            try {
                rewriter = Rewriting.getClient(RewriterOptions.builder(ctx).build());
                available = rewriter.checkFeatureStatus().get() == FeatureStatus.AVAILABLE;
            } catch (Exception ignored) {
                available = false;
            } finally {
                if (rewriter != null) rewriter.close();
            }
            checked = true;
            if (onResult != null) main.post(() -> onResult.accept(available));
        });
    }

    public boolean isAvailable() {
        return checked && available;
    }

    public void rewrite(String text, Tone tone, Callback cb) {
        if (text == null || text.trim().isEmpty()) {
            cb.onError("Nothing to rewrite");
            return;
        }
        executor.execute(() -> {
            Rewriter rewriter = null;
            try {
                rewriter = Rewriting.getClient(RewriterOptions.builder(ctx)
                        .setOutputType(outputType(tone))
                        .setLanguage(RewriterOptions.Language.ENGLISH)
                        .build());
                RewritingResult result = rewriter.runInference(
                        RewritingRequest.builder(text).build()).get();
                String out = result.getResults().isEmpty()
                        ? "" : result.getResults().get(0).getText();
                if (out == null || out.trim().isEmpty()) {
                    main.post(() -> cb.onError("The model returned nothing. Try another style."));
                } else {
                    main.post(() -> cb.onResult(out));
                }
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Rewrite failed" : e.getMessage();
                main.post(() -> cb.onError(message));
            } finally {
                if (rewriter != null) rewriter.close();
            }
        });
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

    public void shutdown() {
        executor.shutdownNow();
    }
}
