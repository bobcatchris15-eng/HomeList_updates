param([switch]$UpdateNow)

# Existing installations may still have a startup shortcut to this file.
if ($UpdateNow) {
    & (Join-Path $PSScriptRoot 'Update-HomeListHub.ps1')
    exit $LASTEXITCODE
}
$Hub = Join-Path $PSScriptRoot 'HomeListHub.exe'
if (-not (Test-Path -LiteralPath $Hub)) {
    Write-Error 'Extract the complete Windows Hub ZIP to install HomeListHub.exe.'
    exit 1
}
& $Hub
exit $LASTEXITCODE
