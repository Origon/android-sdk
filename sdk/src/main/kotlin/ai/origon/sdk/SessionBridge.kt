package ai.origon.sdk

import ai.origon.sdk.bridge.AttachmentPolicy
import ai.origon.sdk.bridge.SessionEvent
import ai.origon.sdk.bridge.StartSessionResponse

/**
 * JNI bridge to the Rust `session` crate.
 *
 * The Rust counterpart lives at `client-sdk/session/src/jni_bridge.rs`
 * and is compiled into `libsession.so` with `--features jni` (see
 * `client-sdk/session/scripts/build-android.sh`). `System.loadLibrary`
 * resolves to that .so via `jniLibs/<abi>/libsession.so`.
 *
 * **ABI-locked.** Method names + signatures here are mirrored by the
 * `Java_ai_origon_sdk_SessionBridge_<methodName>` exports on the Rust
 * side. Renaming here without renaming there will produce
 * `UnsatisfiedLinkError` at first call.
 *
 * Internal — consumers go through `OrigonClient`, which wraps this
 * bridge with Kotlin-idiomatic error handling, coroutines, etc.
 */
internal object SessionBridge {

    init {
        System.loadLibrary("session")
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    @JvmStatic external fun initLogging(filter: String?)

    /**
     * Returns a non-zero opaque handle on success; throws
     * [SessionException] otherwise. Caller owns the handle and must
     * eventually call [destroy].
     */
    @JvmStatic external fun initialize(
        endpoint: String,
        bundleId: String?,
        token: String?,
        userId: String?,
        platform: Int,
        attributesJson: String?,
    ): Long

    @JvmStatic external fun destroy(handle: Long)

    /**
     * Replace session-level attributes injected as `data.attributes` on
     * subsequent `startSession` calls. Null or empty clears.
     */
    @JvmStatic external fun setAttributes(handle: Long, attributesJson: String?)

    // ── Local getters (read from cached /config body) ────────────────

    @JvmStatic external fun getStartMessage(handle: Long): String
    @JvmStatic external fun isMultipleChannelsAllowed(handle: Long): Boolean
    @JvmStatic external fun isChatEnabled(handle: Long): Boolean
    @JvmStatic external fun isCallEnabled(handle: Long): Boolean
    @JvmStatic external fun getAttachmentPolicy(handle: Long): AttachmentPolicy

    // ── Session history fetchers ─────────────────────────────────────

    /** `GET /sessions`. Returns the response body as a JSON string. */
    @JvmStatic external fun getSessions(handle: Long): String

    /** `GET /session/<id>`. Returns the response body as a JSON string. */
    @JvmStatic external fun getSession(handle: Long, id: String): String

    // ── Per-session lifecycle ────────────────────────────────────────

    /**
     * `POST /session/start` — opens a new session, dials its transport.
     * Throws [SessionException] on failure; returns the new session's
     * id, transport URL, and per-session auth token on success.
     *
     * @param sessionId pass an existing session id to resume; null to create new.
     * @param dataJson optional consumer-defined raw JSON payload.
     */
    @JvmStatic external fun startSession(
        handle: Long,
        channel: Int,
        sessionId: String?,
        dataJson: String?,
    ): StartSessionResponse

    /**
     * Attach to a session whose start-session response was obtained out
     * of band (multi-device handoff, deeplink, persisted session).
     * Skips the HTTPS call and dials the transport directly.
     */
    @JvmStatic external fun joinSession(
        handle: Long,
        channel: Int,
        sessionId: String,
        url: String,
        token: String,
    )

    @JvmStatic external fun endSession(handle: Long, id: String)
    @JvmStatic external fun endAllSessions(handle: Long)

    // ── Voice controls ───────────────────────────────────────────────

    @JvmStatic external fun setMute(handle: Long, id: String, muted: Boolean)
    @JvmStatic external fun setMuteAll(handle: Long, muted: Boolean)
    @JvmStatic external fun toggleHold(handle: Long, id: String): Boolean
    @JvmStatic external fun sendDtmf(handle: Long, id: String, digit: Char, durationMs: Int)

    // ── Chat ─────────────────────────────────────────────────────────

    /**
     * POST `<sessionUrl>/message`. `payloadJson` is a JSON-encoded
     * [SendMessagePayload]; pass `null` for an empty payload. Returns
     * the server-issued [Message] as a JSON string for the high-level
     * wrapper to decode.
     */
    @JvmStatic external fun sendMessage(handle: Long, id: String, payloadJson: String?): String

    /** Register a keystroke. The SDK debounces outbound `/typing` POSTs. */
    @JvmStatic external fun notifyTyping(handle: Long, id: String)

    /** Force outbound typing state to "off" immediately. */
    @JvmStatic external fun stopTyping(handle: Long, id: String)

    // ── Active sessions snapshot ─────────────────────────────────────

    /**
     * Returns `[[id, "voice"|"chat"], ...]` — one inner array per
     * active session. Empty array if none.
     */
    @JvmStatic external fun activeSessionIds(handle: Long): Array<Array<String>>

    // ── Event polling ────────────────────────────────────────────────

    /**
     * Non-blocking. Returns null if no event is ready; otherwise a
     * populated [SessionEvent] whose `kind` field selects which
     * variant-specific fields are meaningful.
     */
    @JvmStatic external fun pollEvent(handle: Long): SessionEvent?

    // ── Discriminant constants (mirrored from Rust) ──────────────────

    // Platform — see SessionBridge.initialize(platform: Int).
    const val PLATFORM_NONE = 0
    const val PLATFORM_MOBILE = 1
    const val PLATFORM_WEB = 2

    // Channel — see SessionBridge.startSession(channel: Int).
    const val CHANNEL_CHAT = 0
    const val CHANNEL_VOICE = 1

    // SessionControl — value of SessionEvent.control on CONTROL_UPDATED.
    const val CONTROL_AI = 0
    const val CONTROL_USER = 1

    // Event discriminants — value of SessionEvent.kind.
    const val EVENT_MESSAGE_ADDED = 1
    const val EVENT_MESSAGE_UPDATED = 2
    const val EVENT_SESSION_UPDATED = 3
    const val EVENT_CONTROL_UPDATED = 4
    const val EVENT_TYPING = 6
    const val EVENT_CONNECTED = 7
    const val EVENT_RECONNECTING = 8
    const val EVENT_RECONNECTED = 9
    const val EVENT_PEER_ATTACHED = 10
    const val EVENT_PEER_DETACHED = 11
    const val EVENT_DISCONNECTED = 12
    const val EVENT_CALL_ERROR = 13

    // Error discriminants — value of SessionException.kind.
    const val ERROR_NOT_INITIALIZED = 1
    const val ERROR_NO_SESSION = 2
    const val ERROR_SESSION = 3
    const val ERROR_MISSING_FIELD = 4
    const val ERROR_SERVER_UNAVAILABLE = 5
    const val ERROR_HTTP = 6
    const val ERROR_ATTACHMENT = 7
    const val ERROR_OTHER = 8

    // Disconnect reason discriminants — value of SessionEvent.disconnectReasonKind.
    const val DISCONNECT_REASON_LOCAL_CLOSE = 1
    const val DISCONNECT_REASON_NETWORK_LOSS = 2
    const val DISCONNECT_REASON_ENDPOINT_NOT_PROVISIONED = 3
    const val DISCONNECT_REASON_ENDPOINT_ALREADY_CONNECTED = 4
    const val DISCONNECT_REASON_TOKEN_INVALID = 5
    const val DISCONNECT_REASON_TOKEN_EXPIRED = 6
    const val DISCONNECT_REASON_TOKEN_REPLAYED = 7
    const val DISCONNECT_REASON_PROTOCOL_VIOLATION = 8
    const val DISCONNECT_REASON_CAPABILITY_MISSING = 9
    const val DISCONNECT_REASON_ILLEGAL_STATE = 10
    const val DISCONNECT_REASON_RESOURCE_EXHAUSTED = 11
    const val DISCONNECT_REASON_REPLAY_LOST = 12
    const val DISCONNECT_REASON_SERVER_CLOSED = 13
    const val DISCONNECT_REASON_TRANSPORT_CLOSED = 14
}
