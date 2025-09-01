package com.cyberflux.qwinai.utils

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.model.AIModel
import com.cyberflux.qwinai.model.AIParameters
import com.cyberflux.qwinai.model.ParameterPreset
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import timber.log.Timber

/**
 * Comprehensive AI Parameters Dialog
 * Adapts parameters based on selected AI model and provides intuitive UI
 */
class AIParametersDialog(
    private val context: Context,
    private val currentModel: AIModel,
    private val currentParameters: AIParameters,
    private val onParametersChanged: (AIParameters) -> Unit
) {

    private var dialog: Dialog? = null
    private var workingParameters = currentParameters.adaptForModel(currentModel.id)
    
    // UI Components
    private lateinit var tvCurrentModel: TextView
    private lateinit var switchAdvanced: MaterialSwitch
    private lateinit var cardAdvancedParams: View
    
    // Preset buttons
    private lateinit var btnBalanced: MaterialButton
    private lateinit var btnCreative: MaterialButton
    private lateinit var btnPrecise: MaterialButton
    private lateinit var btnDeterministic: MaterialButton
    private lateinit var btnExperimental: MaterialButton
    private var currentSelectedPreset: MaterialButton? = null
    
    // Core Parameter Controls
    private lateinit var sliderTemperature: Slider
    private lateinit var tvTemperatureValue: TextView
    private lateinit var sliderTopP: Slider
    private lateinit var tvTopPValue: TextView
    private lateinit var sliderMaxTokens: Slider
    private lateinit var tvMaxTokensValue: TextView
    
    // Advanced Parameter Controls
    private lateinit var layoutTopK: LinearLayout
    private lateinit var sliderTopK: Slider
    private lateinit var tvTopKValue: TextView
    private lateinit var layoutRepetitionPenalty: LinearLayout
    private lateinit var sliderRepetitionPenalty: Slider
    private lateinit var tvRepetitionPenaltyValue: TextView
    private lateinit var layoutFrequencyPenalty: LinearLayout
    private lateinit var sliderFrequencyPenalty: Slider
    private lateinit var tvFrequencyPenaltyValue: TextView
    private lateinit var layoutPresencePenalty: LinearLayout
    private lateinit var sliderPresencePenalty: Slider
    private lateinit var tvPresencePenaltyValue: TextView
    private lateinit var switchSeed: MaterialSwitch
    private lateinit var layoutSeedInput: View
    private lateinit var etSeed: TextInputEditText

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_parameters, null)
        initializeViews(view)
        setupParametersForModel()
        setupListeners()
        updateUIFromParameters()
        
        dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()
            
        dialog?.show()
    }

    private fun initializeViews(view: View) {
        // Header
        tvCurrentModel = view.findViewById(R.id.tvCurrentModel)
        
        // Preset buttons
        btnBalanced = view.findViewById(R.id.btnBalanced)
        btnCreative = view.findViewById(R.id.btnCreative)
        btnPrecise = view.findViewById(R.id.btnPrecise)
        btnDeterministic = view.findViewById(R.id.btnDeterministic)
        btnExperimental = view.findViewById(R.id.btnExperimental)
        
        // Set initial selection
        currentSelectedPreset = btnBalanced
        
        // Advanced toggle
        switchAdvanced = view.findViewById(R.id.switchAdvanced)
        cardAdvancedParams = view.findViewById(R.id.cardAdvancedParams)
        
        // Core parameters
        sliderTemperature = view.findViewById(R.id.sliderTemperature)
        tvTemperatureValue = view.findViewById(R.id.tvTemperatureValue)
        sliderTopP = view.findViewById(R.id.sliderTopP)
        tvTopPValue = view.findViewById(R.id.tvTopPValue)
        sliderMaxTokens = view.findViewById(R.id.sliderMaxTokens)
        tvMaxTokensValue = view.findViewById(R.id.tvMaxTokensValue)
        
        // Advanced parameters
        layoutTopK = view.findViewById(R.id.layoutTopK)
        sliderTopK = view.findViewById(R.id.sliderTopK)
        tvTopKValue = view.findViewById(R.id.tvTopKValue)
        layoutRepetitionPenalty = view.findViewById(R.id.layoutRepetitionPenalty)
        sliderRepetitionPenalty = view.findViewById(R.id.sliderRepetitionPenalty)
        tvRepetitionPenaltyValue = view.findViewById(R.id.tvRepetitionPenaltyValue)
        layoutFrequencyPenalty = view.findViewById(R.id.layoutFrequencyPenalty)
        sliderFrequencyPenalty = view.findViewById(R.id.sliderFrequencyPenalty)
        tvFrequencyPenaltyValue = view.findViewById(R.id.tvFrequencyPenaltyValue)
        layoutPresencePenalty = view.findViewById(R.id.layoutPresencePenalty)
        sliderPresencePenalty = view.findViewById(R.id.sliderPresencePenalty)
        tvPresencePenaltyValue = view.findViewById(R.id.tvPresencePenaltyValue)
        switchSeed = view.findViewById(R.id.switchSeed)
        layoutSeedInput = view.findViewById(R.id.layoutSeedInput)
        etSeed = view.findViewById(R.id.etSeed)
        
        // Action buttons
        view.findViewById<View>(R.id.btnResetToDefaults).setOnClickListener {
            resetToDefaults()
        }
        view.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog?.dismiss()
        }
        view.findViewById<View>(R.id.btnApply).setOnClickListener {
            applyParameters()
        }
        
        // Info buttons
        setupInfoButtons(view)
    }

    private fun setupParametersForModel() {
        tvCurrentModel.text = "Current Model: ${currentModel.displayName}"
        
        // Adapt parameters for the current model
        workingParameters = workingParameters.adaptForModel(currentModel.id)
        
        // Configure parameter ranges based on model
        val modelId = currentModel.id.lowercase()
        
        when {
            modelId.contains("gpt") || modelId.contains("o1") || modelId.contains("chatgpt") -> {
                // OpenAI models
                configureSlider(sliderTemperature, 0.0f, 2.0f, 0.1f)
                configureSlider(sliderTopP, 0.0f, 1.0f, 0.05f)
                layoutTopK.visibility = View.GONE // OpenAI doesn't use top-k
                layoutRepetitionPenalty.visibility = View.GONE
                layoutFrequencyPenalty.visibility = View.VISIBLE
                layoutPresencePenalty.visibility = View.VISIBLE
                configureSlider(sliderFrequencyPenalty, -2.0f, 2.0f, 0.1f)
                configureSlider(sliderPresencePenalty, -2.0f, 2.0f, 0.1f)
            }
            
            modelId.contains("claude") -> {
                // Anthropic Claude
                configureSlider(sliderTemperature, 0.0f, 1.0f, 0.1f)
                configureSlider(sliderTopP, 0.0f, 1.0f, 0.05f)
                configureSlider(sliderTopK, 1f, 40f, 1f)
                layoutTopK.visibility = View.VISIBLE
                layoutRepetitionPenalty.visibility = View.VISIBLE
                layoutFrequencyPenalty.visibility = View.GONE
                layoutPresencePenalty.visibility = View.GONE
            }
            
            modelId.contains("gemini") || modelId.contains("palm") || modelId.contains("gemma") -> {
                // Google models
                configureSlider(sliderTemperature, 0.0f, 1.0f, 0.1f)
                configureSlider(sliderTopP, 0.0f, 1.0f, 0.05f)
                configureSlider(sliderTopK, 1f, 40f, 1f)
                layoutTopK.visibility = View.VISIBLE
                layoutRepetitionPenalty.visibility = View.VISIBLE
                layoutFrequencyPenalty.visibility = View.GONE
                layoutPresencePenalty.visibility = View.GONE
            }
            
            modelId.contains("llama") -> {
                // Meta LLaMA
                configureSlider(sliderTemperature, 0.1f, 1.0f, 0.1f)
                configureSlider(sliderTopP, 0.1f, 1.0f, 0.05f)
                configureSlider(sliderTopK, 10f, 100f, 1f)
                layoutTopK.visibility = View.VISIBLE
                layoutRepetitionPenalty.visibility = View.VISIBLE
                layoutFrequencyPenalty.visibility = View.GONE
                layoutPresencePenalty.visibility = View.GONE
            }
            
            modelId.contains("mistral") || modelId.contains("mixtral") -> {
                // Mistral models
                configureSlider(sliderTemperature, 0.0f, 1.0f, 0.1f)
                configureSlider(sliderTopP, 0.0f, 1.0f, 0.05f)
                configureSlider(sliderTopK, 1f, 200f, 1f)
                layoutTopK.visibility = View.VISIBLE
                layoutRepetitionPenalty.visibility = View.VISIBLE
                layoutFrequencyPenalty.visibility = View.GONE
                layoutPresencePenalty.visibility = View.GONE
            }
            
            modelId.contains("cohere") || modelId.contains("command") -> {
                // Cohere models
                configureSlider(sliderTemperature, 0.0f, 5.0f, 0.1f)
                configureSlider(sliderTopP, 0.01f, 0.99f, 0.01f)
                configureSlider(sliderTopK, 1f, 500f, 1f)
                layoutTopK.visibility = View.VISIBLE
                layoutRepetitionPenalty.visibility = View.GONE
                layoutFrequencyPenalty.visibility = View.VISIBLE
                layoutPresencePenalty.visibility = View.VISIBLE
                configureSlider(sliderFrequencyPenalty, 0.0f, 1.0f, 0.1f)
                configureSlider(sliderPresencePenalty, 0.0f, 1.0f, 0.1f)
            }
            
            else -> {
                // Default configuration
                configureSlider(sliderTemperature, 0.0f, 2.0f, 0.1f)
                configureSlider(sliderTopP, 0.0f, 1.0f, 0.05f)
                configureSlider(sliderTopK, 1f, 100f, 1f)
                layoutTopK.visibility = View.VISIBLE
                layoutRepetitionPenalty.visibility = View.VISIBLE
                layoutFrequencyPenalty.visibility = View.GONE
                layoutPresencePenalty.visibility = View.GONE
            }
        }
        
        // Configure max tokens based on model
        val maxTokensLimit = minOf(currentModel.maxTokens, 8000)
        configureSlider(sliderMaxTokens, 50f, maxTokensLimit.toFloat(), 50f)
    }

    private fun configureSlider(slider: Slider, from: Float, to: Float, step: Float) {
        slider.valueFrom = from
        slider.valueTo = to
        slider.stepSize = step
    }

    private fun setupListeners() {
        // Preset button listeners
        btnBalanced.setOnClickListener { selectPreset(btnBalanced, "Balanced") }
        btnCreative.setOnClickListener { selectPreset(btnCreative, "Creative") }
        btnPrecise.setOnClickListener { selectPreset(btnPrecise, "Precise") }
        btnDeterministic.setOnClickListener { selectPreset(btnDeterministic, "Deterministic") }
        btnExperimental.setOnClickListener { selectPreset(btnExperimental, "Experimental") }
        
        // Advanced toggle
        switchAdvanced.setOnCheckedChangeListener { _, isChecked ->
            cardAdvancedParams.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        // Core parameter sliders
        sliderTemperature.addOnChangeListener { _, value, _ ->
            workingParameters = workingParameters.copy(temperature = value)
            tvTemperatureValue.text = String.format("%.1f", value)
            uncheckPresets()
        }
        
        sliderTopP.addOnChangeListener { _, value, _ ->
            workingParameters = workingParameters.copy(topP = value)
            tvTopPValue.text = String.format("%.2f", value)
            uncheckPresets()
        }
        
        sliderMaxTokens.addOnChangeListener { _, value, _ ->
            workingParameters = workingParameters.copy(maxTokens = value.toInt())
            tvMaxTokensValue.text = value.toInt().toString()
            uncheckPresets()
        }
        
        // Advanced parameter sliders
        sliderTopK.addOnChangeListener { _, value, _ ->
            workingParameters = workingParameters.copy(topK = value.toInt())
            tvTopKValue.text = value.toInt().toString()
            uncheckPresets()
        }
        
        sliderRepetitionPenalty.addOnChangeListener { _, value, _ ->
            workingParameters = workingParameters.copy(repetitionPenalty = value)
            tvRepetitionPenaltyValue.text = String.format("%.1f", value)
            uncheckPresets()
        }
        
        sliderFrequencyPenalty.addOnChangeListener { _, value, _ ->
            workingParameters = workingParameters.copy(frequencyPenalty = value)
            tvFrequencyPenaltyValue.text = String.format("%.1f", value)
            uncheckPresets()
        }
        
        sliderPresencePenalty.addOnChangeListener { _, value, _ ->
            workingParameters = workingParameters.copy(presencePenalty = value)
            tvPresencePenaltyValue.text = String.format("%.1f", value)
            uncheckPresets()
        }
        
        // Seed toggle
        switchSeed.setOnCheckedChangeListener { _, isChecked ->
            layoutSeedInput.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                workingParameters = workingParameters.copy(seed = null)
                etSeed.setText("")
            }
        }
    }

    private fun setupInfoButtons(view: View) {
        view.findViewById<ImageButton>(R.id.btnTemperatureInfo).setOnClickListener {
            showParameterInfo("temperature")
        }
        view.findViewById<ImageButton>(R.id.btnTopPInfo).setOnClickListener {
            showParameterInfo("topP")
        }
        view.findViewById<ImageButton>(R.id.btnTopKInfo).setOnClickListener {
            showParameterInfo("topK")
        }
        view.findViewById<ImageButton>(R.id.btnRepetitionPenaltyInfo).setOnClickListener {
            showParameterInfo("repetitionPenalty")
        }
        view.findViewById<ImageButton>(R.id.btnFrequencyPenaltyInfo).setOnClickListener {
            showParameterInfo("frequencyPenalty")
        }
        view.findViewById<ImageButton>(R.id.btnPresencePenaltyInfo).setOnClickListener {
            showParameterInfo("presencePenalty")
        }
    }

    private fun showParameterInfo(parameterName: String) {
        val info = workingParameters.getParameterInfo(parameterName)
        if (info != null) {
            AlertDialog.Builder(context)
                .setTitle(info.displayName)
                .setMessage(info.description)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun applyPreset(presetName: String) {
        val preset = when (presetName) {
            "Balanced" -> ParameterPreset.BALANCED
            "Creative" -> ParameterPreset.CREATIVE
            "Precise" -> ParameterPreset.PRECISE
            "Deterministic" -> ParameterPreset.DETERMINISTIC
            "Experimental" -> ParameterPreset.EXPERIMENTAL
            else -> return
        }
        
        workingParameters = preset.parameters.adaptForModel(currentModel.id)
        updateUIFromParameters()
    }

    private fun selectPreset(button: MaterialButton, presetName: String) {
        // Update button states
        resetPresetButtons()
        currentSelectedPreset = button
        button.background = ContextCompat.getDrawable(context, R.drawable.circle_button_active)
        button.setTextColor(ContextCompat.getColor(context, R.color.white))
        
        // Apply the preset
        applyPreset(presetName)
    }
    
    private fun resetPresetButtons() {
        val buttons = listOf(btnBalanced, btnCreative, btnPrecise, btnDeterministic, btnExperimental)
        buttons.forEach { button ->
            button.background = ContextCompat.getDrawable(context, R.drawable.circle_button_inactive)
            button.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
    }
    
    private fun uncheckPresets() {
        resetPresetButtons()
        currentSelectedPreset = null
    }

    private fun updateUIFromParameters() {
        // Update core sliders
        sliderTemperature.value = workingParameters.temperature
        tvTemperatureValue.text = String.format("%.1f", workingParameters.temperature)
        
        sliderTopP.value = workingParameters.topP
        tvTopPValue.text = String.format("%.2f", workingParameters.topP)
        
        sliderMaxTokens.value = workingParameters.maxTokens.toFloat()
        tvMaxTokensValue.text = workingParameters.maxTokens.toString()
        
        // Update advanced sliders
        sliderTopK.value = workingParameters.topK.toFloat()
        tvTopKValue.text = workingParameters.topK.toString()
        
        sliderRepetitionPenalty.value = workingParameters.repetitionPenalty
        tvRepetitionPenaltyValue.text = String.format("%.1f", workingParameters.repetitionPenalty)
        
        sliderFrequencyPenalty.value = workingParameters.frequencyPenalty
        tvFrequencyPenaltyValue.text = String.format("%.1f", workingParameters.frequencyPenalty)
        
        sliderPresencePenalty.value = workingParameters.presencePenalty
        tvPresencePenaltyValue.text = String.format("%.1f", workingParameters.presencePenalty)
        
        // Update seed
        switchSeed.isChecked = workingParameters.seed != null
        layoutSeedInput.visibility = if (workingParameters.seed != null) View.VISIBLE else View.GONE
        etSeed.setText(workingParameters.seed?.toString() ?: "")
    }

    private fun resetToDefaults() {
        workingParameters = AIParameters.getDefaultForModel(currentModel.id)
        updateUIFromParameters()
        selectPreset(btnBalanced, "Balanced")
        Toast.makeText(context, "Reset to default parameters", Toast.LENGTH_SHORT).show()
    }

    private fun applyParameters() {
        try {
            // Get seed value if enabled
            val seedValue = if (switchSeed.isChecked) {
                val seedText = etSeed.text.toString().trim()
                if (seedText.isNotEmpty()) seedText.toLongOrNull() else System.currentTimeMillis()
            } else null
            
            val finalParameters = workingParameters.copy(seed = seedValue)
            
            // Validate parameters
            val validatedParameters = finalParameters.adaptForModel(currentModel.id)
            
            Timber.d("Applied AI parameters: $validatedParameters")
            
            onParametersChanged(validatedParameters)
            dialog?.dismiss()
            
            Toast.makeText(context, "Parameters applied successfully", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Timber.e(e, "Error applying parameters")
            Toast.makeText(context, "Error applying parameters: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}