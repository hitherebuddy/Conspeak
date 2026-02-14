# Conspeak Troubleshooting

## Discovery Issues

### Desktop doesn't appear on phone
1. **Same network?** Both devices must be on the same Wi-Fi LAN/subnet.
2. **Firewall?** Windows Firewall may block mDNS (UDP 5353) or TCP 29170.
   ```powershell
   netsh advfirewall firewall add rule name="Conspeak" dir=in action=allow protocol=tcp localport=29170
   netsh advfirewall firewall add rule name="Conspeak mDNS" dir=in action=allow protocol=udp localport=5353
   ```
3. **AP Isolation?** Some routers enable "AP/Client Isolation" which blocks
   device-to-device traffic. Check your router settings.
4. **VPN?** Disable VPN on both devices; it may route traffic off-LAN.
5. **mDNS disabled?** Some enterprise networks block multicast. Try manual IP
   entry in the Android app's Advanced settings.

### Phone doesn't appear on desktop
- The phone advertises via mDNS; the desktop browses. If discovery fails,
  try restarting both apps. Check Wi-Fi is connected (not just mobile data).

## Connection Issues

### Pairing fails
- Ensure both apps are on the latest version.
- Cancel and retry. The 6-digit code changes each attempt.
- If codes never appear, TLS handshake may be blocked by network middleware.

### Auto-reconnect not working
- The phone must have the Conspeak foreground service running (check
  notification bar for the persistent notification).
- The desktop must be running (can be in system tray).
- If the desktop IP changed (DHCP), the phone re-discovers via mDNS.

## Audio Issues

### No audio / silence
1. Check the phone's microphone permission is granted.
2. Check the phone isn't muted in the Conspeak app.
3. Check the desktop app shows "Streaming" status.
4. Check your voice app is using "CABLE Output" as the microphone.
5. Check VB-CABLE is installed and visible in Sound Settings → Input.

### Audio is choppy / glitchy
1. **Wi-Fi signal**: Move closer to the router. 5 GHz is better than 2.4 GHz.
2. **Jitter buffer**: Increase from default 60ms to 100-150ms in Advanced.
3. **Frame size**: Use 20ms (default) or 40ms for more resilient frames.
4. **Bitrate**: Lower to 32kbps if bandwidth is constrained.
5. **Other traffic**: Heavy downloads on the same network cause jitter.
6. **USB mode**: For guaranteed low-latency, use USB connection.

### High latency (>200ms)
1. Reduce jitter buffer to minimum (20ms) if audio is otherwise clean.
2. Use 10ms frame size (increases CPU usage).
3. Switch to USB mode (typically <20ms latency).
4. Use "Low Power" preset which uses 16kHz sample rate (lower quality but
   faster encoding/decoding).

### Echo or feedback
- Ensure the desktop speakers aren't feeding back into the phone mic.
- Use headphones on the desktop, or mute desktop speakers.
- Enable AEC (Acoustic Echo Cancellation) in Advanced settings on the phone.

## USB / ADB Issues

### "ADB not found"
- Install Android SDK Platform Tools:
  https://developer.android.com/tools/releases/platform-tools
- Add the folder to your system PATH.
- Or place `adb.exe` in the Conspeak Desktop folder.

### "No ADB devices detected"
1. Is USB Debugging enabled? Settings → Developer Options → USB Debugging.
2. Did you approve the "Allow USB debugging?" dialog on the phone?
3. Try a different USB cable (some are charge-only, no data).
4. Try a different USB port.
5. Run `adb devices` in a terminal — the device should show as "device" (not
   "unauthorized" or "offline").

### "ADB device unauthorized"
- Unlock the phone and approve the USB debugging authorization prompt.
- If no prompt appears: Settings → Developer Options → Revoke USB debugging
  authorizations, then reconnect.

## Performance Tuning

### Low Power Mode
Enable "Low Power" preset in the phone app:
- Sample rate: 16000 Hz
- Bitrate: 24 kbps
- Frame size: 40 ms
- Effects: disabled
This significantly reduces CPU and battery usage at the cost of audio quality.

### High Quality Mode
- Sample rate: 48000 Hz
- Bitrate: 128 kbps
- Frame size: 10 ms
- Effects: NS + AGC enabled
Best audio quality, higher battery usage. Recommended with USB or strong Wi-Fi.

## Logs

### Android
- In-app: Advanced → Diagnostics → View Logs
- ADB: `adb logcat -s Conspeak`

### Desktop
- In-app: Advanced → View Logs
- File: `%APPDATA%\Conspeak\logs\conspeak.log`
