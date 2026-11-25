# HomeList Windows Home Hub
# Zero-dependency local-first family sync relay & Wall Board for Windows 10/11.
# Double-click Start-HomeListHub.bat to launch (no Python, Node, Docker, or Admin needed).

param(
    [switch]$UpdateNow
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigPath = Join-Path $ScriptDir "homelist-hub-config.json"
$DataPath   = Join-Path $ScriptDir "homelist-hub-data.json"
$VersionPath = Join-Path $ScriptDir "VERSION.txt"
$DefaultReleaseUrl = "https://github.com/bobcatchris15-eng/HomeList_updates/releases/latest"

function Get-EpochMs {
    return [long][DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
}

function Get-InstalledVersion {
    if (Test-Path $VersionPath) {
        $v = (Get-Content -Path $VersionPath -Raw -Encoding UTF8).Trim()
        if ($v) { return $v }
    }
    return "1.0"
}

function Get-LocalLanIp {
    try {
        $sock = New-Object System.Net.Sockets.Socket(
            [System.Net.Sockets.AddressFamily]::InterNetwork,
            [System.Net.Sockets.SocketType]::Dgram,
            [System.Net.Sockets.ProtocolType]::Udp
        )
        try {
            $sock.Connect("1.1.1.1", 53)
            $ep = [System.Net.IPEndPoint]$sock.LocalEndPoint
            if ($ep -and $ep.Address -and -not [System.Net.IPAddress]::IsLoopback($ep.Address)) {
                $ipStr = $ep.Address.ToString()
                if (-not $ipStr.StartsWith("169.254.")) { return $ipStr }
            }
        } finally {
            $sock.Close()
        }
    } catch {}

    try {
        $addrs = [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) |
            Where-Object {
                $_.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork -and
                -not [System.Net.IPAddress]::IsLoopback($_) -and
                -not $_.ToString().StartsWith("169.254.")
            }
        foreach ($a in $addrs) {
            if ($a.ToString().StartsWith("192.168.")) { return $a.ToString() }
        }
        foreach ($a in $addrs) {
            $s = $a.ToString()
            if ($s.StartsWith("10.") -or $s -match "^172\.(1[6-9]|2[0-9]|3[0-1])\.") {
                return $s
            }
        }
        if ($addrs.Count -gt 0) { return $addrs[0].ToString() }
    } catch {}
    return "127.0.0.1"
}

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Text, $utf8NoBom)
}

function Normalize-HubUrl([string]$Raw) {
    if ([string]::IsNullOrWhiteSpace($Raw)) { return "" }
    $t = $Raw.Trim().TrimEnd('/')
    if ($t -match '^https?://') {
        return ($t -replace '/api/v1/(gossip|health)$', '').TrimEnd('/')
    }
    $hostPart = ($t -split '/')[0]
    if ($hostPart -match '^[0-9\.]+(:[0-9]+)?$') {
        if ($hostPart -notmatch ':') { return "http://${t}:8765" }
        return "http://$t"
    }
    return "https://$t"
}

function Compare-SemVer([string]$Remote, [string]$Local) {
    $rParts = ($Remote.Trim().TrimStart('v','V') -split '[\.\-\+]' | Where-Object { $_ -match '^\d+$' } | ForEach-Object { [int]$_ })
    $lParts = ($Local.Trim().TrimStart('v','V') -split '[\.\-\+]' | Where-Object { $_ -match '^\d+$' } | ForEach-Object { [int]$_ })
    $maxLen = [Math]::Max($rParts.Count, $lParts.Count)
    for ($i = 0; $i -lt $maxLen; $i++) {
        $rv = if ($i -lt $rParts.Count) { $rParts[$i] } else { 0 }
        $lv = if ($i -lt $lParts.Count) { $lParts[$i] } else { 0 }
        if ($rv -ne $lv) { return ($rv -gt $lv) }
    }
    return $false
}

function Get-GitHubApiLatestUrl([string]$ReleaseUrl) {
    $u = if ([string]::IsNullOrWhiteSpace($ReleaseUrl)) { $DefaultReleaseUrl } else { $ReleaseUrl.Trim() }
    if ($u -match 'github\.com/([^/]+)/([^/]+)') {
        $owner = $Matches[1]
        $repo = $Matches[2] -replace '\.git$', ''
        return "https://api.github.com/repos/$owner/$repo/releases/latest"
    }
    return "https://api.github.com/repos/bobcatchris15-eng/HomeList_updates/releases/latest"
}

function Show-WindowsNotice([string]$Title, [string]$Body) {
    try {
        Add-Type -AssemblyName System.Windows.Forms -ErrorAction SilentlyContinue
        $ni = New-Object System.Windows.Forms.NotifyIcon
        $ni.Icon = [System.Drawing.SystemIcons]::Information
        $ni.BalloonTipTitle = $Title
        $ni.BalloonTipText = $Body
        $ni.Visible = $true
        $ni.ShowBalloonTip(8000)
        Start-Sleep -Milliseconds 300
        $ni.Dispose()
    } catch {}
}

