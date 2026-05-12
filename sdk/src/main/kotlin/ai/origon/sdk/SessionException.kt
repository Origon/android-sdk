package ai.origon.sdk

/**
 * Thrown by [SessionBridge] when the underlying session crate returns
 * an error.
 *
 * The structured fields mirror the Rust `ClientError` discriminants —
 * consumers can dispatch on [kind] (one of [SessionBridge.ERROR_*])
 * for typed handling, or read [message] / [code] for display.
 *
 * **ABI-locked constructor signature.** The Rust JNI bridge
 * (`session/src/jni_bridge.rs`) instantiates this class via
 * reflection looking up `(IILjava/lang/String;Ljava/lang/String;)V`.
 * Do not change the parameter order or types.
 */
class SessionException(
    /** One of [SessionBridge.ERROR_NOT_INITIALIZED] etc. */
    val kind: Int,
    /** HTTP status when applicable (`ERROR_HTTP` / `ERROR_SERVER_UNAVAILABLE`); 0 otherwise. */
    val statusCode: Int,
    /** Machine-readable code (e.g. `"user_unavailable"`) or field name (`MISSING_FIELD`). May be null. */
    val code: String?,
    message: String?,
) : RuntimeException(message ?: "session error")
