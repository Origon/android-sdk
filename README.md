# Origon Android SDK

Android SDK for the Origon platform — voice calling over MOQ, plus
session management.

## Requirements

- Android API 23+ (Android 6.0 Marshmallow)

  The native audio backend uses [Oboe](https://github.com/google/oboe),
  which selects AAudio on API 27+ and OpenSL ES on API 23-26 at runtime.
  No special integration is required on any supported API level.

## Installation

### 1. Add the repository

The SDK is published to [Maven Central](https://central.sonatype.com/artifact/ai.origon/sdk).
No authentication is required.

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

### 2. Add the dependency

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.origon:sdk:0.1.0-alpha.1")
}
```

## Quick Start

```kotlin
import ai.origon.sdk.*

// Optional: install Rust-side logging once at app launch.
OrigonClient.initLogging()

// Create the client. `context` is an Android Context (usually
// `applicationContext`); the SDK uses it to read `packageName` and
// send it as `X-Bundle-Id` on every HTTPS call.
val client = OrigonClient(
    context,
    ClientConfig(
        endpoint = "https://api.origon.ai",
        token = "your-auth-token",
    ),
)

// Start a voice session.
val response = client.startSession(StartSessionOptions(channel = Channel.VOICE))
println("session ${response.sessionId} dialing ${response.url}")

// Drain the event stream.
while (true) {
    when (val event = client.pollEvent()) {
        is ClientEvent.Connected -> println("connected")
        is ClientEvent.PeerAttached -> println("peer ${event.peerEndpointId}")
        is ClientEvent.Disconnected -> { println("disconnected: ${event.reason}"); break }
        null -> Thread.sleep(50)
        else -> {}
    }
}

client.close()
```

### Microphone permission (required for voice calls)

The SDK's manifest declares `RECORD_AUDIO`, so it merges into your app
automatically — but `RECORD_AUDIO` is a **runtime ("dangerous")
permission**. On Android 6+ (API 23+) the manifest declaration alone is
not enough: **your app must request the grant at runtime before starting
a voice session.** Without it, the SDK's audio-capture (the input)
stream fails to open — playback still works, but the call has no outgoing
audio (the peer hears silence). This is the consumer app's
responsibility, not the SDK's — the SDK has no `Activity` to drive the
permission dialog.

```kotlin
// In an Activity / Fragment — request before calling startSession(VOICE).
private val requestMic = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) startVoiceCall() else showMicRequiredMessage()
}

private fun onCallButtonTapped() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED
    ) {
        startVoiceCall()
    } else {
        requestMic.launch(Manifest.permission.RECORD_AUDIO)
    }
}
```

> Symptom if you skip this: logcat shows the capture stream failing to
> open (e.g. `IAudioFlinger: createRecord returned error -1`), followed by
> the SDK logging `audio device start failed, continuing without audio`.

### Voice controls

```kotlin
client.setMute(id = response.sessionId, muted = true)
val onHold = client.toggleHold(id = response.sessionId)
client.sendDtmf(id = response.sessionId, digit = '5', durationMs = 100)
```

### Multiple sessions

```kotlin
val active: List<ActiveSession> = client.activeSessions()
client.setMuteAll(muted = true)
client.endAllSessions()
```

### Joining a pre-obtained session

```kotlin
client.joinSession(JoinSessionInput(
    channel = Channel.VOICE,
    sessionId = "...",
    url = "...",
    token = "...",
))
```

### Chat

`sendMessage`, `notifyTyping`, and `stopTyping` all require an active
chat session. **Call `startSession(channel = Channel.CHAT, ...)`
first** — otherwise these throw `SessionException(kind = NO_SESSION)`.
The same applies after `endSession(id)`.

```kotlin
// Outbound send. The SDK fires ClientEvent.MessageAdded (status =
// SENDING) before the wire round-trip and ClientEvent.MessageUpdated
// (delivered or failed) after — both surface on pollEvent(). The
// return value is the server-issued Message.
val msg = client.sendMessage(
    id = sessionId,
    payload = SendMessagePayload(text = "hello", html = "hello"),
)