function Check-HubUpdate([object]$Config, [switch]$ApplyIfAvailable) {
    $installed = Get-InstalledVersion
    $apiUrl = Get-GitHubApiLatestUrl $Config.updateReleaseUrl
    try {
        $headers = @{
            "Accept"     = "application/vnd.github+json"
            "User-Agent" = "HomeList-Windows-Hub/$installed"
        }
        $rel = Invoke-RestMethod -Uri $apiUrl -Headers $headers -TimeoutSec 10
        $tag = [string]$rel.tag_name
        $remoteVer = $tag.TrimStart('v','V')
        if ([string]::IsNullOrWhiteSpace($remoteVer)) { return $null }

        $zipAsset = $null
        if ($rel.assets) {
            $zipAsset = $rel.assets | Where-Object { $_.name -match 'HomeList-Windows-Hub.*\.zip$' } | Select-Object -First 1
        }
        $pageUrl = if ($rel.html_url) { [string]$rel.html_url } else { $Config.updateReleaseUrl }

        if (Compare-SemVer $remoteVer $installed) {
            Write-Host ""
            Write-Host "============================================================" -ForegroundColor Yellow
            Write-Host "  UPDATE AVAILABLE: HomeList v$remoteVer (installed: v$installed)" -ForegroundColor Yellow
            Write-Host "  Release: $pageUrl" -ForegroundColor Yellow
            if ($zipAsset) {
                Write-Host "  Run Update-HomeListHub.bat (or press U here) to auto-update." -ForegroundColor Yellow
            }
            Write-Host "============================================================" -ForegroundColor Yellow
            Show-WindowsNotice "HomeList Hub v$remoteVer Available" "A new HomeList release is ready on GitHub. Run Update-HomeListHub.bat to update."

            if ($ApplyIfAvailable -and $zipAsset) {
                Write-Host "[Update] Downloading $($zipAsset.name)..." -ForegroundColor Cyan
                $tmpZip = Join-Path $env:TEMP ("HomeListHub-" + [Guid]::NewGuid().ToString("N") + ".zip")
                $tmpDir = Join-Path $env:TEMP ("HomeListHub-" + [Guid]::NewGuid().ToString("N"))
                Invoke-WebRequest -Uri $zipAsset.browser_download_url -Headers @{ "User-Agent" = "HomeList-Windows-Hub/$installed" } -OutFile $tmpZip -TimeoutSec 30
                Expand-Archive -Path $tmpZip -DestinationPath $tmpDir -Force
                $sourceRoot = $tmpDir
                if (Test-Path (Join-Path $tmpDir "windows-hub")) {
                    $sourceRoot = Join-Path $tmpDir "windows-hub"
                }
                Get-ChildItem -Path $sourceRoot -File | Where-Object {
                    $_.Name -notin @("homelist-hub-config.json", "homelist-hub-data.json")
                } | ForEach-Object {
                    Copy-Item -Path $_.FullName -Destination (Join-Path $ScriptDir $_.Name) -Force
                }
                Remove-Item -Path $tmpZip -Force -ErrorAction SilentlyContinue
                Remove-Item -Path $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
                Write-Host "[Update] Updated HomeList Hub to v$remoteVer! Your config & family data were preserved." -ForegroundColor Green
                return @{ updated = $true; version = $remoteVer }
            }
            return @{ updated = $false; available = $true; version = $remoteVer; url = $pageUrl; zipUrl = $(if ($zipAsset) { $zipAsset.browser_download_url } else { "" }) }
        } else {
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Update check: HomeList Hub v$installed is up to date." -ForegroundColor DarkGray
            return @{ updated = $false; available = $false; version = $installed }
        }
    } catch {
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Update check skipped (offline or GitHub unreachable)." -ForegroundColor DarkGray
        return $null
    }
}

function New-PairingCode {
    $alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 6
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $chars = for ($i = 0; $i -lt 6; $i++) { $alphabet[$bytes[$i] % $alphabet.Length] }
    return (-join $chars)
}

