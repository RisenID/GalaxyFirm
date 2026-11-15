# Privacy policy

**GalaxyFirm** (`dev.risenid.galaxyfirm`)
Last updated: 16 November 2026

GalaxyFirm is an independent, open-source app. It is not affiliated with, endorsed by or sponsored by Samsung Electronics.

## What the app collects

Nothing. The app has no account, no analytics, no advertising, no crash reporting and no tracking of any kind. The developer receives no data from the app.

## What stays on your device

- The devices you add: the name you give them, model number, region (CSC) code and device type.
- Firmware versions found for those devices and the history of scans.
- Your settings.

This data is stored only in the app's private storage on your phone. It is removed when you clear the app's data or uninstall it. You can also delete it from **Settings → App & data**.

## What is sent over the network

To check for firmware, the app sends the **model number and region code** of each device you add to Samsung's public firmware servers:

- `neofussvr.sslcs.cdngc.net`
- `fota-cloud-dn.ospserver.net`

Nothing else is sent: no personal information, no device identifiers of your phone and no information about your installed apps. These requests are made directly from your phone to Samsung; the developer does not run any server. Samsung's handling of these requests is covered by Samsung's own privacy policy.

## Backups

**Export devices** writes a JSON file to a location you choose. The app never uploads it anywhere.

## Permissions

| Permission | Why |
| --- | --- |
| Internet, network state | Ask Samsung's servers for the latest firmware |
| Notifications | Alert you when a new firmware build is found |
| Alarms & reminders | Run background checks at the interval you choose |
| Run at startup | Restore scheduled checks after the phone restarts |

## Children

The app is not directed at children and collects no data from anyone.

## Changes

Changes to this policy are published in this file in the project's repository.

## Contact

Open an issue at <https://github.com/RisenID/GalaxyFirm/issues>.
