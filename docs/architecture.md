# Conspeak Architecture

## Overview

Conspeak turns an Android phone into a wireless (or USB-wired) microphone for a
Windows desktop. The system has three deliverables:

| Component | Language / Stack | Role |
|-----------|-----------------|------|
| **protocol** | Kotlin (shared library) | Message definitions, framing, TLS helpers |
| **android-app** | Kotlin, Jetpack Compose, Gradle | Captures mic audio, streams to desktop |
| **desktop-app** | Kotlin, Compose Desktop, Gradle | Receives audio, exposes virtual mic |

```
┌──────────────────────────┐          ┌──────────────────────────┐
│      Android Phone       │          │     Windows Desktop      │
│                          │          │                          │
│  ┌────────────────────┐  │          │  ┌────────────────────┐  │
│  │  AudioRecord (mic) │  │          │  │  Virtual Audio Dev │  │
│  └────────┬───────────┘  │          │  └────────▲───────────┘  │
│           │ PCM           │          │           │ PCM          │
│  ┌────────▼───────────┐  │          │  ┌────────┴───────────┐  │
│  │   Opus Encoder     │  │          │  │   Opus Decoder     │  │
│  └────────┬───────────┘  │          │  └────────▲───────────┘  │
│           │ Opus frames   │          │           │ Opus frames  │
│  ┌────────▼───────────┐  │  TLS/TCP │  ┌────────┴───────────┐  │
│  │ Protocol Client     │──┼──────────┼──│ Protocol Server    │  │
│  │ (LAN / USB-ADB)    │  │          │  │ (LAN / USB-ADB)    │  │
│  └─────────────────────┘  │          │  └─────────────────────┘  │
│                          │          │                          │
│  ┌─────────────────────┐  │          │  ┌─────────────────────┐  │
│  │ mDNS Advertiser    │  │  mDNS   │  │ mDNS Browser       │  │
│  └─────────────────────┘  │◄────────►│  └─────────────────────┘  │
└──────────────────────────┘          └──────────────────────────┘
```

## Connection Modes

### 1. Wi-Fi LAN (MVP)

- **Discovery:** mDNS (DNS-SD). Service type `_conspeak._tcp.local.`
  - Chosen over UDP broadcast because mDNS is standard, works across subnets
    with mDNS repeaters, and has mature libraries on both Android (NsdManager)
    and JVM (JmDNS).
- **Pairing:** First connection requires matching a 6-digit code displayed on
  both devices. The code is derived from a SPAKE2+ exchange (or simplified:
  both sides display a hash of the TLS session and user confirms match).
- **Transport:** TLS 1.3 over TCP. After pairing, the desktop's TLS certificate
  fingerprint is stored on the phone and vice-versa — subsequent connections
  auto-authenticate via certificate pinning.
- **Audio:** Opus-encoded frames in the Conspeak framing protocol over the TLS
  stream.

### 2. USB via ADB (Phase 1.5)

- Requires Developer Mode + USB Debugging on the phone.
- Desktop detects ADB devices, runs `adb reverse tcp:PORT tcp:PORT` so the
  phone can connect to `localhost:PORT` which tunnels over USB.
- Same TLS + protocol on top; transport is just localhost TCP via ADB tunnel.
- Faster, lower latency, no Wi-Fi dependency.

## Audio Pipeline

### Android (Capture)
```
Microphone → AudioRecord (16kHz/48kHz, mono, 16-bit PCM)
           → [NS/AEC/AGC toggle via AudioEffect]
           → [Gain adjustment]
           → Opus encoder (bitrate 24-128 kbps, frame 10/20/40 ms)
           → Protocol framing → TLS socket
```

### Desktop (Playback to Virtual Mic)
```
TLS socket → Protocol de-framing
           → Jitter buffer (adaptive, 20-200 ms)
           → Opus decoder → PCM
           → Virtual audio device write
```

## Virtual Microphone Strategy (Windows)

### MVP: VB-CABLE / Virtual Audio Cable
The user installs [VB-CABLE](https://vb-audio.com/Cable/) (free donationware,
no restrictions). Conspeak Desktop writes PCM audio to VB-CABLE's input endpoint
via WASAPI (Java Sound / JNA). Other apps select "CABLE Output" as their mic.

### Phase 2: Custom Virtual Audio Driver
A dedicated virtual audio driver using Windows Audio Device Graph Isolation
(via a miniport driver or the newer AudioMediaFoundation approach). This would
appear as "Conspeak Microphone" in the system and require no third-party
software. This is a significant undertaking and is deferred.

## Security Model

- All audio transport is encrypted (TLS 1.3, self-signed certs).
- First-time pairing requires visual confirmation of a 6-digit code.
- Certificate fingerprints are persisted; subsequent connections auto-verify.
- No data leaves the LAN. No cloud. No telemetry.

## Dependencies & Licenses

| Dependency | License | Used By |
|-----------|---------|---------|
| Kotlin / Compose | Apache 2.0 | Both |
| JmDNS | Apache 2.0 | Desktop |
| Android NsdManager | Android SDK (Apache 2.0) | Android |
| Opus (concentus) | BSD | Both |
| Bouncy Castle | MIT | TLS helpers |
| JNA | Apache 2.0 / LGPL 2.1 | Desktop (WASAPI) |
| VB-CABLE | Donationware (free) | Desktop (user-installed) |
