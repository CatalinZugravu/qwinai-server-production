package com.cyberflux.qwinai.utils

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.LineBackgroundSpan
import android.text.style.LineHeightSpan
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R

/**
 * Custom span renderers for enhanced markdown formatting.
 * Provides advanced styling capabilities beyond basic Android spans.
 */
class CustomSpanRenderers(private val context: Context) {

    /**
     * Enhanced heading span with underline for H1 and H2
     */
    inner class EnhancedHeadingSpan(
        private val level: Int,
        private val color: Int
    ) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return paint.measureText(text, start, end).toInt()
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
            val oldColor = paint.color
            paint.color = color

            // Draw the text
            canvas.drawText(text ?: "", start, end, x, y.toFloat(), paint)

            // Draw underline for H1 and H2
            if (level <= 2) {
                val underlineY = y + paint.descent() + 8
                val strokeWidth = if (level == 1) 3f else 2f
                paint.strokeWidth = strokeWidth

                val width = paint.measureText(text, start, end)
                canvas.drawLine(x, underlineY, x + width, underlineY, paint)
            }

            paint.color = oldColor
        }
    }

    /**
     * Enhanced horizontal rule span
     */
    inner class EnhancedHorizontalRuleSpan : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            if (fm != null) {
                fm.ascent = -20
                fm.descent = 20
                fm.top = fm.ascent
                fm.bottom = fm.descent
            }
            return 10 // Minimal width
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
            val oldColor = paint.color
            val oldStyle = paint.style
            val oldStrokeWidth = paint.strokeWidth

            paint.color = Color.parseColor("#CCCCCC")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f

            val centerY = (top + bottom) / 2f
            val width = (canvas.width * 0.9f).toInt()
            val startX = (canvas.width - width) / 2f

            // Draw gradient line
            val gradient = LinearGradient(
                startX, centerY,
                startX + width, centerY,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#CCCCCC"),
                    Color.parseColor("#CCCCCC"),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.1f, 0.9f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = gradient

            canvas.drawLine(startX, centerY, startX + width, centerY, paint)

            paint.shader = null
            paint.color = oldColor
            paint.style = oldStyle
            paint.strokeWidth = oldStrokeWidth
        }
    }

    /**
     * Enhanced blockquote span with left border
     */
    inner class EnhancedBlockquoteSpan : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return paint.measureText(text, start, end).toInt()
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
            val oldColor = paint.color

            // Draw left border
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 4f
            canvas.drawLine(x - 16, top.toFloat(), x - 16, bottom.toFloat(), paint)

            // Draw background
            paint.color = Color.parseColor("#F9F9F9")
            paint.style = Paint.Style.FILL
            canvas.drawRect(x - 12, top.toFloat(), canvas.width.toFloat(), bottom.toFloat(), paint)

            // Draw text
            paint.color = Color.parseColor("#555555")
            paint.style = Paint.Style.FILL
            canvas.drawText(text ?: "", start, end, x, y.toFloat(), paint)

            paint.color = oldColor
        }
    }

    /**
     * Line break span for hard breaks
     */
    inner class LineBreakSpan : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return 0
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
            // Line break doesn't need visual representation
        }
    }

    /**
     * Superscript span implementation
     */
    inner class SuperscriptSpan : MetricAffectingSpan() {
        override fun updateMeasureState(paint: TextPaint) {
            paint.baselineShift += (paint.ascent() / 2).toInt()
        }

        override fun updateDrawState(paint: TextPaint) {
            paint.baselineShift += (paint.ascent() / 2).toInt()
        }
    }

    /**
     * Subscript span implementation
     */
    inner class SubscriptSpan : MetricAffectingSpan() {
        override fun updateMeasureState(paint: TextPaint) {
            paint.baselineShift -= (paint.descent() / 2).toInt()
        }

        override fun updateDrawState(paint: TextPaint) {
            paint.baselineShift -= (paint.descent() / 2).toInt()
        }
    }

    /**
     * Keyboard key span with border
     */
    inner class KeyboardSpan : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return (paint.measureText(text, start, end) + 16).toInt()
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
            val oldColor = paint.color
            val oldStyle = paint.style

            val width = paint.measureText(text, start, end) + 16
            val rect = RectF(x, top + 4f, x + width, bottom - 4f)

            // Draw background
            paint.color = Color.parseColor("#F0F0F0")
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, 4f, 4f, paint)

            // Draw border
            paint.color = Color.parseColor("#CCCCCC")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(rect, 4f, 4f, paint)

            // Draw shadow
            paint.color = Color.parseColor("#BBBBBB")
            paint.style = Paint.Style.FILL
            val shadowRect = RectF(x + 1, bottom - 3f, x + width + 1, bottom - 1f)
            canvas.drawRoundRect(shadowRect, 2f, 2f, paint)

            // Draw text
            paint.color = Color.BLACK
            paint.style = Paint.Style.FILL
            canvas.drawText(text ?: "", start, end, x + 8, y.toFloat(), paint)

            paint.color = oldColor
            paint.style = oldStyle
        }
    }

    /**
     * Collapsible content span
     */
    inner class CollapsibleContentSpan(
        private val summary: String,
        private val content: String
    ) : ClickableSpan() {

        private var isExpanded = false

        override fun onClick(widget: View) {
            isExpanded = !isExpanded

            if (widget is TextView) {
                val text = widget.text
                if (text is SpannableStringBuilder) {
                    // Toggle content visibility
                    // In a real implementation, you would update the span content
                    Toast.makeText(
                        context,
                        if (isExpanded) "Expanded: $summary" else "Collapsed: $summary",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.color = Color.parseColor("#0066CC")
            ds.isUnderlineText = false
        }
    }

    /**
     * Rounded background span for inline code
     */
    inner class RoundedBackgroundSpan(
        private val backgroundColor: Int,
        private val textColor: Int = Color.BLACK,
        private val cornerRadius: Float = 4f
    ) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return (paint.measureText(text, start, end) + 8).toInt()
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
            val oldColor = paint.color
            val width = paint.measureText(text, start, end) + 8

            // Draw rounded background
            paint.color = backgroundColor
            val rect = RectF(x, top + 2f, x + width, bottom - 2f)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

            // Draw text
            paint.color = textColor
            canvas.drawText(text ?: "", start, end, x + 4, y.toFloat(), paint)

            paint.color = oldColor
        }
    }

    /**
     * Dotted underline span for abbreviations
     */
    inner class DottedUnderlineSpan : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return paint.measureText(text, start, end).toInt()
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
            val oldColor = paint.color
            val oldStyle = paint.style

            // Draw text
            canvas.drawText(text ?: "", start, end, x, y.toFloat(), paint)

            // Draw dotted underline
            paint.color = Color.parseColor("#666666")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)

            val width = paint.measureText(text, start, end)
            val underlineY = y + paint.descent() + 2
            canvas.drawLine(x, underlineY, x + width, underlineY, paint)

            paint.pathEffect = null
            paint.color = oldColor
            paint.style = oldStyle
        }
    }

    /**
     * Tooltip span for links and abbreviations
     */
    inner class TooltipSpan(private val tooltip: String) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return paint.measureText(text, start, end).toInt()
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
            // Draw text normally
            canvas.drawText(text ?: "", start, end, x, y.toFloat(), paint)

            // Tooltip would be shown on long press or hover
            // This is just a placeholder implementation
        }
    }

    // The following spans are from the second paste file that weren't in the first paste

    /**
     * Enum for table cell alignment
     */
    enum class TableAlignment {
        LEFT, CENTER, RIGHT
    }

    /**
     * Code block span with syntax highlighting and copy functionality.
     */
    inner class EnhancedCodeBlockSpan(
        private val code: String,
        private val language: String,
        private val container: View
    ) : ReplacementSpan() {

        private val backgroundPaint = Paint().apply {
            color = Color.parseColor("#F8F8F8")
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint().apply {
            color = Color.parseColor("#E1E1E8")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        private val headerPaint = Paint().apply {
            color = Color.parseColor("#F0F0F0")
            style = Paint.Style.FILL
        }

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return container.width
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
            val width = container.width.toFloat()
            val codeLines = code.split('\n')
            val lineHeight = paint.fontSpacing
            val headerHeight = 40f
            val totalHeight = headerHeight + (codeLines.size * lineHeight) + 20

            // Draw background
            val bgRect = RectF(x, top.toFloat(), x + width, top + totalHeight)
            canvas.drawRoundRect(bgRect, 12f, 12f, backgroundPaint)

            // Draw border
            canvas.drawRoundRect(bgRect, 12f, 12f, borderPaint)

            // Draw header
            val headerRect = RectF(x, top.toFloat(), x + width, top + headerHeight)
            canvas.drawRect(headerRect, headerPaint)

            // Draw language label
            paint.color = Color.parseColor("#666666")
            paint.textSize = 28f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(language.uppercase(), x + 16, (top + 28).toFloat(), paint)

            // Draw copy button
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("COPY", x + width - 80, (top + 28).toFloat(), paint)

            // Draw code lines with syntax highlighting
            paint.color = Color.BLACK
            paint.textSize = 32f
            paint.typeface = Typeface.MONOSPACE

            codeLines.forEachIndexed { index, line ->
                val lineY = top + headerHeight + 20 + (index * lineHeight)

                // Apply basic syntax highlighting based on language
                val highlightedLine = applySyntaxHighlighting(line, language)
                canvas.drawText(highlightedLine, x + 16, lineY, paint)
            }
        }

        private fun applySyntaxHighlighting(line: String, language: String): String {
            // Basic syntax highlighting - in a real implementation,
            // you would use a proper syntax highlighting library
            return when (language.lowercase()) {
                "kotlin", "java" -> {
                    line.replace(Regex("\\b(fun|val|var|class|if|else|when|for|while)\\b")) {
                        "•${it.value}•" // Placeholder for colored text
                    }
                }
                "javascript", "js" -> {
                    line.replace(Regex("\\b(function|var|let|const|if|else|for|while)\\b")) {
                        "•${it.value}•"
                    }
                }
                else -> line
            }
        }
    }

    /**
     * Table cell span with proper alignment and borders.
     */
    inner class TableCellSpan(
        private val content: String,
        private val alignment: TableAlignment,
        private val isHeader: Boolean = false
    ) : ReplacementSpan() {

        private val backgroundPaint = Paint().apply {
            color = if (isHeader) Color.parseColor("#F5F5F5") else Color.WHITE
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint().apply {
            color = Color.parseColor("#DDDDDD")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return (paint.measureText(content) + 32).toInt()
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
            val width = getSize(paint, text, start, end, null).toFloat()
            bottom - top.toFloat()

            // Draw cell background
            val cellRect = RectF(x, top.toFloat(), x + width, bottom.toFloat())
            canvas.drawRect(cellRect, backgroundPaint)

            // Draw cell border
            canvas.drawRect(cellRect, borderPaint)

            // Draw content with alignment
            paint.color = if (isHeader) Color.parseColor("#333333") else Color.BLACK
            paint.typeface = if (isHeader) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

            val textX = when (alignment) {
                TableAlignment.CENTER -> x + (width - paint.measureText(content)) / 2
                TableAlignment.RIGHT -> x + width - paint.measureText(content) - 16
                else -> x + 16 // LEFT or DEFAULT
            }

            canvas.drawText(content, textX, y.toFloat(), paint)
        }
    }

    /**
     * Footnote reference span with click functionality.
     */
    inner class FootnoteReferenceSpan(
        private val footnoteId: String,
        private val footnoteContent: String,
        private val targetPosition: Int
    ) : ClickableSpan() {

        override fun onClick(widget: View) {
            if (widget is TextView) {
                // Scroll to footnote
                scrollToPosition(widget, targetPosition)

                // Show footnote content in a toast as well
                Toast.makeText(
                    context,
                    footnoteContent,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.color = ContextCompat.getColor(context, R.color.link_color)
            ds.isUnderlineText = false
            ds.isFakeBoldText = true
        }

        private fun scrollToPosition(textView: TextView, position: Int) {
            val layout = textView.layout ?: return
            val line = layout.getLineForOffset(position)
            val y = layout.getLineTop(line)

            // Find ScrollView parent and scroll to position
            var parent = textView.parent
            while (parent != null && parent !is android.widget.ScrollView) {
                parent = parent.parent
            }
            (parent as? android.widget.ScrollView)?.smoothScrollTo(0, y)
        }
    }

    /**
     * Math expression span with better rendering.
     */
    inner class MathExpressionSpan(
        private val expression: String,
        private val isBlock: Boolean = false
    ) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            return paint.measureText(expression).toInt() + if (isBlock) 32 else 8
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
            val originalTypeface = paint.typeface
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)

            if (isBlock) {
                // Draw background for block math
                val bgPaint = Paint().apply {
                    color = Color.parseColor("#F8F8F8")
                    style = Paint.Style.FILL
                }
                val width = getSize(paint, text, start, end, null).toFloat()
                val bgRect = RectF(x, top.toFloat(), x + width, bottom.toFloat())
                canvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)

                canvas.drawText(expression, x + 16, y.toFloat(), paint)
            } else {
                canvas.drawText(expression, x + 4, y.toFloat(), paint)
            }

            paint.typeface = originalTypeface
        }
    }

    /**
     * Gradient text span for enhanced visual effects.
     */
    inner class GradientTextSpan(
        private val startColor: Int,
        private val endColor: Int
    ) : ReplacementSpan() {

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            return paint.measureText(text, start, end).toInt()
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
            val width = getSize(paint, text, start, end, null).toFloat()

            val gradient = LinearGradient(
                x, top.toFloat(), x + width, bottom.toFloat(),
                startColor, endColor,
                Shader.TileMode.CLAMP
            )

            paint.shader = gradient
            if (text != null) {
                canvas.drawText(text, start, end, x, y.toFloat(), paint)
            }
            paint.shader = null
        }
    }
}