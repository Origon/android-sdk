package origon.example.android.ui.call

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The Origon brand "conic" gradient backdrop for the call surface.
 * Android's [SweepGradient] is the View-system analog of iOS's
 * AngularGradient. Rotates slowly with a soft blur.
 */
class ConicGradientView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val colors = intArrayOf(
        0xFF0092FF.toInt(),
        0xFFFD9700.toInt(),
        0xFFFF4400.toInt(),
        0xFFFF2469.toInt(),
        0xFFC65CFF.toInt(),
        0xFF0092FF.toInt(),
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
    }

    private var rotation = 0f
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 6000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { rotation = it.animatedValue as Float; invalidate() }
    }

    init {
        // BlurMaskFilter requires software rendering.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) * 0.78f
        paint.shader = SweepGradient(cx, cy, colors, null)

        canvas.save()
        canvas.rotate(rotation, cx, cy)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.restore()
    }
}