function Load-OrCreateConfig {
    if (Test-Path $ConfigPath) {
        $cfg = Get-Content -Path $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if (-not $cfg.updateReleaseUrl) { $cfg | Add-Member -NotePropertyName updateReleaseUrl -NotePropertyValue $DefaultReleaseUrl -Force }
        if ($null -eq $cfg.hubUrl) { $cfg | Add-Member -NotePropertyName hubUrl -NotePropertyValue "" -Force }
        if (-not $cfg.port) { $cfg | Add-Member -NotePropertyName port -NotePropertyValue 8765 -Force }
        if ($null -eq $cfg.isFounder) { $cfg | Add-Member -NotePropertyName isFounder -NotePropertyValue $true -Force }
        if ([string]::IsNullOrWhiteSpace($cfg.householdId) -and $cfg.isFounder) {
            $cfg | Add-Member -NotePropertyName householdId -NotePropertyValue ([Guid]::NewGuid().ToString()) -Force
        }
        return $cfg
    }

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "     HomeList Windows Founding Hub - First-Time Setup" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "This PC will act as your household's Founding Hub by default." -ForegroundColor Gray
    Write-Host "Family phones simply join using the IP and pairing code below." -ForegroundColor Gray
    Write-Host ""

    $familyName = (Read-Host "1) Household name (Enter for 'Our Family')").Trim()
    if ([string]::IsNullOrWhiteSpace($familyName)) { $familyName = "Our Family" }

    $codeInput = (Read-Host "2) Pairing code (press Enter to auto-generate a new 6-char code)").Trim().ToUpperInvariant()
    $isFounder = $true
    if ([string]::IsNullOrWhiteSpace($codeInput)) {
        $code = New-PairingCode
        Write-Host "   Generated pairing code: $code" -ForegroundColor Green
    } else {
        $code = $codeInput
    }

    $pubUrl = Normalize-HubUrl (Read-Host "3) Optional: Away-from-home URL (e.g. https://hub.yourhome.com, or Enter to skip)")

    $cfg = [PSCustomObject]@{
        nodeId           = [Guid]::NewGuid().ToString()
        householdId      = [Guid]::NewGuid().ToString()
        householdName    = $familyName
        pairingCode      = $code
        displayName      = "$env:COMPUTERNAME Home Hub"
        isFounder        = $isFounder
        port             = 8765
        seedPeerIp       = ""
        hubUrl           = $pubUrl
        updateReleaseUrl = $DefaultReleaseUrl
    }
    Write-Utf8NoBom $ConfigPath ($cfg | ConvertTo-Json -Depth 6)
    Write-Host ""
    Write-Host "Founded '$familyName' and saved settings to $ConfigPath" -ForegroundColor Green
    return $cfg
}

function Create-EmptyStore {
    return @{
        peers         = @{}
        members       = @{}
        chores        = @{}
        completions   = @{}
        places        = @{}
        reminders     = @{}
        events        = @{}
        dinnerOptions = @{}
        groceryItems  = @{}
    }
}

function Load-Store {
    $store = Create-EmptyStore
    if (-not (Test-Path $DataPath)) { return $store }
    try {
        $raw = Get-Content -Path $DataPath -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($p in @($raw.peers))         { if ($p -and $p.nodeId) { $store.peers[$p.nodeId] = $p } }
        foreach ($m in @($raw.members))       { if ($m -and $m.id)     { $store.members[$m.id] = $m } }
        foreach ($c in @($raw.chores))        { if ($c -and $c.id)     { $store.chores[$c.id] = $c } }
        foreach ($x in @($raw.completions))   { if ($x -and $x.id)     { $store.completions[$x.id] = $x } }
        foreach ($l in @($raw.places))        { if ($l -and $l.id)     { $store.places[$l.id] = $l } }
        foreach ($r in @($raw.reminders))     { if ($r -and $r.id)     { $store.reminders[$r.id] = $r } }
        foreach ($e in @($raw.events))        { if ($e -and $e.id)     { $store.events[$e.id] = $e } }
        foreach ($d in @($raw.dinnerOptions)) { if ($d -and $d.id)     { $store.dinnerOptions[$d.id] = $d } }
        foreach ($g in @($raw.groceryItems))  { if ($g -and $g.id)     { $store.groceryItems[$g.id] = $g } }
    } catch {
        Write-Host "[Store] Could not read existing data file; starting fresh." -ForegroundColor Yellow
    }
    return $store
}

function Update-SelfPeer([hashtable]$Store, [object]$Config) {
    $now = Get-EpochMs
    $lanIp = Get-LocalLanIp
    $ver = Get-InstalledVersion
    $Store.peers[$Config.nodeId] = [PSCustomObject]@{
        nodeId         = [string]$Config.nodeId
        memberId       = "hub_" + ([string]$Config.nodeId).Substring(0, 8)
        displayName    = [string]$Config.displayName
        role           = "PARENT"
        deviceType     = "WINDOWS"
        isFounder      = [bool]$Config.isFounder
        lanIp          = $lanIp
        port           = [int]$Config.port
        appVersionCode = 1
        appVersionName = $ver
        lastSeenAt     = $now
        updatedAt      = $now
    }
    # Seed a starter "Home" place when founding a new household so location reminders work right away
    if ($Store.places.Count -eq 0 -and [bool]$Config.isFounder) {
        $homeId = [Guid]::NewGuid().ToString()
        $Store.places[$homeId] = [PSCustomObject]@{
            id           = $homeId
            name         = "Home"
            latitude     = 0.0
            longitude    = 0.0
            radiusMeters = 200
            isHomePlace  = $true
            isDeleted    = $false
            updatedAt    = $now
        }
    }
}

