package ai.origon.sdk

enum class Channel {
    CHAT,
    VOICE;

    internal companion object {
        fun fromNative(value: Int): Channel = when (value) {
            0 -> CHAT
            1 -> VOICE
            else -> throw OrigonException("Unknown channel value: $value")
        }
    }

    internal fun toNative(): Int = ordinal
}

enum class Control {
    AGENT,
    HUMAN;

    internal companion object {
        fun fromNative(value: Int): Control = when (value) {
            0 -> AGENT
            1 -> HUMAN
            else -> throw OrigonException("Unknown control value: $value")
        }
    }

    internal fun toNative(): Int = ordinal
}

enum class MessageRole {
    ASSISTANT,
    USER,
    SUPERVISOR,
    SYSTEM,
    TOOL;

    internal companion object {
        fun fromNative(value: Int): MessageRole = when (value) {
            0 -> ASSISTANT
            1 -> USER
            2 -> SUPERVISOR
            3 -> SYSTEM
            4 -> TOOL
            else -> throw OrigonException("Unknown message role value: $value")
        }
    }

    internal fun toNative(): Int = ordinal
}

data class AttachmentInfo(
    val mediaId: String,
    val url: String
)

data class ToolCall(
    val toolCallId: String,
    val toolName: String,
    val arguments: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ToolCall) return false
        return toolCallId == other.toolCallId &&
            toolName == other.toolName &&
            arguments.contentEquals(other.arguments)
    }

    override fun hashCode(): Int {
        var result = toolCallId.hashCode()
        result = 31 * result + toolName.hashCode()
        result = 31 * result + arguments.contentHashCode()
        return result
    }
}

data class Message(
    val role: MessageRole,
    val text: String?,
    val html: String?,
    val timestamp: String?,
    val loading: Boolean,
    val done: Boolean,
    val errorText: String?,
    val attachments: List<AttachmentInfo>,
    val toolCalls: List<ToolCall>,
    val toolCallId: String?,
    val toolName: String?,
    val meta: Map<String, String>?
)

data class SessionInfo(
    val sessionId: String,
    val messages: List<Message>,
    val control: Control,
    val configData: Map<String, String>,
    val active: Boolean
)

data class SessionSummary(
    val sessionId: String,
    val channel: Channel,
    val createdAt: String,
    val updatedAt: String,
    val lastMessage: Message
)

data class StartSessionOptions(
    val channel: Channel,
    val sessionId: String? = null,
    val fetchSession: Boolean = false
)

data class SendMessagePayload(
    val text: String? = null,
    val html: String? = null,
    val context: ByteArray? = null,
    val attachments: List<AttachmentInfo> = emptyList(),
    val type: String? = null,
    val results: List<ByteArray> = emptyList(),
    val meta: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SendMessagePayload) return false
        return text == other.text &&
            html == other.html &&
            (context contentEquals other.context) &&
            attachments == other.attachments &&
            type == other.type &&
            results.size == other.results.size &&
            results.zip(other.results).all { (a, b) -> a.contentEquals(b) } &&
            meta == other.meta
    }

    override fun hashCode(): Int {
        var result = text?.hashCode() ?: 0
        result = 31 * result + (html?.hashCode() ?: 0)
        result = 31 * result + (context?.contentHashCode() ?: 0)
        result = 31 * result + attachments.hashCode()
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + results.sumOf { it.contentHashCode() }
        result = 31 * result + meta.hashCode()
        return result
    }
}

data class UploadProgress(
    val percent: Double,
    val loaded: Long,
    val total: Long
)

data class ClientConfig(
    val endpoint: String,
    /** Android application id, e.g. `com.acme.android`. Required — the
     *  server reads it on `GET /config` to pick the right tenant. */
    val bundleId: String,
    val token: String? = null,
    val userId: String? = null
)

