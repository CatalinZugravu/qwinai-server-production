package com.cyberflux.qwinai.adapter

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import com.cyberflux.qwinai.utils.HapticManager
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.FrameLayout
import android.os.Build
import androidx.viewpager2.widget.ViewPager2
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.AIModel
import com.cyberflux.qwinai.model.ResponseLength
import com.cyberflux.qwinai.model.ResponsePreferences
import com.cyberflux.qwinai.model.ResponseTone
import com.cyberflux.qwinai.utils.TranslationUtils
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max

/**
 * Enhanced spinner dialog that supports AI model selection and response preferences
 * Modified to apply selections immediately without requiring a "Done" button
 * Added drag-to-dismiss functionality with a handle at the top
 */
class CustomSpinnerDialog(
    private val context: Context,
    private val models: List<AIModel>,
    initialModelPosition: Int,
    private val isTranslationModeFunc: () -> Boolean = { false },
    private val onSelectionComplete: (ResponsePreferences) -> Unit
) : ModelGridAdapter.TranslationModeFetcher {

    // Implement the TranslationModeFetcher interface
    override fun isTranslationMode(): Boolean = isTranslationModeFunc()

    private val dialog: Dialog = Dialog(context)
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var dragHandleContainer: View
    private lateinit var dragHandle: View
    private var fixedDialogWidth: Int = 0
    private var fixedDialogHeight: Int = 0
    private var fixedViewPagerHeight: Int = 0
    // Touch handling variables
    private var initialY: Float = 0f
    private var currentY: Float = 0f
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var isDragging = false

    // Load preferences from SharedPreferences
    private val preferences = loadResponsePreferences(initialModelPosition)

    /**
     * Load saved preferences from SharedPreferences
     */
    private fun loadResponsePreferences(modelPosition: Int): ResponsePreferences {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // Get the saved length preference (default to DEFAULT if not found)
        val lengthStr = prefs.getString("response_length", ResponseLength.DEFAULT.name)
        val length = try {
            ResponseLength.valueOf(lengthStr ?: ResponseLength.DEFAULT.name)
        } catch (e: Exception) {
            ResponseLength.DEFAULT
        }

        // Get the saved tone preference (default to DEFAULT if not found)
        val toneStr = prefs.getString("response_tone", ResponseTone.DEFAULT.name)
        val tone = try {
            ResponseTone.valueOf(toneStr ?: ResponseTone.DEFAULT.name)
        } catch (e: Exception) {
            ResponseTone.DEFAULT
        }

        Timber.d("DIALOG: Loaded preferences from SharedPreferences: length=$lengthStr, tone=$toneStr")

        return ResponsePreferences(
            modelPosition = modelPosition,
            length = length,
            tone = tone
        )
    }

    /**
     * Save preferences and notify listener
     */
    private fun applyPreferences() {
        Timber.d("DIALOG: Applying preferences: model=${preferences.modelPosition}, " +
                "length=${preferences.length.name}, tone=${preferences.tone.name}")
        onSelectionComplete(preferences)
    }

    // In the init block, update the dialog setup with drag handle
// Complete init block for CustomSpinnerDialog class
    // Fixed init block - prevents resizing while keeping beautiful tabs
    init {
        // Set up the dialog with enhanced styling
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_tabbed_spinner)

        // Get references to views
        viewPager = dialog.findViewById(R.id.viewPager)
        tabLayout = dialog.findViewById(R.id.tabLayout)
        dragHandleContainer = dialog.findViewById(R.id.dragHandleContainer)
        dragHandle = dialog.findViewById(R.id.dragHandle)

        // Setup drag-to-dismiss functionality
        setupDragToDismiss()

        // Enable ViewPager2 user-initiated scrolling for swiping between tabs
        viewPager.isUserInputEnabled = true

        // BEAUTIFUL TABS: Enhanced styling
        tabLayout.setSelectedTabIndicatorColor(ContextCompat.getColor(context, R.color.accent_color))
        tabLayout.setSelectedTabIndicatorHeight((context.resources.displayMetrics.density * 4).toInt())
        tabLayout.tabIconTint = ContextCompat.getColorStateList(context, R.color.tab_icon_selector)
        tabLayout.setTabRippleColorResource(R.color.ripple_color)

        // CLEAN ViewPager callback - only handle page indicators
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                // Update page indicators only
                updatePageIndicators(position)

                // Simple dimension maintenance - no forced LayoutParams changes
                dialog.window?.let { window ->
                    val params = window.attributes
                    if (params.width > 0 && params.height > 0) {
                        window.setLayout(params.width, params.height)
                    }
                }
            }
        })

        // Set up the pager adapter - CLEAN version
        val pagerAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val layoutId = when (viewType) {
                    0 -> R.layout.dialog_model_grid
                    1 -> R.layout.layout_response_options
                    else -> throw IllegalArgumentException("Invalid page type: $viewType")
                }

                val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)

                // DON'T TOUCH LAYOUTPARAMS - Let ViewPager2 handle this

                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun getItemCount(): Int = 2
            override fun getItemViewType(position: Int): Int = position

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                when (position) {
                    0 -> setupModelsPage(holder.itemView)
                    1 -> setupPreferencesPage(holder.itemView)
                }
            }
        }

        // Set adapter to ViewPager2
        viewPager.adapter = pagerAdapter

        // BEAUTIFUL TABS: Enhanced TabLayoutMediator
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "AI Models"
                    tab.setIcon(R.drawable.ic_ai_models)
                }
                1 -> {
                    tab.text = "Response Options"
                    tab.setIcon(R.drawable.ic_settings_tune)
                }
            }

            tab.view.setPadding(12, 12, 12, 12)

            val tabTextView = tab.view.findViewById<TextView>(com.google.android.material.R.id.text)
            tabTextView?.let { textView ->
                textView.textSize = 16f
                textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                textView.isAllCaps = false
            }
        }.attach()

        // Enhanced tab listener
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewPager.setCurrentItem(tab.position, true)

                val tabView = tab.view
                val tabTextView = tabView.findViewById<TextView>(com.google.android.material.R.id.text)
                tabTextView?.let { textView ->
                    textView.setTextColor(ContextCompat.getColor(context, R.color.accent_color))
                    textView.setTypeface(textView.typeface, Typeface.BOLD)

                    textView.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(150)
                        .setInterpolator(OvershootInterpolator())
                        .withEndAction {
                            textView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                }

                val iconView = tabView.findViewById<ImageView>(com.google.android.material.R.id.icon)
                iconView?.animate()?.rotation(360f)?.setDuration(300)?.start()

                // Simple haptic feedback
                HapticManager.lightVibration(context)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                val tabView = tab.view
                val tabTextView = tabView.findViewById<TextView>(com.google.android.material.R.id.text)
                tabTextView?.let { textView ->
                    textView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
                }

                val iconView = tabView.findViewById<ImageView>(com.google.android.material.R.id.icon)
                iconView?.animate()?.rotation(0f)?.setDuration(200)?.start()
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                tab.view.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(100)
                    .withEndAction {
                        tab.view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
            }
        })

        // Setup custom page indicator
        setupCustomPageIndicator()

        // Add dismiss listener
        dialog.setOnDismissListener {
            applyPreferences()
        }

        dialog.setCanceledOnTouchOutside(true)
    }    private fun setupCustomPageIndicator() {
        // Get the indicator container from the layout
        val indicatorContainer = dialog.findViewById<LinearLayout>(R.id.pageIndicatorContainer)

        if (indicatorContainer != null) {
            // Create dot indicators manually
            indicatorContainer.removeAllViews()

            // Create first dot (AI Models) - initially active
            val dot1 = View(context)
            val dot1Params = LinearLayout.LayoutParams(
                (context.resources.displayMetrics.density * 8).toInt(),
                (context.resources.displayMetrics.density * 8).toInt()
            )
            dot1Params.setMargins(8, 0, 8, 0)
            dot1.layoutParams = dot1Params
            dot1.background = ContextCompat.getDrawable(context, R.drawable.page_indicator_active)
            dot1.tag = "dot_0"

            // Create second dot (Response Options) - initially inactive
            val dot2 = View(context)
            val dot2Params = LinearLayout.LayoutParams(
                (context.resources.displayMetrics.density * 6).toInt(),
                (context.resources.displayMetrics.density * 6).toInt()
            )
            dot2Params.setMargins(8, 0, 8, 0)
            dot2.layoutParams = dot2Params
            dot2.background = ContextCompat.getDrawable(context, R.drawable.page_indicator_inactive)
            dot2.tag = "dot_1"

            indicatorContainer.addView(dot1)
            indicatorContainer.addView(dot2)

            Timber.d("PAGE INDICATOR: Created ${indicatorContainer.childCount} dots")
        }
    }
    private fun enforceFixedDimensions() {
        if (fixedDialogWidth > 0 && fixedDialogHeight > 0 && fixedViewPagerHeight > 0) {

            // ENFORCE dialog dimensions
            dialog.window?.setLayout(fixedDialogWidth, fixedDialogHeight)

            // ENFORCE ViewPager dimensions ONLY - don't touch RecyclerView LayoutParams
            viewPager.post {
                val currentParams = viewPager.layoutParams
                if (currentParams is LinearLayout.LayoutParams) {
                    currentParams.width = LinearLayout.LayoutParams.MATCH_PARENT
                    currentParams.height = fixedViewPagerHeight
                    currentParams.weight = 0f
                    viewPager.layoutParams = currentParams
                    viewPager.requestLayout()
                }
            }

            Timber.d("ENFORCED: dialog=${fixedDialogWidth}x${fixedDialogHeight}, viewPager=height:${fixedViewPagerHeight}")
        }
    }
    /**
     * Create container for page indicators if it doesn't exist
     */
    private fun createPageIndicatorContainer(): LinearLayout {
        val container = LinearLayout(context)
        container.id = R.id.pageIndicatorContainer
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER

        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(0, 8, 0, 8)
        container.layoutParams = layoutParams

        // Add to dialog layout (you'll need to modify your XML to include this)
        val mainLayout = dialog.findViewById<LinearLayout>(R.id.mainDialogContainer)
        mainLayout?.addView(container, mainLayout.childCount - 1) // Add before ViewPager

        return container
    }

    /**
     * Create individual indicator dot
     */
    private fun createIndicatorDot(isActive: Boolean): View {
        val dot = View(context)
        val size = (context.resources.displayMetrics.density * 8).toInt() // 8dp
        val margin = (context.resources.displayMetrics.density * 4).toInt() // 4dp

        val layoutParams = LinearLayout.LayoutParams(size, size)
        layoutParams.setMargins(margin, 0, margin, 0)
        dot.layoutParams = layoutParams

        dot.background = if (isActive) {
            ContextCompat.getDrawable(context, R.drawable.page_indicator_active)
        } else {
            ContextCompat.getDrawable(context, R.drawable.page_indicator_inactive)
        }

        return dot
    }
    /**
     * Setup drag-to-dismiss functionality
     */
    private fun setupDragToDismiss() {
        dragHandleContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Start tracking velocity
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)

                    // Record initial touch position
                    initialY = event.rawY
                    currentY = initialY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)

                    // Calculate how far we've moved
                    val deltaY = event.rawY - initialY

                    // Start dragging if we've moved past the touch slop
                    if (abs(deltaY) > touchSlop || isDragging) {
                        isDragging = true
                        currentY = event.rawY

                        // Only allow dragging down (positive deltaY)
                        if (deltaY > 0f) {
                            // Move the entire dialog with the finger
                            dialog.window?.decorView?.translationY = deltaY
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val yVelocity = velocityTracker?.yVelocity ?: 0f
                        val deltaY = currentY - initialY

                        // Dismiss if dragged more than 25% of the way down or flicked downward
                        if (deltaY > (dialog.window?.decorView?.height?.times(0.25)?.toFloat() ?: 0f) ||
                            (yVelocity > minFlingVelocity && deltaY > 0f)) {
                            // Animate the rest of the way down and dismiss
                            animateAndDismiss()
                        } else {
                            // Snap back to original position
                            dialog.window?.decorView?.animate()
                                ?.translationY(0f)
                                ?.setDuration(200)
                                ?.start()
                        }
                    }

                    velocityTracker?.recycle()
                    velocityTracker = null
                    isDragging = false
                    true
                }

                else -> false
            }
        }

        // Make drag handle visually respond to touch
        dragHandleContainer.setOnClickListener {
            // Optional: provide feedback that the handle was tapped
            dragHandle.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(150)
                .withEndAction {
                    dragHandle.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                }
                .start()
        }
    }

    /**
     * Animate dialog off screen and dismiss
     */
    private fun animateAndDismiss() {
        val decorView = dialog.window?.decorView ?: return
        val height = decorView.height

        decorView.animate()
            .translationY(height.toFloat())
            .setDuration(200)
            .withEndAction {
                dialog.dismiss()
            }
            .start()
    }

    fun show(anchorView: View) {
        val displayMetrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorBottom = location[1] + anchorView.height

        val exactDialogHeight = screenHeight - anchorBottom

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            val params = attributes
            params.width = screenWidth
            params.height = exactDialogHeight
            params.dimAmount = 0.45f
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = anchorBottom

            attributes = params
            setLayout(screenWidth, exactDialogHeight)
        }

        dialog.show()
        viewPager.setCurrentItem(0, false)
    }    private fun storeFixedDimensions(dialogWidth: Int, dialogHeight: Int, viewPagerHeight: Int) {
        fixedDialogWidth = dialogWidth
        fixedDialogHeight = dialogHeight
        fixedViewPagerHeight = viewPagerHeight
        Timber.d("STORED DIMENSIONS: dialog=${fixedDialogWidth}x${fixedDialogHeight}, viewPager=height:${fixedViewPagerHeight}")
    }
    private fun setupModelsPage(view: View) {
        try {
            val recyclerView = view.findViewById<RecyclerView>(R.id.rvModelGrid)

            // Set up GridLayoutManager
            val spanCount = 2
            val gridLayoutManager = GridLayoutManager(context, spanCount)
            recyclerView.layoutManager = gridLayoutManager

            // Add item decoration
            recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    val spacing = 8
                    val position = parent.getChildAdapterPosition(view)
                    val column = position % spanCount

                    outRect.left = spacing - column * spacing / spanCount
                    outRect.right = (column + 1) * spacing / spanCount

                    if (position < spanCount) {
                        outRect.top = spacing
                    }
                    outRect.bottom = spacing
                }
            })

            // Create clean adapter
            val modelAdapter = ModelGridAdapter(
                context = context,
                models = models,
                selectedPosition = preferences.modelPosition,
                dialogWindow = null, // Remove window reference to avoid LayoutParams issues
                viewPagerRef = null, // Remove ViewPager reference to avoid LayoutParams issues
                onItemClick = { position ->
                    val selectedModel = models[position]

                    if (isTranslationModeFunc() && !TranslationUtils.supportsTranslation(selectedModel.id)) {
                        return@ModelGridAdapter
                    }

                    if (preferences.modelPosition != position) {
                        val changeInfo = PrefChangeInfo(
                            modelPosition = position,
                            length = preferences.length,
                            tone = preferences.tone,
                            changedType = PrefChangeInfo.ChangeType.MODEL
                        )
                        notifyChanges(changeInfo)
                    }

                    // Simple dimension maintenance
                    dialog.window?.let { window ->
                        val params = window.attributes
                        window.setLayout(params.width, params.height)
                    }
                }
            )

            recyclerView.adapter = modelAdapter

        } catch (e: Exception) {
            Timber.e(e, "Error setting up models page: ${e.message}")
        }
    }    private fun setupPreferencesPage(view: View) {
        try {
            val optionsContainer = view.findViewById<ScrollView>(R.id.optionsScrollView)
            val lengthContainer = view.findViewById<LinearLayout>(R.id.lengthOptionsContainer)
            val toneContainer = view.findViewById<LinearLayout>(R.id.toneOptionsContainer)

            // Just ensure containers exist - don't manipulate LayoutParams
            if (lengthContainer == null || toneContainer == null) {
                throw IllegalStateException("Required containers not found in layout")
            }

            // Setup options
            setupLengthOptions(lengthContainer)
            setupToneOptions(toneContainer)

        } catch (e: Exception) {
            Timber.e(e, "Error setting up preferences page: ${e.message}")
        }
    }


    private fun updatePageIndicators(position: Int) {
        val indicatorContainer = dialog.findViewById<LinearLayout>(R.id.pageIndicatorContainer)
        indicatorContainer?.let { container ->
            for (i in 0 until container.childCount) {
                val dot = container.getChildAt(i)
                val isActive = i == position

                if (isActive) {
                    dot.animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .setDuration(250)
                        .setInterpolator(OvershootInterpolator())
                        .start()

                    dot.background = ContextCompat.getDrawable(context, R.drawable.page_indicator_active)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        dot.elevation = 4f
                    }
                } else {
                    dot.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .start()

                    dot.background = ContextCompat.getDrawable(context, R.drawable.page_indicator_inactive)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        dot.elevation = 0f
                    }
                }
            }
        }
    }
    private fun setupLengthOptions(container: LinearLayout) {
        container.removeAllViews()

        val lengthValues = ResponseLength.values()

        for (length in lengthValues) {
            val view = LayoutInflater.from(context).inflate(
                R.layout.compact_length_option, container, false)

            val card = view.findViewById<CardView>(R.id.optionCard)
            val nameView = view.findViewById<TextView>(R.id.tvOptionName)
            val indicator = view.findViewById<View>(R.id.selectionIndicator)
            val checkmarkView = view.findViewById<ImageView>(R.id.ivCheckmark)
            val iconView = view.findViewById<ImageView>(R.id.ivOptionIcon)

            nameView.text = length.displayName

            when (length) {
                ResponseLength.SHORT -> iconView.setImageResource(R.drawable.ic_length_short)
                ResponseLength.DEFAULT -> iconView.setImageResource(R.drawable.ic_length_medium)
                ResponseLength.LONG -> iconView.setImageResource(R.drawable.ic_length_long)
            }

            val isSelected = preferences.length == length
            updateLengthSelectionState(card, indicator, checkmarkView, isSelected)

            // CLEAN click listener - no LayoutParams manipulation
            card.setOnClickListener {
                if (preferences.length != length) {
                    val changeInfo = PrefChangeInfo(
                        modelPosition = preferences.modelPosition,
                        length = length,
                        tone = preferences.tone,
                        changedType = PrefChangeInfo.ChangeType.LENGTH
                    )
                    notifyChanges(changeInfo)
                }

                // Update UI
                for (i in 0 until container.childCount) {
                    val childCard = container.getChildAt(i).findViewById<CardView>(R.id.optionCard)
                    val childIndicator = container.getChildAt(i).findViewById<View>(R.id.selectionIndicator)
                    val childCheckmark = container.getChildAt(i).findViewById<ImageView>(R.id.ivCheckmark)
                    updateLengthSelectionState(childCard, childIndicator, childCheckmark, false)
                }

                updateLengthSelectionState(card, indicator, checkmarkView, true)

                // Simple dimension maintenance
                dialog.window?.let { window ->
                    val params = window.attributes
                    window.setLayout(params.width, params.height)
                }
            }

            container.addView(view)
        }
    }

    // Fixed setupToneOptions with correct ScrollView setup
    private fun setupToneOptions(container: LinearLayout) {
        container.removeAllViews()

        // Create ScrollView - let the XML handle fillViewport
        val scrollView = ScrollView(context)
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        scrollView.layoutParams = scrollParams
        scrollView.isVerticalScrollBarEnabled = true

        val toneValues = ResponseTone.values()

        // Create GridLayout
        val gridLayout = GridLayout(context)
        gridLayout.columnCount = 2
        gridLayout.useDefaultMargins = true
        gridLayout.alignmentMode = GridLayout.ALIGN_BOUNDS

        val gridParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        gridParams.setMargins(8, 8, 8, 8)
        gridLayout.layoutParams = gridParams

        // Calculate card width
        val screenWidth = context.resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth * 0.95 / 2.2).toInt()

        for (tone in toneValues) {
            val view = LayoutInflater.from(context).inflate(
                R.layout.compact_tone_option, null, false)

            val card = view.findViewById<CardView>(R.id.optionCard)
            val nameView = view.findViewById<TextView>(R.id.tvOptionName)
            val descView = view.findViewById<TextView>(R.id.tvOptionDescription)
            val selectionIndicator = view.findViewById<View>(R.id.selectionIndicator)
            val checkmarkView = view.findViewById<ImageView>(R.id.ivCheckmark)

            nameView.text = tone.displayName
            descView.text = tone.description

            // Set GridLayout parameters
            val gridParams = GridLayout.LayoutParams()
            gridParams.width = cardWidth
            gridParams.height = GridLayout.LayoutParams.WRAP_CONTENT
            gridParams.setMargins(4, 4, 4, 4)
            view.layoutParams = gridParams

            val isSelected = preferences.tone == tone
            updateToneSelectionState(card, selectionIndicator, checkmarkView, isSelected)

            // CLEAN click listener
            card.setOnClickListener {
                if (preferences.tone != tone) {
                    val changeInfo = PrefChangeInfo(
                        modelPosition = preferences.modelPosition,
                        length = preferences.length,
                        tone = tone,
                        changedType = PrefChangeInfo.ChangeType.TONE
                    )
                    notifyChanges(changeInfo)
                }

                // Update UI
                for (i in 0 until gridLayout.childCount) {
                    val childView = gridLayout.getChildAt(i)
                    val childCard = childView.findViewById<CardView>(R.id.optionCard)
                    val childIndicator = childView.findViewById<View>(R.id.selectionIndicator)
                    val childCheckmark = childView.findViewById<ImageView>(R.id.ivCheckmark)
                    updateToneSelectionState(childCard, childIndicator, childCheckmark, false)
                }

                updateToneSelectionState(card, selectionIndicator, checkmarkView, true)

                // Simple dimension maintenance
                dialog.window?.let { window ->
                    val params = window.attributes
                    window.setLayout(params.width, params.height)
                }
            }

            gridLayout.addView(view)
        }

        scrollView.addView(gridLayout)
        container.addView(scrollView)
    }    /**
     * Enhanced version of ResponsePreferences that tracks what was changed
     */
    private class PrefChangeInfo(
        val modelPosition: Int,
        val length: ResponseLength,
        val tone: ResponseTone,
        val changedType: ChangeType
    ) {
        enum class ChangeType {
            MODEL, LENGTH, TONE, NONE
        }

        fun toResponsePreferences(): ResponsePreferences {
            return ResponsePreferences(
                modelPosition = modelPosition,
                length = length,
                tone = tone
            )
        }
    }

    /**
     * Notify the callback with change tracking info
     */
    private fun notifyChanges(changeInfo: PrefChangeInfo) {
        // Apply the changes to our internal preferences
        preferences.modelPosition = changeInfo.modelPosition
        preferences.length = changeInfo.length
        preferences.tone = changeInfo.tone

        // Pass the response preferences to the callback
        onSelectionComplete(changeInfo.toResponsePreferences())

        // Log what changed
        when (changeInfo.changedType) {
            PrefChangeInfo.ChangeType.MODEL ->
                Timber.d("SPINNER: Model changed to position ${changeInfo.modelPosition}")
            PrefChangeInfo.ChangeType.LENGTH ->
                Timber.d("SPINNER: Length changed to ${changeInfo.length.name}")
            PrefChangeInfo.ChangeType.TONE ->
                Timber.d("SPINNER: Tone changed to ${changeInfo.tone.name}")
            else ->
                Timber.d("SPINNER: No changes detected")
        }
    }
    private fun updateLengthSelectionState(card: CardView, indicator: View, checkmark: ImageView, isSelected: Boolean) {
        if (isSelected) {
            // Selected state
            card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.selected_option_background))
            indicator.visibility = View.VISIBLE
            checkmark.visibility = View.GONE // Keep hidden

            // Add subtle animation
            card.animate()
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(150)
                .setInterpolator(OvershootInterpolator())
                .start()
        } else {
            // Unselected state - use light gray background
            card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.disabled_card_background))
            indicator.visibility = View.GONE
            checkmark.visibility = View.GONE

            // Reset scale
            card.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(150)
                .start()
        }
    }

    private fun updateToneSelectionState(card: CardView, indicator: View, checkmark: ImageView, isSelected: Boolean) {
        if (isSelected) {
            // Selected state
            card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.selected_option_background))
            indicator.visibility = View.VISIBLE
            checkmark.visibility = View.GONE // Keep gone for backward compatibility

            // Add subtle animation
            card.animate()
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(150)
                .setInterpolator(OvershootInterpolator())
                .start()
        } else {
            // Unselected state - use light gray background
            card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.disabled_card_background))
            indicator.visibility = View.GONE
            checkmark.visibility = View.GONE

            // Reset scale
            card.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(150)
                .start()
        }
    }
}