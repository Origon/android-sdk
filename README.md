# Origon Android SDK

Android SDK for the Origon platform.

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
    implementation("ai.origon:sdk:0.1.0")
}
```

## Quick Start

```kotlin
import ai.origon.sdk.*

// Initialize the client
val client = OrigonClient(
    ClientConfig(
        endpoint = "https://api.origon.ai",
        token = "your-auth-token"
    )
)

// Start a chat session
val session = client.startSession(
    StartSessionOptions(channel = Channel.CHAT)
)

// Send a message
val sessionId = client.sendMessage(
    SendMessagePayload(text = "Hello from Android!")
)

// Poll for events
val event = client.pollEvent()
when (event) {
    is ClientEvent.MessageAdded -> println("New: ${event.message.text}")
    is ClientEvent.Typing -> println("Typing: ${event.isTyping}")
    else -> {}
}

// Clean up
client.close()
```

### Voice Sessions

```kotlin
val session = client.startSession(
    StartSessionOptions(channel = Channel.VOICE)
)

val isMuted = client.toggleMute()
```

### Attachments

```kotlin
import kotlinx.coroutines.flow.collect

val (attachment, progressFlow) = client.uploadAttachment(
    data = fileBytes,
    filename = "photo.jpg"
)

progressFlow.collect { progress ->
    println("Upload: ${progress.percent}%")
}

client.sendMessage(
    SendMessagePayload(text = "See attached", attachments = listOf(attachment))
)
```

### Retrieve Past Sessions

```kotlin
val sessions = client.getSessions()

val (control, messages) = client.getSession(sessions.first().sessionId)
```

## API Reference

### OrigonClient

| Method | Description |
|---|---|
| `OrigonClient(config)` | Create a new client instance |
| `close()` | Destroy the client and free resources |
| `pollEvent()` | Poll for the next event (non-blocking) |
| `startSession(options)` | Start or resume a session |
| `getSessions()` | List all sessions |
| `getSession(sessionId)` | Get session details |
| `endSession()` | End the current session |
| `sendMessage(payload)` | Send a message, returns session ID |
| `uploadAttachment(data, filename)` | Upload a file, returns info and progress flow |
| `deleteAttachment(mediaId)` | Delete an uploaded attachment |
| `getAttachmentUrl(mediaId)` | Get the URL for an attachment |
| `toggleMute()` | Toggle microphone mute, returns new state |

### Types

- `ClientConfig` -- endpoint, token, userId
- `Channel` -- CHAT, VOICE
- `Control` -- AGENT, HUMAN
- `MessageRole` -- ASSISTANT, USER, SUPERVISOR, SYSTEM, TOOL
- `Message` -- chat message with role, text, html, attachments, tool calls, meta
- `SessionInfo` -- full session state
- `SessionSummary` -- session list entry
- `StartSessionOptions` -- channel, optional sessionId, fetchSession flag
- `SendMessagePayload` -- text, html, context, attachments, type, results, meta
- `AttachmentInfo` -- mediaId, url
- `UploadProgress` -- percent, loaded, total
- `ToolCall` -- toolCallId, toolName, arguments
- `ClientEvent` -- sealed class with variants for each event type

## License

Proprietary. All rights reserved.
