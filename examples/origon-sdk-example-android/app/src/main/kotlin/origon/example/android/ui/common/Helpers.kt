package origon.example.android.ui.common

import android.animation.ObjectAnimator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding

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

// MARK: - Window insets

/**
 * Pads a view by the relevant window insets so its content stays within
 * the safe area.
 *
 * Apps targeting SDK 35 are always edge-to-edge on Android 15: content
 * draws under the status and navigation bars, and the legacy
 * `adjustResize` no longer lifts content above the keyboard. These flags
 * restore the safe-area spacing manually.
 *
 * - [top] adds the status-bar inset (e.g. a toolbar).
 * - [bottom] adds the navigation-bar inset.
 * - [ime] keeps the view above the keyboard when it's up, falling back to
 *   the navigation-bar inset otherwise — pair with [bottom] for a
 *   composer / form.
 *
 * The view's original padding is preserved and the insets are added on top.
 */
fun View.applyWindowInsets(
    top: Boolean = false,
    bottom: Boolean = false,
    ime: Boolean = false,
) {
    val initialTop = paddingTop
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        v.updatePadding(
            top = initialTop + if (top) bars.top else 0,
            bottom = initialBottom + when {
                ime -> maxOf(bars.bottom, imeBottom)
                bottom -> bars.bottom
                else -> 0
            },
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

// MARK: - Floating toast

/**
 * Drives a styled overlay TextView (bg_toast background) — fade + slide
 * in, auto-dismiss after 3 s. Mirrors the iOS floating toast.
 */
class ToastController(private val toastView: View) {
    private val handler = toastView.handler ?: android.os.Handler(toastView.context.mainLooper)
    private var dismiss: Runnable? = null

    // The bottom margin declared in the layout — the resting offset above the
    // window bottom. The keyboard / navigation-bar inset is added on top of it
    // each time the toast is shown.
    private val baseBottomMargin =
        (toastView.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

    fun show(message: String) {
        (toastView as? android.widget.TextView)?.text = message
        dismiss?.let { handler.removeCallbacks(it) }
        liftAboveKeyboard()
        toastView.isVisible = true
        toastView.alpha = 0f
        toastView.translationY = 24f
        toastView.animate().alpha(1f).translationY(0f).setDuration(250).start()
        dismiss = Runnable {
            toastView.animate().alpha(0f).translationY(24f).setDuration(250)
                .withEndAction { toastView.isVisible = false }.start()
        }.also { handler.postDelayed(it, 3000) }
    }

    // Edge-to-edge apps don't resize when the keyboard opens, so a
    // bottom-anchored toast would otherwise sit *behind* it (e.g. an endpoint
    // error toast that fires while the URL field is focused). Lift it by the
    // keyboard inset — or the navigation-bar inset when the keyboard is down —
    // via its bottom margin (not padding, which would stretch the pill).
    private fun liftAboveKeyboard() {
        val insets = ViewCompat.getRootWindowInsets(toastView) ?: return
        val navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        (toastView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.bottomMargin = baseBottomMargin + maxOf(navBottom, imeBottom)
            toastView.layoutParams = lp
        }
    }
}
