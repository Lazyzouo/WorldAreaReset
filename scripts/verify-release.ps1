param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
)

$buildFile = Join-Path $ProjectRoot "build.gradle"
$buildText = Get-Content -Raw -Encoding UTF8 -LiteralPath $buildFile
$match = [regex]::Match($buildText, "pluginVersion\s*=\s*'([^']+)'" )
if (-not $match.Success) {
    throw "Could not read pluginVersion from build.gradle"
}

$version = $match.Groups[1].Value
$releaseNotes = Join-Path $ProjectRoot "release-notes\v$version.md"
$changelog = Join-Path $ProjectRoot "CHANGELOG.md"

if (-not (Test-Path -LiteralPath $releaseNotes)) {
    throw "Missing release notes: $releaseNotes"
}

if (-not (Select-String -Quiet -LiteralPath $changelog -Pattern "## [$version]" -SimpleMatch)) {
    throw "CHANGELOG.md does not contain version $version"
}

Write-Output "Release metadata verified for WorldAreaReset v$version"
