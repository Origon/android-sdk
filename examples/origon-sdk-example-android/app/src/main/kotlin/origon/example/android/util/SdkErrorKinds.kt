package origon.example.android.util

/**
 * Mirrors the SDK's `SessionBridge.ERROR_*` discriminants, which are
 * `internal` to the SDK module and therefore not referenceable by
 * consumers. `SessionException.kind` is a public Int that holds one of
 * these values, so the example dispatches on these local copies.
 */
object SdkErrorKinds {
    const val NOT_INITIALIZED = 1
    const val NO_SESSION = 2
    const val SESSION = 3
    const val MISSING_FIELD = 4
    const val SERVER_UNAVAILABLE = 5
    const val HTTP = 6
    const val ATTACHMENT = 7
    const val OTHER = 8
    const val CANCELLED = 9
}