// Typing — call per keystroke (e.g. from a TextWatcher). The SDK
// debounces; only one outbound `{state: "on"}` fires per typing burst
// and a `{state: "off"}` is auto-emitted after ~3 s of no further
// calls. Fire `stopTyping(id)` explicitly when the input clears
// (e.g. user deleted all text) to snap the peer's "typing…"
// indicator off instantly.
client.notifyTyping(id = sessionId)
client.stopTyping(id = sessionId)
```

Polling chat events:

```kotlin
while (true) {
    val event = client.pollEvent() ?: break
    when (event) {
        is ClientEvent.MessageAdded ->
            // Store event.message under (message.localId ?: message.id)
        is ClientEvent.MessageUpdated ->
            // Look up the row by event.id (matches the original lookup key)
        is ClientEvent.Typing ->
            // Show / hide "typing…" indicator
        else -> Unit
    }
}
```

## API Reference

### OrigonClient

| Method | Description |
|---|---|
| `OrigonClient(config)` | Create a new client. Throws `SessionException` on connect failure. |
| `close()` | Release the native handle. |
| `pollEvent()` | Non-blocking poll. Returns `null` when idle. |
| `startSession(options)` | Open a session. Returns `(sessionId, url, token)`. |
| `joinSession(input)` | Attach to a previously-obtained `StartSessionResponse`. |
| `endSession(id)` / `endAllSessions()` | Close a single / every session. |
| `setMute(id, muted)` / `setMuteAll(muted)` | Voice — absolute mute. |
| `toggleHold(id)` | Voice — toggle hold. Returns the new state. |
| `sendDtmf(id, digit, durationMs)` | Voice — send a DTMF digit per RFC 4733. |
| `sendMessage(id, payload)` | Chat — POST `<sessionUrl>/message`. Returns the server-issued `Message`. Fires `MessageAdded` then `MessageUpdated`. |
| `notifyTyping(id)` | Chat — register a keystroke; SDK debounces outbound `/typing` POSTs. |
| `stopTyping(id)` | Chat — force outbound typing state to "off" immediately. |
| `activeSessions()` | Snapshot of every active session. |
| `getSessions()` | `GET /sessions` — list prior sessions for the configured `userId`. |
| `getSession(id)` | `GET /session/<id>` — transcript for one session. |
| `setAttributes(attributes)` | Replace session-level attributes injected as `data.attributes` on `startSession`. |
| `startMessage` / `isChatEnabled` / `isCallEnabled` / `multipleChannels` / `attachmentPolicy` | Cached `/config` getters. |
| `OrigonClient.initLogging(filter)` | Install Rust-side `tracing` subscriber. |

### Types

- `ClientConfig` — endpoint, token, userId, platform, attributes (`JsonObject?`). The application id is resolved automatically from `context.packageName` (passed to `OrigonClient`) and sent as `X-Bundle-Id` on every HTTPS call.
- `Channel` — `CHAT`, `VOICE`.
- `SessionControl` — `AI`, `USER`.
- `MessageRole` — `AI`, `EXTERNAL`, `USER`, `SYSTEM`.
- `MessageStatus` — `SENDING`, `DELIVERED`, `FAILED`.
- `MessageState` — `STREAMING`, `COMPLETED`.
- `Platform` — `MOBILE`, `WEB`, `NONE`.
- `StartSessionOptions` — channel, optional sessionId, optional `data` (raw JSON).
- `StartSessionResponse` — sessionId, url, token.
- `JoinSessionInput` — channel, sessionId, url, token.
- `ActiveSession` — sessionId, channel.
- `AttachmentRule` / `AttachmentPolicy` — tenant policy for attachments.
- `ServerConfig` — full `/config` snapshot (start message, capability flags, attachment policy).
- `DisconnectReason` — sealed class of structured reasons.
- `ClientEvent` — sealed class: `MessageAdded`, `MessageUpdated`, `Connected`, `Reconnecting`, `Reconnected`, `PeerAttached`, `PeerDetached`, `Disconnected`, `CallError`, `ControlUpdated`, `Typing`, `SessionUpdated`. Every variant carries `sessionId`.
- `Message` — typed transcript line. Carries `id`, `localId`, `role`, `text`, `html`, `userId`, `userName`, `timestamp`, `attachments`, `errorText`, `status`, `state`.
- `Attachment`, `Contact`, `SessionSummary`, `SessionHistory` — typed shapes returned by `getSessions()` / `getSession(id)`.
- `SendMessagePayload` — `text`, `html`, `attachments` (input shape for `sendMessage(id, payload)`).

Attachment uploads (`upload_attachment` etc.) are not yet wired
through the SDK; the `attachments` field on `SendMessagePayload` is
reserved for that future surface.

## License

Proprietary. All rights reserved.
