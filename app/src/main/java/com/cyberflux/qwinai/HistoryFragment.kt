package com.cyberflux.qwinai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import com.cyberflux.qwinai.databinding.FragmentHistoryBinding
import com.google.android.material.tabs.TabLayoutMediator

/**
 * History fragment that displays conversation tabs for All and Saved conversations
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConversationsViewModel by activityViewModels()
    private lateinit var pagerAdapter: ConversationPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSearchView()
        setupViewPagerAndTabs()
        setupSettingsButton()
        setupExpandButton()
        observeTotalConversations()
    }

    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            // Animate the button when clicked
            binding.btnSettings.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    binding.btnSettings.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()

                    // Launch SettingsActivity
                    val intent = Intent(requireContext(), SettingsActivity::class.java)
                    startActivity(intent)
                    requireActivity().overridePendingTransition(0, 0) // ULTRAFAST: No transition animation
                }
                .start()

            // Optional: Add haptic feedback if available in the fragment
            (requireActivity() as? StartActivity)?.provideHapticFeedback()
        }
    }

    private fun setupExpandButton() {
        binding.btnExpandConversations.setOnClickListener {
            // Animate the button when clicked
            binding.btnExpandConversations.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    binding.btnExpandConversations.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()

                    // Determine if we're on saved tab (tab 1) or all tab (tab 0)
                    val currentTab = binding.viewPager.currentItem
                    val showSavedOnly = currentTab == 1
                    
                    // Launch AllConversationsActivity with appropriate mode
                    val intent = AllConversationsActivity.createIntent(
                        context = requireContext(),
                        showSavedOnly = showSavedOnly
                    )
                    startActivity(intent)
                    requireActivity().overridePendingTransition(0, 0) // ULTRAFAST: No transition animation
                }
                .start()

            // Optional: Add haptic feedback if available in the fragment
            (requireActivity() as? StartActivity)?.provideHapticFeedback()
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { viewModel.searchConversations(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { viewModel.searchConversations(it) }
                return true
            }
        })

        // Clear button listener
        binding.searchView.setOnCloseListener {
            viewModel.searchConversations("")
            false
        }
    }

    private fun setupViewPagerAndTabs() {
        // Setup ViewPager
        pagerAdapter = ConversationPagerAdapter(requireActivity())
        binding.viewPager.adapter = pagerAdapter

        // Connect TabLayout with ViewPager
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "All Conversations"
                1 -> "Saved"
                else -> null
            }
        }.attach()
    }

    private fun observeTotalConversations() {
        // Observe all conversations for counts
        lifecycleScope.launch {
            viewModel.allConversations.collectLatest { conversations ->
                val savedCount = conversations.count { it.saved }
                val totalCount = conversations.size

            // Update message with counts
            val messageText = "Conversations will be automatically deleted after 30 days.\n" +
                    "Saved: $savedCount  •  Total: $totalCount"
            binding.tvMessage.text = messageText

            // Update tab badge for saved conversations if Material tabs are used
            binding.tabLayout.getTabAt(1)?.let { tab ->
                // Only set badge if we're using MaterialTabs that support badges
                try {
                    if (savedCount > 0) {
                        val badge = tab.orCreateBadge
                        badge.number = savedCount
                        badge.isVisible = true
                    } else {
                        tab.removeBadge()
                    }
                } catch (e: Exception) {
                    // If badges aren't supported in this version, just ignore
                }
            }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadConversations()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}