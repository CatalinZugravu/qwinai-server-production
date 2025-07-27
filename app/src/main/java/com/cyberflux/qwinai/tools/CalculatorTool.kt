package com.cyberflux.qwinai.tools

import org.mariuszgromada.math.mxparser.Expression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import android.content.Context
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.regex.Pattern

/**
 * Advanced calculator tool that handles mathematical expressions and unit conversions
 */
class CalculatorTool(private val context: Context) : Tool {
    override val id: String = "calculator"
    override val name: String = "Calculator"
    override val description: String = "Performs mathematical calculations, solves equations, and handles unit conversions."

    // Regular expressions for detecting calculator queries
    private val calculationPatterns = listOf(
        Pattern.compile("\\bcalculate\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bcompute\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bsolve\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwhat is ([\\d\\s\\+\\-\\*\\/\\^\\(\\)\\%\\.]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([\\d\\s\\+\\-\\*\\/\\^\\(\\)\\%\\.]+)\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+[\\s\\+\\-\\*\\/\\^\\%]\\d+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bconvert\\b.*\\bto\\b", Pattern.CASE_INSENSITIVE)
    )

    override fun canHandle(message: String): Boolean {
        // Check if the message contains calculation patterns
        return calculationPatterns.any { it.matcher(message).find() }
    }

    override suspend fun execute(message: String, parameters: Map<String, Any>): ToolResult = withContext(Dispatchers.Default) {
        try {
            Timber.d("CalculatorTool: Executing with message: ${message.take(50)}...")

            // Extract the mathematical expression
            val expression = extractExpression(message)
            Timber.d("CalculatorTool: Extracted expression: $expression")

            if (expression.isBlank()) {
                return@withContext ToolResult.error(
                    "Could not extract a valid mathematical expression",
                    "I couldn't identify a clear mathematical expression to calculate. Please provide a more explicit calculation."
                )
            }

            // Check for unit conversion
            if (isUnitConversion(message)) {
                return@withContext handleUnitConversion(message)
            }

            // Evaluate the expression
            val result = evaluateExpression(expression)
            Timber.d("CalculatorTool: Evaluation result: $result")

            if (result.isNaN() || result.isInfinite()) {
                return@withContext ToolResult.error(
                    "Invalid calculation result: ${if (result.isNaN()) "Not a Number" else "Infinity"}",
                    "The calculation resulted in ${if (result.isNaN()) "Not a Number" else "Infinity"}. Please check your expression."
                )
            }

            // Format the result with appropriate precision
            val formattedResult = formatResult(result)

            return@withContext ToolResult.success(
                content = "Expression: $expression\nResult: $formattedResult",
                data = mapOf("expression" to expression, "result" to formattedResult),
                metadata = mapOf("operation" to "calculation")
            )
        } catch (e: Exception) {
            Timber.e(e, "CalculatorTool: Error executing calculator: ${e.message}")
            return@withContext ToolResult.error(
                "Calculation error: ${e.message}",
                "There was an error evaluating the expression: ${e.message}"
            )
        }
    }

    /**
     * Extract the mathematical expression from the message
     */
    private fun extractExpression(message: String): String {
        // Try various extraction patterns
        for (pattern in listOf(
            "\\bcalculate\\s+([\\d\\s\\+\\-\\*\\/\\^\\(\\)\\%\\.]+)",
            "\\bcompute\\s+([\\d\\s\\+\\-\\*\\/\\^\\(\\)\\%\\.]+)",
            "\\bsolve\\s+([\\d\\s\\+\\-\\*\\/\\^\\(\\)\\%\\.]+)",
            "\\bwhat is ([\\d\\s\\+\\-\\*\\/\\^\\(\\)\\%\\.]+)",
            "([\\d\\s\\+\\-\\*\\/\\^\\(\\)\\%\\.]+)\\s*=",
            "(\\d+[\\s\\+\\-\\*\\/\\^\\%]\\d+)"
        )) {
            val regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(message)
            if (matcher.find()) {
                return matcher.group(1).trim()
            }
        }

        // Fallback: try to find any sequence that looks like a calculation
        val calculationRegex = "[\\d\\+\\-\\*\\/\\^\\(\\)\\%\\.\\s]+".toRegex()
        val matches = calculationRegex.findAll(message)
        val longestMatch = matches.maxByOrNull { it.value.length }?.value?.trim()

        return longestMatch ?: ""
    }

    /**
     * Evaluate the mathematical expression
     */
    private fun evaluateExpression(expression: String): Double {
        // Use mXparser to evaluate the expression
        val mxExpression = Expression(expression.replace("%", "/100*"))
        return mxExpression.calculate()
    }

    /**
     * Format the result with appropriate precision
     */
    private fun formatResult(result: Double): String {
        // For integer results, don't show decimal places
        if (result.toLong().toDouble() == result) {
            return result.toLong().toString()
        }

        // Otherwise, use BigDecimal for proper rounding
        val bd = BigDecimal(result).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString()
    }

