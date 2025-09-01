package com.cyberflux.qwinai

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.adapter.PromptChipAdapter
import com.cyberflux.qwinai.databinding.FragmentHomeBinding
import com.cyberflux.qwinai.model.AIFeature
import com.cyberflux.qwinai.model.PromptChip
import com.cyberflux.qwinai.model.RecentModel
import com.cyberflux.qwinai.model.TrendingPrompt
import com.cyberflux.qwinai.utils.LocationService
import com.cyberflux.qwinai.utils.ModelManager
import com.cyberflux.qwinai.utils.ModelValidator
import com.cyberflux.qwinai.utils.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone



class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var featureAdapter: FeatureCardAdapter
    private lateinit var promptChipAdapter: PromptChipAdapter
    private lateinit var recentModelsAdapter: RecentModelsAdapter
    private lateinit var trendingPromptsAdapter: TrendingPromptsAdapter


    private lateinit var creativePromptsRecyclerView: RecyclerView
    private lateinit var productivityPromptsRecyclerView: RecyclerView
    private lateinit var educationPromptsRecyclerView: RecyclerView
    private lateinit var techPromptsRecyclerView: RecyclerView
    private lateinit var everydayPromptsRecyclerView: RecyclerView


    // More diverse greetings organized by time of day
    private val earlyMorningGreetings = listOf(
        "Good early morning",
        "Rise and shine",
        "Early bird greetings",
        "Good dawn",
        "Morning sunshine"
    )

    private val morningGreetings = listOf(
        "Good morning",
        "Morning greetings",
        "Beautiful morning",
        "Fresh morning vibes",
        "Lovely morning",
        "Bright morning",
        "Pleasant morning"
    )

    private val lateMorningGreetings = listOf(
        "Good late morning",
        "Almost noon greetings",
        "Late morning hello",
        "Pre-lunch greetings",
        "Mid-morning hello"
    )

    private val afternoonGreetings = listOf(
        "Good afternoon",
        "Afternoon greetings",
        "Hope your day is going well",
        "Pleasant afternoon",
        "Lovely afternoon",
        "Mid-day greetings",
        "Afternoon hello"
    )

    private val earlyEveningGreetings = listOf(
        "Good early evening",
        "Evening greetings",
        "Hope you had a great day",
        "Pleasant evening",
        "Lovely evening",
        "End of day greetings"
    )

    private val eveningGreetings = listOf(
        "Good evening",
        "Evening hello",
        "Relaxing evening greetings",
        "Peaceful evening",
        "Cozy evening",
        "Calm evening vibes"
    )

    private val nightGreetings = listOf(
        "Good night",
        "Late night greetings",
        "Night owl hello",
        "Evening winds down",
        "Peaceful night",
        "Quiet night greetings",
        "Midnight hello"
    )

    private val lateNightGreetings = listOf(
        "Very late night greetings",
        "Burning the midnight oil",
        "Late night productivity",
        "Night owl vibes",
        "Deep night hello",
        "After midnight greetings"
    )


    // AI Tip of the day content
    private val aiTips = listOf(
        "Try being specific with your prompts for better results",
        "You can upload images for analysis with compatible models",
        "Web search-enabled models can provide up-to-date information",
        "Use 'Explain like I'm 5' for simplified explanations of complex topics",
        "Ask for step-by-step instructions when learning something new",
        "Different AI models have different strengths - try several!",
        "For creative writing, provide details about style and tone",
        "Break complex tasks into smaller prompts for better results",
        "Save your favorite conversations for future reference",
        "The Pro subscription unlocks advanced features like image generation"
    )

    // Weather conditions for testing (would be replaced by actual API)
    private val weatherConditions = listOf("sunny", "cloudy", "rainy", "snowy", "windy")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGreeting()
        setupAiTipOfTheDay()
        setupImageGenerationCard()
        setupRecentModels()
        setupTrendingPrompts()
        setupFeatureCards()
        setupPromptChips()
        setupPromptChipRecyclerViews()

        // Apply animations
        applyAnimations()

        // Check for updates and notifications
        checkForUpdatesAndNotifications()


        // Start greeting updates
        scheduleGreetingUpdates()
    }

    private fun setupPromptChipRecyclerViews() {
        // Original quick prompts
        setupQuickPrompts()

        // Setup additional prompt categories
        setupCreativePrompts()
        setupProductivityPrompts()
        setupEducationPrompts()
        setupTechPrompts()
        setupEverydayPrompts()
    }

    private fun setupQuickPrompts() {
        val quickPromptsList = listOf(
            PromptChip("general_1", "Tell me a joke", "quick"),
            PromptChip("general_2", "Write a short story", "quick"),
            PromptChip("general_3", "What's the weather today?", "quick"),
            PromptChip("general_4", "Help me plan a trip", "quick"),
            PromptChip("general_5", "Explain quantum physics", "quick"),
            PromptChip("general_6", "Create a workout routine", "quick"),
            PromptChip("general_7", "Recipe ideas for dinner", "quick")
        )

        val promptChipsAdapter = PromptChipAdapter(quickPromptsList) { promptChip ->
            // Handle prompt chip click
            (activity as? StartActivity)?.startChatWithPrompt(promptChip.text)
        }

        binding.promptChipsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = promptChipsAdapter
        }
    }

    private fun setupCreativePrompts() {
        // Initialize recycler view
        creativePromptsRecyclerView = binding.root.findViewById(R.id.creativePromptsRecyclerView)

        val creativePromptsList = listOf(
            PromptChip("creative_1", "Write a sci-fi story opening", "creative"),
            PromptChip("creative_2", "Design a fantasy character", "creative"),
            PromptChip("creative_3", "Create a haiku about nature", "creative"),
            PromptChip("creative_4", "Draft a movie script scene", "creative"),
            PromptChip("creative_5", "Invent a unique superhero", "creative"),
            PromptChip("creative_6", "Write song lyrics about love", "creative"),
            PromptChip("creative_7", "Design a fictional world", "creative")
        )

        val creativePromptsAdapter = PromptChipAdapter(creativePromptsList) { promptChip ->
            (activity as? StartActivity)?.startChatWithPrompt(promptChip.text)
        }

        creativePromptsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = creativePromptsAdapter
        }
    }

    private fun setupProductivityPrompts() {
        // Initialize recycler view
        productivityPromptsRecyclerView = binding.root.findViewById(R.id.productivityPromptsRecyclerView)

        val productivityPromptsList = listOf(
            PromptChip("productivity_1", "Create a daily schedule", "productivity"),
            PromptChip("productivity_2", "Generate a to-do list template", "productivity"),
            PromptChip("productivity_3", "Time management strategies", "productivity"),
            PromptChip("productivity_4", "Help write a professional email", "productivity"),
            PromptChip("productivity_5", "Meeting agenda template", "productivity"),
            PromptChip("productivity_6", "Weekly planning framework", "productivity"),
            PromptChip("productivity_7", "Focus techniques for work", "productivity")
        )

        val productivityPromptsAdapter = PromptChipAdapter(productivityPromptsList) { promptChip ->
            (activity as? StartActivity)?.startChatWithPrompt(promptChip.text)
        }

        productivityPromptsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = productivityPromptsAdapter
        }
    }

    private fun setupEducationPrompts() {
        // Initialize recycler view
        educationPromptsRecyclerView = binding.root.findViewById(R.id.educationPromptsRecyclerView)

        val educationPromptsList = listOf(
            PromptChip("education_1", "Explain like I'm 5: black holes", "education"),
            PromptChip("education_2", "Help me learn JavaScript", "education"),
            PromptChip("education_3", "Summary of world history", "education"),
            PromptChip("education_4", "Chemistry concepts simplified", "education"),
            PromptChip("education_5", "Math problem solver", "education"),
            PromptChip("education_6", "Language learning tips", "education"),
            PromptChip("education_7", "Study techniques for exams", "education")
        )

        val educationPromptsAdapter = PromptChipAdapter(educationPromptsList) { promptChip ->
            (activity as? StartActivity)?.startChatWithPrompt(promptChip.text)
        }

        educationPromptsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = educationPromptsAdapter
        }
    }

    private fun setupTechPrompts() {
        // Initialize recycler view
        techPromptsRecyclerView = binding.root.findViewById(R.id.techPromptsRecyclerView)

        val techPromptsList = listOf(
            PromptChip("tech_1", "Debug this Android code", "tech"),
            PromptChip("tech_2", "Explain REST API concepts", "tech"),
            PromptChip("tech_3", "Kotlin vs Java comparison", "tech"),
            PromptChip("tech_4", "Android UI best practices", "tech"),
            PromptChip("tech_5", "Write a simple SQLite query", "tech"),
            PromptChip("tech_6", "RecyclerView optimization tips", "tech"),
            PromptChip("tech_7", "Jetpack Compose tutorial", "tech")
        )

        val techPromptsAdapter = PromptChipAdapter(techPromptsList) { promptChip ->
            (activity as? StartActivity)?.startChatWithPrompt(promptChip.text)
        }

        techPromptsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = techPromptsAdapter
        }
    }

    private fun setupEverydayPrompts() {
        // Initialize recycler view
        everydayPromptsRecyclerView = binding.root.findViewById(R.id.everydayPromptsRecyclerView)

        val everydayPromptsList = listOf(
            PromptChip("everyday_1", "Healthy meal ideas", "everyday"),
            PromptChip("everyday_2", "Workout routines at home", "everyday"),
            PromptChip("everyday_3", "Mindfulness exercises", "everyday"),
            PromptChip("everyday_4", "Budget planning tips", "everyday"),
            PromptChip("everyday_5", "Home organization ideas", "everyday"),
            PromptChip("everyday_6", "Quick dinner recipes", "everyday"),
            PromptChip("everyday_7", "Weekend activity suggestions", "everyday")
        )

        val everydayPromptsAdapter = PromptChipAdapter(everydayPromptsList) { promptChip ->
            (activity as? StartActivity)?.startChatWithPrompt(promptChip.text)
        }

        everydayPromptsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = everydayPromptsAdapter
        }
    }

    private fun applyAnimations() {
        // Staggered animations for a more dynamic entrance

        // 1. Fade in greeting with slight upward movement
        binding.greetingTextView.translationY = 20f
        binding.greetingTextView.alpha = 0f
        binding.greetingTextView.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(100)
            .start()

        // 2. Fade in cards with staggered delay
        listOf(
            binding.tipCardView,
            binding.imageGenerationCardView,
            binding.recentModelsLabel,
            binding.recentModelsRecyclerView,
            binding.featuresTitle,
            binding.featuresRecyclerView,
            binding.promptsTitle,
            binding.promptChipsRecyclerView
        ).forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 30f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay(200L + (index * 100))
                .start()
        }
    }

    private fun setupGreeting() {
        // Get user's timezone and current time
        val userTimeZone = TimeZone.getDefault()
        val calendar = Calendar.getInstance(userTimeZone)
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // More precise time-based greeting selection
        val greetingsList = when {
            hourOfDay in 5..6 -> earlyMorningGreetings          // 5:00-6:59 AM
            hourOfDay in 7..10 -> morningGreetings              // 7:00-10:59 AM
            hourOfDay == 11 -> lateMorningGreetings             // 11:00-11:59 AM
            hourOfDay in 12..16 -> afternoonGreetings           // 12:00-4:59 PM
            hourOfDay in 17..18 -> earlyEveningGreetings        // 5:00-6:59 PM
            hourOfDay in 19..21 -> eveningGreetings             // 7:00-9:59 PM
            hourOfDay in 22..23 -> nightGreetings               // 10:00-11:59 PM
            hourOfDay in 0..1 -> lateNightGreetings             // 12:00-1:59 AM
            else -> nightGreetings                              // 2:00-4:59 AM (very late/early)
        }

        // Add time-specific variations for more personality
        val timeSpecificGreeting = when {
            hourOfDay == 6 && minute < 30 -> "Early sunrise greetings"
            hourOfDay == 12 && minute < 15 -> "Lunch time hello"
            hourOfDay == 17 && minute > 45 -> "Almost dinner time greetings"
            hourOfDay == 20 && minute > 30 -> "Getting late evening vibes"
            hourOfDay == 0 && minute < 30 -> "Just past midnight greetings"
            else -> greetingsList.random()
        }

        // Get user name from preferences (or default)
        val userName = PrefsManager.getUserName(requireContext()) ?: "friend"

        // Create greeting - ensure it flows well with the name
        val greeting = timeSpecificGreeting
        val greetingText = "$greeting, $userName!"

        // Create styled text with gradient or color accent
        val spannableString = SpannableString(greetingText)

        // Color the greeting part (before the comma)
        val commaIndex = greetingText.indexOf(',')
        if (commaIndex > 0) {
            spannableString.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.colorPrimary)),
                0,
                commaIndex,
                0
            )

            // Make the name part slightly different color
            spannableString.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.colorAccent)),
                commaIndex,
                greetingText.length,
                0
            )
        }

        binding.greetingTextView.text = spannableString

        // Add subtle animation for greeting changes
        binding.greetingTextView.alpha = 0.7f
        binding.greetingTextView.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        // Attach click listener to edit name with better UX
        binding.greetingTextView.setOnClickListener {
            showNameEditDialog(userName)
        }

        // Log for debugging time-based greetings
        Timber.d("Time: ${hourOfDay}:${String.format("%02d", minute)}, Selected greeting: $greeting")
    }

    private fun showNameEditDialog(currentName: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_name, null)
        val editText = dialogView.findViewById<android.widget.EditText>(R.id.etNameInput)

        // Set current name and select all text for easy editing
        editText.setText(currentName)
        editText.selectAll()

        val dialog = AlertDialog.Builder(requireContext())
            // Remove this line:
            // .setTitle("What should I call you?")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                // Rest of the code remains unchanged
                val newName = editText.text.toString().trim()

                when {
                    newName.isEmpty() -> {
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            "Name cannot be empty",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    newName.length > 20 -> {
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            "Name is too long",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    newName != currentName -> {
                        // Save to preferences
                        PrefsManager.setUserName(requireContext(), newName)

                        // Update greeting with animation
                        binding.greetingTextView.animate()
                            .alpha(0f)
                            .setDuration(150)
                            .withEndAction {
                                setupGreeting()
                                binding.greetingTextView.animate()
                                    .alpha(1f)
                                    .setDuration(150)
                                    .start()
                            }
                            .start()

                        // Show friendly confirmation
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            "Nice to meet you, $newName! 👋",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()

                        // Haptic feedback
                        (activity as? StartActivity)?.provideHapticFeedback()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Focus the input and show keyboard
        editText.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setupAiTipOfTheDay() {
        // Get random tip (or based on day of year for consistency)
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val tip = aiTips[dayOfYear % aiTips.size]

        binding.tipOfDayText.text = tip

        // Add click effect to the tip card
        binding.tipCardView.setOnClickListener {
            // Show a new random tip
            val newTip = aiTips.random()

            // Animate tip change
            binding.tipOfDayText.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    binding.tipOfDayText.text = newTip
                    binding.tipOfDayText.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
                .start()

            (activity as? StartActivity)?.provideHapticFeedback()
        }
    }

    private fun setupImageGenerationCard() {
        // Set up image generation card with beautiful content
        binding.imageGenTextView.text = "Create Images"
        
        // Add click action to open ImageGenerationActivity
        binding.imageGenerationCardView.setOnClickListener {
            try {
                val intent = android.content.Intent(requireContext(), ImageGenerationActivity::class.java)
                startActivity(intent)
                
                // Add transition animation
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                
                Timber.d("Opened ImageGenerationActivity from home card")
            } catch (e: Exception) {
                Timber.e(e, "Error opening ImageGenerationActivity: ${e.message}")
                android.widget.Toast.makeText(requireContext(), "Error opening image generation", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun setupRecentModels() {
        // Get most used models from the tracker
        val recentModels = ModelUsageTracker.getRecentModels(requireContext())

        // Log for debugging
        Timber.d("Loaded recent models: ${recentModels.map { it.displayName }}")

        // If we don't have 3 models yet, fill with defaults
        val modelsToShow = if (recentModels.size < 3) {
            val defaults = listOf(
                RecentModel("Claude 3.7 Sonnet", ModelManager.CLAUDE_3_7_SONNET_ID, R.drawable.ic_claude),
                RecentModel("GPT-4o", ModelManager.GPT_4O_ID, R.drawable.ic_gpt),
                RecentModel("Llama 4 Maverick", ModelManager.LLAMA_4_MAVERICK_ID, R.drawable.ic_llama)
            )

            // Merge user models with defaults, removing duplicates
            val existingIds = recentModels.map { it.id }
            recentModels + defaults.filterNot { it.id in existingIds }.take(3 - recentModels.size)
        } else {
            recentModels
        }

        // Update the adapter
        recentModelsAdapter = RecentModelsAdapter(modelsToShow) { model ->
            // Start chat with selected model
            (activity as? StartActivity)?.startChatWithModel(model.id)
            (activity as? StartActivity)?.provideHapticFeedback()
        }

        binding.recentModelsRecyclerView.apply {
            adapter = recentModelsAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupTrendingPrompts() {
        // Trending prompts that would normally be fetched from backend
        val trendingPrompts = listOf(
            TrendingPrompt(
                "Create a summer vacation itinerary",
                "Plan a 7-day summer trip with activities and sightseeing",
                R.drawable.ic_vacation,
                "#ff9500"
            ),
            TrendingPrompt(
                "Help with coding interview prep",
                "Review common data structures and algorithms for interviews",
                R.drawable.ic_code,
                "#007bff"
            ),
            TrendingPrompt(
                "Generate a weekly meal plan",
                "Create a balanced meal plan with shopping list and recipes",
                R.drawable.ic_food,
                "#28a745"
            ),
            TrendingPrompt(
                "Learn about quantum computing",
                "Explain quantum computing principles in simple terms",
                R.drawable.ic_quantum,
                "#6f42c1"
            )
        )

        trendingPromptsAdapter = TrendingPromptsAdapter(trendingPrompts) { prompt ->
            (activity as? StartActivity)?.startChatWithPrompt(prompt.description)
        }

    }

    private fun setupFeatureCards() {
        val features = getAIFeatures()
        featureAdapter = FeatureCardAdapter(features) { feature ->
            // Let parent activity handle feature clicks
            (activity as? StartActivity)?.handleFeatureClick(feature)
        }

        binding.featuresRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = featureAdapter
        }
    }

    private fun setupPromptChips() {
        val prompts = getPromptChips()
        promptChipAdapter = PromptChipAdapter(prompts) { prompt ->
            // Let parent activity handle prompts
            (activity as? StartActivity)?.startChatWithPrompt(prompt.text)
        }

        binding.promptChipsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = promptChipAdapter
        }
    }

    private fun getAIFeatures(): List<AIFeature> {
        return listOf(
            AIFeature(
                id = "web_search",
                title = "Ask the Web",
                description = "Search the web for real-time information",
                iconResId = R.drawable.ic_search,
                colorResId = R.color.feature_web_search,
            ),
            AIFeature(
                id = "image_upload",
                title = "Image Upload",
                description = "Upload images for AI analysis",
                iconResId = R.drawable.ic_image,
                colorResId = R.color.feature_image_upload,
            ),
            AIFeature(
                id = "ask_by_link",
                title = "Ask by Link",
                description = "Analyze content from web links using Bagoodex",
                iconResId = R.drawable.ic_link,
                colorResId = R.color.feature_ask_by_link,
            ),
            AIFeature(
                id = "prompt_of_day",
                title = "Prompt of the Day",
                description = "Try our curated daily AI prompt",
                iconResId = R.drawable.ic_star,
                colorResId = R.color.feature_prompt_day,
            ),
            AIFeature(
                id = "ocr_reader",
                title = "Pic Reader",
                description = "Extract and summarize text from images",
                iconResId = R.drawable.ic_document_scanner,
                colorResId = R.color.feature_ocr,
            ),
            AIFeature(
                id = "translator",
                title = "Translator",
                description = "Translate text between languages",
                iconResId = R.drawable.ic_translate,
                colorResId = R.color.feature_translator,
            ),
            AIFeature(
                id = "file_upload",
                title = "File Upload",
                description = "Upload and analyze documents of various formats",
                iconResId = R.drawable.ic_file_upload,
                colorResId = R.color.feature_file_upload,
            ),
            AIFeature(
                id = "private_chat",
                title = "Private Chat",
                description = "Start a secure private conversation",
                iconResId = R.drawable.ic_private_chat,
                colorResId = R.color.feature_private_chat,
            ),
            AIFeature(
                id = "voice_chat",
                title = "Voice Chat",
                description = "Talk with AI using voice only",
                iconResId = R.drawable.ic_mic,
                colorResId = R.color.feature_voice_chat,
            ),
            AIFeature(
                id = "image_gallery",
                title = "Image Gallery",
                description = "Browse and manage AI generated images",
                iconResId = R.drawable.ic_gallery,
                colorResId = R.color.feature_image_gallery,
            )
        )
    }
    private fun getPromptChips(): List<PromptChip> {
        return listOf(
            PromptChip("Tell me a joke", "Write a short story", "quick"),
            PromptChip("Write a poem", "Write a short story", "quick"),
            PromptChip("Explain quantum physics", "Write a short story", "quick"),
            PromptChip("Plan my day", "Write a short story", "quick"),
            PromptChip("Creative story ideas", "Write a short story", "quick"),
            PromptChip("Coding help", "Write a short story", "quick"),
            PromptChip("Recipe suggestions", "Write a short story", "quick"),
            PromptChip("Travel recommendations", "Write a short story", "quick"),
            PromptChip("Summarize this article", "Write a short story", "quick"),
            PromptChip("Improve my resume", "Write a short story", "quick"),
            PromptChip("Write a love letter", "Write a short story", "quick"),
            PromptChip("Help with homework", "Write a short story", "quick"),
            PromptChip("Describe this image", "Write a short story", "quick"),
            PromptChip("Translate this text", "Write a short story", "quick"),
            PromptChip("Give me business ideas", "Write a short story", "quick"),
            PromptChip("Generate a workout plan", "Write a short story", "quick"),
            PromptChip("Teach me a new language", "Write a short story", "quick"),
            PromptChip("Draft an email", "Write a short story", "quick"),
            PromptChip("Create a short story", "Write a short story", "quick"),
            PromptChip("Explain this concept simply", "Write a short story", "quick"),
            PromptChip("Generate social media captions", "Write a short story", "quick"),
            PromptChip("Suggest books to read", "Write a short story", "quick"),
            PromptChip("Help me meditate", "Write a short story", "quick"),
            PromptChip("What's trending in tech?", "Write a short story", "quick"),
            PromptChip("Write a song verse", "Write a short story", "quick"),
            PromptChip("Give me movie suggestions", "Write a short story", "quick"),
            PromptChip("Make a shopping list", "Write a short story", "quick"),
            PromptChip("Create a study schedule", "Write a short story", "quick"),
            PromptChip("Give me fun facts", "Write a short story", "quick"),
            PromptChip("Simulate a job interview", "Write a short story", "quick")
        )
    }


    // Add this function to HomeFragment.kt to properly check and update the "What's New" status
    private fun checkForUpdatesAndNotifications() {
        // Get SharedPreferences to track "What's New" status
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Check if user has already seen the current version's "What's New"
        val currentAppVersion = requireContext().packageManager.getPackageInfo(
            requireContext().packageName, 0).versionCode
        val lastSeenVersion = prefs.getInt("last_seen_whats_new_version", -1)

        // Show card only if we have a new version the user hasn't seen yet
        val hasNewFeatures = currentAppVersion > lastSeenVersion

        if (hasNewFeatures) {
            binding.whatsNewCard.visibility = View.VISIBLE
            binding.whatsNewCard.setOnClickListener {
                // Show the dialog
                (activity as? StartActivity)?.showWhatsNewDialog()

                // Mark this version as seen
                prefs.edit().putInt("last_seen_whats_new_version", currentAppVersion).apply()

                // Animate card exit
                binding.whatsNewCard.animate()
                    .alpha(0f)
                    .translationX(200f)
                    .setDuration(300)
                    .withEndAction {
                        binding.whatsNewCard.visibility = View.GONE
                    }
                    .start()
            }
        } else {
            // If user has already seen it, hide the card
            binding.whatsNewCard.visibility = View.GONE
        }
    }
    fun showUpgradeCard() {
        binding.upgradeCard.visibility = View.VISIBLE
        binding.upgradeCard.alpha = 0f
        binding.upgradeCard.translationY = 100f

        binding.upgradeCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .start()

        binding.btnUpgrade.setOnClickListener {
            SubscriptionActivity.start(requireContext())
        }
    }

    fun hideUpgradeCard() {
        binding.upgradeCard.animate()
            .alpha(0f)
            .translationY(100f)
            .setDuration(300)
            .withEndAction {
                binding.upgradeCard.visibility = View.GONE
            }
            .start()
    }

    override fun onResume() {
        super.onResume()

        // Refresh greeting (time of day might have changed)
        setupGreeting()
    }
    private fun scheduleGreetingUpdates() {
        // Check for greeting updates every hour
        val greetingHandler = Handler(Looper.getMainLooper())
        val greetingRunnable = object : Runnable {
            override fun run() {
                setupGreeting() // Refresh greeting based on current time
                greetingHandler.postDelayed(this, 60 * 60 * 1000L) // Every hour
            }
        }

        // Start after 1 hour
        greetingHandler.postDelayed(greetingRunnable, 60 * 60 * 1000L)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}