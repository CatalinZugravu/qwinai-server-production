package com.cyberflux.qwinai.adapter

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.databinding.ItemImageGalleryGridBinding
import com.cyberflux.qwinai.databinding.ItemImageGalleryListBinding
import com.cyberflux.qwinai.model.GeneratedImage
import java.io.File

class ImageGalleryAdapter(
    private val listener: OnImageClickListener
) : ListAdapter<GeneratedImage, RecyclerView.ViewHolder>(ImageDiffCallback()) {
    
    companion object {
        const val VIEW_TYPE_GRID = 0
        const val VIEW_TYPE_LIST = 1
    }
    
    private var viewType = VIEW_TYPE_GRID
    private var selectionMode = false
    private var selectedImages = setOf<String>()
    
    interface OnImageClickListener {
        fun onImageClick(image: GeneratedImage)
        fun onImageLongClick(image: GeneratedImage)
    }
    
    fun setViewType(type: Int) {
        viewType = type
        notifyDataSetChanged()
    }
    
    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) {
            selectedImages = emptySet()
        }
        notifyDataSetChanged()
    }
    
    fun setSelectedImages(selected: Set<String>) {
        selectedImages = selected
        notifyDataSetChanged()
    }
    
    override fun getItemViewType(position: Int): Int = viewType
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GRID -> {
                val binding = ItemImageGalleryGridBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                GridViewHolder(binding)
            }
            VIEW_TYPE_LIST -> {
                val binding = ItemImageGalleryListBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                ListViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val image = getItem(position)
        when (holder) {
            is GridViewHolder -> holder.bind(image)
            is ListViewHolder -> holder.bind(image)
        }
    }
    
    inner class GridViewHolder(
        private val binding: ItemImageGalleryGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(image: GeneratedImage) {
            val file = File(image.filePath)
            
            // Load image with Glide
            Glide.with(binding.imageView.context)
                .load(file)
                .apply(RequestOptions().transform(RoundedCorners(16)))
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .into(binding.imageView)
            
            // Set AI model badge
            binding.textModel.text = image.aiModel
            
            // Set resolution
            binding.textResolution.text = "${image.width}×${image.height}"
            
            // Set download indicator
            binding.iconDownloaded.visibility = if (image.isDownloaded) View.VISIBLE else View.GONE
            
            // Selection state
            binding.checkboxSelect.visibility = if (selectionMode) View.VISIBLE else View.GONE
            binding.checkboxSelect.isChecked = selectedImages.contains(image.id)
            
            binding.overlaySelected.visibility = 
                if (selectionMode && selectedImages.contains(image.id)) View.VISIBLE else View.GONE
            
            // Click listeners
            binding.root.setOnClickListener {
                listener.onImageClick(image)
            }
            
            binding.root.setOnLongClickListener {
                listener.onImageLongClick(image)
                true
            }
        }
    }
    
    inner class ListViewHolder(
        private val binding: ItemImageGalleryListBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(image: GeneratedImage) {
            val file = File(image.filePath)
            
            // Load thumbnail
            Glide.with(binding.imageView.context)
                .load(file)
                .apply(RequestOptions().transform(RoundedCorners(12)))
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .into(binding.imageView)
            
            // Set text information
            binding.textFileName.text = image.fileName
            binding.textModel.text = image.aiModel
            binding.textPrompt.text = image.prompt
            binding.textResolution.text = "${image.width}×${image.height}"
            binding.textFileSize.text = Formatter.formatFileSize(binding.root.context, image.fileSize)
            binding.textTimestamp.text = DateUtils.getRelativeTimeSpanString(
                image.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            
            // Set download indicator
            binding.iconDownloaded.visibility = if (image.isDownloaded) View.VISIBLE else View.GONE
            
            // Selection state
            binding.checkboxSelect.visibility = if (selectionMode) View.VISIBLE else View.GONE
            binding.checkboxSelect.isChecked = selectedImages.contains(image.id)
            
            // Click listeners
            binding.root.setOnClickListener {
                listener.onImageClick(image)
            }
            
            binding.root.setOnLongClickListener {
                listener.onImageLongClick(image)
                true
            }
        }
    }
}

class ImageDiffCallback : DiffUtil.ItemCallback<GeneratedImage>() {
    override fun areItemsTheSame(oldItem: GeneratedImage, newItem: GeneratedImage): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: GeneratedImage, newItem: GeneratedImage): Boolean {
        return oldItem == newItem
    }
}