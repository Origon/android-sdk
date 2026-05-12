package ai.origon.sdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ── Enums ────────────────────────────────────────────────────────────

@Serializable
enum class Channel {
    @SerialName("chat") CHAT,
    @SerialName("voice") VOICE;

    internal fun toBridge(): Int = when (this) {
        CHAT -> SessionBridge.CHANNEL_CHAT
        VOICE -> SessionBridge.CHANNEL_VOICE
    }

    internal companion object {
        fun fromBridge(value: Int): Channel = when (value) {
            SessionBridge.CHANNEL_CHAT -> CHAT
            SessionBridge.CHANNEL_VOICE -> VOICE
            else -> throw SessionException(
                kind = SessionBridge.ERROR_OTHER,
                statusCode = 0,
                code = null,
                message = "unknown channel discriminant: $value",
            )
        }

        fun fromWire(s: String): Channel = when (s) {
            "voice" -> VOICE
            "chat" -> CHAT
            else -> throw SessionException(
                kind = SessionBridge.ERROR_OTHER,
                statusCode = 0,
                code = null,
                message = "unknown channel: $s",
            )
        }
    }
}

enum class Control {
    AGENT,
    HUMAN;

    internal companion object {
        fun fromBridge(value: Int): Control = when (value) {
            SessionBridge.CONTROL_HUMAN -> HUMAN
            else -> AGENT
        }
    }
}

enum class Platform {
    NONE,
    MOBILE,
    WEB;

    internal fun toBridge(): Int = when (this) {
        NONE -> SessionBridge.PLATFORM_NONE
        MOBILE -> SessionBridge.PLATFORM_MOBILE
        WEB -> SessionBridge.PLATFORM_WEB
    }
}

// ── Configuration / requests ─────────────────────────────────────────

data class ClientConfig(
    val endpoint: String,
    /** Android application id. When set, sent as `X-Bundle-Id` on every HTTPS call. */
    val bundleId: String? = null,
    val token: String? = null,
    val userId: String? = null,
    val platform: Platform = Platform.MOBILE,
    /**
     * Initial session-level attributes. Injected as `data.attributes`
     * on `POST /session/start`. Encoded to a JSON string via
     * `kotlinx.serialization` before crossing the native boundary.
     */
    val attributes: JsonObject? = null,
)

data class StartSessionOptions(
    val channel: Channel,
    /** Existing session id to resume; null for a new session. */
    val sessionId: String? = null,
    /** Optional consumer-defined raw JSON forwarded as `data` on the wire. */
    val data: String? = null,
)

data class StartSessionResponse(
    val sessionId: String,
    val url: String,
    /** Per-session auth token, scoped to this session only. */
    val token: String,
)

data class JoinSessionInput(
    val channel: Channel,
    val sessionId: String,
    val url: String,
    val token: String,
)

data class ActiveSession(
    val sessionId: String,
    val channel: Channel,
)

// ── Server config ────────────────────────────────────────────────────

data class AttachmentRule(
    val enabled: Boolean,
    /** Maximum allowed size in megabytes. */
    val maxSize: Int,
)

data class AttachmentPolicy(
    val images: AttachmentRule,
    val documents: AttachmentRule,
    val videos: AttachmentRule,
    val audio: AttachmentRule,
) {
    companion object {
        /** Fallback policy with every category disabled and `maxSize = 0`. */
        val DISABLED = AttachmentPolicy(
            images = AttachmentRule(enabled = false, maxSize = 0),
            documents = AttachmentRule(enabled = false, maxSize = 0),
            videos = AttachmentRule(enabled = false, maxSize = 0),
            audio = AttachmentRule(enabled = false, maxSize = 0),
        )
    }
}

data class ServerConfig(
    val startMessage: String,
    val multipleChannels: Boolean,
    val isChatEnabled: Boolean,
    val isCallEnabled: Boolean,
    val attachmentPolicy: AttachmentPolicy,
)

// ── Session history ──────────────────────────────────────────────────

/** One transcript line / message. Mirrors the Rust `Message` shape. */
@Serializable
data class Message(
    val role: String,
    val id: String,
    val text: String? = null,
    val html: String? = null,
    val timestamp: String? = null,
    val userId: String? = null,
    val userName: String? = null,
)

@Serializable
data class Contact(
    val id: String,
    val name: String,
)

/** Element of the array returned by [OrigonClient.getSessions]. */
@Serializable
data class SessionSummary(
    val sessionId: String,
    val subject: String,
    val channel: Channel,
    val createdAt: String,
    val updatedAt: String,
    val lastMessage: Message? = null,
    val contact: Contact? = null,
)