function Build-GossipPayload([hashtable]$Store, [object]$Config) {
    Update-SelfPeer $Store $Config
    return [ordered]@{
        protocolVersion  = 1
        householdId      = [string]$Config.householdId
        householdName    = [string]$Config.householdName
        senderNodeId     = [string]$Config.nodeId
        sentAt           = Get-EpochMs
        peers            = [object[]]@($Store.peers.Values)
        members          = [object[]]@($Store.members.Values)
        chores           = [object[]]@($Store.chores.Values)
        completions      = [object[]]@($Store.completions.Values)
        places           = [object[]]@($Store.places.Values)
        reminders        = [object[]]@($Store.reminders.Values)
        events           = [object[]]@($Store.events.Values)
        dinnerOptions    = [object[]]@($Store.dinnerOptions.Values)
        groceryItems     = [object[]]@($Store.groceryItems.Values)
        updateReleaseUrl = [string]$Config.updateReleaseUrl
        hubUrl           = [string]$Config.hubUrl
    }
}

function ConvertTo-SafeJson($Object) {
    # Ensure arrays stay JSON arrays even with 0 or 1 elements in PowerShell 5.1
    return ($Object | ConvertTo-Json -Depth 12 -Compress)
}

function Save-Store([hashtable]$Store, [object]$Config) {
    $payload = Build-GossipPayload $Store $Config
    Write-Utf8NoBom $DataPath (ConvertTo-SafeJson $payload)
    Write-Utf8NoBom $ConfigPath ($Config | ConvertTo-Json -Depth 6)
}

function Merge-LwwTable([hashtable]$Table, $IncomingList, [string]$KeyField) {
    if ($null -eq $IncomingList) { return }
    foreach ($item in @($IncomingList)) {
        if ($null -eq $item) { continue }
        $key = [string]$item.$KeyField
        if ([string]::IsNullOrWhiteSpace($key)) { continue }
        $existing = $Table[$key]
        $inTs = if ($null -ne $item.updatedAt) { [long]$item.updatedAt } else { 0L }
        $exTs = if ($null -ne $existing -and $null -ne $existing.updatedAt) { [long]$existing.updatedAt } else { -1L }
        if ($null -eq $existing -or $inTs -gt $exTs) {
            $Table[$key] = $item
        }
    }
}

function Merge-GossipPayload([hashtable]$Store, [object]$Config, $Incoming) {
    if ($null -eq $Incoming) { return $false }
    if (-not [string]::IsNullOrWhiteSpace($Config.householdId) -and
        -not [string]::IsNullOrWhiteSpace($Incoming.householdId) -and
        $Incoming.householdId -ne $Config.householdId) {
        return $false
    }

    $now = Get-EpochMs
    if ([string]::IsNullOrWhiteSpace($Config.householdId) -and -not [string]::IsNullOrWhiteSpace($Incoming.householdId)) {
        $Config.householdId = [string]$Incoming.householdId
    }
    if (-not [string]::IsNullOrWhiteSpace($Incoming.householdName)) {
        $Config.householdName = [string]$Incoming.householdName
    }
    if (-not [string]::IsNullOrWhiteSpace($Incoming.updateReleaseUrl)) {
        $Config.updateReleaseUrl = [string]$Incoming.updateReleaseUrl
    }
    if ([string]::IsNullOrWhiteSpace($Config.hubUrl) -and -not [string]::IsNullOrWhiteSpace($Incoming.hubUrl)) {
        $Config.hubUrl = Normalize-HubUrl ([string]$Incoming.hubUrl)
    }

    # Merge peers with sender lastSeenAt refresh
    foreach ($p in @($Incoming.peers)) {
        if ($null -eq $p -or [string]::IsNullOrWhiteSpace($p.nodeId)) { continue }
        if ($p.nodeId -eq $Config.nodeId) { continue }
        $existing = $Store.peers[$p.nodeId]
        $inTs = if ($null -ne $p.updatedAt) { [long]$p.updatedAt } else { 0L }
        $exTs = if ($null -ne $existing -and $null -ne $existing.updatedAt) { [long]$existing.updatedAt } else { -1L }
        if ($null -eq $existing -or $inTs -gt $exTs) {
            if ($p.nodeId -eq $Incoming.senderNodeId) {
                $p | Add-Member -NotePropertyName lastSeenAt -NotePropertyValue $now -Force
            }
            $Store.peers[$p.nodeId] = $p
        } elseif ($p.nodeId -eq $Incoming.senderNodeId) {
            $existing | Add-Member -NotePropertyName lastSeenAt -NotePropertyValue $now -Force
            $Store.peers[$p.nodeId] = $existing
        }
    }

    Merge-LwwTable $Store.members       $Incoming.members       "id"
    Merge-LwwTable $Store.chores        $Incoming.chores        "id"
    Merge-LwwTable $Store.completions   $Incoming.completions   "id"
    Merge-LwwTable $Store.places        $Incoming.places        "id"
    Merge-LwwTable $Store.reminders     $Incoming.reminders     "id"
    Merge-LwwTable $Store.events        $Incoming.events        "id"
    Merge-LwwTable $Store.dinnerOptions $Incoming.dinnerOptions "id"
    Merge-LwwTable $Store.groceryItems  $Incoming.groceryItems  "id"

    Save-Store $Store $Config
    return $true
}

