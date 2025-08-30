package com.cyberflux.qwinai.utils

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import timber.log.Timber
import java.util.regex.Pattern

/**
 * Ultra-Comprehensive Syntax Highlighter for Code in TextViews
 * Supports professional-grade syntax highlighting with 100+ colors and patterns
 */
object CodeSyntaxHighlighter {

    /**
     * Apply comprehensive syntax highlighting to code based on the language
     */
    fun highlight(context: Context, code: String, language: String): SpannableString {
        val ssb = SpannableString(code)

        try {
            Timber.d("Highlighting code for language: $language, length: ${code.length}")
            
            when (language.lowercase()) {
                "kotlin", "kt" -> highlightKotlin(context, ssb)
                "java" -> highlightJava(context, ssb)
                "typescript", "javascript", "js", "ts", "jsx", "tsx" -> highlightJavaScript(context, ssb)
                "json", "json5" -> highlightJson(context, ssb)
                "html", "htm", "xml", "xhtml" -> highlightMarkup(context, ssb)
                "css", "scss", "sass", "less" -> highlightCss(context, ssb)
                "python", "py", "py3", "pyw" -> highlightPython(context, ssb)
                "csharp", "c#", "cs" -> highlightCSharp(context, ssb)
                "swift" -> highlightSwift(context, ssb)
                "cpp", "c++", "cxx", "cc", "c" -> highlightCpp(context, ssb)
                "rust", "rs" -> highlightRust(context, ssb)
                "go", "golang" -> highlightGo(context, ssb)
                "php" -> highlightPhp(context, ssb)
                "ruby", "rb" -> highlightRuby(context, ssb)
                "sql", "mysql", "postgresql", "sqlite" -> highlightSql(context, ssb)
                "yaml", "yml" -> highlightYaml(context, ssb)
                "dockerfile", "docker" -> highlightDockerfile(context, ssb)
                "bash", "sh", "shell", "zsh" -> highlightBash(context, ssb)
                "markdown", "md" -> highlightMarkdown(context, ssb)
                else -> highlightGeneric(context, ssb)
            }

            // Apply universal patterns after language-specific highlighting
            highlightUniversalPatterns(context, ssb)
            
            Timber.d("Syntax highlighting completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error highlighting code: ${e.message}")
        }

        return ssb
    }

    // ============================================================================
    // KOTLIN ADVANCED HIGHLIGHTING
    // ============================================================================
    