/** Returned by [OrigonClient.getSession]. */
@Serializable
data class SessionHistory(
    val history: List<Message> = emptyList(),
)

// ── Disconnect / events ──────────────────────────────────────────────

sealed class DisconnectReason {
    data object LocalClose : DisconnectReason()
    data object NetworkLoss : DisconnectReason()
    data object EndpointNotProvisioned : DisconnectReason()
    data object EndpointAlreadyConnected : DisconnectReason()
    data object TokenInvalid : DisconnectReason()
    data object TokenExpired : DisconnectReason()
    data object TokenReplayed : DisconnectReason()
    data object ProtocolViolation : DisconnectReason()
    data object CapabilityMissing : DisconnectReason()
    data object IllegalState : DisconnectReason()
    data object ResourceExhausted : DisconnectReason()
    data object ReplayLost : DisconnectReason()
    data class ServerClosed(val code: Long, val detail: String?) : DisconnectReason()

    /**
     * Local transport failed before the MOQ session was established
     * (QUIC dial / DNS / TLS / etc.). [detail] carries the underlying
     * error message for diagnostics.
     */
    data class TransportClosed(val detail: String?) : DisconnectReason()

    internal companion object {
        fun fromBridge(kind: Int, serverCode: Long, serverDetail: String?): DisconnectReason =
            when (kind) {
                SessionBridge.DISCONNECT_REASON_LOCAL_CLOSE -> LocalClose
                SessionBridge.DISCONNECT_REASON_NETWORK_LOSS -> NetworkLoss
                SessionBridge.DISCONNECT_REASON_ENDPOINT_NOT_PROVISIONED -> EndpointNotProvisioned
                SessionBridge.DISCONNECT_REASON_ENDPOINT_ALREADY_CONNECTED -> EndpointAlreadyConnected
                SessionBridge.DISCONNECT_REASON_TOKEN_INVALID -> TokenInvalid
                SessionBridge.DISCONNECT_REASON_TOKEN_EXPIRED -> TokenExpired
                SessionBridge.DISCONNECT_REASON_TOKEN_REPLAYED -> TokenReplayed
                SessionBridge.DISCONNECT_REASON_PROTOCOL_VIOLATION -> ProtocolViolation
                SessionBridge.DISCONNECT_REASON_CAPABILITY_MISSING -> CapabilityMissing
                SessionBridge.DISCONNECT_REASON_ILLEGAL_STATE -> IllegalState
                SessionBridge.DISCONNECT_REASON_RESOURCE_EXHAUSTED -> ResourceExhausted
                SessionBridge.DISCONNECT_REASON_REPLAY_LOST -> ReplayLost
                SessionBridge.DISCONNECT_REASON_SERVER_CLOSED ->
                    ServerClosed(serverCode, serverDetail)
                SessionBridge.DISCONNECT_REASON_TRANSPORT_CLOSED ->
                    TransportClosed(serverDetail)
                else -> ServerClosed(serverCode, serverDetail)
            }
    }
}

/**
 * Async event from a session. All variants carry [sessionId] so the
 * consumer can demultiplex when several sessions are active at once.
 *
 * Chat-side variants (message added/updated, tool calls) are not
 * surfaced in this iteration — `OrigonClient.pollEvent` silently skips
 * them. They will be added when the chat plane lands in the session
 * crate.
 */
sealed class ClientEvent {
    abstract val sessionId: String

    data class SessionUpdated(
        override val sessionId: String,
        val newSessionId: String,
    ) : ClientEvent()

    data class ControlUpdated(
        override val sessionId: String,
        val control: Control,
    ) : ClientEvent()

    data class Typing(
        override val sessionId: String,
        val isTyping: Boolean,
    ) : ClientEvent()

    data class Connected(override val sessionId: String) : ClientEvent()

    data class Reconnecting(
        override val sessionId: String,
        val attempt: Int,
        val reason: DisconnectReason,
    ) : ClientEvent()

    data class Reconnected(override val sessionId: String) : ClientEvent()

    data class PeerAttached(
        override val sessionId: String,
        val peerEndpointId: String,
        val alias: Long,
    ) : ClientEvent()

    data class PeerDetached(
        override val sessionId: String,
        val peerEndpointId: String,
        val alias: Long,
    ) : ClientEvent()

    data class Disconnected(
        override val sessionId: String,
        val reason: DisconnectReason,
    ) : ClientEvent()

    /** Voice-side soft error. `message == null` means a previously-surfaced error has cleared. */
    data class CallError(
        override val sessionId: String,
        val message: String?,
    ) : ClientEvent()
}
