$ErrorActionPreference = 'Stop'

$HubDirectory = $PSScriptRoot
$Hub = Join-Path $HubDirectory 'HomeListHub.exe'
$VersionFile = Join-Path $HubDirectory 'VERSION.txt'
$ConfigFile = Join-Path $HubDirectory 'homelist-hub-config.json'
$ReleaseUrl = 'https://github.com/bobcatchris15-eng/HomeList_updates/releases/latest'
$Nonce = [Guid]::NewGuid().ToString('N')
$ArchiveFile = Join-Path $HubDirectory "hub-$Nonce.zip"
$StagedHub = Join-Path $HubDirectory "hub-$Nonce.exe"
$OldHub = Join-Path $HubDirectory "hub-$Nonce.previous"

try {
    $Running = Get-Process -Name 'HomeListHub' -ErrorAction SilentlyContinue | Where-Object {
        $_.Path -and $_.Path.Equals($Hub, [StringComparison]::OrdinalIgnoreCase)
    }
    if ($Running) {
        throw 'Close this HomeList Hub before updating.'
    }
    if (Test-Path -LiteralPath $ConfigFile) {
        $Config = Get-Content -LiteralPath $ConfigFile -Raw -Encoding utf8 | ConvertFrom-Json
        if ($Config.updateReleaseUrl) {
            $ReleaseUrl = [string]$Config.updateReleaseUrl
        }
    }
    $Repository = [regex]::Match($ReleaseUrl.Trim(), '^https://github\.com/([\w.-]+/[\w.-]+)(?:/releases(?:/latest)?)?/?$')
    if (-not $Repository.Success) {
        throw 'Set updateReleaseUrl to a GitHub repository or releases URL.'
    }

    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $Headers = @{ Accept = 'application/vnd.github+json'; 'User-Agent' = 'HomeList-Windows-Hub' }
    $Release = Invoke-RestMethod -Uri "https://api.github.com/repos/$($Repository.Groups[1].Value)/releases/latest" -Headers $Headers -TimeoutSec 20
    $Tag = [string]$Release.tag_name
    $RemoteVersion = $Tag.TrimStart('v', 'V')
    if ($RemoteVersion -notmatch '^\d+\.\d+\.\d+(?:[+.-][0-9A-Za-z.+-]+)?$') {
        throw 'The latest release has an invalid version tag.'
    }
    if ((Test-Path -LiteralPath $Hub) -and (Test-Path -LiteralPath $VersionFile)) {
        $Installed = (Get-Content -LiteralPath $VersionFile -Raw).Trim()
        if ($Installed -eq $RemoteVersion) {
            Write-Host "HomeList Hub $Installed is current."
            exit 0
        }
        $InstalledNumber = $null
        $RemoteNumber = $null
        if ([version]::TryParse($Installed, [ref]$InstalledNumber) -and
            [version]::TryParse($RemoteVersion, [ref]$RemoteNumber) -and $InstalledNumber -gt $RemoteNumber) {
            throw "Installed version $Installed is newer than $RemoteVersion."
        }
    }
    $Asset = $Release.assets | Where-Object { $_.name -eq "HomeList-Windows-Hub-$Tag.zip" } | Select-Object -First 1
    if (-not $Asset -or ([uri]$Asset.browser_download_url).Scheme -ne 'https') {
        throw 'The release has no Windows Hub ZIP.'
    }
    Invoke-WebRequest -Uri $Asset.browser_download_url -UseBasicParsing -OutFile $ArchiveFile -TimeoutSec 120
    if ($Asset.digest -and $Asset.digest -match '^sha256:([0-9a-f]{64})$') {
        $Expected = $Matches[1]
        $Hasher = [Security.Cryptography.SHA256]::Create()
        $ArchiveStream = [IO.File]::OpenRead($ArchiveFile)
        try {
            $Actual = [BitConverter]::ToString($Hasher.ComputeHash($ArchiveStream)).Replace('-', '')
        } finally {
            $ArchiveStream.Dispose()
            $Hasher.Dispose()
        }
        if ($Actual -ne $Expected) {
            throw 'The Windows Hub ZIP checksum does not match the GitHub release.'
        }
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $Zip = [IO.Compression.ZipFile]::OpenRead($ArchiveFile)
    try {
        $Entry = $Zip.GetEntry('windows-hub/HomeListHub.exe')
        if (-not $Entry) {
            throw 'The release ZIP is missing HomeListHub.exe.'
        }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($Entry, $StagedHub)
    } finally {
        $Zip.Dispose()
    }
    if (Test-Path -LiteralPath $Hub) {
        [IO.File]::Replace($StagedHub, $Hub, $OldHub)
    } else {
        [IO.File]::Move($StagedHub, $Hub)
    }
    [IO.File]::WriteAllText($VersionFile, $RemoteVersion + [Environment]::NewLine)
    Write-Host "Installed HomeList Hub $RemoteVersion. Start the hub to resume syncing."
} catch {
    Write-Error -ErrorAction Continue $_
    exit 1
} finally {
    foreach ($File in @($ArchiveFile, $StagedHub, $OldHub)) {
        Remove-Item -LiteralPath $File -Force -ErrorAction SilentlyContinue
    }
}
