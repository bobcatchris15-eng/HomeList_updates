# HomeList Windows Home Hub (Optional)

Run this lightweight Home Hub on any always-on Windows 10/11 desktop so family phones stay synced in the background—both on home Wi-Fi and away from home.

No Python, Node.js, Docker, or Administrator privileges required.

## Quick Start (2 Minutes)

1. Double-click **`Start-HomeListHub.bat`**.
2. When prompted on first run, enter your household's **6-character Pairing Code** (found in the HomeList phone app under the top-right **Mesh** icon).
   - If Windows Firewall asks, click **Allow access** on Private networks.
3. *(Optional)* Double-click **`Install-AutoStart.bat`** if you want the Hub to start minimized automatically whenever Windows boots.

## Optional: Away-From-Home Sync (Zero Config for Kids)

By default, phones sync with the Windows Hub automatically on home Wi-Fi (and you can open `http://localhost:8765` in your browser to see the live Wall Board).

To also let phones sync over mobile data when away from home without renting any cloud database:

1. Expose `http://localhost:8765` using your preferred method:
   - **Easiest (free HTTPS, no port forwarding):** Install [Cloudflare Tunnel (`cloudflared`)](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/) and run:
     ```bat
     cloudflared tunnel --url http://localhost:8765
     ```
     (Or use a named Cloudflare Tunnel on your own domain for a permanent URL.)
   - **Or** forward port `8765` / use a home WireGuard VPN to your desktop IP.
2. Paste that single URL **once** into **Mesh -> Background & Home Hub Sync** on one Parent phone (or into `homelist-hub-config.json` as `"hubUrl"`).
3. Every other family phone automatically receives that URL on its next sync—kids never have to configure anything.

## Nightly Updates

The Windows Hub checks the exact same GitHub Releases URL (`https://github.com/bobcatchris15-eng/HomeList_updates/releases/latest`) every night at ~2:15 AM and notifies you when a new version is published. Double-click **`Update-HomeListHub.bat`** (or press **`U`** in the Hub window) to update while keeping all saved family data and settings intact.
