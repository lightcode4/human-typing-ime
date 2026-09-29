# Data Safety Form — Answers & Notes

Verified against the codebase on 2026-09-18. Answer "No" to collection/sharing holds **only** because every data store is on-device and `allowBackup` is now `false` in the manifest — that change is required, not optional.

## Data Collection and Security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |

Everything the app handles (clipboard text, vault items, templates, rhythm profile, resume state) stays in the app's private storage; no data is transmitted by the app to the developer or any third party. The remaining questions (encryption in transit, deletion mechanism) will not appear after answering "No".

### Why "No" is defensible on the gray areas

- **SpeechRecognizer (voice input):** audio is handed to Android's platform speech service, not processed or transmitted by the app. Google's guidance treats data handled by Google's own on-device services as not collected by the developer. We now also request `EXTRA_PREFER_OFFLINE`. If you want maximum safety margin, you may optionally declare "Audio files → Voice recordings → Collected → Processed on-device only, not shared" — but the plain "No" is consistent with Google's documentation for platform speech recognition.
- **Proof-of-human tag:** the signature/fingerprint tag leaves the device only inside messages the user chooses to send; the developer has no access. Not a "collection."
- **Clipboard capture:** reads the clipboard on-device only; never transmitted. Note Google Play may still ask IME apps to complete the **Clipboard access declaration / permissions declaration** for `BIND_INPUT_METHOD` — answer that the clipboard is used to provide the app's core typing/history feature, on-device only.
- **ML Kit GenAI:** inference is on-device (AICore). Model components are delivered by Google Play services; the app sends no text.

## App Category Answers

- Is all data encrypted in transit? N/A (answered "No" above)
- Deletion request mechanism? N/A — though note the app does offer in-app deletion everywhere, which is worth mentioning in the listing.

## Data Types

All categories **Not collected / Not shared**: Location, Personal info, Financial info, Health & fitness, Messages, Photos & videos, Audio files, Files & docs, Calendar, Contacts, App activity, Web browsing, App info & performance, Device or other IDs.

## Other Play Console Items (from the original checklist, verified)

- **Accessibility declaration:** not needed — the manifest has no AccessibilityService. The keyboard uses InputMethodService with BIND_INPUT_METHOD. ✔
- **Permissions declaration:** RECORD_AUDIO (voice input) and the IME binding are normal runtime/core permissions; no special declaration form required for them.
- **Content rating:** standard questionnaire; no user-generated content, no sharing → expect Everyone/3+ (answer honestly in Console).
- **Target audience:** 18+ recommended (vault stores credentials/cards).
- **Version code:** currently 1 in `app/build.gradle`. The current final application ID is `com.scottomedo.humantyping`; keep this unchanged after the first Play upload.
- **Release signing:** `keystore.properties` is present and wired into `build.gradle`; run `gradlew.bat assembleRelease` and verify the APK is signed.
- **Assets needed:** 512×512 icon, 1024×500 feature graphic, ≥2 screenshots.
- **Privacy policy:** publish `PRIVACY_POLICY.md` (after filling `[YOUR-EMAIL]`) to a public URL and paste it in Console.

## Corrections vs. the original draft (why they mattered)

1. ❌ "Clipboard History: stored locally in an **encrypted database**" → it is plaintext app-private SharedPreferences. Policy now says "private storage"; vault is the encrypted part. (Encrypting history would be a nice future hardening.)
2. ❌ "Templates: stored locally in a **Room database**" → SharedPreferences, not Room.
3. ❌ "Typing telemetry … **never persisted**" → true, **but** the behavioral rhythm profile and typing-resume state *are* persisted locally; the policy now discloses both.
4. ❌ "Audio … **never sent to a server**" → not guaranteed without `EXTRA_PREFER_OFFLINE`; now requested, and the policy wording no longer over-promises.
5. ❌ "Rewrite as Professional, **Casual**, Shorter, or Longer" → the ML Kit beta supports Professional, Friendly, Shorter, Longer, Rephrase. Listing fixed.
6. ❌ "No data leaves your device" + `allowBackup="true"` → clipboard history/templates would have been uploaded in Android cloud backups. Manifest now `allowBackup="false"` (rebuild required).
7. ➕ Added honest disclosure of the proof-of-human zero-width tag in the privacy policy — reviewers and users should know text typed by the keyboard carries an invisible signature when that feature is enabled.
