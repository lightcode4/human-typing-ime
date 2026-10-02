param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$taskAssetRoot = Join-Path ([IO.Path]::GetFullPath($ProjectRoot)) 'store/assets'
$taskMasterPath = Join-Path $taskAssetRoot 'human-typing-play-icon-master.png'
$taskMaster = [Drawing.Bitmap]::new($taskMasterPath)

try {
    if ($taskMaster.Width -ne $taskMaster.Height) {
        throw 'The Play Store icon master must be square.'
    }
    foreach ($taskSize in @(1024, 512)) {
        $taskOutputPath = Join-Path $taskAssetRoot ("human-typing-play-icon-$taskSize.png")
        $taskExport = [Drawing.Bitmap]::new($taskSize, $taskSize, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $taskGraphics = [Drawing.Graphics]::FromImage($taskExport)
            $taskAttributes = [Drawing.Imaging.ImageAttributes]::new()
            try {
                $taskGraphics.Clear([Drawing.Color]::White)
                $taskGraphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceOver
                $taskGraphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
                $taskGraphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $taskGraphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $taskAttributes.SetWrapMode([Drawing.Drawing2D.WrapMode]::TileFlipXY)
                $taskGraphics.DrawImage($taskMaster, [Drawing.Rectangle]::new(0, 0, $taskSize, $taskSize),
                    0, 0, $taskMaster.Width, $taskMaster.Height, [Drawing.GraphicsUnit]::Pixel, $taskAttributes)
            } finally {
                $taskAttributes.Dispose()
                $taskGraphics.Dispose()
            }
            $taskExport.Save($taskOutputPath, [Drawing.Imaging.ImageFormat]::Png)
        } finally { $taskExport.Dispose() }

        $taskVerified = [Drawing.Bitmap]::new($taskOutputPath)
        try {
            if ($taskVerified.Width -ne $taskSize -or $taskVerified.Height -ne $taskSize) {
                throw "Wrong icon dimensions: $taskOutputPath"
            }
            for ($taskY = 0; $taskY -lt $taskSize; $taskY++) {
                for ($taskX = 0; $taskX -lt $taskSize; $taskX++) {
                    if ($taskVerified.GetPixel($taskX, $taskY).A -ne 255) {
                        throw "Icon has transparent pixels: $taskOutputPath"
                    }
                }
            }
        } finally { $taskVerified.Dispose() }
        $taskPngHeader = [IO.File]::ReadAllBytes($taskOutputPath)
        if ($taskPngHeader[24] -ne 8 -or $taskPngHeader[25] -ne 6) {
            throw "Expected a 32-bit RGBA PNG: $taskOutputPath"
        }
        $taskBytes = (Get-Item -LiteralPath $taskOutputPath).Length
        if ($taskSize -eq 512 -and $taskBytes -gt 1024KB) {
            throw 'The Play Store upload icon exceeds 1024 KB.'
        }
        [pscustomobject]@{ Path = $taskOutputPath; Size = "$taskSize x $taskSize"; Bytes = $taskBytes }
    }
} finally { $taskMaster.Dispose() }
