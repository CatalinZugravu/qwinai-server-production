package com.cyberflux.qwinai.ui.spans

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import timber.log.Timber

class CitationClickableSpan(
    private val url: String,
    private val sourceName: String
) : ClickableSpan() {

    override fun onClick(widget: View) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            widget.context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Error opening URL: $url")
        }
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = false
        ds.color = Color.parseColor("#2563EB") // Blue color for citations
        ds.bgColor = Color.parseColor("#E5E7EB") // Light gray background
    }
}