param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$ForegroundMaster = 'foreground-keycap-master.png',
    [switch]$IncludeSquareFallback
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$taskDrawingReferences = @([Drawing.Bitmap].Assembly.Location, [Drawing.Rectangle].Assembly.Location)
$taskDrawingReferences += @(Get-ChildItem -LiteralPath $PSHOME -Filter 'System.Private.Windows*.dll' |
    ForEach-Object { $_.FullName })
$taskDrawingReferences = @($taskDrawingReferences | Select-Object -Unique)
Add-Type -ReferencedAssemblies $taskDrawingReferences -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;

public static class HumanTypingIconExport {
    public static Rectangle AlphaBounds(Bitmap image, int minimumAlpha) {
        int left = image.Width, top = image.Height, right = -1, bottom = -1;
        for (int y = 0; y < image.Height; y++)
            for (int x = 0; x < image.Width; x++)
                if (image.GetPixel(x, y).A >= minimumAlpha) {
                    left = Math.Min(left, x); top = Math.Min(top, y);
                    right = Math.Max(right, x); bottom = Math.Max(bottom, y);
                }
        if (right < left) throw new InvalidOperationException("Foreground is empty.");
        return Rectangle.FromLTRB(left, top, right + 1, bottom + 1);
    }

    public static Bitmap Resize(Image source, int size) {
        Bitmap result = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (Graphics graphics = Graphics.FromImage(result))
        using (ImageAttributes attributes = new ImageAttributes()) {
            graphics.Clear(Color.Transparent);
            graphics.CompositingMode = CompositingMode.SourceCopy;
            graphics.CompositingQuality = CompositingQuality.HighQuality;
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
            attributes.SetWrapMode(WrapMode.TileFlipXY);
            graphics.DrawImage(source, new Rectangle(0, 0, size, size),
                0, 0, source.Width, source.Height, GraphicsUnit.Pixel, attributes);
        }
        return result;
    }

    public static Bitmap PadForeground(Bitmap source) {
        Rectangle bounds = AlphaBounds(source, 8);
        const int size = 1080;
        // Sixty dp of artwork, centered on Android's 108 dp layer canvas.
        double scale = 600.0 / Math.Max(bounds.Width, bounds.Height);
        int width = (int)Math.Round(bounds.Width * scale);
        int height = (int)Math.Round(bounds.Height * scale);
        Bitmap result = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (Graphics graphics = Graphics.FromImage(result)) {
            graphics.Clear(Color.Transparent);
            graphics.CompositingMode = CompositingMode.SourceCopy;
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
            graphics.DrawImage(source,
                new Rectangle((size - width) / 2, (size - height) / 2, width, height),
                bounds, GraphicsUnit.Pixel);
        }
        return result;
    }

    public static Bitmap Composite(Bitmap foreground, Bitmap background, int size, bool circle) {
        Bitmap result = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (Graphics graphics = Graphics.FromImage(result)) {
            graphics.Clear(Color.Transparent);
            graphics.SmoothingMode = SmoothingMode.AntiAlias;
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
            if (circle) {
                using (GraphicsPath mask = new GraphicsPath()) {
                    mask.AddEllipse(0, 0, size, size);
                    graphics.SetClip(mask);
                }
            }
            // AdaptiveIconDrawable shows the central 72 dp of a 108 dp layer.
            int inset = foreground.Width / 6;
            Rectangle crop = new Rectangle(inset, inset,
                foreground.Width - inset * 2, foreground.Height - inset * 2);
            graphics.DrawImage(background, new Rectangle(0, 0, size, size),
                crop, GraphicsUnit.Pixel);
            graphics.DrawImage(foreground, new Rectangle(0, 0, size, size), crop, GraphicsUnit.Pixel);
        }
        return result;
    }

    public static void Verify(string path, int size, bool foreground) {
        using (Bitmap image = new Bitmap(path)) {
            if (image.Width != size || image.Height != size)
                throw new InvalidOperationException("Wrong dimensions: " + path);
            if (foreground) {
                if (image.GetPixel(0, 0).A != 0 || image.GetPixel(size - 1, 0).A != 0 ||
                    image.GetPixel(0, size - 1).A != 0 || image.GetPixel(size - 1, size - 1).A != 0)
                    throw new InvalidOperationException("Foreground margin is not transparent: " + path);
                Rectangle bounds = AlphaBounds(image, 128);
                double contentDp = Math.Max(bounds.Width, bounds.Height) * 108.0 / size;
                if (contentDp < 48 || contentDp > 66)
                    throw new InvalidOperationException("Artwork outside adaptive size range: " + path);
            } else {
                for (int y = 0; y < size; y++)
                    for (int x = 0; x < size; x++)
                        if (image.GetPixel(x, y).A != 255)
                            throw new InvalidOperationException("Background is not fully opaque: " + path);
            }
        }
    }
}
'@

