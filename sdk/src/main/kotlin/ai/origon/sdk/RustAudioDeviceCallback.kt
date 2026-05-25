package ai.origon.sdk

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo

/**
 * JNI helper for audio device add/remove monitoring (API 23+). Bundled in
 * the SDK AAR so consumers don't have to ship it themselves.
 *
 * The SDK's native code (`client-sdk/android/src/monitor.rs`) looks this
 * class up by name (`ai/origon/sdk/RustAudioDeviceCallback`), binds the
 * [nativeOnDeviceListChanged] method via `RegisterNatives`, instantiates
 * it with a native pointer, reads back [mNativePtr], and registers the
 * instance as an [AudioDeviceCallback].
 *
 * This class extends ONLY [AudioDeviceCallback] (available since API 23)
 * so it loads on every supported API level. The API-31 communication
 * device listener lives in a separate class ([RustCommDeviceListener]) so
 * that referencing the API-31 interface never blocks this class from
 * loading on API 23–30.
 *
 * Rust clears [mNativePtr] back to 0 on teardown; the guarded calls below
 * then become no-ops. Do NOT rename/move without updating the matching
 * constant in `monitor.rs`.
 */
internal class RustAudioDeviceCallback(
    @JvmField var mNativePtr: Long,
) : AudioDeviceCallback() {

    // Implemented in Rust, bound at runtime via RegisterNatives. `(J)V`.
    private external fun nativeOnDeviceListChanged(ptr: Long)

    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
        val ptr = mNativePtr
        if (ptr != 0L) nativeOnDeviceListChanged(ptr)
    }

    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
        val ptr = mNativePtr
        if (ptr != 0L) nativeOnDeviceListChanged(ptr)
    }
}
