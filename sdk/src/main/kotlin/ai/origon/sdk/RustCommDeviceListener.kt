package ai.origon.sdk

import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * JNI helper for communication-device change monitoring. Bundled in the
 * SDK AAR.
 *
 * [AudioManager.OnCommunicationDeviceChangedListener] was added in API 31,
 * so this class is kept SEPARATE from [RustAudioDeviceCallback]: the
 * native side only ever looks it up / instantiates it when
 * `Build.VERSION.SDK_INT >= 31`. On API 23–30 the class is never loaded,
 * so referencing the API-31 interface here can't trigger a
 * `NoClassDefFoundError`.
 *
 * The SDK's native code (`client-sdk/android/src/monitor.rs`) looks this
 * class up by name (`ai/origon/sdk/RustCommDeviceListener`), binds the
 * [nativeOnCommDeviceChanged] method via `RegisterNatives`, instantiates
 * it with a native pointer, reads back [mNativePtr], and registers the
 * instance via `AudioManager.addOnCommunicationDeviceChangedListener`.
 */
internal class RustCommDeviceListener(
    @JvmField var mNativePtr: Long,
) : AudioManager.OnCommunicationDeviceChangedListener {

    // Implemented in Rust, bound at runtime via RegisterNatives. `(J)V`.
    private external fun nativeOnCommDeviceChanged(ptr: Long)

    override fun onCommunicationDeviceChanged(device: AudioDeviceInfo?) {
        val ptr = mNativePtr
        if (ptr != 0L) nativeOnCommDeviceChanged(ptr)
    }
}
