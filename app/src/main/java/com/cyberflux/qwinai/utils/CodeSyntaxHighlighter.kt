package com.cyberflux.qwinai.utils

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import timber.log.Timber
import java.util.regex.Pattern

/**
 * Utility class for syntax highlighting of code in TextViews
 */
object CodeSyntaxHighlighter {

    /**
     * Apply syntax highlighting to code based on the language
     */
    fun highlight(context: Context, code: String, language: String): SpannableString {
        val ssb = SpannableString(code)

        try {
            when (language.lowercase()) {
                "kotlin" -> highlightKotlin(context, ssb)
                "java" -> highlightJava(context, ssb)
                "typescript", "javascript", "js", "ts" -> highlightJavaScript(context, ssb)
                "json" -> highlightJson(context, ssb)
                "html", "xml" -> highlightMarkup(context, ssb)
                "css" -> highlightCss(context, ssb)
                "python", "py" -> highlightPython(context, ssb)
                "csharp", "c#" -> highlightCSharp(context, ssb)
                "swift" -> highlightSwift(context, ssb)
                else -> highlightBasic(context, ssb)
            }

            // Apply common highlighting
            highlightCommonElements(context, ssb)
        } catch (e: Exception) {
            Timber.e(e, "Error highlighting code: ${e.message}")
        }

        return ssb
    }

