# Privacy Policy — Human Typing IME

Public policy URL: https://human-typing-privacy.deoscottfield.chatgpt.site
> Every statement below was verified against the source code on 2026-09-18.

Last Updated: September 18, 2026

Human Typing is a keyboard application (IME) designed with privacy as its core principle. This policy explains what data the app handles and how.

## 1. Data Collection

The developer does not collect, receive, or share any personal data. There are no servers, no analytics, and no advertising SDKs. All data processed by the app stays on your device in the app's private storage.

## 2. What the App Stores on Your Device

- **Clipboard history, tags, pins, and todos:** stored in the app's private storage on this device only. Cloud backup for the app is disabled, so this data is not included in Android device backups.
- **Vault items:** passwords, cards, OTPs, and API keys detected in your clipboard are encrypted with AES-256-GCM using a key that never leaves the Android Keystore, and vault access is gated by biometric authentication. The system clipboard is auto-purged after a short delay.
- **Templates:** stored locally in the app's private storage.
- **Typing resume state:** when a long typing task is interrupted, the keyboard stores the app name, the text, and the position so it can offer to resume. This is cleared when typing completes or is cancelled.
- **Typing rhythm profile (optional):** if you create one in Settings, the app stores only aggregate timing statistics (for example mean and standard deviation of intervals) — never the typed content — and you can delete it at any time from Settings.
- **Typing telemetry:** used for the proof-of-human feature; kept in memory only and never written to storage.

## 3. Proof-of-Human Signature

When enabled, the keyboard appends an invisible metadata tag (a digital signature and aggregate timing fingerprint, encoded in zero-width characters) to text it types. This tag becomes part of the message you send and travels wherever you send it. It allows a reader holding your public key to verify that the text was typed through the app. It does not contain the text itself and cannot be used by the developer to track you.

## 4. Voice Input

Voice input uses Android's platform speech recognition service. The app requests on-device recognition where the device supports it. The app itself never receives, records, or stores your audio; any processing of audio is performed by the speech service under your device manufacturer's and Google's terms.

## 5. On-Device AI Rewriting

On supported devices, the optional rewrite feature runs entirely on the device (Gemini Nano via Android AICore). The app does not send text anywhere for rewriting. Model components are managed by Google Play services on the device; no rewrite text is transmitted by this app.

## 6. Permissions

- **RECORD_AUDIO:** only for the optional hold-to-speak feature.
- **USE_BIOMETRIC:** only to gate access to the encrypted vault.
- **FOREGROUND_SERVICE / FOREGROUND_SERVICE_DATA_SYNC / POST_NOTIFICATIONS:** only to show an ongoing notification while a long typing task runs.

The keyboard itself uses the standard Android InputMethodService (BIND_INPUT_METHOD); it does not use the AccessibilityService API.

## 7. Data Deletion

You can delete data individually in the app (clipboard history, vault items, templates, rhythm profile), or remove everything by clearing the app's storage in Android Settings or uninstalling the app. Because cloud backup is disabled, uninstalling removes all app data.

## 8. Children

The app is not directed at children and does not knowingly collect any data from anyone, including children.

## 9. Contact

For questions about this policy, contact: gidifix.software.team@proton.me
