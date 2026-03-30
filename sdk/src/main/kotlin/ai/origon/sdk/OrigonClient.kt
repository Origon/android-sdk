package ai.origon.sdk

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

class OrigonClient(config: ClientConfig) : AutoCloseable {

    private var handle: Long = 0L
    private val closed = AtomicBoolean(false)

    init {
        handle = NativeBridge.nativeClientCreate(
            config.endpoint,
            config.token,
            config.userId
        )
        if (handle == 0L) {
            throw OrigonException("Failed to create native client")
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            NativeBridge.nativeClientDestroy(handle)
            handle = 0L
        }
    }

    private fun ensureOpen() {
        if (closed.get()) throw OrigonException("Client has been closed")
    }

    fun pollEvent(): ClientEvent? {
        ensureOpen()
        val raw = NativeBridge.nativePollEventFull(handle) ?: return null

        val eventType = raw[0] as Int
        return when (eventType) {
            0 -> null // ORIGON_EVENT_NONE
            1 -> ClientEvent.MessageAdded(
                message = parseMessage(raw[1] as Array<Any?>),
                index = raw[2] as Int
            )
            2 -> ClientEvent.MessageUpdated(
                message = parseMessage(raw[1] as Array<Any?>),
                index = raw[2] as Int
            )
            3 -> ClientEvent.SessionUpdated(
                sessionId = raw[3] as String
            )
            4 -> ClientEvent.ControlUpdated(
                control = Control.fromNative(raw[4] as Int)
            )
            5 -> {
                @Suppress("UNCHECKED_CAST")
                val toolCallsRaw = raw[5] as Array<Array<Any?>>
                ClientEvent.ToolCalls(
                    toolCalls = toolCallsRaw.map { parseToolCall(it) }
                )
            }
            6 -> ClientEvent.Typing(
                isTyping = (raw[6] as Int) != 0
            )
            7 -> ClientEvent.CallStatus(
                status = raw[7] as String
            )
            8 -> ClientEvent.CallError(
                error = raw[8] as? String
            )
            else -> null
        }
    }

    fun startSession(options: StartSessionOptions): SessionInfo {
        ensureOpen()
        val result = NativeBridge.nativeStartSession(
            handle,
            options.channel.toNative(),
            options.sessionId,
            options.fetchSession
        ) ?: throw OrigonException("Failed to start session")

        return parseSessionInfo(result)
    }

    fun getSessions(): List<SessionSummary> {
        ensureOpen()
        val result = NativeBridge.nativeGetSessions(handle)
            ?: throw OrigonException("Failed to get sessions")

        return result.map { parseSessionSummary(it) }
    }

    fun getSession(sessionId: String): Pair<Control, List<Message>> {
        ensureOpen()
        val result = NativeBridge.nativeGetSession(handle, sessionId)
            ?: throw OrigonException("Failed to get session: $sessionId")

        val control = Control.fromNative(result[0] as Int)
        @Suppress("UNCHECKED_CAST")
        val messagesRaw = result[1] as Array<Array<Any?>>
        val messages = messagesRaw.map { parseMessage(it) }
        return Pair(control, messages)
    }

    fun endSession() {
        ensureOpen()
        val rc = NativeBridge.nativeEndSession(handle)
        if (rc != 0) throw OrigonException("Failed to end session")
    }

    fun sendMessage(payload: SendMessagePayload): String {
        ensureOpen()
        val attachmentMediaIds = payload.attachments.map { it.mediaId }.toTypedArray()
        val attachmentUrls = payload.attachments.map { it.url }.toTypedArray()
        val metaKeys = payload.meta.keys.toTypedArray()
        val metaValues = payload.meta.values.toTypedArray()
        val results = if (payload.results.isNotEmpty()) payload.results.toTypedArray() else null

        val sessionId = NativeBridge.nativeSendMessage(
            handle,
            payload.text,
            payload.html,
            payload.context,
            if (attachmentMediaIds.isNotEmpty()) attachmentMediaIds else null,
            if (attachmentUrls.isNotEmpty()) attachmentUrls else null,
            payload.type,
            results,
            if (metaKeys.isNotEmpty()) metaKeys else null,
            if (metaValues.isNotEmpty()) metaValues else null
        ) ?: throw OrigonException("Failed to send message")

        return sessionId
    }