    private fun highlightKotlin(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(fun|val|var|if|else|when|for|while|do|break|continue|return|class|interface|object|companion|override|private|protected|public|internal|import|package|as|is|in|out|get|set|by|constructor|init|where|data|sealed|enum|annotation|vararg|reified|tailrec|operator|infix|typealias|suspend|yield|this|super|lateinit|const)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\bfun\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_function)
        highlightPattern(context, ssb, "\\bclass\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_class)
        highlightPattern(context, ssb, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function)
        highlightPattern(context, ssb, "(\\.)([a-zA-Z_][a-zA-Z0-9_]*)\\b(?!\\s*\\()", R.color.code_property)
        highlightPattern(context, ssb, "@([a-zA-Z_][a-zA-Z0-9_\\.]*)", R.color.code_annotation)
        highlightPattern(context, ssb, "\\b(true|false|null)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "<([A-Za-z0-9_,\\s]+)>", R.color.code_generic)
    }

    private fun highlightJava(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\b(public|private|protected|static|final|abstract|synchronized|volatile|transient|native)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\bclass\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_class)
        highlightPattern(context, ssb, "(?:public|private|protected|static|final|\\s)+(?:[a-zA-Z_][a-zA-Z0-9_]*\\s+)+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function)
        highlightPattern(context, ssb, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function)
        highlightPattern(context, ssb, "\\b(true|false|null)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "@([a-zA-Z_][a-zA-Z0-9_\\.]*)", R.color.code_annotation)
    }

    // Add all other highlighting methods...
    private fun highlightJavaScript(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(async|await|break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|from|function|if|import|in|instanceof|let|new|of|return|super|switch|this|throw|try|typeof|var|void|while|with|yield)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\b(abstract|as|declare|enum|implements|interface|module|namespace|private|protected|public|readonly|static|type)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "=>", R.color.code_keyword)
        highlightPattern(context, ssb, "\\bfunction\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_function)
        highlightPattern(context, ssb, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function)
        highlightPattern(context, ssb, "(\\.)([a-zA-Z_][a-zA-Z0-9_]*)\\b(?!\\s*\\()", R.color.code_property)
        highlightPattern(context, ssb, "`[^`]*`", R.color.code_string)
        highlightPattern(context, ssb, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*:", R.color.code_property)
    }

    private fun highlightJson(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\"([^\"]+)\"\\s*:", R.color.code_property)
        highlightPattern(context, ssb, ":\\s*\"([^\"]+)\"", R.color.code_string)
        highlightPattern(context, ssb, ":\\s*(\\d+\\.?\\d*)", R.color.code_number)
        highlightPattern(context, ssb, ":\\s*(true|false|null)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "[\\[\\]{}]", R.color.code_punctuation)
    }

    private fun highlightMarkup(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "<[^>]*>", R.color.code_punctuation)
        highlightPattern(context, ssb, "</?([a-zA-Z][a-zA-Z0-9\\-_]*)", R.color.code_tag)
        highlightPattern(context, ssb, "\\s([a-zA-Z-]+)=", R.color.code_attribute)
        highlightPattern(context, ssb, "=\"([^\"]*)\"", R.color.code_string)
        highlightPattern(context, ssb, "='([^']*)'", R.color.code_string)
        highlightPattern(context, ssb, "<!DOCTYPE[^>]*>", R.color.code_doctype)
        highlightPattern(context, ssb, "<!--[\\s\\S]*?-->", R.color.code_comment)
    }

    private fun highlightCss(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "([.#]?[a-zA-Z0-9_-]+)\\s*\\{", R.color.code_selector)
        highlightPattern(context, ssb, "\\{[^}]*\\b([a-zA-Z-]+)\\s*:", R.color.code_property)
        highlightPattern(context, ssb, ":\\s*([^;\\}]+)", R.color.code_value)
        highlightPattern(context, ssb, "\\b(\\d+)(px|em|rem|vh|vw|%|s|ms|deg)\\b", R.color.code_number)
        highlightPattern(context, ssb, "#([0-9a-fA-F]{3,6})\\b", R.color.code_color)
        highlightPattern(context, ssb, "!important\\b", R.color.code_important)
        highlightPattern(context, ssb, "@([a-zA-Z-]+)\\b", R.color.code_at_rule)
    }

    private fun highlightPython(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(and|as|assert|async|await|break|class|continue|def|del|elif|else|except|finally|for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|with|yield)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\b(abs|all|any|ascii|bin|bool|bytearray|bytes|callable|chr|classmethod|compile|complex|delattr|dict|dir|divmod|enumerate|eval|exec|filter|float|format|frozenset|getattr|globals|hasattr|hash|help|hex|id|input|int|isinstance|issubclass|iter|len|list|locals|map|max|memoryview|min|next|object|oct|open|ord|pow|print|property|range|repr|reversed|round|set|setattr|slice|sorted|staticmethod|str|sum|super|tuple|type|vars|zip|__import__)\\b", R.color.code_builtin)
        highlightPattern(context, ssb, "\\bdef\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_function)
        highlightPattern(context, ssb, "\\bclass\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_class)
        highlightPattern(context, ssb, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function)
        highlightPattern(context, ssb, "\\bself\\b", R.color.code_self)
        highlightPattern(context, ssb, "@([a-zA-Z_][a-zA-Z0-9_\\.]*)", R.color.code_decorator)
        highlightPattern(context, ssb, "f['\"].*?['\"]", R.color.code_string)
    }

    private fun highlightCSharp(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(abstract|as|base|bool|break|byte|case|catch|char|checked|class|const|continue|decimal|default|delegate|do|double|else|enum|event|explicit|extern|false|finally|fixed|float|for|foreach|goto|if|implicit|in|int|interface|internal|is|lock|long|namespace|new|null|object|operator|out|override|params|private|protected|public|readonly|ref|return|sbyte|sealed|short|sizeof|stackalloc|static|string|struct|switch|this|throw|true|try|typeof|uint|ulong|unchecked|unsafe|ushort|using|virtual|void|volatile|while)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\b(from|where|select|group|into|orderby|join|let|in|on|equals|by|ascending|descending)\\b", R.color.code_linq)
        highlightPattern(context, ssb, "\\bclass\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_class)
        highlightPattern(context, ssb, "(?:public|private|protected|internal|static|virtual|override|\\s)+(?:[a-zA-Z_][a-zA-Z0-9_<,>]*\\s+)+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function)
        highlightPattern(context, ssb, "\\[([^\\]]+)\\]", R.color.code_attribute)
        highlightPattern(context, ssb, "#\\w+", R.color.code_preprocessor)
        highlightPattern(context, ssb, "<([A-Za-z0-9_,\\s]+)>", R.color.code_generic)
    }

    private fun highlightSwift(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(associatedtype|class|deinit|enum|extension|fileprivate|func|import|init|inout|internal|let|open|operator|private|protocol|public|rethrows|static|struct|subscript|typealias|var|break|case|continue|default|defer|do|else|fallthrough|for|guard|if|in|repeat|return|switch|where|while|as|Any|catch|false|is|nil|super|self|Self|throw|throws|true|try|_|#available|#colorLiteral|#column|#else|#elseif|#endif|#file|#fileLiteral|#function|#if|#imageLiteral|#line|#selector|#sourceLocation|associativity|convenience|dynamic|didSet|final|get|infix|indirect|lazy|left|mutating|none|nonmutating|optional|override|postfix|precedence|prefix|Protocol|required|right|set|Type|unowned|weak|willSet)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\b(Int|Float|Double|Bool|String|Character|Array|Dictionary|Set)\\b", R.color.code_type)
        highlightPattern(context, ssb, "\\bfunc\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_function)
        highlightPattern(context, ssb, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function)
        highlightPattern(context, ssb, "(\\.)([a-zA-Z_][a-zA-Z0-9_]*)\\b(?!\\s*\\()", R.color.code_property)
        highlightPattern(context, ssb, "\\\\\\([^)]+\\)", R.color.code_interpolation)
    }

    private fun highlightBasic(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\\b(function|var|let|const|if|else|for|while|do|switch|case|break|continue|return|new|this|try|catch|throw|finally|class|interface|enum|extends|implements|import|export|package|namespace|using|typedef|struct|union|template|public|private|protected|static|virtual|abstract|override|final|const|volatile|extern|register|void|int|char|bool|float|double|long|short|unsigned|signed|size_t|true|false|null|undefined|NaN|Infinity)\\b", R.color.code_keyword)
        highlightPattern(context, ssb, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(", R.color.code_function)
        highlightPattern(context, ssb, "(\\.)([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_property)
        highlightPattern(context, ssb, "\\b(function|def|fun|func|method|procedure)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_function)
        highlightPattern(context, ssb, "\\b(class|interface|trait|struct|enum)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", R.color.code_class)
    }

    private fun highlightCommonElements(context: Context, ssb: SpannableString) {
        highlightPattern(context, ssb, "\"(\\\\.|[^\"])*\"", R.color.code_string)
        highlightPattern(context, ssb, "'(\\\\.|[^'])*'", R.color.code_string)
        highlightPattern(context, ssb, "\\b(\\d+\\.?\\d*f?|0x[a-fA-F0-9]+)\\b", R.color.code_number)
        highlightPattern(context, ssb, "//.*", R.color.code_comment)
        highlightPattern(context, ssb, "/\\*[\\s\\S]*?\\*/", R.color.code_comment)
        highlightPattern(context, ssb, "#.*", R.color.code_comment)
        highlightPattern(context, ssb, "[+\\-*/%=&|^~<>!]", R.color.code_operator)
        highlightPattern(context, ssb, "[(){}\\[\\]:;,.]", R.color.code_punctuation)
    }

    private fun highlightPattern(context: Context, ssb: SpannableString, pattern: String, colorResId: Int) {
        try {
            val matcher = Pattern.compile(pattern).matcher(ssb)
            while (matcher.find()) {
                val color = ContextCompat.getColor(context, colorResId)
                ssb.setSpan(
                    ForegroundColorSpan(color),
                    matcher.start(),
                    matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error highlighting pattern: $pattern")
        }
    }
}