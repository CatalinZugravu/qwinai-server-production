package com.cyberflux.qwinai.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import timber.log.Timber
import java.util.regex.Pattern

/**
 * Processor for Mermaid diagrams in Markdown
 */
class MermaidProcessor(private val context: Context) {
    
    data class ProtectedRange(val start: Int, val end: Int, val type: String)

    fun processMermaid(
        ssb: SpannableStringBuilder,
        protectedRanges: MutableList<ProtectedRange>
    ) {
        val pattern = Pattern.compile("```mermaid\\s*\\n(.*?)\\n```", Pattern.DOTALL)
        val matcher = pattern.matcher(ssb)
        val matches = mutableListOf<Triple<Int, Int, String>>()

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val content = matcher.group(1)?.trim() ?: continue
            matches.add(Triple(start, end, content))
        }

        matches.reversed().forEach { (start, end, content) ->
            // Create placeholder view for mermaid diagram
            val mermaidView = createMermaidView(content)

            if (mermaidView != null) {
                ssb.replace(start, end, " ")

                val bitmap = getBitmapFromView(mermaidView)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(context.resources, bitmap)
                    drawable.setBounds(0, 0, bitmap.width, bitmap.height)

                    val imageSpan = ImageSpan(drawable)
                    ssb.setSpan(imageSpan, start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else {
                // Fallback to code block
                ssb.replace(start, end, "```\n$content\n```")
            }

            protectedRanges.add(start to end)
        }
    }

    private fun createMermaidView(content: String): View? {
        return try {
            // Create a simple text representation of the diagram
            // In a real implementation, you would use a Mermaid rendering library
            val textView = TextView(context).apply {
                text = "Mermaid Diagram:\n$content"
                setPadding(20, 20, 20, 20)
                setBackgroundColor(Color.parseColor("#F0F8FF"))
                setTextColor(Color.BLACK)
                textSize = 14f
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            textView
        } catch (e: Exception) {
            Timber.e(e, "Error creating mermaid view")
            null
        }
    }

    private fun getBitmapFromView(view: View): Bitmap? {
        return try {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.UNSPECIFIED
            )

            val width = view.measuredWidth
            val height = view.measuredHeight

            if (width <= 0 || height <= 0) return null

            view.layout(0, 0, width, height)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)

            bitmap
        } catch (e: Exception) {
            Timber.e(e, "Error creating bitmap from view")
            null
        }
    }
}

private fun <E> MutableList<E>.add(element: Pair<Int, Int>) {

}