    fun uploadAttachment(data: ByteArray, filename: String): Pair<AttachmentInfo, Flow<UploadProgress>> {
        ensureOpen()
        val result = NativeBridge.nativeUploadAttachment(handle, data, filename)
            ?: throw OrigonException("Failed to upload attachment")

        val mediaId = result[0] as String
        val url = result[1] as String
        val progressHandle = result[2] as Long

        val attachmentInfo = AttachmentInfo(mediaId = mediaId, url = url)

        val progressFlow: Flow<UploadProgress> = callbackFlow {
            try {
                while (true) {
                    val progress = NativeBridge.nativeProgressPoll(progressHandle)
                    if (progress == null) {
                        break
                    }
                    val uploadProgress = UploadProgress(
                        percent = progress[0],
                        loaded = progress[1].toLong(),
                        total = progress[2].toLong()
                    )
                    trySend(uploadProgress)
                    if (uploadProgress.percent >= 100.0) break
                    delay(50)
                }
            } finally {
                NativeBridge.nativeProgressFree(progressHandle)
            }
            close()
            awaitClose()
        }

        return Pair(attachmentInfo, progressFlow)
    }

    fun deleteAttachment(mediaId: String) {
        ensureOpen()
        val rc = NativeBridge.nativeDeleteAttachment(handle, mediaId)
        if (rc != 0) throw OrigonException("Failed to delete attachment: $mediaId")
    }

    fun getAttachmentUrl(mediaId: String): String {
        ensureOpen()
        return NativeBridge.nativeGetAttachmentUrl(handle, mediaId)
            ?: throw OrigonException("Failed to get attachment URL: $mediaId")
    }

    fun toggleMute(): Boolean {
        ensureOpen()
        val result = NativeBridge.nativeToggleMute(handle)
        if (result < 0) throw OrigonException("Failed to toggle mute")
        return result == 1
    }

    // ── Internal parsers ──

    private fun parseMessage(raw: Array<Any?>): Message {
        return Message(
            role = MessageRole.fromNative(raw[0] as Int),
            text = raw[1] as? String,
            html = raw[2] as? String,
            timestamp = raw[3] as? String,
            loading = (raw[4] as Int) != 0,
            done = (raw[5] as Int) != 0,
            errorText = raw[6] as? String,
            attachments = parseAttachments(raw[7]),
            toolCalls = parseToolCalls(raw[8]),
            toolCallId = raw[9] as? String,
            toolName = raw[10] as? String,
            meta = parseMeta(raw[11])
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAttachments(raw: Any?): List<AttachmentInfo> {
        if (raw == null) return emptyList()
        val arr = raw as Array<Array<Any?>>
        return arr.map { AttachmentInfo(mediaId = it[0] as String, url = it[1] as String) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseToolCalls(raw: Any?): List<ToolCall> {
        if (raw == null) return emptyList()
        val arr = raw as Array<Array<Any?>>
        return arr.map { parseToolCall(it) }
    }

    private fun parseToolCall(raw: Array<Any?>): ToolCall {
        return ToolCall(
            toolCallId = raw[0] as String,
            toolName = raw[1] as String,
            arguments = raw[2] as ByteArray
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMeta(raw: Any?): Map<String, String>? {
        if (raw == null) return null
        val arr = raw as Array<Array<Any?>>
        return arr.associate { (it[0] as String) to (it[1] as String) }
    }

    private fun parseSessionInfo(raw: Array<Any?>): SessionInfo {
        @Suppress("UNCHECKED_CAST")
        val messagesRaw = raw[1] as Array<Array<Any?>>
        @Suppress("UNCHECKED_CAST")
        val configDataRaw = raw[3] as? Array<Array<Any?>>

        return SessionInfo(
            sessionId = raw[0] as String,
            messages = messagesRaw.map { parseMessage(it) },
            control = Control.fromNative(raw[2] as Int),
            configData = configDataRaw?.associate { (it[0] as String) to (it[1] as String) } ?: emptyMap(),
            active = (raw[4] as Int) != 0
        )
    }

    private fun parseSessionSummary(raw: Array<Any?>): SessionSummary {
        @Suppress("UNCHECKED_CAST")
        return SessionSummary(
            sessionId = raw[0] as String,
            channel = Channel.fromNative(raw[1] as Int),
            createdAt = raw[2] as String,
            updatedAt = raw[3] as String,
            lastMessage = parseMessage(raw[4] as Array<Any?>)
        )
    }
}
