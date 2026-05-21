package origon.example.android.ui.common

import android.animation.ObjectAnimator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.view.isVisible

// MARK: - Haptics

object Haptics {
    fun error(context: Context) = vibrate(context, longArrayOf(0, 40, 60, 40))
    fun light(context: Context) = vibrate(context, longArrayOf(0, 20))

    private fun vibrate(context: Context, pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}

// MARK: - Shake

/**
 * Horizontal "hit and ring out" shake — mirrors the iOS ShakeEffect.
 * Decaying oscillation on translationX.
 */
fun View.shake() {
    ObjectAnimator.ofFloat(
        this, "translationX",
        0f, -10f, 9f, -7f, 5f, -3f, 1f, 0f,
    ).apply {
        duration = 550
        interpolator = LinearInterpolator()
        start()
    }
}

// MARK: - Press-scale feedback

/**
 * Shared press-down feedback: scales to 0.95 on press, restores on
 * release. Mirrors the iOS PressScaleButtonStyle.
 */
fun View.applyPressScale() {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(120).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        false // don't consume — let click handling proceed
    }
}

// MARK: - Floating toast

/**
 * Drives a styled overlay TextView (bg_toast background) — fade + slide
 * in, auto-dismiss after 3 s. Mirrors the iOS floating toast.
 */
class ToastController(private val toastView: View) {
    private val handler = toastView.handler ?: android.os.Handler(toastView.context.mainLooper)
    private var dismiss: Runnable? = null

    fun show(message: String) {
        (toastView as? android.widget.TextView)?.text = message
        dismiss?.let { handler.removeCallbacks(it) }
        toastView.isVisible = true
        toastView.alpha = 0f
        toastView.translationY = 24f
        toastView.animate().alpha(1f).translationY(0f).setDuration(250).start()
        dismiss = Runnable {
            toastView.animate().alpha(0f).translationY(24f).setDuration(250)
                .withEndAction { toastView.isVisible = false }.start()
        }.also { handler.postDelayed(it, 3000) }
    }
}
