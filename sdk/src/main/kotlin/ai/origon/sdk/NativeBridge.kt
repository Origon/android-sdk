package ai.origon.sdk

internal object NativeBridge {

    init {
        System.loadLibrary("native_client")
    }

    // ── Lifecycle ──

    @JvmStatic
    external fun nativeClientCreate(endpoint: String, token: String?, externalId: String?): Long

    @JvmStatic
    external fun nativeClientDestroy(handle: Long)

    // ── Events ──
    // Returns a serialised event as fields packed into an Object array.
    // Layout: [eventType: Int, ...payload fields]
    // ORIGON_EVENT_NONE => null return

    @JvmStatic
    external fun nativePollEvent(handle: Long): LongArray?

    @JvmStatic
    external fun nativePollEventMessage(handle: Long): Array<Any?>?

    @JvmStatic
    external fun nativePollEventSessionId(handle: Long): String?

    @JvmStatic
    external fun nativePollEventControl(handle: Long): Int

    @JvmStatic
    external fun nativePollEventToolCalls(handle: Long): Array<Array<Any?>>?

    @JvmStatic
    external fun nativePollEventTyping(handle: Long): Int

    @JvmStatic
    external fun nativePollEventCallStatus(handle: Long): String?

    @JvmStatic
    external fun nativePollEventCallError(handle: Long): String?

    @JvmStatic
    external fun nativePollEventFull(handle: Long): Array<Any?>?

    // ── Sessions ──

    @JvmStatic
    external fun nativeStartSession(
        handle: Long,
        channelOrdinal: Int,
        sessionId: String?,
        fetchSession: Boolean
    ): Array<Any?>?

    @JvmStatic
    external fun nativeGetSessions(handle: Long): Array<Array<Any?>>?

    @JvmStatic
    external fun nativeGetSession(handle: Long, sessionId: String): Array<Any?>?

    @JvmStatic
    external fun nativeEndSession(handle: Long): Int

    // ── Messaging ──

    @JvmStatic
    external fun nativeSendMessage(
        handle: Long,
        text: String?,
        html: String?,
        context: ByteArray?,
        attachmentMediaIds: Array<String>?,
        attachmentUrls: Array<String>?,
        type: String?,
        results: Array<ByteArray>?,
        metaKeys: Array<String>?,
        metaValues: Array<String>?
    ): String?

    // ── Attachments ──

    @JvmStatic
    external fun nativeUploadAttachment(
        handle: Long,
        data: ByteArray,
        filename: String
    ): Array<Any?>?

    @JvmStatic
    external fun nativeProgressPoll(progressHandle: Long): DoubleArray?

    @JvmStatic
    external fun nativeProgressFree(progressHandle: Long)

    @JvmStatic
    external fun nativeDeleteAttachment(handle: Long, mediaId: String): Int

    @JvmStatic
    external fun nativeGetAttachmentUrl(handle: Long, mediaId: String): String?

    // ── Voice ──

    @JvmStatic
    external fun nativeToggleMute(handle: Long): Int
}