sealed class ClientEvent {
    data class MessageAdded(val message: Message, val index: Int) : ClientEvent()
    data class MessageUpdated(val message: Message, val index: Int) : ClientEvent()
    data class SessionUpdated(val sessionId: String) : ClientEvent()
    data class ControlUpdated(val control: Control) : ClientEvent()
    data class ToolCalls(val toolCalls: List<ToolCall>) : ClientEvent()
    data class Typing(val isTyping: Boolean) : ClientEvent()
    data class CallStatus(val status: String) : ClientEvent()
    data class CallError(val error: String?) : ClientEvent()
}

// ── Server config ──

/**
 * Tenant configuration returned by `GET /config` at connect time.
 * Exposed so consumers can gate UI on chat/call availability, render
 * attachment limits, or read the start message.
 */
data class ServerConfig(
    val startMessage: String,
    val concurrentChannels: Boolean,
    val isChatEnabled: Boolean,
    val isCallEnabled: Boolean,
    val attachmentPolicy: AttachmentPolicy
)

data class AttachmentPolicy(
    val images: AttachmentRule,
    val documents: AttachmentRule,
    val videos: AttachmentRule,
    val audio: AttachmentRule
) {
    companion object {
        /** Fallback returned when the native layer refuses to hand back
         *  the policy (e.g. null handle). All categories disabled. */
        val DISABLED = AttachmentPolicy(
            images = AttachmentRule(enabled = false, maxSize = 0u),
            documents = AttachmentRule(enabled = false, maxSize = 0u),
            videos = AttachmentRule(enabled = false, maxSize = 0u),
            audio = AttachmentRule(enabled = false, maxSize = 0u),
        )
    }
}

data class AttachmentRule(
    val enabled: Boolean,
    /** Maximum allowed size in megabytes. */
    val maxSize: UInt
)

// ── Errors ──

/**
 * Structured failure reason surfaced by [OrigonClient]'s constructor.
 * The type lets the caller dispatch on `code` (e.g. `bundle_id_not_allowed`)
 * rather than string-matching messages.
 */
sealed class ConnectError {
    /** `field` names the missing input — `endpoint` or `bundle_id`. */
    data class MissingField(val field: String) : ConnectError()
    /** DNS/TLS/connect/timeout/body-decode failure. */
    data class Transport(val message: String) : ConnectError()
    /** 403 from the server; `code` is the machine-readable envelope code. */
    data class Forbidden(val code: String, val message: String) : ConnectError()
    /** Other non-2xx, non-5xx response with the envelope attached. */
    data class Http(val status: Int, val code: String, val message: String) : ConnectError()
    /** 5xx — treat as transient and let the user retry. */
    data class ServerUnavailable(val status: Int) : ConnectError()
    /** Unexpected native-layer failure. */
    data class Unknown(val message: String) : ConnectError()

    internal companion object {
        // Mirror of `OrigonConnectErrorKind` in native_client.h.
        private const val NONE = 0
        private const val MISSING_FIELD = 1
        private const val TRANSPORT = 2
        private const val FORBIDDEN = 3
        private const val HTTP = 4
        private const val SERVER_UNAVAILABLE = 5
        private const val UNKNOWN = 6

        fun fromNative(kind: Int, status: Int, code: String, message: String): ConnectError =
            when (kind) {
                MISSING_FIELD -> MissingField(field = code.ifEmpty { "unknown" })
                TRANSPORT -> Transport(message = message)
                FORBIDDEN -> Forbidden(code = code, message = message)
                HTTP -> Http(status = status, code = code, message = message)
                SERVER_UNAVAILABLE -> ServerUnavailable(status = status)
                NONE, UNKNOWN -> Unknown(message = message.ifEmpty { "client create failed" })
                else -> Unknown(message = "unexpected error kind: $kind")
            }
    }
}

class OrigonException(message: String) : Exception(message)

/** Thrown when `OrigonClient(config)` fails. Carries the structured
 *  [ConnectError] so the host app can build a readable message. */
class OrigonConnectException(val reason: ConnectError) :
    Exception(reason.toString())
