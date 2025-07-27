package com.cyberflux.qwinai.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.AIModel
import com.cyberflux.qwinai.utils.ModelIconUtils
import com.cyberflux.qwinai.utils.TranslationUtils
import timber.log.Timber

/**
 * Enhanced ModelGridAdapter with better visuals
 * Designed to work with the CustomSpinnerDialog
 */
/**
 * Clean ModelGridAdapter - NO LayoutParams manipulation
 */
class ModelGridAdapter(
    private val context: Context,
    private val models: List<AIModel>,
    private var selectedPosition: Int,
    private val dialogWindow: Window? = null, // Made nullable to avoid issues
    private val viewPagerRef: ViewPager2? = null, // Made nullable to avoid issues
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<ModelGridAdapter.ViewHolder>() {

    init {
        Timber.d("SPINNER: ModelGridAdapter created with selectedPosition=$selectedPosition")
    }

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.modelCard)
        val iconView: ImageView = view.findViewById(R.id.ivModelIcon)
        val textView: TextView = view.findViewById(R.id.tvModelName)
        val proBadge: TextView = view.findViewById(R.id.tvProBadge)
        val checkmark: ImageView = view.findViewById(R.id.ivCheckmark)
        val selectionIndicator: View = view.findViewById(R.id.selectionIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_enhanced_model, parent, false)

        // DON'T touch LayoutParams - let RecyclerView handle everything

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = models.size

    fun updateSelectedPosition(position: Int) {
        if (position != selectedPosition) {
            val oldPosition = selectedPosition
            selectedPosition = position

            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)

            Timber.d("SPINNER: ModelGridAdapter updated selectedPosition from $oldPosition to $position")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]

        // Set model name
        holder.textView.text = model.displayName

        // Set icon
        val iconResource = ModelIconUtils.getIconResourceForModel(model.id)
        holder.iconView.setImageResource(iconResource)
        holder.iconView.setPadding(4, 4, 4, 4)

        // Check translation mode
        val isTranslationMode = (context as? TranslationModeFetcher)?.isTranslationMode() ?: false
        val isDisabledInTranslationMode = isTranslationMode && !TranslationUtils.supportsTranslation(model.id)

        if (isDisabledInTranslationMode) {
            // Disabled state
            holder.view.alpha = 0.5f
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.disabled_card_background))
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.text_disabled))
            holder.textView.text = "${model.displayName}\n(Not for translation)"
            holder.iconView.alpha = 0.3f

            holder.proBadge.visibility = View.VISIBLE
            holder.proBadge.text = "DISABLED"
            holder.proBadge.setBackgroundResource(R.drawable.disabled_badge_background)

            holder.view.isClickable = false
            holder.view.isFocusable = false
            holder.view.setOnClickListener(null)
            holder.checkmark.visibility = View.GONE

            val disabledOverlay = holder.view.findViewById<ImageView>(R.id.ivDisabledOverlay)
            disabledOverlay?.visibility = View.VISIBLE
        } else {
            // Normal state
            holder.view.alpha = 1.0f
            holder.textView.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            holder.iconView.alpha = 1.0f

            holder.view.isClickable = true
            holder.view.isFocusable = true

            // CLEAN click listener - NO LayoutParams manipulation
            holder.view.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = position

                // Update UI
                notifyItemChanged(oldPosition)
                notifyItemChanged(position)

                // Simple animation
                holder.card.animate()
                    .scaleX(1.03f)
                    .scaleY(1.03f)
                    .setDuration(150)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        holder.card.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start()
                    }
                    .start()

                // NO LayoutParams manipulation - just call the callback
                onItemClick(position)
            }

            // Selection state
            if (position == selectedPosition) {
                holder.selectionIndicator.visibility = View.VISIBLE
                holder.checkmark.visibility = View.GONE
                holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.selected_item_background))
                holder.textView.setTypeface(holder.textView.typeface, android.graphics.Typeface.BOLD)

                holder.proBadge.visibility = if (!model.isFree) View.VISIBLE else View.GONE

                if (!model.isFree) {
                    holder.proBadge.setBackgroundResource(R.drawable.premium_badge_gradient)
                    holder.proBadge.text = "PRO"
                }

                val disabledOverlay = holder.view.findViewById<ImageView>(R.id.ivDisabledOverlay)
                disabledOverlay?.visibility = View.GONE
            } else {
                holder.selectionIndicator.visibility = View.GONE
                holder.checkmark.visibility = View.GONE
                holder.card.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
                holder.textView.setTypeface(holder.textView.typeface, android.graphics.Typeface.NORMAL)

                holder.proBadge.visibility = if (!model.isFree) View.VISIBLE else View.GONE

                if (!model.isFree) {
                    holder.proBadge.setBackgroundResource(R.drawable.premium_badge_gradient)
                    holder.proBadge.text = "PRO"
                }

                val disabledOverlay = holder.view.findViewById<ImageView>(R.id.ivDisabledOverlay)
                disabledOverlay?.visibility = View.GONE
            }
        }
    }

    // Interface for translation mode
    interface TranslationModeFetcher {
        fun isTranslationMode(): Boolean
    }
}