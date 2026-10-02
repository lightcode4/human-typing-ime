# Human Typing launcher artwork

The launcher uses the complete white 3D fingerprint keycap as its foreground, with transparent space outside the keycap, over an opaque full-bleed cyan-to-blue gradient background. The keycap's own rounded edges, bevels, and sidewall are retained from the supplied artwork. The outside mockup backdrop, ground shadow, and watermark are removed. The background has no baked-in outer mask or rounded corners.

## Resources

Each folder under `app/src/main/res/mipmap-<density>/` contains the following PNGs:

- `ic_launcher_fg.png`: padded adaptive foreground, referenced by both adaptive-icon XML files.
- `ic_launcher_bg.png`: opaque adaptive background, referenced by both adaptive-icon XML files.
- `app_icon_human_typing_ime_mipmap_<density>.png`: separate foreground export at the originally requested size.
- `app_icon_human_typing_ime_background_mipmap_<density>.png`: separate background export at the originally requested size.
- `ic_launcher_legacy.png`: square fallback composition of the same keycap and gradient, shared by both non-v26 wrappers. No separate legacy-round bitmap is referenced, exported, or committed. The app's minimum SDK is 26, so its launchers use the adaptive XML resources. The rejected legacy-round artwork is not restored.

| Density | Requested separate exports | Adaptive foreground and background |
| --- | --- | --- |
| mdpi | 48 × 48 px | 108 × 108 px |
| hdpi | 72 × 72 px | 162 × 162 px |
| xhdpi | 96 × 96 px | 216 × 216 px |
| xxhdpi | 144 × 144 px | 324 × 324 px |
| xxxhdpi | 192 × 192 px | 432 × 432 px |

