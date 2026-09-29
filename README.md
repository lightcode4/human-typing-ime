# Human Typing IME for Android

Custom Android Input Method Editor (IME) that reads the clipboard and types it out character-by-character with realistic human-like delays, jitter, punctuation pauses, and typo-corrections.

## Why this works
- Uses official Android `InputMethodService` and `InputConnection.commitText()`.
- Allowed to read clipboard on Android 10+ (Q) without extra background permissions because it runs as the user's active keyboard.
- Injects text as genuine keystroke inputs, bypassing anti-paste restrictions in web views and apps.

## Configured Parameters:
- Min Keystroke Delay: 30 ms
- Max Keystroke Delay: 120 ms
- Punctuation / Space Pause: +100–300 ms
- Typo Chance: 2.0%
- Correction Delay: 50–150 ms

## Setup Instructions:
1. Open this folder in Android Studio (Giraffe / Iguana / Koala or newer).
2. Sync Gradle and build the APK (`Build > Build Bundle(s) / APK(s) > Build APK(s)`).
3. Install on your Android phone or emulator.
4. Go to **Settings > System > Languages & input > On-screen keyboard > Manage keyboards**.
5. Enable **Human Typing IME**.
6. Switch to it when tapping any input field and tap **"Type Clipboard (Human-like)"**!
