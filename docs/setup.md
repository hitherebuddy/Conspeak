# Conspeak Setup Guide

## Prerequisites

### Android App
- Android 8.0 (API 26) or later
- Microphone permission
- Wi-Fi connection to the same LAN as the desktop

### Desktop App (Windows)
- Windows 10 or 11
- Java 17+ (bundled with the release, or install separately for dev builds)
- VB-CABLE Virtual Audio Device (free): https://vb-audio.com/Cable/
- Firewall: allow TCP port 29170 (inbound) and mDNS (UDP 5353)

### For USB Mode (Optional)
- Android: Developer Mode enabled, USB Debugging enabled
- Desktop: ADB installed and in PATH (or use the bundled ADB)
- USB cable connecting phone to PC

## Installation

### Step 1: Install VB-CABLE (Desktop)

1. Download VB-CABLE from https://vb-audio.com/Cable/
2. Extract the ZIP file
3. Right-click `VBCABLE_Setup_x64.exe` → "Run as administrator"
4. Click "Install Driver" and follow prompts
5. Restart your computer
6. Verify: Open Sound Settings → Input devices → you should see "CABLE Output"

### Step 2: Install Desktop App

**From release:**
```
1. Download Conspeak-Desktop-x.x.x.zip from Releases
2. Extract to a folder (e.g., C:\Program Files\Conspeak)
3. Run conspeak-desktop.bat (or .exe if packaged)
```

**From source:**
```bash
cd desktop-app
./gradlew run
```

### Step 3: Install Android App

**From release:**
```
1. Download conspeak-android-x.x.x.apk from Releases
2. Enable "Install from unknown sources" for your file manager
3. Open the APK and install
```

**From source:**
```bash
cd android-app
./gradlew installDebug
```

## First-Time Pairing (Wi-Fi)

1. Ensure both devices are on the same Wi-Fi network
2. Open Conspeak on your desktop — it will show "Waiting for devices..."
3. Open Conspeak on your phone — it will discover the desktop automatically
4. Tap the desktop name on your phone
5. Both devices will display a 6-digit pairing code
6. Verify the codes match, then tap "Confirm" on your phone
7. Connected! The status will show "Streaming"

## Using as a Microphone

1. After connecting, go to your voice app (Discord, Zoom, Teams, etc.)
2. Open audio/voice settings
3. Select **"CABLE Output (VB-Audio Virtual Cable)"** as your microphone
4. Speak into your phone — audio will flow through to the app

## USB Mode Setup

1. Enable Developer Mode on your Android phone:
   - Go to Settings → About Phone → tap "Build Number" 7 times
2. Enable USB Debugging:
   - Settings → Developer Options → USB Debugging → ON
3. Connect phone to PC via USB cable
4. On the phone, approve the "Allow USB debugging?" prompt
5. In Conspeak Desktop, the USB device will appear in the device list
6. Click "Connect via USB" — the app handles ADB forwarding automatically

## Firewall Configuration

If devices can't discover each other:

### Windows Firewall
```powershell
# Allow Conspeak (run as Administrator)
netsh advfirewall firewall add rule name="Conspeak TCP" dir=in action=allow protocol=tcp localport=29170
netsh advfirewall firewall add rule name="Conspeak mDNS" dir=in action=allow protocol=udp localport=5353
```

### Common Issues
- **"No devices found"**: Check both devices are on the same Wi-Fi network/subnet
- **Pairing codes don't match**: Cancel and retry; ensure no MITM
- **Audio choppy**: Increase jitter buffer in Advanced settings; check Wi-Fi signal
- **High latency**: Use 10ms frame size; switch to USB mode; reduce bitrate
- **VB-CABLE not showing**: Restart PC after VB-CABLE install; check Device Manager
- **ADB not found**: Install Android Platform Tools and add to PATH
