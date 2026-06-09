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
// `userId` is optional — when omitted, the SDK falls back to the device
// identifier (Settings.Secure.ANDROID_ID) so anonymous users still get a
// stable identity.
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
        is ClientEvent.AudioRouteChanged -> speakerOn = event.route == AudioOutputRoute.SPEAKER
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

### Bluetooth permission (for headset calls on Android 12+)

When a Bluetooth (hands-free / HFP) headset is connected, the SDK routes
the call to it automatically — mic **and** earpiece — matching the native
phone app. The required permissions are declared in the SDK manifest and
merge into your app:

| Permission | API | Type | Who grants it |
| --- | --- | --- | --- |
| `MODIFY_AUDIO_SETTINGS` | all | normal | auto-granted at install |
| `BLUETOOTH` (`maxSdkVersion=30`) | ≤ 30 | normal | auto-granted at install |
| `BLUETOOTH_CONNECT` | 31+ | **runtime** | **your app must request it** |

On **Android 11 and below nothing extra is needed** — `BLUETOOTH` is a
normal permission and is granted at install.

On **Android 12+ (API 31+)**, `BLUETOOTH_CONNECT` is a runtime permission.
The SDK declares it but **cannot grant it** — it has no `Activity` to show
the permission dialog, so **your app must request it at runtime** for
Bluetooth headset routing to work.

Request it **only when a Bluetooth headset is actually connected** — its
system dialog reads "find, connect to, and determine the relative position
of nearby devices" (the generic *Nearby devices* group text; the permission
itself only connects to already-paired devices, no scanning/location), so
prompting every user for it would be confusing. `AudioManager.getDevices`
needs no Bluetooth permission, so it's safe to check first:

```kotlin
// Only needed on Android 12+, and only when a BT headset is present.
private val requestBt = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { /* granted or not — the call proceeds either way (see fallback below) */ }

private fun maybeRequestBluetooth(am: AudioManager) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val headsetConnected = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    if (headsetConnected &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
        != PackageManager.PERMISSION_GRANTED
    ) {
        requestBt.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }
}
```

See `RootChatFragment.startCall()` in the example app for the full flow
(mic + conditional Bluetooth) in one place.

**Graceful fallback:** this permission is *not* required for a call to
succeed. If `BLUETOOTH_CONNECT` is missing (or the headset's SCO link
otherwise fails to come up), the SDK waits ~4 s for the link, then falls
back to the built-in mic/earpiece so the call keeps working — you simply
don't get Bluetooth routing. Requesting it is what lets the headset be
used.

> Symptom if you skip this on Android 12+: logcat shows
> `Bluetooth SCO did not connect within 4s; falling back to built-in audio`,
> and the call uses the phone's mic/earpiece instead of the headset.

### Voice controls

```kotlin
// Mute (per session).
client.setMute(id = response.sessionId, muted = true)

// Audio output route — process-global, so no session id. Applied via
// AudioManager (speakerphone / Bluetooth SCO). Resets to AUTOMATIC on each
// new call.
client.setAudioOutput(AudioOutputRoute.SPEAKER)     // force the loudspeaker
client.setAudioOutput(AudioOutputRoute.AUTOMATIC)   // back to the default route (earpiece / wired / Bluetooth)
```

A speaker toggle is typically
`client.setAudioOutput(if (on) AudioOutputRoute.SPEAKER else AudioOutputRoute.AUTOMATIC)`.

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

### Push notifications

Register this device's FCM token so the backend can deliver push
notifications. The host app owns token acquisition — set up
[Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging/android/client)
(its `google-services.json`, the `com.google.gms.google-services` plugin,
and the `com.google.firebase:firebase-messaging` dependency), then
forward the token from your `FirebaseMessagingService`:

```kotlin
import ai.origon.sdk.OrigonClient
import com.google.firebase.messaging.FirebaseMessagingService

class AppMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        OrigonClient.registerForPushNotifications(token)
    }
}
```

Declare the service in your `AndroidManifest.xml`:

