# Conspeak - Phone as Microphone

Use your Android phone as a wireless microphone for your Windows desktop.
Free, unlimited, no accounts, no cloud, no telemetry.

## How It Works

```
[Android Phone] ---(Wi-Fi / USB)---> [Desktop App] ---> [Virtual Microphone] ---> Discord/Zoom/etc.
```

The Android app captures mic audio, encodes it with Opus, and streams it over
an encrypted TLS connection to the desktop app. The desktop decodes the audio
and writes it to a virtual audio device that other apps can use as a microphone.

## Project Structure

```
/android-app    Kotlin + Jetpack Compose Android application
/desktop-app    Kotlin + Compose Desktop Windows application
/protocol       Shared protocol library (messages, framing, crypto, jitter buffer)
/docs           Architecture, protocol spec, setup guide, troubleshooting
```

## Quick Start

### Prerequisites

- **Android**: Phone running Android 8.0+
- **Desktop**: Windows 10/11 with Java 17+
- **VB-CABLE**: Free virtual audio driver from https://vb-audio.com/Cable/

### Build & Run

**Desktop:**
```bash
cd desktop-app
./gradlew run
```

**Android:**
```bash
cd android-app
./gradlew installDebug
```

**Protocol tests:**
```bash
cd protocol
./gradlew test
```

### Connect

1. Start the desktop app (it begins listening automatically)
2. Open the Android app on the same Wi-Fi network
3. Tap the discovered desktop to connect
4. Verify the 6-digit pairing code matches on both devices
5. In your voice app, select "CABLE Output" as the microphone

## Features

### MVP (Wi-Fi LAN)
- Encrypted audio streaming over local network (TLS 1.3)
- mDNS device discovery (automatic)
- One-time pairing with 6-digit visual code
- Auto-reconnect after disconnection
- Background streaming with persistent notification
- Opus codec (24-128 kbps, configurable)
- Noise suppression, echo cancellation, auto gain control
- Jitter buffer with packet loss concealment
- Low Power / Balanced / High Quality presets

### Phase 1.5 (USB via ADB)
- ADB device detection and management
- One-click reverse port forwarding setup
- Lower latency than Wi-Fi (<30ms)
- Automatic transport fallback

## Architecture

See [docs/architecture.md](docs/architecture.md) for the full system design.

## Protocol

See [docs/protocol-spec.md](docs/protocol-spec.md) for the wire protocol specification.

## Virtual Microphone (Windows)

**MVP approach:** Install VB-CABLE (free donationware). The desktop app writes
decoded audio to VB-CABLE's input. Other apps select "CABLE Output" as their
microphone input.

**Phase 2:** A custom virtual audio driver that appears as "Conspeak Microphone"
in Windows, eliminating the need for third-party software.

## Security

- All audio is encrypted with TLS 1.3 (self-signed certificates)
- First-time pairing requires visual confirmation of a 6-digit code
- Certificate fingerprints pinned after pairing (no re-prompts)
- No data leaves the local network
- No accounts, no cloud, no telemetry

## License & Dependencies

| Dependency | License |
|-----------|---------|
| Kotlin | Apache 2.0 |
| Jetpack Compose | Apache 2.0 |
| Compose Desktop | Apache 2.0 |
| Concentus (Opus) | BSD |
| JmDNS | Apache 2.0 |
| JNA | Apache 2.0 / LGPL 2.1 |
| VB-CABLE | Free donationware |

## Troubleshooting

See [docs/troubleshooting.md](docs/troubleshooting.md) for common issues.
