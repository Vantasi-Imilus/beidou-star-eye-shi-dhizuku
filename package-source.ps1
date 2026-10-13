param(
    [string]$Version = "0.1.0"
)

# Package the project source into a zip while preserving directory structure.
# Excludes build output (.gradle / build / dist).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$dest = $root + "\dist\BeidouStarEye-src-v" + $Version + ".zip"
if (Test-Path $dest) { Remove-Item $dest -Force }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($dest, [System.IO.Compression.ZipArchiveMode]::Create)

Get-ChildItem -Recurse -File | Where-Object {
    $_.FullName -notlike '*\build\*' -and
    $_.FullName -notlike '*\.gradle\*' -and
    $_.FullName -notlike '*\dist\*'
} | ForEach-Object {
    $rel = $_.FullName.Substring($root.Length).TrimStart('\').Replace('\', '/')
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $rel) | Out-Null
}

$zip.Dispose()
Write-Output ("OK: " + $dest)
