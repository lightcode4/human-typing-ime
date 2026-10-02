# Google Play listing icon

The supplied keyboard-and-fingerprint mockup was prepared as opaque full-square store artwork using the built-in image-editing tool. The outside photographic backdrop, rounded blue tile boundary, exterior shadow, and watermark were removed. The white sculpted keyboard and fingerprint keycap were retained. This listing icon is separate from the adaptive launcher foreground/background assets.

## Deliverables

- [512 × 512 upload icon](human-typing-play-icon-512.png): use this file in Play Console's main store listing **App icon** field.
- [1024 × 1024 master export](human-typing-play-icon-1024.png): larger reusable export; do not upload it to the 512 px App icon field.
- [Edited full-resolution master](human-typing-play-icon-master.png): image-editing output used for exports.
- [Adaptive launcher assets](launcher/README.md): the installed app's icon layers and density exports.

[Google Play's icon specification](https://developer.android.com/distribute/google-play/resources/icon-design-specifications) requires 512 × 512 px, 32-bit PNG, sRGB, and no more than 1024 KB. Play supplies the outside rounded mask and drop shadow. The upload icon is a 32-bit PNG with fully opaque pixels and a full-bleed background; internal artwork shading is retained. Export validation checks dimensions, PNG bit depth/color type, opacity, and the upload size limit.

The original attachment `C:\Users\CATALYST\Documents\new\豆包 (10).png` is unchanged. Its actual source dimensions are 2048 × 2048 px; the 1024 px and 512 px files above are exact-size exports.

## Regenerate exports

Run from the repository root on Windows:

```powershell
.\tools\export-play-store-icon.ps1
```

No image service is needed to regenerate exports from the saved master. The older `human-typing-icon-source.png` is preserved but is not the current Play upload asset. None of these files have been uploaded to Play Console by this task.

## Exact image-editing prompt

Mode: built-in image editing, not CLI.

Use case: precise-object-edit.
Asset type: Google Play listing icon, full-square production artwork.
Input image 1: edit target, the supplied Human Typing white keyboard-and-fingerprint icon on a blue rounded tile.
Primary request: Prepare this exact design as a clean full-bleed square app-store icon. Keep the central sculpted WHITE fingerprint keycap and the subtle WHITE keyboard keys beneath it, with the same delicate pale-blue fingerprint curves, left dot and white triangular accent with cyan glow. Preserve their identity, relative arrangement, soft ceramic material and refined 3D shading.
Change only the outer presentation: remove the white photographic/mockup backdrop, all text and watermark, the outer rounded blue tile boundary and the exterior drop shadow. Extend the cyan-to-blue gradient background smoothly all the way to every edge and corner of the square image. Let Google Play apply the outside corner mask itself. Keep internal sculpted shadows inside the keyboard/keycap artwork, not around the square asset.
Composition: centered keyboard/keycap artwork occupies approximately 82 percent of the square width, leaving balanced blue breathing room, all essential fingerprint and key shapes comfortably away from the square corners. Front view, not a perspective product shot.
Avoid: text, watermark, new logos, added symbols, external border, outer white margin, rounded outside corners, transparency, noisy textures, changing the fingerprint design. Opaque full-square blue background; production-quality large master suitable for clean downsampling to 1024x1024 and 512x512.
