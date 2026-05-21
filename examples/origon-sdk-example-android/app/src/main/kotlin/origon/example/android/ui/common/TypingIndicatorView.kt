package origon.example.android.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import origon.example.android.R
import kotlin.math.max
import kotlin.math.sin

/**
 * Three dots bouncing in a rounded "remote bubble". Mirrors the iOS
 * TypingIndicator: each dot rises with a staggered phase offset.
 */
class TypingIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val dotRadius = 4f * density
    private val dotSpacing = 5f * density
    private val bubblePadH = 14f * density
    private val bubblePadV = 12f * density
    private val bounce = 4f * density

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.origon_remote_bubble)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.origon_text_secondary)
    }

    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (bubblePadH * 2 + dotRadius * 6 + dotSpacing * 2).toInt()
        val height = (bubblePadV * 2 + dotRadius * 2 + bounce).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val bubbleH = bubblePadV * 2 + dotRadius * 2
        val radius = bubbleH / 2
        val bubble = RectF(0f, bounce, bubblePadH * 2 + dotRadius * 6 + dotSpacing * 2, bounce + bubbleH)
        canvas.drawRoundRect(bubble, radius, radius, bubblePaint)

        val cy = bounce + bubblePadV + dotRadius
        var cx = bubblePadH + dotRadius
        for (i in 0 until 3) {
            val offset = max(0f, sin(phase - i * 0.6f)) * bounce
            canvas.drawCircle(cx, cy - offset, dotRadius, dotPaint)
            cx += dotRadius * 2 + dotSpacing
        }
    }
}