[Android's adaptive-icon specification](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) requires a 108 dp layer canvas, with essential artwork between 48 and 66 dp. The foreground is centered within a 60 dp art area, leaving transparent overscan margins. The requested 48–192 px exports are retained separately; the app uses the correctly sized 108–432 px adaptive layers.

The integration files are `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`. Both reference `@mipmap/ic_launcher_bg` and `@mipmap/ic_launcher_fg`. The manifest already points to `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`. The About screen also displays the new launcher artwork. Existing notification artwork is unchanged.

## Source and previews

- [Current full-keycap foreground master](foreground-keycap-master.png)
- [Background master](background-master.png)
- [Current square composite preview](adaptive-preview-square.png)
- [Current circular-mask preview](adaptive-preview-circle.png)
- [Previous fingerprint-only master, retained for rollback](foreground-master.png)
- The older `preview-square.png` and `preview-circle.png` are retained with the previous artwork; they are not current previews.

The previews are desktop composites, not screenshots from an Android device. Actual launcher masks and motion effects vary by device.

## Regenerate density exports

Run from the repository root on Windows:

```powershell
.\tools\export-launcher-icons.ps1 -IncludeSquareFallback
```

The exporter uses System.Drawing, preserves foreground transparency, creates all five foreground/background density sets and current previews, and validates dimensions, opaque backgrounds, transparent foreground margins, and artwork sizing. It reads `foreground-keycap-master.png` and `background-master.png`; no image service is needed to regenerate exports. The `-IncludeSquareFallback` switch also regenerates the shared square fallback. There is no legacy-round export option.

The optional `-ForegroundMaster foreground-master.png` selects the previous fingerprint-only master.

## Image editing provenance

The supplied mockups were prepared with the built-in image-editing tool, not the CLI. The current foreground preserves the entire white keycap from `C:\Users\CATALYST\Documents\new\豆包 (1).png`, rather than extracting only the fingerprint. The already prepared blue gradient is reused unchanged. Original attachment files and the previous masters were not changed. Deterministic sizing and compositing were performed locally by the exporter.

### Initial keycap extraction prompt (discarded intermediate)

Use case: background-extraction.
Asset type: Human Typing IME Android adaptive-icon foreground master.
Input image 1: edit target, the supplied white 3D keycap containing a pale blue fingerprint and glowing cyan-and-white triangular accent.
Primary request: Extract the ENTIRE white keycap as one clean cutout onto a genuinely transparent background. Remove only the surrounding photographic white/blue backdrop, the ground shadow outside the object, and the watermark outside the object.
Preserve unchanged: the complete white rounded-square keycap, its sculpted bevels and lower sidewall, its original proportions and soft white/pale-blue material shading, every pale blue fingerprint curve and rounded end, the small left-hand dot, and the white triangular accent with its compact cyan glow. The white surface between the fingerprint curves must stay opaque white: do NOT isolate only the fingerprint glyph.
Composition: a single complete centered keycap, not cropped, on a square transparent canvas with clear margin on every side. Keep the reference's near-front view and orientation.
Constraints: actual transparent alpha outside the object, no white rectangle behind it, no checkered pixels baked into the image, no outside scene, no new keyboard keys, no blue circular disk, no text, no watermark, no redesign.

### Initial keycap edge-cleanup prompt (discarded intermediate)

Use case: background-extraction.
Input image 1: edit target, the extracted transparent white 3D fingerprint keycap.
Primary request: Clean ONLY the alpha silhouette. Remove the isolated white flecks outside the tile, the jagged paper-like white fringe along the top and bottom edges, and stray pixels surrounding the keycap. Produce one smooth, continuous, anti-aliased outer edge with true transparent alpha everywhere outside it. No baked checkerboard, no white rectangular backdrop.
Preserve unchanged: the whole opaque white keycap face and thick sculpted lower sidewall, the current centered placement, dimensions and proportions, soft white-to-pale-blue shading, pale blue fingerprint curves, left dot, white triangular accent and cyan glow. All white between the fingerprint lines stays opaque; do not extract the fingerprint alone. No restyling, no new elements, no additional shadow, no text. Keep transparent square canvas and generous margin.

### Intermediate keycap preparation prompt (discarded)

Use case: background-extraction.
Asset type: production Android adaptive-icon foreground, clean 3D rendered keycap cutout.
Input image 1: reference and edit target for the complete keycap design.
Primary request: Prepare the complete white fingerprint keycap as a pristine isolated icon on TRUE TRANSPARENT ALPHA. Re-render the outer silhouette cleanly if needed instead of retaining photographic background remnants. The outer silhouette must be ONE mathematically smooth rounded-square object with a continuous gently beveled edge, no jagged white fringe, no white flecks, no scraps, and no pixels floating outside the object.
Keep the complete white keycap face and thick pale-blue lower sidewall, original pale-blue fingerprint paths and left dot, white triangle with compact cyan glow, delicate ceramic-like 3D materials, centered front-facing orientation, original relative arrangement and proportions. Keep opaque white between every fingerprint curve. This is the white keycap, NOT the fingerprint alone and NOT a circular blue disk.
Empty canvas outside the keycap must be completely transparent, with smooth anti-aliased edge transitions immediately at the silhouette. No ground, photographic background, outside shadow, halo, rectangle, checkerboard pattern, text or watermark. Square canvas, full object visible with balanced transparent margins.

### Direct keycap preparation prompt (discarded intermediate)

Use case: background-extraction.
Asset type: transparent Android adaptive launcher foreground.
Image 1 is the design reference and edit target. Prepare the ENTIRE white ceramic fingerprint keycap, including its sculpted front face and pale-blue sidewall, as a pristine transparent-background PNG. Keep the exact pale-blue fingerprint curves, small left dot, downward white triangle and compact cyan glow, original proportions, near-front view, and delicate 3D shading.
Use a clean continuous pale-blue bevel to define the keycap's outer edge. The silhouette must be smooth, not ragged: four rounded corners joined by straight smooth sides. No scraps, edge flecks, floating pixels, paper texture, outer white halo, ground plane, or cast shadow. Preserve opaque white everywhere on the keycap face between the fingerprint paths. Only the outside background becomes transparent. Do not extract the fingerprint alone, add keyboard keys, or make a circular blue disk.
One complete centered object on a square TRUE ALPHA canvas with generous transparent margins; no background scene, checkerboard pixels, text, watermark, or border. Prioritize a clean production-ready alpha silhouette.

### Final keycap matte preparation prompt

Use case: precise-object-edit.
Image 1 is the edit target. Change ONLY the background outside the complete white fingerprint keycap to a perfectly uniform opaque charcoal color #202020. Remove the outer ground shadow and watermark. Keep the entire white 3D keycap exactly intact: rounded sculpted outer shape, bevel, pale-blue lower sidewall, blue fingerprint, left dot, white triangle and cyan glow. Keep the opaque white keycap surface between the fingerprint curves. Crisp smooth boundaries where the object meets the charcoal background; no stray white specks, irregular fringe, blue scraps, or background remnants outside the tile. Center the complete keycap with a generous uniform charcoal margin on a square canvas. This is an intermediate high-contrast matte for clean background removal. No extra objects or text.

### Final keycap transparency prompt

Use case: background-extraction.
Input image 1: edit target, the complete white fingerprint keycap on a charcoal matte.
Remove ONLY the charcoal background and replace it with true transparent alpha. Preserve the entire opaque white rounded-square keycap, including the pale-blue lower sidewall, smooth outer edge, bevel, blue fingerprint paths, left dot, white triangle, and cyan glow. All opaque white between the blue paths must remain opaque white. Preserve the existing keycap position, proportions, colors, and shading.
The keycap has one clean, smooth continuous silhouette with anti-aliased edges. Remove every dark background pixel and every stray pixel outside that silhouette. No white fringe, black fringe, opaque matte, outside shadow, flecks, paper-like scraps, checkerboard image, text, new objects, or redesign. Keep the square canvas and transparent margins. Production-ready cutout on genuine transparent alpha.

### Previous fingerprint-only cleanup prompt (retained provenance)

Use case: background-extraction. Edit target: the supplied transparent blue fingerprint emblem. Remove only the unwanted irregular WHITE patches, speckles, paper-like remnants and halos in the gaps between fingerprint strokes. All gaps and all exterior canvas must be truly transparent alpha, with smooth clean edges. Keep the small WHITE triangular accent at the lower-right; it is the only white shape that should remain. Preserve the existing blue fingerprint curves, rounded stroke ends, left dot, delicate sculpted shading and compact cyan glow around the triangle. No redesign, no text, no tile, no solid background. Keep the emblem centered and at its existing scale and placement. Production-ready Android adaptive-icon foreground, clean alpha cutout.

### Background prompt

Use case: precise-object-edit. Input image 1: edit target, blue gradient adaptive-icon background mockup. Turn the blue gradient surface in the middle into a flat full-bleed SQUARE background layer, extending the same cyan at the upper-left through sky blue in the center to richer blue at the lower-right across the whole square image. Remove rounded corners, the outer white backdrop, tile edges, 3D thickness, shadows, and all text. Smooth opaque blue gradient from edge to edge; no transparent margin, no fingerprint, no logo, no text, no texture noise, no watermark. Preserve the provided blue palette and gentle refined gradient appearance. This is a background layer for an Android adaptive launcher icon; Android applies the mask itself.
