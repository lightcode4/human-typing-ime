package com.example.humantypingime;

public class TemplateTriggerWatcher {

    private static final int BUFFER_MAX = 64;
    private static final int TRIGGER_PREFIX_LEN = 2; // "::"
    private static final int TRIGGER_NAME_MAX = 24;

    private final StringBuilder buffer = new StringBuilder();

    public interface OnTriggerListener {
        /**
         * Called on the main thread when a trigger is detected.
         * @param template   the matched template
         * @param triggerLen length of the "::name" text to delete (prefix + name)
         */
        void onTrigger(Template template, int triggerLen);
    }

    private final TemplateRepository repo;
    private final OnTriggerListener listener;
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    public TemplateTriggerWatcher(TemplateRepository repo, OnTriggerListener listener) {
        this.repo = repo;
        this.listener = listener;
    }

    /** Feed committed text into the buffer. Call this from commitText. */
    public void onTextCommitted(CharSequence text) {
        if (text == null || text.length() == 0) return;
        append(text.toString());
        checkForTrigger();
    }

    /** Feed a single key event. Only printable letter keys matter for triggers. */
    public void onKeyEvent(int keyCode) {
        // Reset on navigation/whitespace keys
        if (keyCode == android.view.KeyEvent.KEYCODE_SPACE
                || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                || keyCode == android.view.KeyEvent.KEYCODE_DEL
                || keyCode == android.view.KeyEvent.KEYCODE_TAB) {
            clear();
            return;
        }
    }

    /** Explicitly clear the buffer, e.g. when the field changes. */
    public void clear() {
        buffer.setLength(0);
    }

    private void append(String s) {
        buffer.append(s);
        if (buffer.length() > BUFFER_MAX) {
            buffer.delete(0, buffer.length() - BUFFER_MAX);
        }
    }

    private void checkForTrigger() {
        int idx = buffer.lastIndexOf("::");
        if (idx < 0) return;

        String after = buffer.substring(idx + TRIGGER_PREFIX_LEN);
        if (after.isEmpty() || after.length() > TRIGGER_NAME_MAX) return;

        // Only allow a valid trigger name — stop as soon as we hit anything else
        if (!after.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            // partial match still in progress, keep waiting
            return;
        }

        final Template t = repo.findByTrigger(after);
        if (t == null) return;

        final int triggerLen = TRIGGER_PREFIX_LEN + after.length();

        // Clear immediately so we don't re-fire while the popup is up
        clear();

        mainHandler.post(() -> listener.onTrigger(t, triggerLen));
    }
}
