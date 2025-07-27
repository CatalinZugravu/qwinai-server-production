package com.cyberflux.qwinai.utils

import timber.log.Timber

/**
 * Simplified Grammar locator that disables Prism4j syntax highlighting.
 * We'll rely on the CodeSyntaxHighlighter's regex-based approach instead.
 * This avoids dependency issues with Nekogram Prism4j while still providing highlighting.
 */
class MyGrammarLocator {
    
    fun languages(): Set<String> {
        // Return empty set to disable Prism4j completely
        // CodeSyntaxHighlighter will handle all syntax highlighting
        Timber.d("MyGrammarLocator: Using CodeSyntaxHighlighter for highlighting")
        return emptySet()
    }
    
    fun grammar(prism4j: Any?, language: String) = null
}