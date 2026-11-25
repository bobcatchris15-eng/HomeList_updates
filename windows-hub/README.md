# HomeList Windows Founding Hub

Run this lightweight Founding Hub on your Windows 10/11 desktop so it holds the household's always-on copy and keeps every family phone synced in the background (both on home Wi-Fi and optionally away from home).

No Python, Node.js, Docker, or Administrator privileges required.

## Quick Start (2 Minutes)

1. Double-click **`Start-HomeListHub.bat`**.
2. Enter your **Household Name** and press **Enter** to auto-generate your **6-character Pairing Code** (or type one you prefer).
   - If Windows Firewall asks, click **Allow access** on Private networks.
3. On each family phone:
   - Open HomeList -> **Join a Family**
   - Enter the **Join Address** (e.g., `192.168.x.x:8765`) and **Pairing Code** printed in the Hub window.
   - Choose **Parent** or **Child** and tap **Connect & Join Family**.
4. *(Optional)* Double-click **`Install-AutoStart.bat`** so the Hub starts minimized automatically whenever Windows boots.

Because this PC holds the persistent copy in `homelist-hub-data.json`, even if a phone is reset or reinstalled, re-joining with the pairing code immediately restores the whole family!

## Optional: Away-From-Home Sync (Zero Config for Kids)

To let phones sync over mobile data when away from home without renting any cloud database:

1. Expose `http://localhost:8765` using your preferred method:
   - **Easiest (free HTTPS, no port forwarding):** Install [Cloudflare Tunnel (`cloudflared`)](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/) and run:
     ```bat
     cloudflared tunnel --url http://localhost:8765
     ```
     (Or use a named Cloudflare Tunnel on your own domain for a permanent URL.)
   - **Or** forward port `8765` / use a home WireGuard VPN to your desktop IP.
2. Put that URL once into `"hubUrl"` in `homelist-hub-config.json` (or under **Mesh -> Background & Home Hub Sync** on a Parent phone).
3. Every family phone automatically receives that URL on its next sync—kids never have to configure anything.

## Nightly Updates

The Windows Hub checks the exact same GitHub Releases URL (`https://github.com/bobcatchris15-eng/HomeList_updates/releases/latest`) every night at ~2:15 AM and notifies you when a new version is published. Double-click **`Update-HomeListHub.bat`** (or press **`U`** in the Hub window) to update while keeping `homelist-hub-config.json` and `homelist-hub-data.json` intact.
