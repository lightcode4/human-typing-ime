param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [switch]$Replace
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression

$taskRoot = [IO.Path]::GetFullPath($ProjectRoot)
$taskArchivePath = Join-Path $taskRoot 'store/assets/HumanTypingIME-icon-assets.zip'
$taskFiles = @(
    'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
    'app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml',
    'app/src/main/res/mipmap-anydpi/ic_launcher.xml',
    'app/src/main/res/mipmap-anydpi/ic_launcher_round.xml',
    'store/assets/human-typing-play-icon-1024.png',
    'store/assets/human-typing-play-icon-512.png',
    'store/assets/human-typing-play-icon-master.png',
    'store/assets/launcher/foreground-keycap-master.png',
    'store/assets/launcher/background-master.png',
    'store/assets/launcher/adaptive-preview-square.png',
    'store/assets/launcher/adaptive-preview-circle.png',
    'store/assets/ICON_ASSETS_README.md',
    'store/assets/launcher/README.md',
    'store/assets/PLAY_ICON_README.md',
    'tools/export-launcher-icons.ps1',
    'tools/export-play-store-icon.ps1',
    'tools/package-icon-assets.ps1'
)
foreach ($taskDensity in @('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi')) {
    $taskFolder = "app/src/main/res/mipmap-$taskDensity"
    $taskFiles += "$taskFolder/ic_launcher_fg.png"
    $taskFiles += "$taskFolder/ic_launcher_bg.png"
    $taskFiles += "$taskFolder/ic_launcher_legacy.png"
    $taskFiles += "$taskFolder/app_icon_human_typing_ime_mipmap_$taskDensity.png"
    $taskFiles += "$taskFolder/app_icon_human_typing_ime_background_mipmap_$taskDensity.png"
}

foreach ($taskRelativePath in $taskFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $taskRoot $taskRelativePath) -PathType Leaf)) {
        throw "Required icon asset is missing: $taskRelativePath"
    }
}
if ((Test-Path -LiteralPath $taskArchivePath) -and -not $Replace) {
    throw 'The icon archive already exists. Use -Replace to regenerate it.'
}

$taskFileMode = if ($Replace) { [IO.FileMode]::Create } else { [IO.FileMode]::CreateNew }
$taskStream = [IO.File]::Open($taskArchivePath, $taskFileMode, [IO.FileAccess]::Write, [IO.FileShare]::None)
$taskArchive = $null
try {
    $taskArchive = [IO.Compression.ZipArchive]::new($taskStream, [IO.Compression.ZipArchiveMode]::Create, $true)
    foreach ($taskRelativePath in $taskFiles) {
        $taskEntry = $taskArchive.CreateEntry($taskRelativePath, [IO.Compression.CompressionLevel]::Optimal)
        $taskInput = [IO.File]::OpenRead((Join-Path $taskRoot $taskRelativePath))
        $taskOutput = $null
        try {
            $taskOutput = $taskEntry.Open()
            $taskInput.CopyTo($taskOutput)
        } finally {
            if ($null -ne $taskOutput) { $taskOutput.Dispose() }
            $taskInput.Dispose()
        }
    }
} finally {
    if ($null -ne $taskArchive) { $taskArchive.Dispose() }
    $taskStream.Dispose()
}

$taskCheck = [IO.Compression.ZipFile]::OpenRead($taskArchivePath)
try {
    if ($taskCheck.Entries.Count -ne $taskFiles.Count) { throw 'Archive entry count does not match.' }
    foreach ($taskRelativePath in $taskFiles) {
        $taskEntry = $taskCheck.GetEntry($taskRelativePath)
        $taskExpectedLength = (Get-Item -LiteralPath (Join-Path $taskRoot $taskRelativePath)).Length
        if ($null -eq $taskEntry -or $taskEntry.Length -ne $taskExpectedLength) {
            throw "Archive entry is missing or has the wrong size: $taskRelativePath"
        }
    }
    [pscustomobject]@{
        Archive = $taskArchivePath
        Entries = $taskCheck.Entries.Count
        Bytes = (Get-Item -LiteralPath $taskArchivePath).Length
    }
} finally { $taskCheck.Dispose() }
