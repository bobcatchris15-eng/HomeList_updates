# HomeList Windows Hub

A Windows PC can keep a copy of one or more households and sync with Android devices while it is running. HomeList uses encrypted iroh connections on Wi-Fi and mobile data. Download the Windows Hub ZIP from the same HomeList release as the Android APK.

## Start the hub and pair a phone

1. Extract the full ZIP to a writable folder. Run **Start-HomeListHub.bat** and enter a household name.
2. Install the current HomeList app on the phone. Scan the QR code shown in the hub window, or paste the printed invite under **Join Household**. Enter a display name and choose Parent or Child.
3. Leave the hub running for background sync. **Install-AutoStart.bat** toggles its startup shortcut.

A HomeList invite contains the household pairing code. Share it with household members.

## Host more households

Stop the hub before modifying its configuration from the command line. Run these commands from its folder.

- `HomeListHub.exe --add "Grandma's Cabin"` creates a household with its own invite.
- `HomeListHub.exe --join "homelist://join?…"` adds a household using an invite from a running HomeList device. The printed Endpoint ID can also be saved in that household's app settings.
- `HomeListHub.exe --invites` reprints all invites and the hub Endpoint ID.

Restart the hub after adding a household. Open [the Wall Board](http://localhost:8765) in a browser on the hub PC and use the dropdown to choose a household.

## Upgrade from the PowerShell hub

Stop the old hub and extract the new release into the same folder. On first launch the new hub imports the existing household and its data, saves a copy of the v1 configuration, and prints its iroh Endpoint ID. Upgrade the Android devices in place. On a phone already in the household, open its settings and save the new hub Endpoint ID. That connects the existing membership to the new hub and lets the phone sync its endpoint with the other household devices. Existing PowerShell startup shortcuts forward to the new executable after you extract the full ZIP.

## Updates and local data

The hub checks GitHub Releases at startup and daily while running. Stop it and run **Update-HomeListHub.bat** to install a new binary. The updater preserves the hub identity, configuration, and household snapshots.

The hub creates `homelist-hub-key.bin`, `homelist-hub-config.json`, and a `homelist-hub-data-<householdId>.json` snapshot for each household as it syncs. Back up those files together to retain the same endpoint and household state when moving the hub to a new PC.
