package ai.origon.sdk.bridge

/**
 * Per-attachment-type rule.
 *
 * **ABI-locked constructor signature** — `(ZI)V`. The Rust JNI bridge
 * instantiates via reflection.
 */
internal data class AttachmentRule(
    val enabled: Boolean,
    /** Maximum allowed size in megabytes. */
    val maxSize: Int,
)

/**
 * Returned by `SessionBridge.getAttachmentPolicy`. Mirrors the
 * `chat.attachmentPolicy` block in the `/config` body.
 *
 * **ABI-locked constructor signature** — four `AttachmentRule`s in
 * the order shown.
 */
internal data class AttachmentPolicy(
    val images: AttachmentRule,
    val documents: AttachmentRule,
    val videos: AttachmentRule,
    val audio: AttachmentRule,
)
