# Origon Android SDK

Android SDK for the Origon platform — voice calling over MOQ, plus
session management.

## Requirements

- Android API 24+ (Android 7.0)

## Installation

### 1. Add the repository

In your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/Origon/android-sdk")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

### 2. Add the dependency

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.origon:sdk:0.2.0")
}
```

## Quick Start

```kotlin
import ai.origon.sdk.*

// Optional: install Rust-side logging once at app launch.
OrigonClient.initLogging()

// Create the client.
val client = OrigonClient(
    ClientConfig(
        endpoint = "https://api.origon.ai",
        bundleId = "com.acme.android",
        token = "your-auth-token",
    )
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
| `activeSessions()` | Snapshot of every active session. |
| `getSessions()` | `GET /sessions` — list prior sessions for the configured `userId`. |
| `getSession(id)` | `GET /session/<id>` — transcript for one session. |
| `setAttributes(attributes)` | Replace session-level attributes injected as `data.attributes` on `startSession`. |
| `startMessage` / `isChatEnabled` / `isCallEnabled` / `multipleChannels` / `attachmentPolicy` | Cached `/config` getters. |
| `OrigonClient.initLogging(filter)` | Install Rust-side `tracing` subscriber. |

### Types

- `ClientConfig` — endpoint, bundleId, token, userId, platform, attributes (`JsonObject?`).
- `Channel` — `CHAT`, `VOICE`.
- `Control` — `AGENT`, `HUMAN`.
- `Platform` — `MOBILE`, `WEB`, `NONE`.
- `StartSessionOptions` — channel, optional sessionId, optional `data` (raw JSON).
- `StartSessionResponse` — sessionId, url, token.
- `JoinSessionInput` — channel, sessionId, url, token.
- `ActiveSession` — sessionId, channel.
- `AttachmentRule` / `AttachmentPolicy` — tenant policy for attachments.
- `ServerConfig` — full `/config` snapshot (start message, capability flags, attachment policy).
- `DisconnectReason` — sealed class of structured reasons.
- `ClientEvent` — sealed class: `Connected`, `Reconnecting`, `Reconnected`, `PeerAttached`, `PeerDetached`, `Disconnected`, `CallError`, `ControlUpdated`, `Typing`, `SessionUpdated`. Every variant carries `sessionId`.
- `Message`, `Contact`, `SessionSummary`, `SessionHistory` — typed shapes returned by `getSessions()` / `getSession(id)`.

Chat-side messaging and attachments (`send_message`,
`upload_attachment`, etc.) will be added when the underlying chat
plane lands in the session crate.

## License

Proprietary. All rights reserved.