$taskRoot = [IO.Path]::GetFullPath($ProjectRoot)
$taskAssetRoot = Join-Path $taskRoot 'store/assets/launcher'
$taskResourceRoot = Join-Path $taskRoot 'app/src/main/res'
$taskForeground = [Drawing.Bitmap]::new((Join-Path $taskAssetRoot $ForegroundMaster))
$taskBackground = [Drawing.Bitmap]::new((Join-Path $taskAssetRoot 'background-master.png'))
$taskPadded = $null
$taskBackgroundCanvas = $null

try {
    $taskPadded = [HumanTypingIconExport]::PadForeground($taskForeground)
    $taskBackgroundCanvas = [HumanTypingIconExport]::Resize($taskBackground, 1080)
    $taskDensities = @(
        @{ Name = 'mdpi'; Launcher = 48; Adaptive = 108 },
        @{ Name = 'hdpi'; Launcher = 72; Adaptive = 162 },
        @{ Name = 'xhdpi'; Launcher = 96; Adaptive = 216 },
        @{ Name = 'xxhdpi'; Launcher = 144; Adaptive = 324 },
        @{ Name = 'xxxhdpi'; Launcher = 192; Adaptive = 432 }
    )
    foreach ($taskDensity in $taskDensities) {
        $taskFolder = Join-Path $taskResourceRoot ("mipmap-" + $taskDensity.Name)
        New-Item -ItemType Directory -Path $taskFolder -Force | Out-Null
        $taskImages = @{
            'ic_launcher_fg.png' = [HumanTypingIconExport]::Resize($taskPadded, $taskDensity.Adaptive)
            'ic_launcher_bg.png' = [HumanTypingIconExport]::Resize($taskBackgroundCanvas, $taskDensity.Adaptive)
            ("app_icon_human_typing_ime_mipmap_" + $taskDensity.Name + '.png') =
                [HumanTypingIconExport]::Resize($taskPadded, $taskDensity.Launcher)
            ("app_icon_human_typing_ime_background_mipmap_" + $taskDensity.Name + '.png') =
                [HumanTypingIconExport]::Resize($taskBackgroundCanvas, $taskDensity.Launcher)
        }
        if ($IncludeSquareFallback) {
            $taskImages['ic_launcher_legacy.png'] =
                [HumanTypingIconExport]::Composite($taskPadded, $taskBackgroundCanvas, $taskDensity.Launcher, $false)
        }
        foreach ($taskName in $taskImages.Keys) {
            try {
                $taskImages[$taskName].Save((Join-Path $taskFolder $taskName), [Drawing.Imaging.ImageFormat]::Png)
            } finally { $taskImages[$taskName].Dispose() }
        }
        [HumanTypingIconExport]::Verify((Join-Path $taskFolder 'ic_launcher_fg.png'), $taskDensity.Adaptive, $true)
        [HumanTypingIconExport]::Verify((Join-Path $taskFolder 'ic_launcher_bg.png'), $taskDensity.Adaptive, $false)
        [HumanTypingIconExport]::Verify(
            (Join-Path $taskFolder ("app_icon_human_typing_ime_mipmap_" + $taskDensity.Name + '.png')),
            $taskDensity.Launcher, $true)
        [HumanTypingIconExport]::Verify(
            (Join-Path $taskFolder ("app_icon_human_typing_ime_background_mipmap_" + $taskDensity.Name + '.png')),
            $taskDensity.Launcher, $false)
        [pscustomobject]@{ Density = $taskDensity.Name; Requested = $taskDensity.Launcher; Adaptive = $taskDensity.Adaptive }
    }
    foreach ($taskRound in @($false, $true)) {
        $taskPreview = [HumanTypingIconExport]::Composite($taskPadded, $taskBackgroundCanvas, 512, $taskRound)
        try {
            $taskPreviewName = if ($taskRound) { 'adaptive-preview-circle.png' } else { 'adaptive-preview-square.png' }
            $taskPreview.Save((Join-Path $taskAssetRoot $taskPreviewName), [Drawing.Imaging.ImageFormat]::Png)
        } finally { $taskPreview.Dispose() }
    }
} finally {
    if ($null -ne $taskPadded) { $taskPadded.Dispose() }
    if ($null -ne $taskBackgroundCanvas) { $taskBackgroundCanvas.Dispose() }
    $taskForeground.Dispose()
    $taskBackground.Dispose()
}
