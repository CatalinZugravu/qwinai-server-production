package com.cyberflux.qwinai.ui.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan

class RoundedBackgroundSpan(
    private val backgroundColor: Int,
    private val textColor: Int,
    private val cornerRadius: Float = 12f,
    private val paddingHorizontal: Float = 16f,
    private val paddingVertical: Float = 4f
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        return (paint.measureText(text, start, end) + 2 * paddingHorizontal).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val width = paint.measureText(text, start, end)
        val rect = RectF(
            x,
            top.toFloat() + paddingVertical,
            x + width + 2 * paddingHorizontal,
            bottom.toFloat() - paddingVertical
        )

        // Draw background
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // Draw text
        paint.color = textColor
        canvas.drawText(
            text!!,
            start,
            end,
            x + paddingHorizontal,
            y.toFloat(),
            paint
        )
    }
}