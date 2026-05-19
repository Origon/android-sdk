package ai.origon.sdk

import ai.origon.sdk.bridge.SessionEvent
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The primary interface to the Origon platform on Android.
 *
 * Backed by `libsession.so` via [SessionBridge]. One instance owns one
 * native handle and one tokio runtime; create at app start, call
 * [close] (or use `use { }`) at app shutdown.
 *
 * All fallible methods throw [SessionException] with a structured
 * `kind` / `statusCode` / `code` / `message`.
 */
class OrigonClient(
    context: android.content.Context,
    config: ClientConfig,
) : AutoCloseable {

    private val handle: Long = SessionBridge.initialize(
        endpoint = config.endpoint,
        bundleId = context.applicationContext.packageName,
        token = config.token,
        userId = config.userId,
        platform = config.platform.toBridge(),
        attributesJson = config.attributes?.let { JSON.encodeToString(JsonObject.serializer(), it) },
    )
    private val closed = AtomicBoolean(false)

    init {
        // SessionBridge.initialize throws SessionException on failure;
        // a zero handle without an exception would be a bridge bug.
        if (handle == 0L) {
            throw SessionException(
                kind = SessionBridge.ERROR_OTHER,
                statusCode = 0,
                code = null,
                message = "session bridge returned null handle",
            )
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            SessionBridge.destroy(handle)
        }
    }

    private fun ensureOpen() {
        if (closed.get()) {
            throw SessionException(
                kind = SessionBridge.ERROR_NOT_INITIALIZED,
                statusCode = 0,
                code = null,
                message = "client has been closed",
            )
        }
    }

    // ── Cached /config getters ───────────────────────────────────────

    /** Pre-populated first assistant message configured for the tenant. */
    val startMessage: String
        get() {
            ensureOpen()
            return SessionBridge.getStartMessage(handle)
        }

    val isChatEnabled: Boolean
        get() {
            ensureOpen()
            return SessionBridge.isChatEnabled(handle)
        }

    val isCallEnabled: Boolean
        get() {
            ensureOpen()
            return SessionBridge.isCallEnabled(handle)
        }

    /** True when chat and voice may share one session. */
    val multipleChannels: Boolean
        get() {
            ensureOpen()
            return SessionBridge.isMultipleChannelsAllowed(handle)
        }

    val attachmentPolicy: AttachmentPolicy
        get() {
            ensureOpen()
            val raw = SessionBridge.getAttachmentPolicy(handle)
            return AttachmentPolicy(
                images = AttachmentRule(raw.images.enabled, raw.images.maxSize),
                documents = AttachmentRule(raw.documents.enabled, raw.documents.maxSize),
                videos = AttachmentRule(raw.videos.enabled, raw.videos.maxSize),
                audio = AttachmentRule(raw.audio.enabled, raw.audio.maxSize),
            )
        }

    val serverConfig: ServerConfig
        get() = ServerConfig(
            startMessage = startMessage,
            multipleChannels = multipleChannels,
            isChatEnabled = isChatEnabled,
            isCallEnabled = isCallEnabled,
            attachmentPolicy = attachmentPolicy,
        )

    /**
     * Replace session-level attributes injected as `data.attributes` on
     * subsequent [startSession] calls. Pass null to clear.
     */
    fun setAttributes(attributes: JsonObject?) {
        ensureOpen()
        val json = attributes?.let { JSON.encodeToString(JsonObject.serializer(), it) }
        SessionBridge.setAttributes(handle, json)
    }

    // ── Session history ──────────────────────────────────────────────

    /** `GET /sessions` — prior sessions for the configured `userId`. */
    fun getSessions(): List<SessionSummary> {
        ensureOpen()
        val body = SessionBridge.getSessions(handle)
        return try {
            JSON.decodeFromString(body)
        } catch (e: Throwable) {
            throw SessionException(
                kind = SessionBridge.ERROR_OTHER,
                statusCode = 0,
                code = null,
                message = "decode getSessions: ${e.message}",
            )
        }
    }

    /** `GET /session/<id>` — history for one session. */
    fun getSession(id: String): SessionHistory {
        ensureOpen()
        val body = SessionBridge.getSession(handle, id)
        return try {
            JSON.decodeFromString(body)
        } catch (e: Throwable) {
            throw SessionException(
                kind = SessionBridge.ERROR_OTHER,
                statusCode = 0,
                code = null,
                message = "decode getSession: ${e.message}",
            )
        }
    }

    // ── Session lifecycle ────────────────────────────────────────────

    fun startSession(options: StartSessionOptions): StartSessionResponse {
        ensureOpen()
        val raw = SessionBridge.startSession(
            handle = handle,
            channel = options.channel.toBridge(),
            sessionId = options.sessionId,
            dataJson = options.data,
        )
        return StartSessionResponse(
            sessionId = raw.sessionId,
            url = raw.url,
            token = raw.token,
        )
    }

    /**
     * Attach to a session whose [StartSessionResponse] was obtained out
     * of band (multi-device handoff, deeplink, persisted session).
     */
    fun joinSession(input: JoinSessionInput) {
        ensureOpen()
        SessionBridge.joinSession(
            handle = handle,
            channel = input.channel.toBridge(),
            sessionId = input.sessionId,
            url = input.url,
            token = input.token,
        )
    }

    fun endSession(id: String) {
        ensureOpen()
        SessionBridge.endSession(handle, id)
    }

    fun endAllSessions() {
        ensureOpen()
        SessionBridge.endAllSessions(handle)
    }

    /** Snapshot of every active session. */
    fun activeSessions(): List<ActiveSession> {
        ensureOpen()
        val raw = SessionBridge.activeSessionIds(handle)
        return raw.map { row ->
            ActiveSession(sessionId = row[0], channel = Channel.fromWire(row[1]))
        }
    }

    // ── Voice controls ───────────────────────────────────────────────

    fun setMute(id: String, muted: Boolean) {
        ensureOpen()
        SessionBridge.setMute(handle, id, muted)
    }

    fun setMuteAll(muted: Boolean) {
        ensureOpen()
        SessionBridge.setMuteAll(handle, muted)
    }

    /** Returns the new hold state. */
    fun toggleHold(id: String): Boolean {
        ensureOpen()
        return SessionBridge.toggleHold(handle, id)
    }

    /** `digit` must be one of `0-9`, `*`, `#`, `A-D` per RFC 4733. */
    fun sendDtmf(id: String, digit: Char, durationMs: Int) {
        ensureOpen()
        SessionBridge.sendDtmf(handle, id, digit, durationMs)
    }

    // ── Chat ─────────────────────────────────────────────────────────

    /**
     * Chat-only — send a text / HTML message on the named session.
     *
     * Requires an active chat session for [id] (call [startSession]
     * first). The SDK fires [ClientEvent.MessageAdded] (provisional,
     * `status == SENDING`) before the wire round-trip and
     * [ClientEvent.MessageUpdated] (delivered or failed) after — both
     * surface on [pollEvent]. Returns the server-issued [Message].
     */
    fun sendMessage(id: String, payload: SendMessagePayload): Message {
        ensureOpen()
        val payloadJson = JSON.encodeToString(SendMessagePayload.serializer(), payload)
        val responseJson = SessionBridge.sendMessage(handle, id, payloadJson)
        return JSON.decodeFromString(Message.serializer(), responseJson)
    }

    /**
     * Chat-only — register a keystroke on the named session. Cheap to
     * call from a `TextWatcher`; the SDK debounces outbound
     * `<sessionUrl>/typing` POSTs so only one wire call fires per
     * typing burst.
     */
    fun notifyTyping(id: String) {
        ensureOpen()
        SessionBridge.notifyTyping(handle, id)
    }

    /**
     * Chat-only — force outbound typing state to "off" immediately,
     * cancelling any in-flight debounce. UI fires this on empty-text
     * transitions; the SDK also fires it implicitly on [sendMessage]
     * and on [endSession].
     */
    fun stopTyping(id: String) {
        ensureOpen()
        SessionBridge.stopTyping(handle, id)
    }

    // ── Attachments ──────────────────────────────────────────────────

    /**
     * Upload a file from the local filesystem to the named session and
     * return the server-issued [Attachment]. The SDK streams the body
     * straight from disk; auto-detects MIME from a 256-byte head plus
     * the [fileName] extension. Runs on [Dispatchers.IO].
     *
     * [uploadId] doubles as the cancellation key — pass it as
     * `attachmentId` to [deleteAttachment] while the upload is in
     * flight to abort it (throws with `kind = ERROR_CANCELLED`). After
     * completion, use the server-issued `attachment.id` for deletion.
     *
     * [onProgress] fires from a JNI worker thread; hop to the main
     * thread before touching UI state. `percent` is `null` when the
     * total size is unknown.
     *
     * Throws [SessionException]: `ERROR_OTHER` for filesystem errors,
     * `ERROR_ATTACHMENT` for precheck failures (`empty_file`,
     * `policy_unsupported_type`, `policy_type_disabled`,
     * `policy_too_large`), `ERROR_HTTP` / `ERROR_SERVER_UNAVAILABLE`
     * for wire failures, `ERROR_CANCELLED` when cancelled.
     */
    suspend fun uploadAttachment(
        sessionId: String,
        path: String,
        fileName: String,
        uploadId: String = UUID.randomUUID().toString(),
        onProgress: ((UploadProgress) -> Unit)? = null,
    ): Attachment {
        ensureOpen()
        val callback = onProgress?.let { cb ->
            UploadProgressCallback { uploaded, total ->
                val totalOpt: Long? = if (total < 0) null else total
                val percent: Int? = totalOpt?.let {
                    (uploaded * 100 / it.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                }
                cb(UploadProgress(uploaded, totalOpt, percent))
            }
        }
        val json = withContext(Dispatchers.IO) {
            SessionBridge.uploadAttachment(
                handle,
                sessionId,
                uploadId,
                path,
                fileName,
                callback,
            )
        }
        return JSON.decodeFromString(Attachment.serializer(), json)
    }

    /**
     * Convenience overload that copies a [content://] (or `file://` /
     * `android.resource://`) [uri] into `context.cacheDir` before
     * uploading, then deletes the cache file once the upload settles.
     * The SDK can't open `content://` URIs directly.
     */
    suspend fun uploadAttachment(
        sessionId: String,
        context: android.content.Context,
        uri: android.net.Uri,
        fileName: String,
        uploadId: String = UUID.randomUUID().toString(),
        onProgress: ((UploadProgress) -> Unit)? = null,
    ): Attachment {
        ensureOpen()
        val tempFile = withContext(Dispatchers.IO) {
            val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
            val suffix = if (ext.isEmpty()) "" else ".$ext"
            val out = java.io.File.createTempFile("upload-", suffix, context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: throw SessionException(
                SessionBridge.ERROR_OTHER,
                0,
                null,
                "uploadAttachment: could not open content URI $uri",
            )
            out
        }
        return try {
            uploadAttachment(
                sessionId = sessionId,
                path = tempFile.absolutePath,
                fileName = fileName,
                uploadId = uploadId,
                onProgress = onProgress,
            )
        } finally {
            withContext(Dispatchers.IO) { tempFile.delete() }
        }
    }

    /**
     * Convenience overload for in-memory [bytes]: writes them to
     * `context.cacheDir` first, then delegates to the path-based
     * overload.
     */
    suspend fun uploadAttachment(
        sessionId: String,
        context: android.content.Context,
        bytes: ByteArray,
        fileName: String,
        uploadId: String = UUID.randomUUID().toString(),
        onProgress: ((UploadProgress) -> Unit)? = null,
    ): Attachment {
        ensureOpen()
        val tempFile = withContext(Dispatchers.IO) {
            val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
            val suffix = if (ext.isEmpty()) "" else ".$ext"
            val out = java.io.File.createTempFile("upload-", suffix, context.cacheDir)
            out.outputStream().use { it.write(bytes) }
            out
        }
        return try {
            uploadAttachment(
                sessionId = sessionId,
                path = tempFile.absolutePath,
                fileName = fileName,
                uploadId = uploadId,
                onProgress = onProgress,
            )
        } finally {
            withContext(Dispatchers.IO) { tempFile.delete() }
        }
    }

    /**
     * Cancel an in-flight upload or delete a completed attachment on
     * the named session.
     *
     * `attachmentId` is dual-purpose: it can be either the `uploadId`
     * passed to [uploadAttachment] (cancels the in-flight upload — no
     * network call, the upload's awaiter throws [SessionException]
     * with `kind = SessionBridge.ERROR_CANCELLED`) or the server-issued
     * `attachment.id` of a completed upload (issues `DELETE` on the
     * server). The SDK figures it out: it checks its in-flight uploads
     * table first, then falls through to the wire call.
     *
     * Runs on [Dispatchers.IO]. A 404 from the server surfaces as
     * [SessionException] with `kind = SessionBridge.ERROR_HTTP` and
     * `statusCode == 404` — safe to treat as success when your intent
     * was "remove the draft from the UI".
     */
    suspend fun deleteAttachment(sessionId: String, attachmentId: String) {
        ensureOpen()
        withContext(Dispatchers.IO) {
            SessionBridge.deleteAttachment(handle, sessionId, attachmentId)
        }
    }

    // ── Events ───────────────────────────────────────────────────────

    /** Polls the next event. Returns null when the queue is idle. */
    fun pollEvent(): ClientEvent? {
        ensureOpen()
        val raw = SessionBridge.pollEvent(handle) ?: return null
        return mapEvent(raw)
    }

    private fun mapEvent(raw: SessionEvent): ClientEvent? {
        val sid = raw.sessionId ?: return null
        return when (raw.kind) {
            SessionBridge.EVENT_MESSAGE_ADDED -> {
                val msg = decodeMessage(raw.messageJson) ?: return null
                ClientEvent.MessageAdded(sid, msg)
            }

            SessionBridge.EVENT_MESSAGE_UPDATED -> {
                val msg = decodeMessage(raw.messageJson) ?: return null
                ClientEvent.MessageUpdated(sid, raw.updateId.orEmpty(), msg)
            }

            SessionBridge.EVENT_SESSION_UPDATED ->
                ClientEvent.SessionUpdated(sid, raw.newSessionId.orEmpty())

            SessionBridge.EVENT_CONTROL_UPDATED ->
                ClientEvent.ControlUpdated(sid, SessionControl.fromBridge(raw.control))

            SessionBridge.EVENT_TYPING ->
                ClientEvent.Typing(sid, raw.typing)

            SessionBridge.EVENT_CONNECTED ->
                ClientEvent.Connected(sid)

            SessionBridge.EVENT_RECONNECTING ->
                ClientEvent.Reconnecting(
                    sessionId = sid,
                    attempt = raw.reconnectAttempt,
                    reason = DisconnectReason.fromBridge(
                        raw.disconnectReasonKind,
                        raw.disconnectReasonServerCode,
                        raw.disconnectReasonServerDetail,
                    ),
                )

            SessionBridge.EVENT_RECONNECTED ->
                ClientEvent.Reconnected(sid)

            SessionBridge.EVENT_PEER_ATTACHED ->
                ClientEvent.PeerAttached(sid, raw.peerEndpointId.orEmpty(), raw.peerAlias)

            SessionBridge.EVENT_PEER_DETACHED ->
                ClientEvent.PeerDetached(sid, raw.peerEndpointId.orEmpty(), raw.peerAlias)

            SessionBridge.EVENT_DISCONNECTED ->
                ClientEvent.Disconnected(
                    sessionId = sid,
                    reason = DisconnectReason.fromBridge(
                        raw.disconnectReasonKind,
                        raw.disconnectReasonServerCode,
                        raw.disconnectReasonServerDetail,
                    ),
                )

            SessionBridge.EVENT_CALL_ERROR ->
                ClientEvent.CallError(
                    sessionId = sid,
                    message = if (raw.callErrorPresent) raw.callErrorMessage else null,
                )

            else -> null
        }
    }

    /**
     * Decode the bridge's `messageJson` field into a typed [Message].
     * Returns `null` on any parse failure (caller treats as "drop the
     * event"). Lenient — unknown keys are ignored so SDK-vs-server
     * schema drift doesn't lose the whole event.
     */
    private fun decodeMessage(json: String?): Message? {
        if (json.isNullOrEmpty()) return null
        return try {
            JSON.decodeFromString(Message.serializer(), json)
        } catch (e: Throwable) {
            null
        }
    }

    companion object {
        /**
         * Install the global tracing subscriber. Idempotent — only the
         * first call installs; subsequent calls are no-ops.
         *
         * `filter` accepts `RUST_LOG`-style directives. Pass null for
         * the SDK default.
         */
        fun initLogging(filter: String? = null) {
            SessionBridge.initLogging(filter)
        }

        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