function Sync-WithPeerEndpoint([hashtable]$Store, [object]$Config, [string]$HostIp, [int]$Port) {
    if ([string]::IsNullOrWhiteSpace($HostIp)) { return $false }
    try {
        $url = "http://${HostIp}:${Port}/api/v1/gossip"
        $body = if ([string]::IsNullOrWhiteSpace($Config.householdId)) {
            ""
        } else {
            ConvertTo-SafeJson (Build-GossipPayload $Store $Config)
        }
        $headers = @{ "X-HomeList-Code" = [string]$Config.pairingCode }
        $resp = Invoke-RestMethod -Method Post -Uri $url -Headers $headers -Body $body -ContentType "application/json; charset=utf-8" -TimeoutSec 5
        if ($resp -and (Merge-GossipPayload $Store $Config $resp)) {
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Synced with LAN peer ${HostIp}:${Port} ($($Config.householdName))" -ForegroundColor Green
            return $true
        }
    } catch {}
    return $false
}

function Sync-KnownLanPeers([hashtable]$Store, [object]$Config) {
    $myIp = Get-LocalLanIp
    $targets = New-Object System.Collections.Generic.HashSet[string]
    if (-not [string]::IsNullOrWhiteSpace($Config.seedPeerIp)) {
        $seed = $Config.seedPeerIp.Trim()
        if ($seed -notmatch ':') { $seed = "${seed}:8765" }
        [void]$targets.Add($seed)
    }
    foreach ($peer in @($Store.peers.Values)) {
        if ($peer.nodeId -ne $Config.nodeId -and -not [string]::IsNullOrWhiteSpace($peer.lanIp) -and $peer.lanIp -ne $myIp) {
            $p = if ($peer.port) { [int]$peer.port } else { 8765 }
            [void]$targets.Add("$($peer.lanIp):$p")
        }
    }
    foreach ($ep in $targets) {
        $parts = $ep -split ':'
        $h = $parts[0]
        $p = if ($parts.Count -gt 1) { [int]$parts[1] } else { 8765 }
        [void](Sync-WithPeerEndpoint $Store $Config $h $p)
    }
}

