package com.cyberflux.qwinai.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cyberflux.qwinai.R

class MediaThumbnailAdapter(
    private val context: Context,
    private val onCameraClick: () -> Unit,
    private val onImageClick: (Uri) -> Unit,
    private val onUploadClick: () -> Unit
) : RecyclerView.Adapter<MediaThumbnailAdapter.MediaViewHolder>() {

    private val mediaItems = mutableListOf<MediaItem>()

    data class MediaItem(
        val type: ItemType,
        val uri: Uri? = null
    )

    enum class ItemType {
        CAMERA,
        IMAGE,
        UPLOAD
    }

    init {
        setupInitialItems()
    }

    private fun setupInitialItems() {
        mediaItems.clear()
        // Add camera button as first item
        mediaItems.add(MediaItem(ItemType.CAMERA))
        
        // Add upload button as last item (placeholder for now)
        mediaItems.add(MediaItem(ItemType.UPLOAD))
        
        notifyDataSetChanged()
    }

    fun updateRecentImages(imageUris: List<Uri>) {
        mediaItems.clear()
        
        // Add camera button as first item
        mediaItems.add(MediaItem(ItemType.CAMERA))
        
        // Add recent images (max 10)
        imageUris.take(10).forEach { uri ->
            mediaItems.add(MediaItem(ItemType.IMAGE, uri))
        }
        
        // Add upload button as last item
        mediaItems.add(MediaItem(ItemType.UPLOAD))
        
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_thumbnail, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val item = mediaItems[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = mediaItems.size

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cameraLayout: LinearLayout = itemView.findViewById(R.id.layout_camera)
        private val imageView: ImageView = itemView.findViewById(R.id.iv_thumbnail)
        private val uploadLayout: LinearLayout = itemView.findViewById(R.id.layout_upload)
        private val cardView: CardView = itemView.findViewById<CardView>(R.id.item_media_thumbnail) ?: 
            itemView as CardView

        fun bind(item: MediaItem) {
            // Hide all layouts first
            cameraLayout.visibility = View.GONE
            imageView.visibility = View.GONE
            uploadLayout.visibility = View.GONE

            when (item.type) {
                ItemType.CAMERA -> {
                    cameraLayout.visibility = View.VISIBLE
                    cardView.setOnClickListener { onCameraClick() }
                }
                ItemType.IMAGE -> {
                    imageView.visibility = View.VISIBLE
                    item.uri?.let { uri ->
                        Glide.with(context)
                            .load(uri)
                            .centerCrop()
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_error)
                            .into(imageView)
                        
                        cardView.setOnClickListener { onImageClick(uri) }
                    }
                }
                ItemType.UPLOAD -> {
                    uploadLayout.visibility = View.VISIBLE
                    cardView.setOnClickListener { onUploadClick() }
                }
            }
        }
    }
}