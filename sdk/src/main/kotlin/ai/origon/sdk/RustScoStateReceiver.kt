package ai.origon.sdk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * JNI helper that observes Bluetooth SCO audio-state changes (API 23+).
 * Bundled in the SDK AAR so consumers don't have to ship it themselves.
 *
 * On the OpenSL ES audio path (API 23–26) the native router routes voice to a
 * Bluetooth headset by calling `AudioManager.startBluetoothSco()`. That call is
 * asynchronous — `setBluetoothScoOn(true)` only takes effect once the SCO link
 * is actually connected — so the native code registers this receiver for
 * [AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED] and applies the routing flag
 * only when [AudioManager.SCO_AUDIO_STATE_CONNECTED] arrives.
 *
 * The SDK's native code (`client-sdk/android/src/router.rs`) looks this class
 * up by name (`ai/origon/sdk/RustScoStateReceiver`), binds the
 * [nativeOnScoStateChanged] method via `RegisterNatives`, instantiates it with
 * a native pointer, reads back [mNativePtr], and registers the instance via
 * `Context.registerReceiver`.
 *
 * Rust clears [mNativePtr] back to 0 on teardown; the guarded call below then
 * becomes a no-op. Do NOT rename/move without updating the matching constant in
 * `router.rs`.
 */
internal class RustScoStateReceiver(
    @JvmField var mNativePtr: Long,
) : BroadcastReceiver() {

    // Implemented in Rust, bound at runtime via RegisterNatives. `(JI)V`.
    private external fun nativeOnScoStateChanged(ptr: Long, state: Int)

    override fun onReceive(context: Context?, intent: Intent?) {
        val ptr = mNativePtr
        if (ptr == 0L) return
        val state = intent?.getIntExtra(
            AudioManager.EXTRA_SCO_AUDIO_STATE,
            AudioManager.SCO_AUDIO_STATE_ERROR,
        ) ?: AudioManager.SCO_AUDIO_STATE_ERROR
        nativeOnScoStateChanged(ptr, state)
    }
}
