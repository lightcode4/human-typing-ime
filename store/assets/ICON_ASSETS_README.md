# Human Typing icon asset pack

The current adaptive foreground retains the full white 3D fingerprint keycap. Its exterior is transparent. The background is an opaque, full-bleed blue gradient. The Play Store icon separately retains the keyboard beneath the keycap.

## Density files

All PNGs are installed under `app/src/main/res/mipmap-<density>/`.

| Density | Requested separate foreground/background exports | App adaptive layers |
| --- | --- | --- |
| mdpi | 48 × 48 px | 108 × 108 px |
| hdpi | 72 × 72 px | 162 × 162 px |
| xhdpi | 96 × 96 px | 216 × 216 px |
| xxhdpi | 144 × 144 px | 324 × 324 px |
| xxxhdpi | 192 × 192 px | 432 × 432 px |

Within each density folder:

- `app_icon_human_typing_ime_mipmap_<density>.png` is the foreground at the requested size.
- `app_icon_human_typing_ime_background_mipmap_<density>.png` is the background at the requested size.
- `ic_launcher_fg.png` and `ic_launcher_bg.png` are the correctly sized adaptive layers consumed by the app.

[Android's adaptive-icon specification](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) calls for 108 dp layers. Keep the larger adaptive files for integration; the 48–192 px files are separate requested exports.

## Integration

The existing `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` both use:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_bg"/>
    <foreground android:drawable="@mipmap/ic_launcher_fg"/>
</adaptive-icon>
```

The manifest already references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`. The round XML uses the same two adaptive layers; it is not a separate legacy-round illustration. Both non-v26 wrappers reference the same square `ic_launcher_legacy` composition. No separate legacy-round bitmap is referenced or included in the asset pack. Notification artwork is unchanged. Android can still apply a circular or rounded-square launcher mask, independent of the asset's square background.

## Play Store files

- [1024 × 1024 requested export](human-typing-play-icon-1024.png)
- [512 × 512 Play Console upload icon](human-typing-play-icon-512.png)

[Google Play requires](https://developer.android.com/distribute/google-play/resources/icon-design-specifications) a 512 × 512, 32-bit PNG no larger than 1024 KB for the App icon upload. The 1024 px image is the larger reusable export, not the upload size.

## Preparation and regeneration

Raster preparation used the built-in image-editing tool, not the CLI. Exact prompts, source descriptions, and previews are documented in [launcher/README.md](launcher/README.md) and [PLAY_ICON_README.md](PLAY_ICON_README.md). Original uploads and previous masters are preserved in the workspace.

Run from the repository root on Windows:

```powershell
.\tools\export-launcher-icons.ps1 -IncludeSquareFallback
.\tools\export-play-store-icon.ps1
.\tools\package-icon-assets.ps1
```

The archive `HumanTypingIME-icon-assets.zip` contains the current density exports, adaptive XML files, store exports, current masters, previews, documentation, and export scripts at their repository-relative paths. It excludes the rejected legacy-round artwork and does not include an APK or the rest of the app source. Use `-Replace` when intentionally regenerating an existing archive.

No GitHub push or Play Console upload is performed by these scripts.