    /**
     * Check if the message is requesting a unit conversion
     */
    private fun isUnitConversion(message: String): Boolean {
        val conversionPattern = Pattern.compile("\\bconvert\\b.*\\bto\\b", Pattern.CASE_INSENSITIVE)
        return conversionPattern.matcher(message).find()
    }

    /**
     * Handle unit conversion requests
     */
    private suspend fun handleUnitConversion(message: String): ToolResult = withContext(Dispatchers.Default) {
        try {
            // Extract conversion parameters
            val conversionPattern = Pattern.compile("convert\\s+([\\d\\.]+)\\s+([a-zA-Z]+)\\s+to\\s+([a-zA-Z]+)", Pattern.CASE_INSENSITIVE)
            val matcher = conversionPattern.matcher(message)

            if (!matcher.find()) {
                return@withContext ToolResult.error(
                    "Invalid conversion format",
                    "I couldn't parse your conversion request. Please use format like 'convert 5 km to miles'."
                )
            }

            val value = matcher.group(1).toDoubleOrNull() ?: return@withContext ToolResult.error(
                "Invalid number for conversion",
                "I couldn't understand the number to convert. Please provide a valid number."
            )

            val fromUnit = matcher.group(2).lowercase()
            val toUnit = matcher.group(3).lowercase()

            // Perform the conversion
            val (convertedValue, success) = performConversion(value, fromUnit, toUnit)

            if (!success) {
                return@withContext ToolResult.error(
                    "Unsupported conversion",
                    "I don't know how to convert from $fromUnit to $toUnit. Please try a different conversion."
                )
            }

            val formattedResult = formatResult(convertedValue)

            return@withContext ToolResult.success(
                content = "$value $fromUnit = $formattedResult $toUnit",
                data = mapOf(
                    "value" to value,
                    "fromUnit" to fromUnit,
                    "toUnit" to toUnit,
                    "result" to formattedResult
                ),
                metadata = mapOf("operation" to "conversion")
            )
        } catch (e: Exception) {
            Timber.e(e, "CalculatorTool: Error in unit conversion: ${e.message}")
            return@withContext ToolResult.error(
                "Conversion error: ${e.message}",
                "There was an error performing the conversion: ${e.message}"
            )
        }
    }

    /**
     * Perform unit conversion
     * @return Pair(convertedValue, success)
     */
    private fun performConversion(value: Double, fromUnit: String, toUnit: String): Pair<Double, Boolean> {
        // Define conversion factors
        val conversions = mapOf(
            // Length
            "m" to mapOf("cm" to 100.0, "mm" to 1000.0, "km" to 0.001, "inch" to 39.3701, "ft" to 3.28084, "yd" to 1.09361, "mile" to 0.000621371),
            "cm" to mapOf("m" to 0.01, "mm" to 10.0, "km" to 0.00001, "inch" to 0.393701, "ft" to 0.0328084),
            "km" to mapOf("m" to 1000.0, "mile" to 0.621371, "ft" to 3280.84),
            "mile" to mapOf("km" to 1.60934, "m" to 1609.34, "ft" to 5280.0),
            "ft" to mapOf("m" to 0.3048, "cm" to 30.48, "inch" to 12.0, "yd" to 0.333333),

            // Weight
            "kg" to mapOf("g" to 1000.0, "lb" to 2.20462, "oz" to 35.274),
            "g" to mapOf("kg" to 0.001, "lb" to 0.00220462, "oz" to 0.035274),
            "lb" to mapOf("kg" to 0.453592, "g" to 453.592, "oz" to 16.0),

            // Temperature - special case handled separately

            // Volume
            "l" to mapOf("ml" to 1000.0, "gal" to 0.264172, "qt" to 1.05669, "cup" to 4.22675),
            "ml" to mapOf("l" to 0.001, "oz" to 0.033814),
            "gal" to mapOf("l" to 3.78541, "qt" to 4.0)
        )

        // Special case for temperature conversions
        if (fromUnit == "c" && toUnit == "f") {
            return Pair(value * 9/5 + 32, true)
        } else if (fromUnit == "f" && toUnit == "c") {
            return Pair((value - 32) * 5/9, true)
        } else if (fromUnit == "c" && toUnit == "k") {
            return Pair(value + 273.15, true)
        } else if (fromUnit == "k" && toUnit == "c") {
            return Pair(value - 273.15, true)
        } else if (fromUnit == "f" && toUnit == "k") {
            return Pair((value - 32) * 5/9 + 273.15, true)
        } else if (fromUnit == "k" && toUnit == "f") {
            return Pair((value - 273.15) * 9/5 + 32, true)
        }

        // Handle regular conversions
        val conversionFactor = conversions[fromUnit]?.get(toUnit)

        if (conversionFactor != null) {
            return Pair(value * conversionFactor, true)
        }

        // Try reverse conversion
        val reverseConversionFactor = conversions[toUnit]?.get(fromUnit)

        if (reverseConversionFactor != null) {
            return Pair(value / reverseConversionFactor, true)
        }

        return Pair(0.0, false)
    }
}