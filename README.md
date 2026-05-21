# Origon Android SDK

Android SDK for the Origon platform — voice calling over MOQ, plus
session management.

## Requirements

- Android API 26+ (Android 8.0 Oreo)

  The SDK links AAudio (`libaaudio.so`), which was introduced in API 26.
  Apps with a lower `minSdk` can still consume the SDK — see
  [Consumers with `minSdk` < 26](#consumers-with-minsdk--26) below.

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

### Consumers with `minSdk` < 26

If your app targets a lower `minSdk` (e.g. 23), the manifest merger will
fail with a `minSdkVersion 23 cannot be smaller than version 26 declared
in library` error. Two steps to integrate:

1. **Override the SDK's `minSdk` declaration** in your app's
   `AndroidManifest.xml`:

   ```xml
   <manifest xmlns:android="http://schemas.android.com/apk/res/android"
             xmlns:tools="http://schemas.android.com/tools">
       <uses-sdk tools:overrideLibrary="ai.origon.sdk" />
       ...
   </manifest>
   ```

2. **Runtime-gate SDK usage** so it's only initialized / called on
   devices with sufficient OS support:

   ```kotlin
   import android.os.Build

   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
       val client = OrigonClient(context, ClientConfig(...))
       // ... use the SDK
   } else {
       // Hide / disable Origon features on Android < 8.0
   }
   ```

   On API 23-25 devices, `System.loadLibrary` for the SDK's native
   library will fail because `libaaudio.so` is not present. The runtime
   gate must keep `OrigonClient` from being instantiated on those
   devices.

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