```xml
<service
    android:name=".AppMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

`registerForPushNotifications(token)` is a **companion-object** method
and is safe to call **before** the client is initialized — the token is
buffered and sent automatically once `OrigonClient` is created. It is
also safe to call repeatedly (e.g. from `onNewToken`); the latest token
wins. The call returns immediately and runs the network request in the
background; failures are logged, not thrown. FCM has no sandbox/
production split, so no environment is sent.

```kotlin
// On logout:
OrigonClient.unregisterForPushNotifications()
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
| `setAudioOutput(route)` | Voice — override the audio output route (`SPEAKER` / `AUTOMATIC` / `BLUETOOTH`). Process-global. |
| `sendMessage(id, payload)` | Chat — POST `<sessionUrl>/message`. Returns the server-issued `Message`. Fires `MessageAdded` then `MessageUpdated`. |
| `notifyTyping(id)` | Chat — register a keystroke; SDK debounces outbound `/typing` POSTs. |
| `stopTyping(id)` | Chat — force outbound typing state to "off" immediately. |
| `activeSessions()` | Snapshot of every active session. |
| `getSessions()` | `GET /sessions` — list prior sessions for the configured `userId`. |
| `getSession(id)` | `GET /session/<id>` — transcript for one session. |
| `setAttributes(attributes)` | Replace session-level attributes injected as `data.attributes` on `startSession`. |
| `OrigonClient.registerForPushNotifications(token)` | Companion. Register an FCM token (buffered until init; latest wins). |
| `OrigonClient.unregisterForPushNotifications()` | Companion. Remove this device's push registration (e.g. on logout). |
| `startMessage` / `isChatEnabled` / `isCallEnabled` / `multipleChannels` / `attachmentPolicy` | Cached `/config` getters. |
| `OrigonClient.initLogging(filter)` | Install Rust-side `tracing` subscriber. |

### Types

- `ClientConfig` — endpoint, token, optional userId, platform, attributes (`JsonObject?`). `userId` defaults to the device identifier (`Settings.Secure.ANDROID_ID`) when omitted. The application id is resolved automatically from `context.packageName` (passed to `OrigonClient`) and sent as `X-Bundle-Id` on every HTTPS call.
- `Channel` — `CHAT`, `VOICE`.
- `SessionControl` — `AI`, `USER`.
- `MessageRole` — `AI`, `EXTERNAL`, `USER`, `SYSTEM`.
- `MessageStatus` — `SENDING`, `DELIVERED`, `FAILED`.
- `MessageState` — `STREAMING`, `COMPLETED`.
- `Platform` — `MOBILE`, `WEB`, `NONE`.
- `AudioOutputRoute` — `AUTOMATIC` (default route — earpiece / wired / Bluetooth), `SPEAKER` (loudspeaker), `BLUETOOTH`. Argument to `setAudioOutput(route)`.
- `StartSessionOptions` — channel, optional sessionId, optional `data` (raw JSON).
- `StartSessionResponse` — sessionId, url, token.
- `JoinSessionInput` — channel, sessionId, url, token.
- `ActiveSession` — sessionId, channel.
- `AttachmentRule` / `AttachmentPolicy` — tenant policy for attachments.
- `ServerConfig` — full `/config` snapshot (start message, capability flags, attachment policy).
- `DisconnectReason` — sealed class of structured reasons.
- `ClientEvent` — sealed class: `MessageAdded`, `MessageUpdated`, `Connected`, `Reconnecting`, `Reconnected`, `PeerAttached`, `PeerDetached`, `Disconnected`, `CallError`, `AudioRouteChanged`, `ControlUpdated`, `Typing`, `SessionUpdated`. Every variant carries `sessionId`. `AudioRouteChanged` carries the now-current `AudioOutputRoute` (drive a speaker toggle from `route == AudioOutputRoute.SPEAKER`); it fires on OS-driven route changes (headset plug/unplug) as well as your own `setAudioOutput`.
- `Message` — typed transcript line. Carries `id`, `localId`, `role`, `text`, `html`, `userId`, `userName`, `timestamp`, `attachments`, `errorText`, `status`, `state`.
- `Attachment`, `Contact`, `SessionSummary`, `SessionHistory` — typed shapes returned by `getSessions()` / `getSession(id)`.
- `SendMessagePayload` — `text`, `html`, `attachments` (input shape for `sendMessage(id, payload)`).

Attachment uploads (`upload_attachment` etc.) are not yet wired
through the SDK; the `attachments` field on `SendMessagePayload` is
reserved for that future surface.

## License

Proprietary. All rights reserved.
