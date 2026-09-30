package com.example.humantypingime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.util.Log;
import java.util.UUID;

/** Main-thread clipboard actions; pending handoffs hold an ID, never plaintext. */
final class ClipboardActions {
    private static final String TAG = "ClipboardActions";
    private static final String RECEIPT = "com.example.humantypingime.clip_receipt";
    private static final String SENSITIVE = "android.content.extra.IS_SENSITIVE";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static String pendingReceipt;
    private static long pendingUntil;
    private ClipboardActions() { }

    static boolean copy(Context context, String label, String text, boolean sensitive, boolean expire) {
        try {
            ClipboardManager clipboard = clipboard(context);
            if (clipboard == null || text == null || text.isEmpty()) return false;
            ClipData clip = ClipData.newPlainText(label, text);
            PersistableBundle extras = new PersistableBundle();
            extras.putString(RECEIPT, UUID.randomUUID().toString());
            extras.putBoolean(SENSITIVE, sensitive);
            clip.getDescription().setExtras(extras);
            pendingReceipt = null;
            clipboard.setPrimaryClip(clip);
            if (expire) scheduleClear(context, clipboard.getPrimaryClip());
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not copy text", e);
            return false;
        }
    }

    static boolean requestTyping(Context context, String label, String text,
                                 boolean sensitive, boolean expire) {
        if (!copy(context, label, text, sensitive, expire)) return false;
        ClipboardManager clipboard = clipboard(context);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        pendingReceipt = receipt(clip);
        if (pendingReceipt == null) return false;
        pendingUntil = SystemClock.elapsedRealtime() + 30_000L;
        context.sendBroadcast(new Intent(HumanTypingIME.ACTION_TYPE_CLIPBOARD)
                .setPackage(context.getPackageName()));
        return true;
    }

    static String consumeForTyping(Context context) {
        if (pendingReceipt == null) return null;
        String expected = pendingReceipt;
        pendingReceipt = null;
        if (SystemClock.elapsedRealtime() >= pendingUntil) return null;
        ClipboardManager clipboard = clipboard(context);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        if (!expected.equals(receipt(clip)) || clip.getItemCount() == 0) return null;
        CharSequence text = clip.getItemAt(0).coerceToText(context);
        return text == null ? null : text.toString();
    }

    static boolean isSensitive(ClipData clip) {
        PersistableBundle extras = clip == null ? null : clip.getDescription().getExtras();
        return extras != null && extras.getBoolean(SENSITIVE, false);
    }

    static void scheduleClear(Context context, ClipData snapshot) {
        if (snapshot == null || snapshot.getItemCount() == 0) return;
        // Application context keeps cleanup alive after the originating dialog closes.
        Context app = context.getApplicationContext();
        String expectedReceipt = receipt(snapshot);
        long timestamp = snapshot.getDescription().getTimestamp();
        CharSequence expectedText = snapshot.getItemAt(0).coerceToText(app);
        if (expectedText == null) return;
        String text = expectedText.toString();
        MAIN.postDelayed(() -> {
            try {
                ClipboardManager clipboard = clipboard(app);
                ClipData current = clipboard == null ? null : clipboard.getPrimaryClip();
                if (current == null || current.getItemCount() == 0) return;
                CharSequence currentText = current.getItemAt(0).coerceToText(app);
                boolean matches = expectedReceipt != null
                        ? expectedReceipt.equals(receipt(current))
                        : timestamp == current.getDescription().getTimestamp()
                            && currentText != null && text.contentEquals(currentText);
                // A newer copy belongs to another action; never erase it.
                if (!matches) return;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip();
                else clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                if (expectedReceipt != null && expectedReceipt.equals(pendingReceipt))
                    pendingReceipt = null;
            } catch (RuntimeException e) {
                Log.w(TAG, "Clipboard cleanup could not run", e);
            }
        }, 30_000L);
    }

    private static ClipboardManager clipboard(Context context) {
        return (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    private static String receipt(ClipData clip) {
        PersistableBundle extras = clip == null ? null : clip.getDescription().getExtras();
        return extras == null ? null : extras.getString(RECEIPT);
    }
}
