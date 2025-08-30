package com.cyberflux.qwinai.utils

import android.content.Context
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.R
import kotlinx.coroutines.*
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * UI manager for conversation search functionality
 * Provides search interface, results display, and interaction handling
 */
class SearchUIManager(
    private val context: Context,
    private val searchManager: ConversationSearchManager,
    private val coroutineScope: CoroutineScope
) {
    
    private var searchJob: Job? = null
    private var searchCallback: SearchCallback? = null
    
    companion object {
        private const val SEARCH_DEBOUNCE_DELAY = 300L
        private const val MIN_QUERY_LENGTH = 2
    }
    
    /**
     * Callback interface for search events
     */
    interface SearchCallback {
        fun onSearchStarted()
        fun onSearchResults(results: ConversationSearchManager.SearchResults)
        fun onSearchError(error: String)
        fun onConversationSelected(conversation: com.cyberflux.qwinai.model.Conversation)
        fun onMessageSelected(message: com.cyberflux.qwinai.model.ChatMessage, conversation: com.cyberflux.qwinai.model.Conversation)
    }
    
    /**
     * Set search callback
     */
    fun setSearchCallback(callback: SearchCallback) {
        this.searchCallback = callback
    }
    
    /**
     * Create search input view with suggestions
     */
    fun createSearchView(parent: ViewGroup): View {
        val searchView = LayoutInflater.from(context)
            .inflate(R.layout.view_search_input, parent, false)
        
        setupSearchInput(searchView)
        setupSearchFilters(searchView)
        
        return searchView
    }
    
    /**
     * Create search results view
     */
    fun createSearchResultsView(parent: ViewGroup): View {
        val resultsView = LayoutInflater.from(context)
            .inflate(R.layout.view_search_results, parent, false)
        
        setupSearchResults(resultsView)
        
        return resultsView
    }
    
    /**
     * Setup search input with auto-complete and suggestions
     */
    private fun setupSearchInput(searchView: View) {
        val searchInput = searchView.findViewById<EditText>(R.id.searchInput)
        val suggestionsRecyclerView = searchView.findViewById<RecyclerView>(R.id.suggestionsRecyclerView)
        val clearButton = searchView.findViewById<ImageView>(R.id.clearSearchButton)
        
        // Setup suggestions adapter
        val suggestionsAdapter = SearchSuggestionsAdapter { suggestion ->
            searchInput.setText(suggestion)
            searchInput.setSelection(suggestion.length)
            performSearch(suggestion, getCurrentFilters(searchView))
        }
        
        suggestionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = suggestionsAdapter
        }
        
        // Setup search input text watcher
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                
                // Show/hide clear button
                clearButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                
                // Show suggestions for short queries
                if (query.length in 1 until MIN_QUERY_LENGTH) {
                    showSuggestions(query, suggestionsAdapter)
                    suggestionsRecyclerView.visibility = View.VISIBLE
                } else {
                    suggestionsRecyclerView.visibility = View.GONE
                }
                
                // Perform search with debounce
                if (query.length >= MIN_QUERY_LENGTH) {
                    debounceSearch(query, getCurrentFilters(searchView))
                }
            }
        })
        
        // Setup clear button
        clearButton.setOnClickListener {
            searchInput.text.clear()
            suggestionsRecyclerView.visibility = View.GONE
        }
    }
    
    /**
     * Setup search filters UI
     */
    private fun setupSearchFilters(searchView: View) {
        val filtersToggle = searchView.findViewById<ImageView>(R.id.filtersToggle)
        val filtersContainer = searchView.findViewById<LinearLayout>(R.id.filtersContainer)
        
        filtersToggle.setOnClickListener {
            val isVisible = filtersContainer.visibility == View.VISIBLE
            filtersContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
            
            // Rotate filter icon
            val rotation = if (isVisible) 0f else 180f
            filtersToggle.animate().rotation(rotation).setDuration(200).start()
        }
        
        // Setup individual filter controls
        setupDateRangeFilter(searchView)
        setupModelFilter(searchView)
        setupTypeFilter(searchView)
    }
    
    /**
     * Setup date range filter
     */
    private fun setupDateRangeFilter(searchView: View) {
        val dateRangeSpinner = searchView.findViewById<Spinner>(R.id.dateRangeSpinner)
        
        val dateRangeOptions = arrayOf(
            "Any time",
            "Today",
            "This week",
            "This month",
            "This year",
            "Custom range"
        )
        
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, dateRangeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dateRangeSpinner.adapter = adapter
        
        dateRangeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val query = searchView.findViewById<EditText>(R.id.searchInput).text.toString()
                if (query.length >= MIN_QUERY_LENGTH) {
                    debounceSearch(query, getCurrentFilters(searchView))
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    /**
     * Setup model filter
     */
    private fun setupModelFilter(searchView: View) {
        val modelSpinner = searchView.findViewById<Spinner>(R.id.modelSpinner)
        
        // This would be populated with actual models from the database
        val modelOptions = arrayOf("All models", "GPT-4", "GPT-3.5", "Claude", "Gemini")
        
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, modelOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = adapter
    }
    
    /**
     * Setup content type filter
     */
    private fun setupTypeFilter(searchView: View) {
        val conversationsCheckbox = searchView.findViewById<CheckBox>(R.id.includeConversationsCheckbox)
        val messagesCheckbox = searchView.findViewById<CheckBox>(R.id.includeMessagesCheckbox)
        val userMessagesCheckbox = searchView.findViewById<CheckBox>(R.id.onlyUserMessagesCheckbox)
        val aiMessagesCheckbox = searchView.findViewById<CheckBox>(R.id.onlyAiMessagesCheckbox)
        
        // Setup mutual exclusion for user/AI messages
        userMessagesCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) aiMessagesCheckbox.isChecked = false
        }
        
        aiMessagesCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) userMessagesCheckbox.isChecked = false
        }
    }
    
    /**
     * Setup search results RecyclerView
     */
    private fun setupSearchResults(resultsView: View) {
        val recyclerView = resultsView.findViewById<RecyclerView>(R.id.searchResultsRecyclerView)
        val emptyView = resultsView.findViewById<LinearLayout>(R.id.emptySearchView)
        val loadingView = resultsView.findViewById<ProgressBar>(R.id.searchLoadingIndicator)
        
        val adapter = SearchResultsAdapter(
            onConversationClick = { conversation ->
                searchCallback?.onConversationSelected(conversation)
            },
            onMessageClick = { message, conversation ->
                searchCallback?.onMessageSelected(message, conversation)
            }
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
    }
    
    /**
     * Get current filters from UI
     */
    private fun getCurrentFilters(searchView: View): ConversationSearchManager.SearchFilters {
        val includeConversations = searchView.findViewById<CheckBox>(R.id.includeConversationsCheckbox).isChecked
        val includeMessages = searchView.findViewById<CheckBox>(R.id.includeMessagesCheckbox).isChecked
        val onlyUserMessages = searchView.findViewById<CheckBox>(R.id.onlyUserMessagesCheckbox).isChecked
        val onlyAiMessages = searchView.findViewById<CheckBox>(R.id.onlyAiMessagesCheckbox).isChecked
        
        val dateRange = getSelectedDateRange(searchView)
        val modelId = getSelectedModel(searchView)
        
        return ConversationSearchManager.SearchFilters(
            includeConversations = includeConversations,
            includeMessages = includeMessages,
            onlyUserMessages = onlyUserMessages,
            onlyAiMessages = onlyAiMessages,
            modelId = modelId,
            dateRange = dateRange
        )
    }
    
    /**
     * Get selected date range from UI
     */
    private fun getSelectedDateRange(searchView: View): ConversationSearchManager.DateRange? {
        val dateRangeSpinner = searchView.findViewById<Spinner>(R.id.dateRangeSpinner)
        val selection = dateRangeSpinner.selectedItemPosition
        
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        
        return when (selection) {
            1 -> { // Today
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                ConversationSearchManager.DateRange(calendar.timeInMillis, endTime)
            }
            2 -> { // This week
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                ConversationSearchManager.DateRange(calendar.timeInMillis, endTime)
            }
            3 -> { // This month
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                ConversationSearchManager.DateRange(calendar.timeInMillis, endTime)
            }
            4 -> { // This year
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                ConversationSearchManager.DateRange(calendar.timeInMillis, endTime)
            }
            else -> null // Any time or custom range
        }
    }
    
    /**
     * Get selected model from UI
     */
    private fun getSelectedModel(searchView: View): String? {
        val modelSpinner = searchView.findViewById<Spinner>(R.id.modelSpinner)
        val selection = modelSpinner.selectedItemPosition
        
        return if (selection > 0) {
            modelSpinner.selectedItem.toString()
        } else null
    }
    
    /**
     * Show search suggestions
     */
    private fun showSuggestions(query: String, adapter: SearchSuggestionsAdapter) {
        coroutineScope.launch {
            try {
                val recentSearches = searchManager.getRecentSearches()
                val suggestions = recentSearches
                    .filter { it.query.contains(query, ignoreCase = true) }
                    .map { it.query }
                    .distinct()
                    .take(5)
                
                adapter.submitList(suggestions)
            } catch (e: Exception) {
                Timber.e(e, "Error loading search suggestions")
            }
        }
    }
    
    /**
     * Perform search with debounce
     */
    private fun debounceSearch(query: String, filters: ConversationSearchManager.SearchFilters) {
        searchJob?.cancel()
        
        searchJob = coroutineScope.launch {
            delay(SEARCH_DEBOUNCE_DELAY)
            performSearch(query, filters)
        }
    }
    
    /**
     * Perform actual search
     */
    private fun performSearch(query: String, filters: ConversationSearchManager.SearchFilters) {
        searchJob?.cancel()
        
        searchJob = coroutineScope.launch {
            try {
                searchCallback?.onSearchStarted()
                
                val results = searchManager.search(query, filters)
                
                withContext(Dispatchers.Main) {
                    searchCallback?.onSearchResults(results)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Search error: ${e.message}")
                withContext(Dispatchers.Main) {
                    searchCallback?.onSearchError(e.message ?: "Search failed")
                }
            }
        }
    }
    
    /**
     * Create highlighted text with search query
     */
    fun highlightSearchQuery(text: String, query: String): SpannableString {
        val spannableString = SpannableString(text)
        
        if (query.isNotEmpty()) {
            val queryLower = query.lowercase()
            val textLower = text.lowercase()
            
            var startIndex = 0
            while (true) {
                val index = textLower.indexOf(queryLower, startIndex)
                if (index == -1) break
                
                val endIndex = index + query.length
                
                // Highlight background
                spannableString.setSpan(
                    BackgroundColorSpan(ContextCompat.getColor(context, R.color.search_highlight_background)),
                    index, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                // Highlight text color
                spannableString.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.search_highlight_text)),
                    index, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                startIndex = endIndex
            }
        }
        
        return spannableString
    }
    
    /**
     * Cancel ongoing search
     */
    fun cancelSearch() {
        searchJob?.cancel()
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        searchJob?.cancel()
        searchCallback = null
    }
}

