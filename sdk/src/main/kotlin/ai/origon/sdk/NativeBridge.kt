package ai.origon.sdk

internal object NativeBridge {

    init {
        System.loadLibrary("native_client")
    }

    // ── Lifecycle ──

    /**
     * Creates a client and fetches `/config`.
     *
     * Returns an Object array with this fixed layout:
     *   [0] handle: Long  (0 on failure, non-zero on success)
     *   [1] errorKind: Int — see `OrigonConnectErrorKind` in native_client.h
     *   [2] status: Int — HTTP status when applicable, else 0
     *   [3] code: String? — envelope `code` or field name (nullable)
     *   [4] message: String? — human-readable detail (nullable)
     *
     * On success slots [1..4] are (0, 0, null, null). The JNI layer
     * always hands back 5 slots so the Kotlin side never needs to null-check
     * the array itself.
     */
    @JvmStatic
    external fun nativeClientCreate(
        endpoint: String,
        bundleId: String,
        token: String?,
        userId: String?
    ): Array<Any?>

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

    @JvmStatic
    external fun nativeAttachmentsAllowed(handle: Long): Int

    // ── Server config ──

    @JvmStatic
    external fun nativeGetStartMessage(handle: Long): String?

    @JvmStatic
    external fun nativeIsChatEnabled(handle: Long): Int

    @JvmStatic
    external fun nativeIsCallEnabled(handle: Long): Int

    @JvmStatic
    external fun nativeConcurrentChannels(handle: Long): Int

    /**
     * Attachment policy as a flat int array. Returns null on handle error.
     * Layout: [imgEn, imgSz, docEn, docSz, vidEn, vidSz, audEn, audSz]
     *   *En   — 0/1 enabled flag
     *   *Sz   — max allowed size in MB
     */
    @JvmStatic
    external fun nativeGetAttachmentPolicy(handle: Long): IntArray?

    // ── Voice ──

    @JvmStatic
    external fun nativeToggleMute(handle: Long): Int
}
