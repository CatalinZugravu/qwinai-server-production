package com.cyberflux.qwinai.utils

import timber.log.Timber

/**
 * Helper for testing markdown functionality
 */
object MarkdownTestHelper {
    
    fun getSampleMarkdownWithCode(): String {
        return """
# Markdown Test

Here's some **bold** and *italic* text.

## Code Blocks

Here's a JavaScript code block:

```javascript
function greet(name) {
    console.log("Hello, " + name + "!");
    return true;
}

greet("World");
```

And a Python example:

```python
def fibonacci(n):
    if n <= 1:
        return n
    return fibonacci(n-1) + fibonacci(n-2)

print(fibonacci(10))
```

## Features

- [x] Code blocks with syntax highlighting
- [x] Custom layout with copy button
- [ ] More features coming soon

> This is a blockquote with some important information.

| Language | Syntax Highlighting |
|----------|-------------------|
| JavaScript | ✅ |
| Python | ✅ |
| Kotlin | ✅ |

That's all folks!
        """.trimIndent()
    }
    
    fun logPerformanceTest(processor: UltraFastStreamingProcessor) {
        val stats = processor.getPerformanceStats()
        Timber.d("Markdown Performance Stats: $stats")
    }
}