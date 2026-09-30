/*
 * HumanTypingIME.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: FIX(i) — deterministically release SpeechRecognizer-backed
 *             VoiceInputManager, the primary-clip listener, and the pending
 *             clipboard-purge callback in onDestroy; guard teardown with a
 *             shutdown flag so resources are released at most once.
 * 2026-09-30: HARDEN — replace the three silent empty catch blocks (clipboard
 *             purge, input-view rebuild, best-effort proof embedding) with
 *             Log.w diagnostics; runtime behavior unchanged.
 * 2026-09-30: UI — status strip on both keyboards (Ready / Listening… / Typing… / Stopped / Done
 *             / "Typing X of N" every 25 chars); themed inflater for all popups so dark mode is
 *             correct; mic key shows permission/recording state; empty vault key dimmed; KEYBOARD_TAP
 *             haptics on Type/Stop/Delete; "Captured…" debug toast removed, vault toast no longer
 *             reveals the detected category; new ACTION_TYPE_CLIPBOARD broadcast received so
 *             Clipboard/Vault/Rewrite screens can hand text to the IME.
 */

package com.example.humantypingime;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.inputmethodservice.InputMethodService;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class HumanTypingIME extends InputMethodService {

    private static final String TAG = "HumanTypingIME";

    public static final String PREFS_NAME = "human_typing_prefs";
    public static final String KEY_MIN_DELAY = "min_delay";
    public static final String KEY_MAX_DELAY = "max_delay";
    public static final String KEY_PUNCT_DELAY = "punct_delay";
    public static final String KEY_TYPO_PROB = "typo_prob";
    public static final String KEY_PROOF_ENABLED = "proof_enabled";
    public static final String KEY_FOCUS_MODE = "focus_mode";
    public static final String KEY_TTS_ON_TAP = "tts_on_tap";
    public static final String KEY_SLOW_KEYS = "slow_keys";
    public static final String KEY_BOUNCE_KEYS = "bounce_keys";
    public static final String KEY_AI_REWRITE = "ai_rewrite_enabled";
    /** Broadcast: asks the IME to type the current clipboard content (human cadence). */
    public static final String ACTION_TYPE_CLIPBOARD = "com.example.humantypingime.ACTION_TYPE_CLIPBOARD";
    public static final int DEF_SLOW_KEYS_MS = 300;

    public static final int DEF_MIN_DELAY = 30;
    public static final int DEF_MAX_DELAY = 120;
    public static final int DEF_PUNCT_DELAY = 300;
    public static final float DEF_TYPO_PROB = 0.020f;

    private static final int LONG_TEXT_THRESHOLD = 500;
    // ML Kit GenAI rewriting accepts short text (<256 tokens); keep a safe
    // character ceiling so long prompts do not reach AICore and fail inference.
    private static final int REWRITE_MAX_CHARS = 600;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private volatile boolean isTyping = false;
    private volatile boolean destroyed = false;

    private ClipboardHistoryStore historyStore;
    private VaultStore vaultStore;
    private TypingResumeStore resumeStore;
    private VoiceInputManager voiceManager;
    private AiRewriteManager aiRewrite;
    private Toast voiceToast;
    private TypingTelemetry telemetry;
    private boolean proofEmbeddingEnabled = true;
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private boolean clipListenerRegistered;
    private String currentPackage = "";
    private TemplateRepository templateRepository;
    private TemplateTriggerWatcher triggerWatcher;
    private String activeText;
    private int activeIndex;
    private TypingTts typingTts;
    private char lastCommittedChar;
    private long lastCommittedAt;

    // UI state of the input view (status strip, mic button, vault dimming)
    private TextView statusView;
    private TextView focusStatusView;
    private View micButton;
    private TextView micLabel;
    private View vaultButton;

    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (TypingForegroundService.ACTION_STOP_TYPING.equals(action)) {
                stopTyping();
            } else if (ACTION_TYPE_CLIPBOARD.equals(action)) {
                handler.post(HumanTypingIME.this::consumeTypingHandoff);
            }
        }
    };

    private final BroadcastReceiver themeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ThemeManager.ACTION_THEME_CHANGED.equals(intent.getAction())) {
                rebuildInputView();
            }
        }
    };
    private boolean themeReceiverRegistered;

    @Override
    public void onCreate() {
        super.onCreate();
        telemetry = new TypingTelemetry();
        historyStore = new ClipboardHistoryStore(this);
        vaultStore = new VaultStore(this);
        resumeStore = new TypingResumeStore(this);
        templateRepository = new TemplateRepository(this);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        aiRewrite = new AiRewriteManager(this);
        aiRewrite.checkAvailability(null);

        triggerWatcher = new TemplateTriggerWatcher(templateRepository, (template, triggerLen) -> {
            // Fired on the main thread by the watcher when ::trigger is matched
            showTemplateFillPopup(template, triggerLen, null);
        });

        clipListener = () -> {
            if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) return;
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence textCs = clip.getItemAt(0).coerceToText(this);
            if (textCs == null || textCs.length() == 0) return;

            String text = textCs.toString();
            if (resumeStore != null) resumeStore.clearIfTextDiffers(text);
            SensitivePatternDetector.Kind kind = SensitivePatternDetector.classify(text);

            if (kind != SensitivePatternDetector.Kind.NONE || ClipboardActions.isSensitive(clip)) {
                if (kind != SensitivePatternDetector.Kind.NONE) vaultStore.add(text, kind);
                scheduleClipboardPurge();
                // Neutral feedback: never reveal the category or the content on screen.
                if (!ClipboardActions.isSensitive(clip)) {
                    Toast.makeText(this, "Saved to vault", Toast.LENGTH_SHORT).show();
                }
            } else {
                historyStore.add(text);
            }
        };

        IntentFilter filter = new IntentFilter(TypingForegroundService.ACTION_STOP_TYPING);
        filter.addAction(ACTION_TYPE_CLIPBOARD);
        ContextCompat.registerReceiver(this, stopReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        IntentFilter themeFilter = new IntentFilter(ThemeManager.ACTION_THEME_CHANGED);
        ContextCompat.registerReceiver(this, themeReceiver, themeFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        themeReceiverRegistered = true;
    }

    private void scheduleClipboardPurge() {
        if (clipboardManager != null) {
            ClipboardActions.scheduleClear(this, clipboardManager.getPrimaryClip());
        }
    }

    @Override
    public void onDestroy() {
        if (destroyed) return;
        destroyed = true;
        pauseTypingForResume();
        // Unregister the clip listener if onFinishInputView never ran (e.g. service
        // destroyed while the input view was still shown)
        if (clipboardManager != null && clipListener != null && clipListenerRegistered) {
            try {
                clipboardManager.removePrimaryClipChangedListener(clipListener);
            } catch (Exception e) {
                Log.w(TAG, "removePrimaryClipChangedListener failed", e);
            }
            clipListenerRegistered = false;
        }
        try {
            unregisterReceiver(stopReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "stopReceiver not registered", e);
        }
        if (themeReceiverRegistered) {
            try {
                unregisterReceiver(themeReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "themeReceiver not registered", e);
            }
            themeReceiverRegistered = false;
        }
        // Deterministically release the SpeechRecognizer held by the voice manager
        if (voiceManager != null) { voiceManager.cancel(); voiceManager = null; }
        if (typingTts != null) { typingTts.shutdown(); typingTts = null; }
        if (aiRewrite != null) { aiRewrite.shutdown(); aiRewrite = null; }
        super.onDestroy();
    }

    private Context buildThemedContext() {
        int mode = ThemeManager.getMode(this);
        android.content.res.Configuration configuration =
                new android.content.res.Configuration(getResources().getConfiguration());
        int nightMode;
        if (mode == ThemeManager.MODE_DARK) {
            nightMode = android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } else if (mode == ThemeManager.MODE_LIGHT) {
            nightMode = android.content.res.Configuration.UI_MODE_NIGHT_NO;
        } else {
            nightMode = getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        }
        configuration.uiMode = (configuration.uiMode
                & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        return createConfigurationContext(configuration);
    }

    /** Inflates popups from the active theme context so colors resolve in dark mode too. */
    private android.view.LayoutInflater themedInflater() {
        return getLayoutInflater().cloneInContext(buildThemedContext());
    }

    /** Updates the status strip on both the normal and the focus keyboard. */
    private void setStatus(String text) {
        if (statusView != null) statusView.setText(text);
        if (focusStatusView != null) focusStatusView.setText(text);
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Reflects recording state and permission state on the mic key. */
    private void refreshMicUi(boolean recording) {
        if (micButton == null) return;
        if (recording) {
            micButton.setAlpha(1f);
            if (micLabel != null) micLabel.setText("Listening…");
        } else {
            boolean permitted = hasMicPermission();
            micButton.setAlpha(permitted ? 1f : 0.35f);
            if (micLabel != null) {
                micLabel.setText(permitted ? "Hold to speak" : "Mic needs permission");
            }
        }
    }

    private void rebuildInputView() {
        try {
            setInputView(null);
            setInputView(onCreateInputView());
        } catch (Exception e) {
            // The next normal IME view creation will use the saved mode.
            Log.w(TAG, "rebuildInputView failed; will recreate on next view", e);
        }
    }

    @Override
    public View onCreateInputView() {
        boolean ttsEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_TTS_ON_TAP, false);
        if (ttsEnabled && typingTts == null) typingTts = new TypingTts(this);
        if (!ttsEnabled && typingTts != null) { typingTts.shutdown(); typingTts = null; }
        Context themedContext = buildThemedContext();
        if (getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_FOCUS_MODE, false)) {
            View focus = android.view.LayoutInflater.from(themedContext)
                    .inflate(R.layout.ime_layout_focus, null);
            wireFocusLayout(focus);
            return focus;
        }
        View view = android.view.LayoutInflater.from(themedContext)
                .inflate(R.layout.ime_layout, null);

        View btnType = view.findViewById(R.id.btn_type_clipboard);
        View btnHistory = view.findViewById(R.id.btn_history);
        View btnTemplates = view.findViewById(R.id.btn_templates);
        View btnVault = view.findViewById(R.id.btn_vault);
        View btnStop = view.findViewById(R.id.btn_stop);
        View btnSettings = view.findViewById(R.id.btn_settings);
        View btnMic = view.findViewById(R.id.btn_mic);
        View btnBackspace = view.findViewById(R.id.btn_backspace);
        View btnRewrite = view.findViewById(R.id.btn_rewrite);

        statusView = view.findViewById(R.id.tv_ime_status);
        micButton = btnMic;
        micLabel = view.findViewById(R.id.tv_mic_label);
        vaultButton = btnVault;
        setStatus("Ready");
        refreshMicUi(false);
        // Dim the vault key when there is nothing to open yet (still tappable to set up).
        if (vaultButton != null && vaultStore != null && vaultStore.getAll().isEmpty()) {
            vaultButton.setAlpha(0.5f);
        }

        btnType.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startTypingFromClipboard();
        });
        btnHistory.setOnClickListener(this::showHistoryPopup);
        if (btnTemplates != null) {
            btnTemplates.setOnClickListener(this::showTemplatesPickerPopup);
        }
        if (btnVault != null) {
            btnVault.setOnClickListener(v -> {
                Intent intent = new Intent(this, VaultActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            });
        }
        if (btnMic != null) {
            btnMic.setOnTouchListener(new View.OnTouchListener() {
                private boolean cancelled;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            cancelled = false;
                            startVoiceRecording();
                            v.setPressed(true);
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            boolean outside = event.getX() < 0 || event.getX() > v.getWidth()
                                    || event.getY() < 0 || event.getY() > v.getHeight();
                            if (outside && !cancelled) {
                                cancelled = true;
                                v.setPressed(false);
                                cancelVoiceRecording();
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            v.setPressed(false);
                            if (!cancelled) stopVoiceRecordingAndTranscribe();
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            v.setPressed(false);
                            if (!cancelled) {
                                cancelled = true;
                                cancelVoiceRecording();
                            }
                            return true;
                        default:
                            return false;
                    }
                }
            });
        }
        if (btnBackspace != null) {
            btnBackspace.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                deleteOne();
            });
            btnBackspace.setOnLongClickListener(v -> {
                showDeleteMenu(v);
                return true;
            });
        }
        if (btnRewrite != null) {
            btnRewrite.setOnClickListener(this::showRewritePopup);
            aiRewrite.checkAvailability(available -> btnRewrite.setAlpha(available ? 1f : 0.35f));
        }
        btnStop.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            stopTyping();
        });
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        return view;
    }

    private void wireFocusLayout(View view) {
        focusStatusView = view.findViewById(R.id.focus_tv_status);
        setStatus("Ready");
        view.findViewById(R.id.focus_btn_type).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            startTypingFromClipboard();
        });
        view.findViewById(R.id.focus_btn_stop).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            stopTyping();
        });
        View delete = view.findViewById(R.id.focus_btn_backspace);
        delete.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            deleteOne();
        });
        delete.setOnLongClickListener(v -> { showDeleteMenu(v); return true; });
        view.findViewById(R.id.focus_btn_more).setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
    }

    private void startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Grant microphone permission first", Toast.LENGTH_LONG).show();
            return;
        }
        if (voiceManager == null) {
            voiceManager = new VoiceInputManager(this, voiceCallback);
        }
        if (voiceManager.isRunning()) return;
        stopTyping();
        voiceManager.start();
        refreshMicUi(true);
        setStatus("Listening…");
    }

    private void stopVoiceRecordingAndTranscribe() {
        if (voiceManager != null && voiceManager.isRunning()) voiceManager.stop();
        refreshMicUi(false);
        setStatus("");
    }

    private void cancelVoiceRecording() {
        if (voiceManager != null && voiceManager.isRunning()) {
            voiceManager.cancel();
            if (voiceToast != null) voiceToast.cancel();
            Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
        }
        refreshMicUi(false);
        setStatus("");
    }

    /** Deletes the character immediately to the left of the cursor. */
    private void deleteOne() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) inputConnection.deleteSurroundingText(1, 0);
    }

    /** Deletes the previous word and any whitespace between it and the cursor. */
    private void deleteWord() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null) return;

        CharSequence beforeCursor = inputConnection.getTextBeforeCursor(40, 0);
        if (beforeCursor == null || beforeCursor.length() == 0) return;
        String before = beforeCursor.toString();

        int wordEnd = before.length();
        while (wordEnd > 0 && Character.isWhitespace(before.charAt(wordEnd - 1))) wordEnd--;
        int wordStart = wordEnd;
        while (wordStart > 0 && !Character.isWhitespace(before.charAt(wordStart - 1))) wordStart--;

        if (wordEnd == wordStart) {
            deleteOne();
            return;
        }
        inputConnection.deleteSurroundingText(before.length() - wordStart, 0);
    }

    /** Clears the text on both sides of the cursor in the current input field. */
    private void deleteAll() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null) return;

        CharSequence before = inputConnection.getTextBeforeCursor(100000, 0);
        CharSequence after = inputConnection.getTextAfterCursor(100000, 0);
        int beforeLength = before == null ? 0 : before.length();
        int afterLength = after == null ? 0 : after.length();
        inputConnection.deleteSurroundingText(beforeLength, afterLength);
    }

    /** Shows an IME-window-safe deletion menu after a long press on Backspace. */
    private void showDeleteMenu(View anchor) {
        View popupView = themedInflater().inflate(R.layout.popup_delete_menu, null);
        TextView one = popupView.findViewById(R.id.menu_backspace);
        TextView word = popupView.findViewById(R.id.menu_delete_word);
        TextView all = popupView.findViewById(R.id.menu_clear_field);

        PopupWindow menu = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        menu.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        menu.setOutsideTouchable(true);
        menu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        one.setOnClickListener(v -> { menu.dismiss(); deleteOne(); });
        word.setOnClickListener(v -> { menu.dismiss(); deleteWord(); });
        all.setOnClickListener(v -> { menu.dismiss(); deleteAll(); });

        popupView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = popupView.getMeasuredHeight();
        menu.showAsDropDown(anchor, 0, -anchor.getHeight() - height);
    }

    /** Rewrites the current field contents on-device, then re-types the result with human cadence. */
    private void showRewritePopup(View anchor) {
        if (!getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_AI_REWRITE, true)) {
            Toast.makeText(this, "AI rewriting is off in Settings", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!aiRewrite.isAvailable()) {
            Toast.makeText(this, aiRewrite.getStatusMessage(),
                    Toast.LENGTH_LONG).show();
            aiRewrite.checkAvailability(available -> {
                Toast.makeText(this, available ? "Rewrite ready — tap Rewrite again."
                        : aiRewrite.getStatusMessage(), Toast.LENGTH_LONG).show();
            });
            return;
        }
        String source = readCurrentFieldText();
        if (source == null || source.trim().isEmpty()) {
            Toast.makeText(this, "Nothing in the field to rewrite", Toast.LENGTH_SHORT).show();
            return;
        }
        if (source.length() > REWRITE_MAX_CHARS) {
            Toast.makeText(this, "Use a draft up to " + REWRITE_MAX_CHARS
                    + " characters in the separate Rewrite screen.", Toast.LENGTH_LONG).show();
            return;
        }
        final String textToRewrite = source;
        final InputConnection rewriteConnection = getCurrentInputConnection();
        final String rewritePackage = currentPackage;

        View popupView = themedInflater().inflate(R.layout.popup_rewrite, null);
        TextView status = popupView.findViewById(R.id.tv_rewrite_status);

        PopupWindow popup = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        int[] optionIds = {
                R.id.rewrite_professional, R.id.rewrite_friendly, R.id.rewrite_shorter,
                R.id.rewrite_longer, R.id.rewrite_rephrase
        };
        AiRewriteManager.Tone[] tones = {
                AiRewriteManager.Tone.PROFESSIONAL, AiRewriteManager.Tone.FRIENDLY,
                AiRewriteManager.Tone.SHORTER, AiRewriteManager.Tone.ELABORATE,
                AiRewriteManager.Tone.REPHRASE
        };
        final boolean[] running = {false};
        View.OnClickListener handler = v -> {
            if (running[0]) return;
            running[0] = true;
            status.setVisibility(View.VISIBLE);
            status.setText("Rewriting…");
            for (int id : optionIds) popupView.findViewById(id).setEnabled(false);

            int optionIndex = 0;
            for (int k = 0; k < optionIds.length; k++) {
                if (optionIds[k] == v.getId()) { optionIndex = k; break; }
            }
            AiRewriteManager.Tone tone = tones[optionIndex];
            aiRewrite.rewrite(textToRewrite, tone, new AiRewriteManager.Callback() {
                @Override public void onResult(String rewritten) {
                    if (!popup.isShowing()) return;
                    if (!rewritePackage.equals(currentPackage)
                            || rewriteConnection != getCurrentInputConnection()
                            || !textToRewrite.equals(readCurrentFieldText())) {
                        running[0] = false;
                        status.setText("The field changed. Close this menu and try again.");
                        for (int id : optionIds) popupView.findViewById(id).setEnabled(true);
                        return;
                    }
                    popup.dismiss();
                    deleteAll();
                    startTypingText(rewritten);
                }

                @Override public void onError(String message) {
                    if (!popup.isShowing()) return;
                    running[0] = false;
                    status.setText("Failed: " + message);
                    for (int id : optionIds) popupView.findViewById(id).setEnabled(true);
                }
            });
        };
        for (int id : optionIds) popupView.findViewById(id).setOnClickListener(handler);

        popupView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = popupView.getMeasuredHeight();
        if (popupHeight == 0) popupHeight = 400;
        popup.showAsDropDown(anchor, 0, -anchor.getHeight() - popupHeight);
    }

    /** Reads up to 5000 chars on each side of the cursor. */
    private String readCurrentFieldText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return null;
        CharSequence before = ic.getTextBeforeCursor(5000, 0);
        CharSequence after = ic.getTextAfterCursor(5000, 0);
        StringBuilder sb = new StringBuilder();
        if (before != null) sb.append(before);
        if (after != null) sb.append(after);
        return sb.toString();
    }

    private final VoiceInputManager.Callback voiceCallback = new VoiceInputManager.Callback() {
        @Override
        public void onPartial(String text) {
            if (voiceToast != null) voiceToast.cancel();
            voiceToast = Toast.makeText(HumanTypingIME.this,
                    "Listening: " + text, Toast.LENGTH_SHORT);
            voiceToast.show();
        }

        @Override
        public void onFinal(String processedText) {
            if (voiceToast != null) voiceToast.cancel();
            refreshMicUi(false);
            setStatus("");
            if (processedText == null || processedText.isEmpty()) {
                Toast.makeText(HumanTypingIME.this,
                        "Nothing recognized", Toast.LENGTH_SHORT).show();
                return;
            }
            startTypingText(processedText);
        }

        @Override
        public void onError(String message) {
            if (voiceToast != null) voiceToast.cancel();
            refreshMicUi(false);
            setStatus("");
            Toast.makeText(HumanTypingIME.this, message, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onReady() {
            Toast.makeText(HumanTypingIME.this, "Listening…", Toast.LENGTH_SHORT).show();
            setStatus("Listening…");
        }

        @Override
        public void onEnd() {
            refreshMicUi(false);
            setStatus("");
        }
    };

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (triggerWatcher != null) triggerWatcher.clear();
        currentPackage = info != null && info.packageName != null ? info.packageName : "";
        if (clipboardManager != null && clipListener != null && !clipListenerRegistered) {
            clipboardManager.addPrimaryClipChangedListener(clipListener);
            clipListenerRegistered = true;
        }
        // Capture whatever is currently on the clipboard too
        if (clipboardManager != null && clipboardManager.hasPrimaryClip()) {
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence cs = clip.getItemAt(0).coerceToText(this);
                if (cs != null && cs.length() > 0) {
                    String text = cs.toString();
                    if (resumeStore != null) resumeStore.clearIfTextDiffers(text);
                    SensitivePatternDetector.Kind kind = SensitivePatternDetector.classify(text);
                    if (kind != SensitivePatternDetector.Kind.NONE || ClipboardActions.isSensitive(clip)) {
                        if (kind != SensitivePatternDetector.Kind.NONE) vaultStore.add(text, kind);
                        scheduleClipboardPurge();
                    } else {
                        historyStore.add(text);
                    }
                }
            }
        }
        handler.post(this::consumeTypingHandoff);
    }

    private void consumeTypingHandoff() {
        if (destroyed || !isInputViewShown() || getCurrentInputConnection() == null
                || currentPackage.isEmpty() || getPackageName().equals(currentPackage)) return;
        String text = ClipboardActions.consumeForTyping(this);
        if (text != null && !text.isEmpty()) startTypingText(text);
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        if (clipboardManager != null && clipListener != null && clipListenerRegistered) {
            clipboardManager.removePrimaryClipChangedListener(clipListener);
            clipListenerRegistered = false;
        }
        pauseTypingForResume();
        super.onFinishInputView(finishingInput);
    }

    private void showHistoryPopup(View anchor) {
        final List<String> items = historyStore.getAll();

        if (items.isEmpty()) {
            Toast.makeText(this, "History is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // Inflate the popup layout
        View popupView = themedInflater().inflate(R.layout.popup_history, null);
        ListView listView = popupView.findViewById(R.id.lv_history);
        View btnClear = popupView.findViewById(R.id.btn_clear_history);

        // Create display strings (truncated for the list)
        String[] display = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            String s = items.get(i).replace("\n", " ");
            display[i] = s.length() > 50 ? s.substring(0, 47) + "..." : s;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.item_history, R.id.tv_history_item, display);
        listView.setAdapter(adapter);

        // Create the PopupWindow
        PopupWindow popupWindow = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);

        // Crucial for IME: Prevent the popup from stealing the input connection
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);

        // Dismiss when touching outside
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Handle item clicks
        listView.setOnItemClickListener((parent, view, position, id) -> {
            popupWindow.dismiss();
            startTypingText(items.get(position));
        });

        // Handle clear button
        btnClear.setOnClickListener(v -> {
            historyStore.clear();
            popupWindow.dismiss();
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
        });

        // --- FIX: Measure the popup and show it ABOVE the anchor ---
        popupView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = popupView.getMeasuredHeight();

        if (popupHeight == 0) popupHeight = 480;
        int gap = (int) (8 * getResources().getDisplayMetrics().density);
        popupWindow.showAsDropDown(anchor, 0, -anchor.getHeight() - popupHeight - gap);
    }

    private void showTemplatesPickerPopup(View anchor) {
        final List<Template> templates = templateRepository.getAll();
        if (templates.isEmpty()) {
            Toast.makeText(this, "No templates saved", Toast.LENGTH_SHORT).show();
            return;
        }

        View popupView = themedInflater().inflate(R.layout.popup_templates, null);
        ListView listView = popupView.findViewById(R.id.lv_templates);
        View btnClose = popupView.findViewById(R.id.btn_close_templates);

        String[] display = new String[templates.size()];
        for (int i = 0; i < templates.size(); i++) {
            Template t = templates.get(i);
            display[i] = "::" + t.getTrigger() + " - " + t.getName();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.item_history, R.id.tv_history_item, display);
        listView.setAdapter(adapter);

        PopupWindow popupWindow = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            popupWindow.dismiss();
            showTemplateFillPopup(templates.get(position), 0, anchor);
        });

        btnClose.setOnClickListener(v -> popupWindow.dismiss());

        popupView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = popupView.getMeasuredHeight();
        if (popupHeight == 0) popupHeight = 480;
        int gap = (int) (8 * getResources().getDisplayMetrics().density);
        popupWindow.showAsDropDown(anchor, 0, -anchor.getHeight() - popupHeight - gap);
    }

    private void showTemplateFillPopup(Template template, int triggerLength, View anchor) {
        if (template == null) return;
        List<String> vars = template.getVariables();

        if (vars.isEmpty()) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null && triggerLength > 0) {
                ic.deleteSurroundingText(triggerLength, 0);
            }
            startTypingText(template.getBody());
            return;
        }

        View popupView = themedInflater().inflate(R.layout.popup_template_fill, null);
        TextView tvTitle = popupView.findViewById(R.id.tv_template_title);
        TextView tvPreview = popupView.findViewById(R.id.tv_template_preview);
        LinearLayout fieldsLayout = popupView.findViewById(R.id.layout_template_fields);
        Button btnCancel = popupView.findViewById(R.id.btn_cancel_template);
        Button btnType = popupView.findViewById(R.id.btn_type_template);

        tvTitle.setText(template.getName() + " (::" + template.getTrigger() + ")");
        tvPreview.setText(template.getBody());

        Map<String, EditText> fieldMap = new HashMap<>();
        for (String var : vars) {
            TextView label = new TextView(this);
            label.setText("{" + var + "}:");
            label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            label.setTextSize(12f);
            label.setPadding(0, 8, 0, 2);
            fieldsLayout.addView(label);

            EditText et = new EditText(this);
            et.setHint("Value for " + var);
            et.setTextSize(13f);
            fieldsLayout.addView(et);
            fieldMap.put(var, et);
        }

        PopupWindow popup = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        btnCancel.setOnClickListener(v -> popup.dismiss());

        btnType.setOnClickListener(v -> {
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, EditText> entry : fieldMap.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getText().toString().trim());
            }
            String rendered = template.render(values);

            InputConnection ic = getCurrentInputConnection();
            if (ic != null && triggerLength > 0) {
                ic.deleteSurroundingText(triggerLength, 0);
            }
            popup.dismiss();
            startTypingText(rendered);
        });

        popupView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int h = popupView.getMeasuredHeight();
        if (h == 0) h = 420;

        View anchorView = anchor;
        if (anchorView == null) {
            // Anchor to the IME's input view if available, else the decor view
            View iv = getWindow() != null && getWindow().getWindow() != null
                    ? getWindow().getWindow().getDecorView() : null;
            anchorView = iv;
        }
        if (anchorView == null) {
            // Last resort: just show centered
            popup.showAtLocation(getWindow().getWindow().getDecorView(), Gravity.CENTER, 0, 0);
            return;
        }
        popup.showAsDropDown(anchorView, 0, -anchorView.getHeight() - h);
    }

    private void startTypingFromClipboard() {
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) return;
        ClipData clip = clipboardManager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence clipboardText = clip.getItemAt(0).coerceToText(this);
        if (clipboardText == null || clipboardText.length() == 0) return;
        String text = clipboardText.toString();

        if (resumeStore != null) resumeStore.clearIfTextDiffers(text);
        TypingResumeStore.State resume = resumeStore == null
                ? null : resumeStore.getFor(currentPackage, text);
        if (resume != null) {
            stopTyping(false);
            Toast.makeText(this, "Resuming from character " + resume.index
                    + " of " + text.length(), Toast.LENGTH_SHORT).show();
            beginTyping(text, resume.index);
        } else {
            stopTyping(true);
            beginTyping(text, 0);
        }
    }

    private void startTypingText(String text) {
        stopTyping();
        beginTyping(text, 0);
    }

    private void beginTyping(String text, int startIndex) {
        if (telemetry != null) {
            telemetry.reset();
        }
        isTyping = true;
        activeText = text;
        activeIndex = startIndex;
        setStatus("Typing…");

        // Start foreground service for long texts
        if (text.length() > LONG_TEXT_THRESHOLD) {
            Intent svc = new Intent(this, TypingForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        }

        typeText(text, startIndex);
    }

    private void stopTyping() {
        stopTyping(true);
    }

    private void stopTyping(boolean clearResume) {
        isTyping = false;
        handler.removeCallbacksAndMessages(null);
        stopService(new Intent(this, TypingForegroundService.class));
        setStatus("Stopped");
        if (clearResume && resumeStore != null) {
            resumeStore.clear();
            activeText = null;
            activeIndex = 0;
        }
    }

    private void typeText(String text) {
        typeText(text, 0);
    }

    private void typeText(String text, int startIndex) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            isTyping = false;
            return;
        }
        activeText = text;
        activeIndex = startIndex;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Try app-specific profile first, fall back to default
        String prefix = currentPackage.isEmpty() ? "" : currentPackage + "_";
        int minDelay = prefs.getInt(prefix + KEY_MIN_DELAY,
                prefs.getInt(KEY_MIN_DELAY, DEF_MIN_DELAY));
        int maxDelay = prefs.getInt(prefix + KEY_MAX_DELAY,
                prefs.getInt(KEY_MAX_DELAY, DEF_MAX_DELAY));
        int punctDelay = prefs.getInt(prefix + KEY_PUNCT_DELAY,
                prefs.getInt(KEY_PUNCT_DELAY, DEF_PUNCT_DELAY));
        float typoProb = prefs.getFloat(prefix + KEY_TYPO_PROB,
                prefs.getFloat(KEY_TYPO_PROB, DEF_TYPO_PROB));

        int[] index = {startIndex};
        Runnable[] typeRunnable = new Runnable[1];

        typeRunnable[0] = new Runnable() {
            @Override
            public void run() {
                if (!isTyping || index[0] >= text.length()) {
                    if (isTyping) {
                        finishTyping(text, index[0]);
                    } else {
                        isTyping = false;
                    }
                    return;
                }

                char c = text.charAt(index[0]);

                // Handle newlines via KeyEvent for maximum compatibility
                if (c == '\n') {
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                    if (telemetry != null) telemetry.recordKeystroke();
                    index[0]++;
                    activeIndex = index[0];
                    scheduleNext(typeRunnable[0], text, index, minDelay, maxDelay, punctDelay);
                    return;
                }

                // Handle tabs via KeyEvent
                if (c == '\t') {
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB));
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB));
                    if (telemetry != null) telemetry.recordKeystroke();
                    index[0]++;
                    activeIndex = index[0];
                    scheduleNext(typeRunnable[0], text, index, minDelay, maxDelay, punctDelay);
                    return;
                }

                // Handle surrogate pairs (emojis, rare Unicode)
                if (Character.isHighSurrogate(c) && index[0] + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(index[0] + 1))) {
                    String surrogatePair = text.substring(index[0], index[0] + 2);
                    ic.commitText(surrogatePair, 1);
                    if (telemetry != null) telemetry.recordKeystroke();
                    index[0] += 2; // skip both halves
                    activeIndex = index[0];
                    scheduleNext(typeRunnable[0], text, index, minDelay, maxDelay, punctDelay);
                    return;
                }

                // Handle combining marks (accents that attach to previous char)
                if (Character.getType(c) == Character.NON_SPACING_MARK) {
                    ic.commitText(String.valueOf(c), 1);
                    if (telemetry != null) telemetry.recordKeystroke();
                    index[0]++;
                    activeIndex = index[0];
                    scheduleNext(typeRunnable[0], text, index, minDelay, maxDelay, punctDelay);
                    return;
                }

                // Standard character with typo simulation (restricted to ASCII Latin letters)
                if (random.nextFloat() < typoProb && Character.isLetter(c) && c < 128) {
                    char wrong = (char) (c + (random.nextBoolean() ? 1 : -1));
                    ic.commitText(String.valueOf(wrong), 1);
                    if (telemetry != null) telemetry.recordKeystroke();
                    handler.postDelayed(() -> {
                        if (!isTyping) return;
                        ic.deleteSurroundingText(1, 0);
                        ic.commitText(String.valueOf(c), 1);
                        if (telemetry != null) telemetry.recordKeystroke();
                        index[0]++;
                        activeIndex = index[0];
                        scheduleNext(typeRunnable[0], text, index, minDelay, maxDelay, punctDelay);
                    }, randomDelay(minDelay, maxDelay));
                } else {
                    SharedPreferences accessibilityPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    long now = System.currentTimeMillis();
                    if (accessibilityPrefs.getBoolean(KEY_BOUNCE_KEYS, false)
                            && c == lastCommittedChar && now - lastCommittedAt < 500) {
                        index[0]++;
                        activeIndex = index[0];
                        scheduleNext(typeRunnable[0], text, index, minDelay, maxDelay, punctDelay);
                        return;
                    }
                    ic.commitText(String.valueOf(c), 1);
                    lastCommittedChar = c;
                    lastCommittedAt = now;
                    if (typingTts != null) typingTts.speak(c);
                    if (telemetry != null) telemetry.recordKeystroke();
                    index[0]++;
                    activeIndex = index[0];
                    scheduleNext(typeRunnable[0], text, index, minDelay, maxDelay, punctDelay);
                }
            }
        };

        handler.post(typeRunnable[0]);
    }

    private void scheduleNext(Runnable r, String text, int[] index,
                              int minDelay, int maxDelay, int punctDelay) {
        if (!isTyping || index[0] >= text.length()) {
            if (isTyping) {
                finishTyping(text, index[0]);
            } else {
                isTyping = false;
            }
            return;
        }
        char last = text.charAt(index[0] - 1);
        long delay = randomDelay(minDelay, maxDelay);

        // Longer pause after punctuation and whitespace
        if (last == ' ' || last == '.' || last == ',' || last == '\n'
                || last == '!' || last == '?' || last == ';' || last == ':') {
            delay += randomDelay(0, punctDelay);
        }

        // Emojis and surrogate pairs: extra pause to let renderers catch up
        if (Character.isLowSurrogate(last)) {
            delay += randomDelay(100, 200);
        }

        // Keep the status strip alive during long texts (every 25 chars).
        if ((index[0] % 25) == 0) {
            setStatus("Typing " + index[0] + " / " + text.length());
        }

        handler.postDelayed(r, delay);
    }

    private void pauseTypingForResume() {
        if (isTyping) saveResumeState();
        isTyping = false;
        handler.removeCallbacksAndMessages(null);
        stopService(new Intent(this, TypingForegroundService.class));
    }

    private void saveResumeState() {
        if (resumeStore == null || activeText == null
                || activeIndex <= 0 || activeIndex >= activeText.length()) {
            return;
        }
        resumeStore.save(currentPackage, activeText, activeIndex);
    }

    private void finishTyping(String text, int completedIndex) {
        isTyping = false;
        activeIndex = completedIndex;
        activeText = null;
        setStatus("Done");
        if (resumeStore != null) resumeStore.clear();
        maybeAttachProof(text);
    }

    private void maybeAttachProof(String text) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_PROOF_ENABLED, true);
        if (!enabled) return;
        if (telemetry == null || !telemetry.hasEnoughSamples()) return;

        try {
            FingerprintBuilder.Fingerprint fp =
                    FingerprintBuilder.build(telemetry.getIntervals());
            byte[] fpBytes = fp.serialize();

            String sigAlg = android.os.Build.VERSION.SDK_INT >= 33 ? "ed25519" : "ecdsa-p256";
            byte[] signature = SignatureCrypto.sign(fpBytes);

            String tagged = ProofEmbedder.embed(text, sigAlg, signature, fpBytes);

            InputConnection ic = getCurrentInputConnection();
            if (ic == null) return;

            // Append only the invisible metadata tag at the end
            String tagOnly = tagged.substring(text.length());
            ic.commitText(tagOnly, 1);
        } catch (Exception e) {
            // Best-effort proofing: never block or surface an error to the user.
            Log.w(TAG, "Proof attachment failed (non-blocking)", e);
        }
    }

    private long randomDelay(int min, int max) {
        long delay = max <= min ? min : min + random.nextInt(max - min + 1);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SLOW_KEYS, false)) delay = Math.max(delay, DEF_SLOW_KEYS_MS);
        return delay;
    }

    @Override
    public void onFinishInput() {
        pauseTypingForResume();
        super.onFinishInput();
        if (voiceManager != null && voiceManager.isRunning()) {
            voiceManager.cancel();
        }
    }
}
//（注：内容由AI生成）