/**
 * Adapter for search suggestions
 */
private class SearchSuggestionsAdapter(
    private val onSuggestionClick: (String) -> Unit
) : ListAdapter<String, SearchSuggestionsAdapter.ViewHolder>(StringDiffCallback()) {
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val suggestionText: TextView = itemView.findViewById(R.id.suggestionText)
        val suggestionIcon: ImageView = itemView.findViewById(R.id.suggestionIcon)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_suggestion, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val suggestion = getItem(position)
        
        holder.suggestionText.text = suggestion
        holder.itemView.setOnClickListener {
            onSuggestionClick(suggestion)
        }
    }
}

/**
 * Adapter for search results
 */
private class SearchResultsAdapter(
    private val onConversationClick: (com.cyberflux.qwinai.model.Conversation) -> Unit,
    private val onMessageClick: (com.cyberflux.qwinai.model.ChatMessage, com.cyberflux.qwinai.model.Conversation) -> Unit
) : ListAdapter<ConversationSearchManager.SearchResult, SearchResultsAdapter.ViewHolder>(SearchResultDiffCallback()) {
    
    abstract class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    
    class ConversationViewHolder(itemView: View) : ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.conversationTitle)
        val snippetText: TextView = itemView.findViewById(R.id.conversationSnippet)
        val timestampText: TextView = itemView.findViewById(R.id.conversationTimestamp)
        val modelBadge: TextView = itemView.findViewById(R.id.modelBadge)
    }
    
    class MessageViewHolder(itemView: View) : ViewHolder(itemView) {
        val conversationTitle: TextView = itemView.findViewById(R.id.conversationTitle)
        val messageSnippet: TextView = itemView.findViewById(R.id.messageSnippet)
        val timestampText: TextView = itemView.findViewById(R.id.messageTimestamp)
        val userIndicator: ImageView = itemView.findViewById(R.id.userIndicator)
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ConversationSearchManager.SearchResult.ConversationResult -> 0
            is ConversationSearchManager.SearchResult.MessageResult -> 1
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            0 -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_search_result_conversation, parent, false)
                ConversationViewHolder(view)
            }
            1 -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_search_result_message, parent, false)
                MessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val result = getItem(position)) {
            is ConversationSearchManager.SearchResult.ConversationResult -> {
                bindConversationResult(holder as ConversationViewHolder, result)
            }
            is ConversationSearchManager.SearchResult.MessageResult -> {
                bindMessageResult(holder as MessageViewHolder, result)
            }
        }
    }
    
    private fun bindConversationResult(holder: ConversationViewHolder, result: ConversationSearchManager.SearchResult.ConversationResult) {
        holder.titleText.text = result.conversation.title
        holder.snippetText.text = result.snippet
        holder.timestampText.text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(result.timestamp))
        holder.modelBadge.text = result.conversation.modelId
        
        holder.itemView.setOnClickListener {
            onConversationClick(result.conversation)
        }
    }
    
    private fun bindMessageResult(holder: MessageViewHolder, result: ConversationSearchManager.SearchResult.MessageResult) {
        holder.conversationTitle.text = result.conversation.title
        holder.messageSnippet.text = result.snippet
        holder.timestampText.text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            .format(Date(result.timestamp))
        
        holder.userIndicator.setImageResource(
            if (result.message.isUser) R.drawable.ic_person else R.drawable.ic_auto_awesome
        )
        
        holder.itemView.setOnClickListener {
            onMessageClick(result.message, result.conversation)
        }
    }
}

/**
 * DiffUtil callback for strings
 */
private class StringDiffCallback : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
}

/**
 * DiffUtil callback for search results
 */
private class SearchResultDiffCallback : DiffUtil.ItemCallback<ConversationSearchManager.SearchResult>() {
    override fun areItemsTheSame(oldItem: ConversationSearchManager.SearchResult, newItem: ConversationSearchManager.SearchResult): Boolean {
        return when {
            oldItem is ConversationSearchManager.SearchResult.ConversationResult &&
            newItem is ConversationSearchManager.SearchResult.ConversationResult -> 
                oldItem.conversation.id == newItem.conversation.id
            
            oldItem is ConversationSearchManager.SearchResult.MessageResult &&
            newItem is ConversationSearchManager.SearchResult.MessageResult -> 
                oldItem.message.id == newItem.message.id
            
            else -> false
        }
    }
    
    override fun areContentsTheSame(oldItem: ConversationSearchManager.SearchResult, newItem: ConversationSearchManager.SearchResult): Boolean {
        return oldItem == newItem
    }
}