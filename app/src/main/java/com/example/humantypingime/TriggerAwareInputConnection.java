package com.example.humantypingime;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

public class TriggerAwareInputConnection extends InputConnectionWrapper {

    private final TemplateTriggerWatcher watcher;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public TriggerAwareInputConnection(InputConnection target,
                                       boolean mutable,
                                       TemplateTriggerWatcher watcher) {
        super(target, mutable);
        this.watcher = watcher;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (watcher != null && text != null) {
            watcher.onTextCommitted(text);
        }
        return super.commitText(text, newCursorPosition);
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        return super.setComposingText(text, newCursorPosition);
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (watcher != null && event != null
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            watcher.onKeyEvent(event.getKeyCode());
        }
        return super.sendKeyEvent(event);
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (watcher != null && beforeLength > 0) {
            // User is backspacing — reset the rolling buffer
            watcher.clear();
        }
        return super.deleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        if (watcher != null && beforeLength > 0) {
            watcher.clear();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
        }
        return super.deleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public boolean finishComposingText() {
        return super.finishComposingText();
    }
}
