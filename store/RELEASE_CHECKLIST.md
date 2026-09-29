# Release Checklist

## Build

- [x] `com.scottomedo.humantyping` selected as the permanent application ID
- [x] Release APK produced
- [x] Release APK verified with APK Signature Scheme v2
- [ ] Re-run `:app:assembleRelease` after the Gradle wrapper lock is cleared
- [ ] Run `:app:lint` and resolve any release findings
- [ ] Increment `versionCode` for every Play upload after the first

## Device QA

Test on at least one Android 13/14+ physical device and one emulator.

- [ ] Install the release APK and launch the setup screen
- [ ] Enable Human Typing in system keyboard settings
- [ ] Switch to Human Typing in a plain text field
- [ ] Type clipboard text with realistic delays
- [ ] Pause, resume, and cancel a long typing job
- [ ] Verify backspace and long-press deletion
- [ ] Verify clipboard history search, tags, pins, todos, and deletion
- [ ] Verify sensitive clipboard detection, vault save, biometric unlock, and purge
- [ ] Verify voice input with offline recognition available and unavailable
- [ ] Verify templates, variables, trigger expansion, and picker insertion
- [ ] Verify rewrite behavior on supported and unsupported devices
- [ ] Verify Focus Mode, Slow Keys, Bounce Keys, and text-to-speech
- [ ] Verify foreground-service notification and Stop action during long typing
- [ ] Rotate the device and background/restore the app during each long flow

## Play Store

- [x] Publish `PRIVACY_POLICY.md` at a stable HTTPS URL and replace its placeholder
- [ ] Upload 512×512 app icon
- [ ] Upload 1024×500 feature graphic
- [x] Capture at least two phone screenshots from the release build
- [ ] Paste the listing from `PLAY_STORE_LISTING.md`
- [ ] Complete Data safety, content rating, target audience, and IME declarations
- [ ] Confirm the signed APK and version code in Play Console

- [x] Generate 1024x500 feature graphic
