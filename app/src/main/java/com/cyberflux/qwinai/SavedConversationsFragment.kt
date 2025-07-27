package com.cyberflux.qwinai

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible

class SavedConversationsFragment : BaseConversationsFragment() {

    override fun observeViewModel() {
        // Observe saved grouped conversations
        viewModel.savedGroupedConversations.observe(viewLifecycleOwner) { groups: List<com.cyberflux.qwinai.model.ConversationGroup> ->
            groupedConversationAdapter.submitGroups(groups)
            emptyStateView.isVisible = groups.isEmpty()

            // Update empty state message
            view?.findViewById<TextView>(R.id.tvEmptyStateMessage)?.text =
                "No saved conversations found"
        }
    }
}