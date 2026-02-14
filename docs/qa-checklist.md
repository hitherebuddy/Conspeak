# Conspeak QA Checklist

## Acceptance Tests (Automated)

### Protocol Module
- [ ] `FrameCodecTest`: All message types encode/decode correctly
- [ ] `FrameCodecTest`: Multiple messages in a stream decode in order
- [ ] `FrameCodecTest`: Header format (type + 3-byte length) is correct
- [ ] `JitterBufferTest`: Frames delivered in order
- [ ] `JitterBufferTest`: Buffering state before enough frames arrive
- [ ] `JitterBufferTest`: Missing frames reported correctly
- [ ] `JitterBufferTest`: Late frames dropped
- [ ] `JitterBufferTest`: Reset clears state
- [ ] `JitterBufferTest`: Packet loss ratio computed
- [ ] `CryptoUtilsTest`: Pairing code is 6 digits
- [ ] `CryptoUtilsTest`: Pairing code is deterministic
- [ ] `CryptoUtilsTest`: Pairing code is order-independent
- [ ] `CryptoUtilsTest`: Different fingerprints → different codes
- [ ] `CryptoUtilsTest`: Self-signed SSL context created successfully

### Run: `cd protocol && ./gradlew test`

## Manual QA Checklist

### Android App - Installation & Permissions
- [ ] APK installs on Android 8+ without errors
- [ ] Microphone permission requested on first launch
- [ ] Notification permission requested on Android 13+
- [ ] App launches to home screen after permission grant

### Android App - Discovery
- [ ] Discovers desktop running on same Wi-Fi within 10 seconds
- [ ] Desktop name and IP displayed correctly
- [ ] Tapping a desktop initiates connection
- [ ] Discovery resumes after toggling Wi-Fi off/on

### Android App - Pairing
- [ ] 6-digit pairing code displayed after tapping desktop
- [ ] Code matches the code on desktop
- [ ] Confirming code establishes connection
- [ ] Canceling code disconnects cleanly
- [ ] Second connection to same desktop auto-pairs (no code prompt)

### Android App - Streaming
- [ ] Audio streams after pairing (status shows "Streaming")
- [ ] Audio level meter responds to voice
- [ ] Mute button silences audio (meter still shows level)
- [ ] Disconnect button stops streaming
- [ ] Gain slider adjusts volume (test at 0.5x and 2.0x)

### Android App - Background
- [ ] Persistent notification appears when streaming starts
- [ ] Audio continues when app is backgrounded
- [ ] Audio continues when screen is locked
- [ ] Notification mute button works
- [ ] Notification stop button stops streaming and removes notification
- [ ] Service restarts after system kills it (START_STICKY)

### Android App - Settings
- [ ] Preset buttons (Balanced/Low Power/High Quality) apply settings
- [ ] Mic source options displayed; MIC is default
- [ ] Audio effects toggles reflect device capability
- [ ] Gain slider changes persist
- [ ] Codec settings (sample rate, bitrate, frame size) update correctly
- [ ] Jitter buffer slider works

### Android App - Reconnection
- [ ] Toggling desktop app off/on triggers auto-reconnect
- [ ] Reconnect succeeds without re-pairing
- [ ] Connection status shows "Reconnecting..." during attempts
- [ ] Reconnection gives up after all backoff delays

### Desktop App - Startup
- [ ] Application starts and shows "Waiting for phone..."
- [ ] IP address and port displayed correctly
- [ ] mDNS service registered (verify with `dns-sd -B _conspeak._tcp`)

### Desktop App - VB-CABLE Detection
- [ ] If VB-CABLE installed: shows "VB-CABLE detected"
- [ ] If not installed: shows warning with install instructions
- [ ] Audio device dropdown lists all compatible devices
- [ ] Selecting a device persists after restart

### Desktop App - Connection & Pairing
- [ ] Pairing code appears when phone initiates pairing
- [ ] Code matches the phone's code
- [ ] After phone confirms, status changes to "Connected"
- [ ] Auto-pair works for previously paired phones

### Desktop App - Audio Reception
- [ ] Audio level meter active during streaming
- [ ] Audio plays through selected output device
- [ ] VB-CABLE audio usable by Discord/Zoom as microphone
- [ ] Mute button silences output

### Desktop App - USB (ADB)
- [ ] ADB availability detected (shows status)
- [ ] Connected ADB devices listed with model name
- [ ] "Setup USB" button sets up reverse forwarding
- [ ] Phone can connect via localhost after USB setup
- [ ] Unauthorized devices shown with warning

### Desktop App - Advanced
- [ ] Device name editable and saved
- [ ] Jitter buffer slider works
- [ ] Diagnostics panel shows live stats
- [ ] Frame count, gap count, data volume update during streaming

### Cross-Cutting
- [ ] TLS encryption verified (Wireshark shows encrypted traffic)
- [ ] No plaintext audio visible on network
- [ ] No telemetry or network calls to external servers
- [ ] CPU usage reasonable during streaming (<15% on modern hardware)
- [ ] Battery usage on Android reasonable (test 30-min session)
- [ ] Latency under 100ms on local Wi-Fi (measure with tap test)
- [ ] Audio quality acceptable for voice chat (test in Discord call)

### Edge Cases
- [ ] Phone connects to desktop that's already connected to another phone
  (second phone should be rejected or queued)
- [ ] Both devices on different subnets: mDNS fails gracefully
- [ ] Desktop firewall blocks connection: error shown on phone
- [ ] Phone loses Wi-Fi mid-stream: reconnect or clean error
- [ ] Desktop crashes mid-stream: phone shows reconnecting
- [ ] Very high packet loss (10%+): audio degrades gracefully, no crash
- [ ] Very high latency (500ms+): jitter buffer adapts

### Performance Targets
| Metric | Target | Acceptable |
|--------|--------|------------|
| End-to-end latency (Wi-Fi) | <80ms | <150ms |
| End-to-end latency (USB) | <30ms | <50ms |
| CPU (Android, streaming) | <10% | <20% |
| CPU (Desktop, receiving) | <5% | <10% |
| Bandwidth (64kbps Opus) | ~80 kbps | <120 kbps |
| Battery drain (1 hr) | <8% | <15% |
