# 666 Router — Android app for ZTE F670L (PTCL)

Control your PTCL ZTE F670L router from your phone: see connected devices with
live connection time and data, block/unblock any device with a toggle, track
data usage that survives router reboots, and change Wi-Fi name/password — all
through the limited `user` account (no admin needed).

Dark "666" theme: deep ink, warm cream, gold accent.

## Features
- **Login with remember-me** — saves host/username/password on first login.
- **Device list** — every Wi-Fi client, how long connected, data up/down.
- **Block / unblock** — flip a device's toggle (ON = internet allowed, OFF =
  blocked). Uses Parental Controls with the router's RSA "Check" signature.
- **Persistent MB counter** — accumulates per-device + network total. Because
  the router keeps no history and resets counters on reboot, the app samples
  every 60s and adds up the deltas itself, so the total survives router reboots.
- **Counter reset, your choice** — manual button, or auto monthly on a day you
  pick (set via the schedule icon on the usage card).
- **Wi-Fi settings** — change SSID name and/or password for any of the 8 SSIDs
  (AES-encrypted exactly like the router's own web page).

## How to get the APK (no software to install)

This repo includes a GitHub Actions workflow that builds the APK in the cloud.

1. Create a free GitHub account.
2. Make a new repository (e.g. `666-router`).
3. Upload **all** these files/folders to it (drag the whole project in via
   GitHub's "Add file > Upload files", or use `git push`).
4. Go to the repo's **Actions** tab. The "Build APK" workflow runs automatically
   (or click it and press **Run workflow**).
5. When it finishes (~3-5 min), open the run and download the
   **666-router-apk** artifact at the bottom. Inside is `app-debug.apk`.
6. Copy that APK to your phone and open it to install. You'll need to allow
   "install from unknown sources" once (Android will prompt you).

### Or build locally with Android Studio
Open the project folder, let it sync, then **Build > Build APK(s)**. The APK
lands in `app/build/outputs/apk/debug/`.

## First run
1. Open the app. The login screen is pre-filled with `192.168.1.1` / `user`.
2. Enter your router password, keep "Remember me" on, tap **Connect**.
3. You must be on the router's Wi-Fi for the app to reach it.

## Notes & limits
- Reads Wi-Fi clients; a device on an Ethernet cable won't appear.
- Blocking is keyed to a device's MAC; a phone using MAC randomization may
  reappear as a new device.
- The app talks plain HTTP to 192.168.1.1 on your LAN (routers don't use HTTPS
  locally); this is allowed only for that address via the network security config.
- All logic was derived from the real F670L (firmware V9.0.11P3N22B); other
  firmware may differ.
