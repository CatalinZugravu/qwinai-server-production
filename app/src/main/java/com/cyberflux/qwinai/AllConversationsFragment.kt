package com.cyberflux.qwinai

import android.widget.TextView
import androidx.core.view.isVisible

class AllConversationsFragment : BaseConversationsFragment() {

    override fun observeViewModel() {
        // Observe grouped conversations
        viewModel.groupedConversations.observe(viewLifecycleOwner) { groups: List<com.cyberflux.qwinai.model.ConversationGroup> ->
            groupedConversationAdapter.submitGroups(groups)
            emptyStateView.isVisible = groups.isEmpty()

            // Update empty state message
            view?.findViewById<TextView>(R.id.tvEmptyStateMessage)?.text =
                "No conversations found"
        }
    }
}