package com.cyberflux.qwinai.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.adapter.ChatAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import timber.log.Timber

class SourcesBottomSheet(
    context: Context,
    private val sources: List<ChatAdapter.WebSearchSource>
) : BottomSheetDialog(context) {

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_sources, null)
        setContentView(view)

        setupViews(view)

        // Make it expand to 70% of screen height
        behavior.peekHeight = (context.resources.displayMetrics.heightPixels * 0.7).toInt()
    }

    private fun setupViews(view: View) {
        view.findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            dismiss()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvSources)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = SourcesAdapter(sources)
    }

    inner class SourcesAdapter(
        private val sources: List<ChatAdapter.WebSearchSource>
    ) : RecyclerView.Adapter<SourcesAdapter.SourceViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_source_card, parent, false)
            return SourceViewHolder(view)
        }

        override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
            holder.bind(sources[position])
        }

        override fun getItemCount() = sources.size

        inner class SourceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val favicon: ImageView = itemView.findViewById(R.id.ivFavicon)
            private val title: TextView = itemView.findViewById(R.id.tvSourceTitle)
            private val domain: TextView = itemView.findViewById(R.id.tvSourceDomain)
            private val snippet: TextView = itemView.findViewById(R.id.tvSourceSnippet)

            fun bind(source: ChatAdapter.WebSearchSource) {
                title.text = source.title
                domain.text = source.displayLink
                snippet.text = source.snippet

                // Construct favicon URL properly
                val faviconUrl = source.favicon ?: try {
                    val domain = Uri.parse(source.url).host ?: source.displayLink
                    "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                } catch (e: Exception) {
                    null
                }

                // Load favicon with better error handling
                if (!faviconUrl.isNullOrEmpty()) {
                    Glide.with(itemView.context)
                        .load(faviconUrl)
                        .placeholder(R.drawable.ic_web)
                        .error(R.drawable.ic_web)
                        .timeout(5000) // 5 second timeout
                        .override(32, 32) // Force size
                        .into(favicon)
                } else {
                    favicon.setImageResource(R.drawable.ic_web)
                }

                itemView.setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                        itemView.context.startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Error opening URL: ${source.url}")
                    }
                }
            }
        }
    }
}