function Build-WallSnapshot([hashtable]$Store, [object]$Config) {
    $today = (Get-Date).ToString("yyyy-MM-dd")
    $activeMembers = @($Store.members.Values | Where-Object { -not $_.isDeleted })
    $activeChores = @($Store.chores.Values | Where-Object { -not $_.isDeleted })
    $doneSet = New-Object System.Collections.Generic.HashSet[string]
    foreach ($c in @($Store.completions.Values)) {
        if ($c.isCompleted) { [void]$doneSet.Add([string]$c.id) }
    }

    $todayChores = @($activeChores | Where-Object {
        if ($null -eq $_.isRecurring -or $_.isRecurring) { $true }
        else { [string]::IsNullOrWhiteSpace($_.dueDateKey) -or $_.dueDateKey -le $today }
    })
    $overallDone = (@($todayChores | Where-Object { $doneSet.Contains("$($_.id)_$today") })).Count

    $memberSummaries = @()
    foreach ($m in $activeMembers) {
        $mine = @($todayChores | Where-Object { $_.assignedToMemberId -eq $m.id })
        $myDone = (@($mine | Where-Object { $doneSet.Contains("$($_.id)_$today") })).Count
        $memberSummaries += [ordered]@{
            displayName    = [string]$m.displayName
            role           = [string]$m.role
            colorKey       = if ($m.avatarColorKey) { [string]$m.avatarColorKey } else { "terracotta" }
            todayCompleted = $myDone
            todayTotal     = $mine.Count
            currentStreak  = 0
            weekCells      = @("none","none","none","none","none","none","none")
        }
    }

    $todayDinners = @($Store.dinnerOptions.Values | Where-Object { -not $_.isDeleted -and $_.dateKey -eq $today })
    $winner = $todayDinners | Where-Object { $_.isWinner } | Select-Object -First 1
    $dinnerText = if ($winner) { [string]$winner.mealName } elseif ($todayDinners.Count -gt 0) { "$($todayDinners[0].mealName) (leading vote)" } else { "" }

    $upcomingEvents = @($Store.events.Values | Where-Object { -not $_.isDeleted -and $_.dateKey -ge $today } | Sort-Object dateKey | Select-Object -First 10 | ForEach-Object {
        [ordered]@{
            dateKey   = [string]$_.dateKey
            title     = [string]$_.title
            timeLabel = if ($_.allDay) { "All day" } else { "{0:D2}:{1:D2}" -f [int]$_.startHour, [int]$_.startMinute }
            location  = [string]$_.location
        }
    })

    return [ordered]@{
        householdName    = [string]$Config.householdName
        generatedAt      = Get-EpochMs
        todayDateLabel   = (Get-Date).ToString("dddd, MMM d")
        dayHeaders       = @("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
        overallCompleted = $overallDone
        overallTotal     = $todayChores.Count
        peerCount        = $Store.peers.Count
        tonightDinner    = $dinnerText
        members          = [object[]]$memberSummaries
        upcomingEvents   = [object[]]$upcomingEvents
    }
}

$WallHtml = @"
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>HomeList Home Hub</title>
<style>
  :root { --bg:#FAF6F0; --card:#FFFDF9; --border:#E4D9CB; --terra:#C66B4F; --sage:#5F8068; --sageBg:#DDE9DF; --ink:#2B241E; --muted:#6E6258; }
  * { box-sizing:border-box; }
  body { margin:0; padding:28px; background:var(--bg); color:var(--ink); font-family:system-ui,-apple-system,sans-serif; }
  .top { display:flex; justify-content:space-between; align-items:flex-end; margin-bottom:24px; border-bottom:1px solid var(--border); padding-bottom:16px; }
  h1 { margin:0; font-family:Georgia,serif; font-size:36px; font-weight:600; }
  .date { color:var(--muted); font-size:18px; margin-top:4px; }
  .clock { font-family:Georgia,serif; font-size:44px; color:var(--terra); font-weight:600; }
  .grid { display:grid; grid-template-columns:2fr 1fr; gap:20px; }
  .card { background:var(--card); border:1px solid var(--border); border-radius:20px; padding:20px; }
  .pill { display:inline-block; padding:4px 10px; border-radius:999px; font-size:13px; background:var(--sageBg); color:var(--sage); font-weight:600; }
  .row { display:flex; justify-content:space-between; padding:10px 0; border-bottom:1px solid var(--border); }
</style>
</head>
<body>
  <div class="top">
    <div><h1 id="title">HomeList Home Hub</h1><div class="date" id="dateLabel">Windows Family Sync Hub</div></div>
    <div style="text-align:right"><div class="clock" id="clock">--:--</div><div class="pill" id="summaryPill">Hub Active</div></div>
  </div>
  <div class="grid">
    <div class="card"><h2 style="margin-top:0;font-family:Georgia,serif">Today's Family Progress</h2><div id="members"></div></div>
    <div class="card">
      <h2 style="margin-top:0;font-family:Georgia,serif">Tonight's Dinner</h2>
      <div id="dinner" style="font-size:18px;font-weight:600;color:var(--terra);margin-bottom:18px">Not decided yet</div>
      <h2 style="margin-top:0;font-family:Georgia,serif">Upcoming Events</h2>
      <div id="events"></div>
    </div>
  </div>
<script>
function tick(){ document.getElementById('clock').textContent = new Date().toLocaleTimeString([],{hour:'numeric',minute:'2-digit'}); }
async function load(){
  try {
    const r = await fetch('/api/v1/wall-snapshot');
    if(!r.ok) return;
    const d = await r.json();
    document.getElementById('title').textContent = d.householdName || 'HomeList Home Hub';
    document.getElementById('dateLabel').textContent = d.todayDateLabel || '';
    document.getElementById('summaryPill').textContent = d.overallCompleted + ' of ' + d.overallTotal + ' done \u2022 ' + d.peerCount + ' nodes';
    document.getElementById('dinner').textContent = d.tonightDinner || 'Vote open on HomeList';
    const mEl = document.getElementById('members'); mEl.textContent = '';
    (d.members || []).forEach(m => {
      const div = document.createElement('div'); div.className = 'row';
      div.innerHTML = '<strong>' + m.displayName.replace(/</g,'&lt;') + '</strong><span>' + m.todayCompleted + ' / ' + m.todayTotal + ' done</span>';
      mEl.appendChild(div);
    });
    const eEl = document.getElementById('events'); eEl.textContent = '';
    (d.upcomingEvents || []).forEach(ev => {
      const div = document.createElement('div'); div.className = 'row';
      div.innerHTML = '<span>' + ev.title.replace(/</g,'&lt;') + '</span><span style="color:#6E6258">' + ev.dateKey + ' ' + ev.timeLabel + '</span>';
      eEl.appendChild(div);
    });
  } catch(e){}
}
tick(); load(); setInterval(tick,15000); setInterval(load,15000);
</script>
</body>
</html>
"@

function Read-HttpLine([System.IO.BufferedStream]$Stream) {
    $ms = New-Object System.IO.MemoryStream
    $prev = -1
    while ($true) {
        $b = $Stream.ReadByte()
        if ($b -eq -1) {
            if ($ms.Length -eq 0) { return $null }
            return [System.Text.Encoding]::UTF8.GetString($ms.ToArray())
        }
        if ($b -eq 10) {
            $arr = $ms.ToArray()
            $len = if ($prev -eq 13 -and $arr.Length -gt 0) { $arr.Length - 1 } else { $arr.Length }
            return [System.Text.Encoding]::UTF8.GetString($arr, 0, $len)
        }
        $ms.WriteByte([byte]$b)
        $prev = $b
    }
}

function Write-HttpResponse([System.IO.Stream]$Stream, [int]$Status, [string]$Reason, [string]$ContentType, [string]$BodyText) {
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($BodyText)
    $headerText = "HTTP/1.1 $Status $Reason`r`nContent-Type: $ContentType`r`nContent-Length: $($bodyBytes.Length)`r`nCache-Control: no-store`r`nConnection: close`r`n`r`n"
    $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($headerText)
    $Stream.Write($headerBytes, 0, $headerBytes.Length)
    $Stream.Write($bodyBytes, 0, $bodyBytes.Length)
    $Stream.Flush()
}

function Handle-HttpClient([System.Net.Sockets.TcpClient]$Client, [hashtable]$Store, [object]$Config) {
    try {
        $Client.ReceiveTimeout = 8000
        $Client.SendTimeout = 8000
        $netStream = $Client.GetStream()
        $bufStream = New-Object System.IO.BufferedStream($netStream)

        $reqLine = Read-HttpLine $bufStream
        if ([string]::IsNullOrWhiteSpace($reqLine)) { return }
        $parts = $reqLine -split ' '
        if ($parts.Count -lt 2) { return }
        $method = $parts[0].ToUpperInvariant()
        $path = ($parts[1] -split '\?')[0]

        $headers = @{}
        while ($true) {
            $line = Read-HttpLine $bufStream
            if ($null -eq $line -or $line -eq "") { break }
            $idx = $line.IndexOf(':')
            if ($idx -gt 0) {
                $k = $line.Substring(0, $idx).Trim().ToLowerInvariant()
                $v = $line.Substring($idx + 1).Trim()
                $headers[$k] = $v
            }
        }

        $contentLen = 0
        if ($headers.ContainsKey("content-length")) {
            [void][int]::TryParse($headers["content-length"], [ref]$contentLen)
        }
        if ($contentLen -gt 2097152) { $contentLen = 2097152 }

        $bodyText = ""
        if ($contentLen -gt 0) {
            $buf = New-Object byte[] $contentLen
            $readTotal = 0
            while ($readTotal -lt $contentLen) {
                $n = $bufStream.Read($buf, $readTotal, $contentLen - $readTotal)
                if ($n -le 0) { break }
                $readTotal += $n
            }
            $bodyText = [System.Text.Encoding]::UTF8.GetString($buf, 0, $readTotal)
        }

        if ($method -eq "GET" -and $path -eq "/api/v1/health") {
            $health = [ordered]@{
                app            = "HomeList"
                protocolVersion = 1
                nodeId         = [string]$Config.nodeId
                householdId    = [string]$Config.householdId
                householdName  = [string]$Config.householdName
                displayName    = [string]$Config.displayName
                deviceType     = "WINDOWS"
                isFounder      = [bool]$Config.isFounder
                port           = [int]$Config.port
                appVersionCode = 1
                appVersionName = Get-InstalledVersion
            }
            Write-HttpResponse $netStream 200 "OK" "application/json; charset=utf-8" (ConvertTo-SafeJson $health)
            return
        }

        if ($method -eq "POST" -and $path -eq "/api/v1/gossip") {
            $provided = if ($headers.ContainsKey("x-homelist-code")) { $headers["x-homelist-code"].Trim().ToUpperInvariant() } else { "" }
            $expected = ([string]$Config.pairingCode).Trim().ToUpperInvariant()
            if ($provided -ne $expected -or [string]::IsNullOrWhiteSpace($provided)) {
                Write-HttpResponse $netStream 401 "Unauthorized" "application/json; charset=utf-8" '{"error":"invalid_pairing_code"}'
                return
            }

            if (-not [string]::IsNullOrWhiteSpace($bodyText)) {
                try {
                    $incoming = $bodyText | ConvertFrom-Json
                    [void](Merge-GossipPayload $Store $Config $incoming)
                    $remoteIp = $Client.Client.RemoteEndPoint.Address.ToString()
                    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Synced gossip with $remoteIp ($($Store.members.Count) members, $($Store.chores.Count) chores, $($Store.groceryItems.Count) shopping)" -ForegroundColor Green
                } catch {
                    Write-HttpResponse $netStream 400 "Bad Request" "application/json; charset=utf-8" '{"error":"invalid_payload"}'
                    return
                }
            }

            $respJson = ConvertTo-SafeJson (Build-GossipPayload $Store $Config)
            Write-HttpResponse $netStream 200 "OK" "application/json; charset=utf-8" $respJson
            return
        }

        if ($method -eq "GET" -and $path -eq "/api/v1/wall-snapshot") {
            $snap = Build-WallSnapshot $Store $Config
            Write-HttpResponse $netStream 200 "OK" "application/json; charset=utf-8" (ConvertTo-SafeJson $snap)
            return
        }

        if ($method -eq "GET" -and ($path -eq "/" -or $path -eq "/wall")) {
            Write-HttpResponse $netStream 200 "OK" "text/html; charset=utf-8" $WallHtml
            return
        }

        Write-HttpResponse $netStream 404 "Not Found" "application/json; charset=utf-8" '{"error":"not_found"}'
    } catch {
        # Ignore abrupt client disconnects
    } finally {
        try { $Client.Close() } catch {}
    }
}

# ==============================================================================
# Entrypoint
# ==============================================================================

$Config = Load-OrCreateConfig

if ($UpdateNow) {
    $res = Check-HubUpdate $Config -ApplyIfAvailable
    if ($res -and $res.updated) {
        Write-Host "Restart Start-HomeListHub.bat to run the updated version." -ForegroundColor Green
    } elseif ($res -and -not $res.available) {
        Write-Host "Already on the latest version (v$($res.version))." -ForegroundColor Green
    }
    exit 0
}

$Store = Load-Store
Update-SelfPeer $Store $Config
Save-Store $Store $Config

$LanIp = Get-LocalLanIp
$Port = [int]$Config.port
$InstalledVer = Get-InstalledVersion

$JoinLink = "homelist://${LanIp}:${Port}?code=$($Config.pairingCode)"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  HomeList Windows Founding Hub v$InstalledVer" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  Household     : $($Config.householdName)"
Write-Host "  Pairing Code  : $($Config.pairingCode)" -ForegroundColor Yellow
Write-Host "  Join Address  : ${LanIp}:${Port}" -ForegroundColor Cyan
Write-Host "  Join Link     : $JoinLink" -ForegroundColor Cyan
if (-not [string]::IsNullOrWhiteSpace($Config.hubUrl)) {
    Write-Host "  Away Hub URL  : $($Config.hubUrl) (auto-shared to family phones)" -ForegroundColor Cyan
}
Write-Host "  Wall Board    : http://localhost:${Port}"
Write-Host "  Updates URL   : $($Config.updateReleaseUrl) (auto-checks nightly)"
Write-Host ""
Write-Host "  On each phone: Join a Family -> enter ${LanIp}:${Port} + code $($Config.pairingCode)" -ForegroundColor Gray
Write-Host "  Keys: [S] Sync LAN peers now  |  [U] Check/Install Update  |  [Ctrl+C] Stop" -ForegroundColor Gray
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""

# Initial pull from seed peer or known LAN peers & initial update check
Sync-KnownLanPeers $Store $Config
[void](Check-HubUpdate $Config)

$Listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Any, $Port)
try {
    $Listener.Start()
} catch {
    Write-Host "ERROR: Could not listen on port $Port ($($_.Exception.Message))." -ForegroundColor Red
    Write-Host "Is another copy of HomeList Hub already running?" -ForegroundColor Yellow
    exit 1
}

$LastLanSync = [DateTime]::UtcNow
$LastUpdateDay = (Get-Date).ToString("yyyy-MM-dd")

try {
    while ($true) {
        if ($Listener.Pending()) {
            $client = $Listener.AcceptTcpClient()
            Handle-HttpClient $client $Store $Config
            continue
        }

        # Handle optional interactive shortcuts in console window
        try {
            if ([Console]::KeyAvailable) {
                $key = [Console]::ReadKey($true).Key
                if ($key -eq [ConsoleKey]::S) {
                    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Triggering LAN peer sync..." -ForegroundColor Cyan
                    Sync-KnownLanPeers $Store $Config
                    $LastLanSync = [DateTime]::UtcNow
                } elseif ($key -eq [ConsoleKey]::U) {
                    $u = Check-HubUpdate $Config -ApplyIfAvailable
                    if ($u -and $u.updated) {
                        Write-Host "Update installed! Please restart Start-HomeListHub.bat." -ForegroundColor Green
                    }
                }
            }
        } catch {}

        # Periodic 5-minute passive LAN sync with family phones
        if (([DateTime]::UtcNow - $LastLanSync).TotalMinutes -ge 5) {
            Sync-KnownLanPeers $Store $Config
            $LastLanSync = [DateTime]::UtcNow
        }

        # Nightly update check (~2:15 AM local time, once per calendar day)
        $nowLocal = Get-Date
        $todayStr = $nowLocal.ToString("yyyy-MM-dd")
        if ($todayStr -ne $LastUpdateDay -and $nowLocal.Hour -ge 2 -and $nowLocal.Minute -ge 15) {
            $LastUpdateDay = $todayStr
            [void](Check-HubUpdate $Config)
        }

        Start-Sleep -Milliseconds 100
    }
} finally {
    $Listener.Stop()
    Save-Store $Store $Config
    Write-Host "HomeList Home Hub stopped cleanly." -ForegroundColor Yellow
}
