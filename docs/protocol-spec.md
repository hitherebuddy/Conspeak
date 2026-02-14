# Conspeak Protocol Specification v1

## Transport

All communication happens over a TCP stream secured with TLS 1.3
(self-signed certificates, pinned after first pairing).

Port: **29170** (default, configurable).

## Message Framing

Every message on the wire is:

```
┌─────────┬─────────┬──────────────────┐
│ Type(1) │ Len(3)  │ Payload(0..16MB) │
├─────────┼─────────┼──────────────────┤
│  u8     │ u24 BE  │ bytes            │
└─────────┴─────────┴──────────────────┘
```

- **Type**: 1-byte message type identifier
- **Length**: 3-byte big-endian payload length (max 16 MB, but audio frames
  will be <2 KB typically)
- **Payload**: Protobuf-style hand-coded binary or JSON for control messages

## Message Types

| Type | Name | Direction | Description |
|------|------|-----------|-------------|
| 0x01 | PAIR_REQUEST | Phone→Desktop | Initiate pairing |
| 0x02 | PAIR_CHALLENGE | Desktop→Phone | Challenge with cert fingerprint |
| 0x03 | PAIR_CONFIRM | Phone→Desktop | User confirmed code match |
| 0x04 | PAIR_COMPLETE | Desktop→Phone | Pairing stored, ready |
| 0x10 | SESSION_REQUEST | Phone→Desktop | Request to start streaming |
| 0x11 | SESSION_ACCEPT | Desktop→Phone | Accepted, negotiated params |
| 0x12 | SESSION_REJECT | Desktop→Phone | Rejected with reason |
| 0x13 | SESSION_END | Either | Graceful session close |
| 0x20 | AUDIO_FRAME | Phone→Desktop | Opus-encoded audio data |
| 0x21 | AUDIO_CONFIG | Either | Codec parameter change |
| 0x30 | KEEPALIVE | Either | Connection liveness check |
| 0x31 | KEEPALIVE_ACK | Either | Response to keepalive |
| 0x40 | STATUS_UPDATE | Either | Status/diagnostics info |

## Discovery (mDNS)

Desktop advertises:
```
Service: _conspeak._tcp.local.
Port: 29170
TXT records:
  version=1
  name=<user-chosen device name>
  fingerprint=<first 8 hex chars of cert SHA-256>
```

Phone browses for `_conspeak._tcp.local.` and displays discovered desktops.

## Pairing Handshake

```
Phone                                Desktop
  │                                      │
  │──── PAIR_REQUEST ───────────────────►│
  │     {phone_name, phone_cert_fp}      │
  │                                      │
  │◄─── PAIR_CHALLENGE ─────────────────│
  │     {desktop_name, desktop_cert_fp}  │
  │                                      │
  │  Both display: SHA256(phone_fp ‖ desktop_fp)[0:6] as 6-digit code
  │  User verifies they match on both screens
  │                                      │
  │──── PAIR_CONFIRM ───────────────────►│
  │     {confirmed: true}                │
  │                                      │
  │◄─── PAIR_COMPLETE ──────────────────│
  │     {paired: true}                   │
  │                                      │
```

After pairing, both sides store the peer's certificate fingerprint. Future
connections skip the pairing flow and auto-authenticate via TLS cert pinning.

## Session Negotiation

```
Phone                                Desktop
  │                                      │
  │──── SESSION_REQUEST ────────────────►│
  │     {codec: OPUS,                    │
  │      sample_rate: 48000,             │
  │      channels: 1,                    │
  │      bitrate: 64000,                 │
  │      frame_size_ms: 20}             │
  │                                      │
  │◄─── SESSION_ACCEPT ────────────────│
  │     {codec: OPUS,                    │
  │      sample_rate: 48000,             │
  │      channels: 1,                    │
  │      bitrate: 64000,                 │
  │      frame_size_ms: 20}             │
  │                                      │
```

## Audio Frame Format

```
AUDIO_FRAME payload:
┌────────┬────────────┬─────────┬──────────────┐
│ Seq(4) │ Tstamp(8)  │ Len(2)  │ Opus Data    │
├────────┼────────────┼─────────┼──────────────┤
│ u32 BE │ i64 BE ms  │ u16 BE  │ bytes        │
└────────┴────────────┴─────────┴──────────────┘
```

- **Sequence**: Monotonically increasing frame counter (wraps at u32 max)
- **Timestamp**: Milliseconds since session start (sender clock)
- **Length**: Opus frame byte length
- **Opus Data**: The encoded audio

## Keepalive

- Sent every 5 seconds by both sides when no other traffic.
- If no KEEPALIVE_ACK received within 15 seconds, connection is considered lost.
- Reconnect strategy: exponential backoff 1s, 2s, 4s, 8s, 16s, then every 30s.

## Error Handling

- Unknown message types: log and skip (forward compatibility).
- Sequence gaps: jitter buffer handles by inserting silence or PLC (Opus).
- Corrupt frames: TLS ensures integrity; decoder errors → skip frame, log.