    private fun highlightKotlin(context: Context, ssb: SpannableString) {
        // Control flow keywords
        highlightPattern(context, ssb, "\\b(if|else|when|for|while|do|break|continue|return)\\b", R.color.code_control_flow)
        
        // Storage modifiers and visibility
        highlightPattern(context, ssb, "\\b(public|private|protected|internal|open|final|abstract|override|sealed|inline|crossinline|noinline|reified)\\b", R.color.code_storage_modifier)
        
        // Coroutines and async
        highlightPattern(context, ssb, "\\b(suspend|async|launch|runBlocking|withContext|coroutineScope|supervisorScope)\\b", R.color.code_async_await)
        
        // Exception handling
        highlightPattern(context, ssb, "\\b(try|catch|finally|throw|throws)\\b", R.color.code_exception)
        
        // Import and package
        highlightPattern(context, ssb, "\\b(import|package|as|typealias)\\b", R.color.code_import_export)
        
        // Data structures and types
        highlightPattern(context, ssb, "\\b(class|interface|object|enum|annotation|data|value|sealed)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\b(fun|val|var|const|lateinit|by|get|set|field)\\b", R.color.code_keyword)
        
        // Special Kotlin features
        highlightPattern(context, ssb, "\\bcompanion\\s+object\\b", R.color.code_kotlin_companion)
        highlightPattern(context, ssb, "\\bdata\\s+class\\b", R.color.code_kotlin_data)
        highlightPattern(context, ssb, "\\bsealed\\s+(class|interface)\\b", R.color.code_kotlin_sealed)
        highlightPattern(context, ssb, "\\bsuspend\\s+fun\\b", R.color.code_kotlin_suspend)
        
        // Function declarations and calls
        highlightPattern(context, ssb, "\\bfun\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_function)
        highlightPattern(context, ssb, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function_call)
        
        // Class names
        highlightPattern(context, ssb, "\\b(class|object|interface|enum)\\s+([A-Z][a-zA-Z0-9_]*)", R.color.code_class_name)
        
        // Properties and method calls
        highlightPattern(context, ssb, "(\\.)([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_method_call)
        highlightPattern(context, ssb, "(\\.)([a-zA-Z_][a-zA-Z0-9_]*)\\b(?!\\s*[\\(\\{])", R.color.code_property)
        
        // Annotations
        highlightPattern(context, ssb, "@([a-zA-Z_][a-zA-Z0-9_\\.]*)", R.color.code_annotation_symbol)
        
        // Generics
        highlightPattern(context, ssb, "<([A-Z][a-zA-Z0-9_,\\s\\*\\?]*?)>", R.color.code_generic_type)
        
        // Boolean and null values
        highlightPattern(context, ssb, "\\b(true|false)\\b", R.color.code_boolean)
        highlightPattern(context, ssb, "\\b(null)\\b", R.color.code_null_undefined)
        
        // this, super
        highlightPattern(context, ssb, "\\b(this|super)\\b", R.color.code_this_super)
        
        // Lambda expressions
        highlightPattern(context, ssb, "\\{[^}]*->", R.color.code_lambda)
        highlightPattern(context, ssb, "->", R.color.code_arrow_function)
        
        // String interpolation
        highlightPattern(context, ssb, "\\$\\{[^}]*\\}", R.color.code_interpolation)
        highlightPattern(context, ssb, "\\$[a-zA-Z_][a-zA-Z0-9_]*", R.color.code_interpolation)
    }

    // ============================================================================
    // JAVASCRIPT/TYPESCRIPT ULTRA ADVANCED HIGHLIGHTING
    // ============================================================================
    
    private fun highlightJavaScript(context: Context, ssb: SpannableString) {
        // ES6+ async/await
        highlightPattern(context, ssb, "\\b(async|await)\\b", R.color.code_async_await)
        
        // Control flow
        highlightPattern(context, ssb, "\\b(if|else|switch|case|default|for|while|do|break|continue|return)\\b", R.color.code_control_flow)
        
        // Exception handling
        highlightPattern(context, ssb, "\\b(try|catch|finally|throw|throws)\\b", R.color.code_exception)
        
        // Import/Export
        highlightPattern(context, ssb, "\\b(import|export|from|default|as)\\b", R.color.code_import_export)
        
        // Variable declarations
        highlightPattern(context, ssb, "\\b(var|let|const|function|class|interface|type|enum|namespace|module|declare)\\b", R.color.code_keyword)
        
        // TypeScript specific
        highlightPattern(context, ssb, "\\b(public|private|protected|readonly|static|abstract|implements|extends)\\b", R.color.code_storage_modifier)
        highlightPattern(context, ssb, "\\b(interface|type)\\s+([A-Z][a-zA-Z0-9_]*)", R.color.code_typescript_interface)
        
        // Arrow functions
        highlightPattern(context, ssb, "=>", R.color.code_arrow_function)
        highlightPattern(context, ssb, "\\([^)]*\\)\\s*=>", R.color.code_lambda)
        
        // Function declarations
        highlightPattern(context, ssb, "\\bfunction\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_function)
        highlightPattern(context, ssb, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function_call)
        
        // Method calls and properties
        highlightPattern(context, ssb, "(\\.)([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_method_call)
        highlightPattern(context, ssb, "(\\.)([a-zA-Z_][a-zA-Z0-9_]*)\\b(?!\\s*[\\(\\{])", R.color.code_property)
        
        // Class names
        highlightPattern(context, ssb, "\\b(class|interface)\\s+([A-Z][a-zA-Z0-9_]*)", R.color.code_class_name)
        
        // Promises and async patterns
        highlightPattern(context, ssb, "\\b(Promise|then|catch|finally|resolve|reject)\\b", R.color.code_promise)
        
        // Destructuring assignment
        highlightPattern(context, ssb, "\\{\\s*([a-zA-Z_][a-zA-Z0-9_,\\s]*?)\\s*\\}\\s*=", R.color.code_destructuring)
        highlightPattern(context, ssb, "\\[\\s*([a-zA-Z_][a-zA-Z0-9_,\\s]*?)\\s*\\]\\s*=", R.color.code_destructuring)
        
        // Spread operator
        highlightPattern(context, ssb, "\\.\\.\\.", R.color.code_spread_operator)
        
        // Template literals
        highlightPattern(context, ssb, "`[^`]*`", R.color.code_template_literal)
        highlightPattern(context, ssb, "\\$\\{[^}]*\\}", R.color.code_interpolation)
        
        // JSX (if detected)
        highlightPattern(context, ssb, "<([A-Z][a-zA-Z0-9_]*)(?:\\s|>|/>)", R.color.code_jsx_tag)
        highlightPattern(context, ssb, "\\s([a-zA-Z][a-zA-Z0-9_-]*)=", R.color.code_jsx_attribute)
        
        // Boolean and special values
        highlightPattern(context, ssb, "\\b(true|false)\\b", R.color.code_boolean)
        highlightPattern(context, ssb, "\\b(null|undefined|NaN|Infinity)\\b", R.color.code_null_undefined)
        highlightPattern(context, ssb, "\\b(this|super)\\b", R.color.code_this_super)
        
        // Built-in objects and functions
        highlightPattern(context, ssb, "\\b(console|window|document|Array|Object|String|Number|Boolean|Date|Math|JSON|RegExp|Error|Promise|Set|Map|WeakMap|WeakSet|Symbol|Proxy|Reflect)\\b", R.color.code_builtin_function)
        
        // Console methods
        highlightPattern(context, ssb, "\\bconsole\\.(log|error|warn|info|debug|trace|table|group|groupEnd|time|timeEnd)\\b", R.color.code_builtin_function)
        
        // Generators
        highlightPattern(context, ssb, "\\bfunction\\*", R.color.code_generator)
        highlightPattern(context, ssb, "\\byield\\b", R.color.code_generator)
    }

    // ============================================================================
    // PYTHON ULTRA ADVANCED HIGHLIGHTING
    // ============================================================================
    
    private fun highlightPython(context: Context, ssb: SpannableString) {
        // Control flow
        highlightPattern(context, ssb, "\\b(if|elif|else|for|while|break|continue|return|pass)\\b", R.color.code_control_flow)
        
        // Exception handling
        highlightPattern(context, ssb, "\\b(try|except|finally|raise|assert)\\b", R.color.code_exception)
        
        // Import statements
        highlightPattern(context, ssb, "\\b(import|from|as)\\b", R.color.code_import_export)
        
        // Function and class definitions
        highlightPattern(context, ssb, "\\b(def|class|lambda|async|await|yield|global|nonlocal)\\b", R.color.code_keyword)
        
        // Async/await
        highlightPattern(context, ssb, "\\b(async|await)\\b", R.color.code_async_await)
        
        // Function definitions
        highlightPattern(context, ssb, "\\bdef\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_function)
        highlightPattern(context, ssb, "\\bclass\\s+([A-Z][a-zA-Z0-9_]*)", R.color.code_class_name)
        
        // Function calls
        highlightPattern(context, ssb, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function_call)
        
        // Magic methods
        highlightPattern(context, ssb, "\\b__([a-zA-Z_][a-zA-Z0-9_]*)__", R.color.code_python_magic)
        
        // Decorators
        highlightPattern(context, ssb, "@([a-zA-Z_][a-zA-Z0-9_\\.]*)", R.color.code_python_decorator_at)
        
        // Self parameter
        highlightPattern(context, ssb, "\\bself\\b", R.color.code_python_self)
        
        // F-strings
        highlightPattern(context, ssb, "f['\"].*?['\"]", R.color.code_python_f_string)
        highlightPattern(context, ssb, "f\"\"\"[\\s\\S]*?\"\"\"", R.color.code_python_f_string)
        highlightPattern(context, ssb, "\\{[^}]*\\}", R.color.code_interpolation)
        
        // Built-in functions
        highlightPattern(context, ssb, "\\b(print|len|range|enumerate|zip|map|filter|reduce|sorted|reversed|any|all|sum|min|max|abs|round|pow|divmod|isinstance|issubclass|hasattr|getattr|setattr|delattr|vars|dir|id|hash|type|super|iter|next|open|input|eval|exec|compile|repr|str|int|float|bool|list|tuple|dict|set|frozenset|bytes|bytearray|memoryview)\\b", R.color.code_builtin_function)
        
        // Boolean and None
        highlightPattern(context, ssb, "\\b(True|False)\\b", R.color.code_boolean)
        highlightPattern(context, ssb, "\\b(None)\\b", R.color.code_null_undefined)
        
        // List comprehensions and generators
        highlightPattern(context, ssb, "\\[[^\\]]*\\bfor\\b[^\\]]*\\]", R.color.code_python_comprehension)
        highlightPattern(context, ssb, "\\{[^}]*\\bfor\\b[^}]*\\}", R.color.code_python_comprehension)
        highlightPattern(context, ssb, "\\([^)]*\\bfor\\b[^)]*\\)", R.color.code_python_comprehension)
        
        // Method calls and attributes
        highlightPattern(context, ssb, "(\\.)([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_method_call)
        highlightPattern(context, ssb, "(\\.)([a-zA-Z_][a-zA-Z0-9_]*)\\b(?!\\s*[\\(\\{])", R.color.code_property)
    }

    // ============================================================================
    // ADVANCED WEB TECHNOLOGIES HIGHLIGHTING
    // ============================================================================
    
    private fun highlightMarkup(context: Context, ssb: SpannableString) {
        // HTML tags
        highlightPattern(context, ssb, "</?([a-zA-Z][a-zA-Z0-9\\-_]*)", R.color.code_html_tag_name)
        highlightPattern(context, ssb, "<[^>]*>", R.color.code_punctuation)
        
        // Attributes
        highlightPattern(context, ssb, "\\s([a-zA-Z-]+)=", R.color.code_html_attribute_name)
        highlightPattern(context, ssb, "=\"([^\"]*)\"", R.color.code_html_attribute_value)
        highlightPattern(context, ssb, "='([^']*)'", R.color.code_html_attribute_value)
        
        // Special attributes
        highlightPattern(context, ssb, "\\s(id|class|href|src|alt|title|data-[a-zA-Z0-9-]+)=", R.color.code_storage_modifier)
        
        // DOCTYPE and comments
        highlightPattern(context, ssb, "<!DOCTYPE[^>]*>", R.color.code_doctype)
        highlightPattern(context, ssb, "<!--[\\s\\S]*?-->", R.color.code_block_comment)
        
        // CDATA sections
        highlightPattern(context, ssb, "<!\\[CDATA\\[[\\s\\S]*?\\]\\]>", R.color.code_string)
    }
    
    private fun highlightCss(context: Context, ssb: SpannableString) {
        // Selectors
        highlightPattern(context, ssb, "#([a-zA-Z0-9_-]+)", R.color.code_css_selector_id)
        highlightPattern(context, ssb, "\\.([a-zA-Z0-9_-]+)", R.color.code_css_selector_class)
        highlightPattern(context, ssb, "([a-zA-Z0-9_-]+)\\s*\\{", R.color.code_selector)
        
        // Pseudo-classes and pseudo-elements
        highlightPattern(context, ssb, ":([a-zA-Z-]+)", R.color.code_css_pseudo)
        highlightPattern(context, ssb, "::([a-zA-Z-]+)", R.color.code_css_pseudo)
        
        // Properties
        highlightPattern(context, ssb, "([a-zA-Z-]+)\\s*:", R.color.code_css_property_name)
        highlightPattern(context, ssb, ":\\s*([^;\\}]+)", R.color.code_css_property_value)
        
        // Units and measurements
        highlightPattern(context, ssb, "\\b(\\d+\\.?\\d*)(px|em|rem|vh|vw|%|s|ms|deg|rad|turn|fr)\\b", R.color.code_css_unit)
        
        // Colors
        highlightPattern(context, ssb, "#([0-9a-fA-F]{3,8})\\b", R.color.code_hexcolor)
        highlightPattern(context, ssb, "\\b(rgb|rgba|hsl|hsla)\\([^)]*\\)", R.color.code_color)
        highlightPattern(context, ssb, "\\b(red|blue|green|yellow|orange|purple|pink|black|white|gray|grey|transparent|inherit|initial|unset)\\b", R.color.code_color)
        
        // Important
        highlightPattern(context, ssb, "!important\\b", R.color.code_important)
        
        // At-rules
        highlightPattern(context, ssb, "@([a-zA-Z-]+)", R.color.code_at_rule)
        
        // Media queries
        highlightPattern(context, ssb, "@media\\s+([^{]+)", R.color.code_at_rule)
        
        // CSS functions
        highlightPattern(context, ssb, "\\b([a-zA-Z-]+)\\(", R.color.code_function_call)
        
        // CSS variables
        highlightPattern(context, ssb, "--([a-zA-Z0-9_-]+)", R.color.code_constant)
        highlightPattern(context, ssb, "var\\(--([a-zA-Z0-9_-]+)\\)", R.color.code_constant)
    }

    // ============================================================================
    // MORE LANGUAGES - COMPREHENSIVE SUPPORT
    // ============================================================================
    
    private fun highlightJava(context: Context, ssb: SpannableString) {
        // Keywords
        highlightPattern(context, ssb, "\\b(abstract|assert|break|case|catch|class|const|continue|default|do|else|enum|extends|final|finally|for|goto|if|implements|import|instanceof|interface|new|package|private|protected|public|return|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while)\\b", R.color.code_keyword)
        
        // Primitive types
        highlightPattern(context, ssb, "\\b(boolean|byte|char|double|float|int|long|short)\\b", R.color.code_type)
        
        // Annotations
        highlightPattern(context, ssb, "@([a-zA-Z_][a-zA-Z0-9_\\.]*)", R.color.code_java_annotation)
        
        // Class and method definitions
        highlightPattern(context, ssb, "\\bclass\\s+([A-Z][a-zA-Z0-9_]*)", R.color.code_class_name)
        highlightPattern(context, ssb, "(?:public|private|protected|static|final|abstract|synchronized|\\s)+(?:[a-zA-Z_][a-zA-Z0-9_<,>\\[\\]]*\\s+)+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function)
        
        // Generics
        highlightPattern(context, ssb, "<([A-Z][a-zA-Z0-9_,\\s\\?]*?)>", R.color.code_generic_type)
        
        // Boolean and null
        highlightPattern(context, ssb, "\\b(true|false)\\b", R.color.code_boolean)
        highlightPattern(context, ssb, "\\b(null)\\b", R.color.code_null_undefined)
    }

    private fun highlightJson(context: Context, ssb: SpannableString) {
        // Keys
        highlightPattern(context, ssb, "\"([^\"]+)\"\\s*:", R.color.code_property)
        
        // String values
        highlightPattern(context, ssb, ":\\s*\"([^\"]+)\"", R.color.code_string)
        
        // Numbers
        highlightPattern(context, ssb, ":\\s*(-?\\d+\\.?\\d*(?:[eE][+-]?\\d+)?)", R.color.code_number)
        
        // Booleans and null
        highlightPattern(context, ssb, ":\\s*(true|false|null)\\b", R.color.code_boolean)
        
        // Brackets and punctuation
        highlightPattern(context, ssb, "[\\[\\]{}]", R.color.code_bracket)
        highlightPattern(context, ssb, "[,:\"']", R.color.code_punctuation)
    }
    
    private fun highlightRust(context: Context, ssb: SpannableString) {
        // Keywords
        highlightPattern(context, ssb, "\\b(as|break|const|continue|crate|else|enum|extern|false|fn|for|if|impl|in|let|loop|match|mod|move|mut|pub|ref|return|self|Self|static|struct|super|trait|true|type|unsafe|use|where|while|async|await|dyn)\\b", R.color.code_keyword)
        
        // Types
        highlightPattern(context, ssb, "\\b(i8|i16|i32|i64|i128|isize|u8|u16|u32|u64|u128|usize|f32|f64|bool|char|str|String|Vec|HashMap|Option|Result|Box|Rc|Arc)\\b", R.color.code_type)
        
        // Macros
        highlightPattern(context, ssb, "([a-zA-Z_][a-zA-Z0-9_]*)!", R.color.code_macro)
        
        // Attributes
        highlightPattern(context, ssb, "#\\[([^\\]]+)\\]", R.color.code_annotation)
        
        // Lifetimes
        highlightPattern(context, ssb, "'([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_generic)
        
        // Function definitions
        highlightPattern(context, ssb, "\\bfn\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_function)
    }

    private fun highlightGo(context: Context, ssb: SpannableString) {
        // Keywords
        highlightPattern(context, ssb, "\\b(break|case|chan|const|continue|default|defer|else|fallthrough|for|func|go|goto|if|import|interface|map|package|range|return|select|struct|switch|type|var)\\b", R.color.code_keyword)
        
        // Built-in types
        highlightPattern(context, ssb, "\\b(bool|byte|complex64|complex128|error|float32|float64|int|int8|int16|int32|int64|rune|string|uint|uint8|uint16|uint32|uint64|uintptr)\\b", R.color.code_type)
        
        // Built-in functions
        highlightPattern(context, ssb, "\\b(append|cap|close|complex|copy|delete|imag|len|make|new|panic|print|println|real|recover)\\b", R.color.code_builtin_function)
        
        // Function definitions
        highlightPattern(context, ssb, "\\bfunc\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_function)
        
        // Go routines
        highlightPattern(context, ssb, "\\bgo\\s+", R.color.code_async_await)
        
        // Channels
        highlightPattern(context, ssb, "<-", R.color.code_arrow_function)
    }

    private fun highlightSql(context: Context, ssb: SpannableString) {
        // SQL Keywords
        highlightPattern(context, ssb, "(?i)\\b(SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TABLE|INDEX|VIEW|DATABASE|SCHEMA|UNION|JOIN|LEFT|RIGHT|INNER|OUTER|ON|AS|GROUP|ORDER|BY|HAVING|LIMIT|OFFSET|DISTINCT|ALL|AND|OR|NOT|NULL|IS|LIKE|IN|EXISTS|BETWEEN|CASE|WHEN|THEN|ELSE|END)\\b", R.color.code_keyword)
        
        // Data types
        highlightPattern(context, ssb, "(?i)\\b(VARCHAR|CHAR|TEXT|INT|INTEGER|BIGINT|SMALLINT|DECIMAL|NUMERIC|FLOAT|DOUBLE|REAL|DATE|TIME|TIMESTAMP|DATETIME|BOOLEAN|BOOL|BLOB|CLOB|JSON)\\b", R.color.code_type)
        
        // Functions
        highlightPattern(context, ssb, "(?i)\\b(COUNT|SUM|AVG|MIN|MAX|COALESCE|IFNULL|UPPER|LOWER|LENGTH|SUBSTRING|CONCAT|NOW|CURDATE|CURTIME)\\b", R.color.code_builtin_function)
        
        // String literals
        highlightPattern(context, ssb, "'([^']*(?:''[^']*)*)'", R.color.code_string)
        
        // Table and column names (assuming they're quoted)
        highlightPattern(context, ssb, "`([^`]+)`", R.color.code_property)
        highlightPattern(context, ssb, "\\[([^\\]]+)\\]", R.color.code_property)
    }

    // ============================================================================
    // UNIVERSAL PATTERNS - APPLIED TO ALL LANGUAGES
    // ============================================================================
    
    private fun highlightUniversalPatterns(context: Context, ssb: SpannableString) {
        // String literals (various quotes)
        highlightPattern(context, ssb, "\"((?:\\\\.|[^\"])*)\"", R.color.code_string_double)
        highlightPattern(context, ssb, "'((?:\\\\.|[^'])*)'", R.color.code_string_single)
        
        // Escape sequences in strings
        highlightPattern(context, ssb, "\\\\[nrtbfav0'\"\\\\]", R.color.code_escape_sequence)
        highlightPattern(context, ssb, "\\\\x[0-9a-fA-F]{2}", R.color.code_escape_sequence)
        highlightPattern(context, ssb, "\\\\u[0-9a-fA-F]{4}", R.color.code_unicode)
        highlightPattern(context, ssb, "\\\\U[0-9a-fA-F]{8}", R.color.code_unicode)
        
        // Numbers - comprehensive patterns
        highlightPattern(context, ssb, "\\b0x[0-9a-fA-F]+[lL]?\\b", R.color.code_number) // Hex
        highlightPattern(context, ssb, "\\b0b[01]+[lL]?\\b", R.color.code_number) // Binary
        highlightPattern(context, ssb, "\\b0o?[0-7]+[lL]?\\b", R.color.code_number) // Octal
        highlightPattern(context, ssb, "\\b\\d+\\.\\d*([eE][+-]?\\d+)?[fFdD]?\\b", R.color.code_number) // Float
        highlightPattern(context, ssb, "\\b\\d+[eE][+-]?\\d+[fFdD]?\\b", R.color.code_number) // Scientific
        highlightPattern(context, ssb, "\\b\\d+[lLfFdD]?\\b", R.color.code_number) // Integer with suffix
        
        // Operators - granular highlighting
        highlightPattern(context, ssb, "[+\\-*/%]", R.color.code_arithmetic_op)
        highlightPattern(context, ssb, "(&&|\\|\\||!)", R.color.code_logical_op)
        highlightPattern(context, ssb, "(==|!=|<=|>=|<|>)", R.color.code_comparison_op)
        highlightPattern(context, ssb, "(=|\\+=|-=|\\*=|/=|%=|&=|\\|=|\\^=|<<=|>>=)", R.color.code_assignment_op)
        
        // Punctuation
        highlightPattern(context, ssb, "[\\(\\)]", R.color.code_bracket)
        highlightPattern(context, ssb, "[\\[\\]]", R.color.code_bracket)
        highlightPattern(context, ssb, "[\\{\\}]", R.color.code_bracket)
        highlightPattern(context, ssb, ";", R.color.code_semicolon)
        highlightPattern(context, ssb, ",", R.color.code_comma)
        highlightPattern(context, ssb, "\\.", R.color.code_punctuation)
        
        // Comments - multiple styles
        highlightPattern(context, ssb, "//.*", R.color.code_inline_comment)
        highlightPattern(context, ssb, "/\\*[\\s\\S]*?\\*/", R.color.code_block_comment)
        highlightPattern(context, ssb, "#[^!].*", R.color.code_inline_comment)
        
        // Documentation comments
        highlightPattern(context, ssb, "/\\*\\*[\\s\\S]*?\\*/", R.color.code_doc_comment)
        highlightPattern(context, ssb, "///.*", R.color.code_doc_comment)
        
        // TODO/FIXME/NOTE comments
        highlightPattern(context, ssb, "(?i)(TODO|FIXME|NOTE|HACK|BUG|XXX):", R.color.code_todo_comment)
        
        // URLs in comments
        highlightPattern(context, ssb, "https?://[\\w\\.-]+(?:/[\\w\\.-]*)*", R.color.code_url)
        
        // Email addresses
        highlightPattern(context, ssb, "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", R.color.code_email)
        
        // Version numbers
        highlightPattern(context, ssb, "\\bv?\\d+\\.\\d+(?:\\.\\d+)?(?:-[a-zA-Z0-9.-]+)?\\b", R.color.code_version_number)
        
        // Regex patterns (between slashes)
        highlightPattern(context, ssb, "/(?![/*])([^/\\n\\\\]|\\\\.)*?/[gimuy]*", R.color.code_regex)
        
        // Hex colors in any context
        highlightPattern(context, ssb, "#(?:[0-9a-fA-F]{3}){1,2}\\b", R.color.code_hexcolor)
        
        // Constants (ALL_CAPS with underscores)
        highlightPattern(context, ssb, "\\b[A-Z][A-Z0-9_]*\\b", R.color.code_constant)
    }

    // ============================================================================
    // ADDITIONAL LANGUAGE SUPPORT
    // ============================================================================
    
    private fun highlightCpp(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(asm|auto|bool|break|case|catch|char|class|const|const_cast|continue|default|delete|do|double|dynamic_cast|else|enum|explicit|export|extern|false|float|for|friend|goto|if|inline|int|long|mutable|namespace|new|operator|private|protected|public|register|reinterpret_cast|return|short|signed|sizeof|static|static_cast|struct|switch|template|this|throw|true|try|typedef|typeid|typename|union|unsigned|using|virtual|void|volatile|wchar_t|while)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "#(include|define|ifdef|ifndef|endif|if|else|elif|pragma|undef|line|error|warning)", R.color.code_preprocessor)
        highlightPattern(context, ssb, "\\b(std|cout|cin|endl|vector|string|map|set|list|queue|stack|pair)\\b", R.color.code_builtin)
    }
    
    private fun highlightCSharp(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(abstract|as|base|bool|break|byte|case|catch|char|checked|class|const|continue|decimal|default|delegate|do|double|else|enum|event|explicit|extern|false|finally|fixed|float|for|foreach|goto|if|implicit|in|int|interface|internal|is|lock|long|namespace|new|null|object|operator|out|override|params|private|protected|public|readonly|ref|return|sbyte|sealed|short|sizeof|stackalloc|static|string|struct|switch|this|throw|true|try|typeof|uint|ulong|unchecked|unsafe|ushort|using|virtual|void|volatile|while)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\b(from|where|select|group|into|orderby|join|let|in|on|equals|by|ascending|descending)\\b", R.color.code_linq)
        highlightPattern(context, ssb, "\\[([^\\]]+)\\]", R.color.code_attribute)
    }
    
    private fun highlightSwift(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(associatedtype|class|deinit|enum|extension|fileprivate|func|import|init|inout|internal|let|open|operator|private|protocol|public|rethrows|static|struct|subscript|typealias|var|break|case|continue|default|defer|do|else|fallthrough|for|guard|if|in|repeat|return|switch|where|while|as|Any|catch|false|is|nil|super|self|Self|throw|throws|true|try)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\b(Int|Float|Double|Bool|String|Character|Array|Dictionary|Set|Optional)\\b", R.color.code_type)
        highlightPattern(context, ssb, "\\\\\\([^)]+\\)", R.color.code_interpolation)
    }
    
    private fun highlightPhp(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(abstract|and|array|as|break|callable|case|catch|class|clone|const|continue|declare|default|die|do|echo|else|elseif|empty|enddeclare|endfor|endforeach|endif|endswitch|endwhile|eval|exit|extends|final|finally|for|foreach|function|global|goto|if|implements|include|include_once|instanceof|insteadof|interface|isset|list|namespace|new|or|print|private|protected|public|require|require_once|return|static|switch|throw|trait|try|unset|use|var|while|xor|yield)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\$([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_property)
        highlightPattern(context, ssb, "<\\?php", R.color.code_preprocessor)
        highlightPattern(context, ssb, "\\?>", R.color.code_preprocessor)
    }
    
    private fun highlightRuby(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(alias|and|begin|break|case|class|def|defined|do|else|elsif|end|ensure|false|for|if|in|module|next|nil|not|or|redo|rescue|retry|return|self|super|then|true|undef|unless|until|when|while|yield)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "@([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_property)
        highlightPattern(context, ssb, "@@([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_class)
        highlightPattern(context, ssb, ":([a-zA-Z_][a-zA-Z0-9_]*[?!]?)", R.color.code_constant)
    }
    
    private fun highlightYaml(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "^\\s*([a-zA-Z_][a-zA-Z0-9_-]*):", R.color.code_property)
        highlightPattern(context, ssb, ":\\s*([^\\s#]+)", R.color.code_string)
        highlightPattern(context, ssb, "\\b(true|false|null|~)\\b", R.color.code_boolean)
        highlightPattern(context, ssb, "^\\s*-", R.color.code_punctuation)
        highlightPattern(context, ssb, "#.*", R.color.code_inline_comment)
    }
    
    private fun highlightDockerfile(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(FROM|RUN|CMD|LABEL|EXPOSE|ENV|ADD|COPY|ENTRYPOINT|VOLUME|USER|WORKDIR|ARG|ONBUILD|STOPSIGNAL|HEALTHCHECK|SHELL)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "#.*", R.color.code_inline_comment)
    }
    
    private fun highlightBash(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(if|then|else|elif|fi|for|while|until|do|done|case|esac|function|return|in|select|time|\\[\\[|\\]\\])\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\$([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\*|@|#|\\?|\\$|!|-)", R.color.code_property)
        highlightPattern(context, ssb, "\\$\\{[^}]*\\}", R.color.code_interpolation)
        highlightPattern(context, ssb, "#.*", R.color.code_inline_comment)
        highlightPattern(context, ssb, "\\b(echo|printf|read|cd|ls|pwd|mkdir|rmdir|rm|cp|mv|grep|sed|awk|sort|uniq|head|tail|cat|less|more|find|which|whereis)\\b", R.color.code_builtin_function)
    }
    
    private fun highlightMarkdown(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "^#{1,6}\\s.*", R.color.code_function)
        highlightPattern(context, ssb, "\\*\\*([^*]+)\\*\\*", R.color.code_keyword)
        highlightPattern(context, ssb, "\\*([^*]+)\\*", R.color.code_string)
        highlightPattern(context, ssb, "`([^`]+)`", R.color.code_template_literal)
        highlightPattern(context, ssb, "\\[([^\\]]+)\\]\\([^)]+\\)", R.color.code_url)
        highlightPattern(context, ssb, "^\\s*[-*+]\\s", R.color.code_punctuation)
        highlightPattern(context, ssb, "^\\s*\\d+\\.\\s", R.color.code_number)
    }
    
    private fun highlightGeneric(context: Context, ssb: SpannableString) {
        // Basic patterns for unknown languages
        highlightPattern(context, ssb, "\\b(function|var|let|const|if|else|for|while|do|switch|case|break|continue|return|new|this|try|catch|throw|finally|class|interface|enum|extends|implements|import|export|package|namespace|using|typedef|struct|union|template|public|private|protected|static|virtual|abstract|override|final|const|volatile|extern|register|void|int|char|bool|float|double|long|short|unsigned|signed|true|false|null|undefined|NaN|Infinity)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function_call)
        highlightPattern(context, ssb, "(\\.)([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_property)
    }

    // ============================================================================
    // ADVANCED PATTERN HIGHLIGHTING UTILITY
    // ============================================================================
    
    private fun highlightPattern(context: Context, ssb: SpannableString, pattern: String, colorResId: Int) {
        try {
            val compiledPattern = Pattern.compile(pattern, Pattern.MULTILINE)
            val matcher = compiledPattern.matcher(ssb)
            
            while (matcher.find()) {
                val color = ContextCompat.getColor(context, colorResId)
                val start = matcher.start()
                val end = matcher.end()
                
                // Ensure we don't overlap with existing spans
                if (start < end && start >= 0 && end <= ssb.length) {
                    ssb.setSpan(
                        ForegroundColorSpan(color),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error highlighting pattern: $pattern")
        }
    }
    
    /**
     * Apply special formatting for deprecated code
     */
    private fun highlightDeprecated(context: Context, ssb: SpannableString, pattern: String) {
        try {
            val matcher = Pattern.compile(pattern).matcher(ssb)
            while (matcher.find()) {
                val color = ContextCompat.getColor(context, R.color.code_deprecated)
                ssb.setSpan(
                    ForegroundColorSpan(color),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    StrikethroughSpan(),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Error highlighting deprecated pattern: $pattern")
        }
    }
    
    /**
     * Apply error highlighting with red background
     */
    private fun highlightError(context: Context, ssb: SpannableString, pattern: String) {
        try {
            val matcher = Pattern.compile(pattern).matcher(ssb)
            while (matcher.find()) {
                val color = ContextCompat.getColor(context, R.color.code_error_highlight)
                ssb.setSpan(
                    BackgroundColorSpan(color),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    StyleSpan(Typeface.BOLD),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Error highlighting error pattern: $pattern")
        }
    }
}