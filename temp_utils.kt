    
    // ==== Utility Methods for Comprehensive Markdown Support ====
    
    /**
     * Validate if a URL is a valid image URL.
     */
    private fun isValidImageUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            val isValidScheme = scheme in SAFE_PROTOCOLS
            val hasImageExtension = url.lowercase().matches(Regex(".*\.(jpg|jpeg|png|gif|bmp|webp|svg)(\?.*)?$"))
            isValidScheme && (hasImageExtension || url.contains("image") || url.contains("img"))
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Open image URL in external viewer or browser.
     */
    private fun openImageUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open image URL: $url")
            Toast.makeText(context, "Cannot open image", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Open URL in browser with security validation.
     */
    private fun openUrl(url: String) {
        try {
            if (securityManager.isUrlSafe(url)) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "URL blocked for security", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open URL: $url")
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
        }
    }

