package com.cyberflux.qwinai.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyberflux.qwinai.R
import com.cyberflux.qwinai.tools.DocumentFormatting
import com.cyberflux.qwinai.tools.DocumentMetadata
import com.cyberflux.qwinai.tools.DocumentRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class FileGenerationDialog(
    private val context: Context,
    private val onGenerate: (DocumentRequest) -> Unit
) : Dialog(context) {

    private lateinit var fileTypeSpinner: Spinner
    private lateinit var titleInput: TextInputEditText
    private lateinit var contentInput: TextInputEditText
    private lateinit var dataRecyclerView: RecyclerView
    private lateinit var headerInput: TextInputEditText
    private lateinit var addHeaderButton: MaterialButton
    private lateinit var addDataRowButton: MaterialButton
    private lateinit var fontSizeInput: TextInputEditText
    private lateinit var fontFamilySpinner: Spinner
    private lateinit var boldCheckbox: CheckBox
    private lateinit var italicCheckbox: CheckBox
    private lateinit var alignmentSpinner: Spinner
    private lateinit var pageSizeSpinner: Spinner
    private lateinit var orientationSpinner: Spinner
    private lateinit var authorInput: TextInputEditText
    private lateinit var subjectInput: TextInputEditText
    private lateinit var keywordsInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var generateButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    
    private val headers = mutableListOf<String>()
    private val dataRows = mutableListOf<MutableMap<String, Any>>()
    private lateinit var headerAdapter: HeaderAdapter
    private lateinit var dataAdapter: DataRowAdapter

    init {
        setupDialog()
    }

    private fun setupDialog() {
        setContentView(R.layout.dialog_file_generation)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        
        initViews()
        setupSpinners()
        setupRecyclerViews()
        setupListeners()
    }

    private fun initViews() {
        fileTypeSpinner = findViewById(R.id.fileTypeSpinner)
        titleInput = findViewById(R.id.titleInput)
        contentInput = findViewById(R.id.contentInput)
        dataRecyclerView = findViewById(R.id.dataRecyclerView)
        headerInput = findViewById(R.id.headerInput)
        addHeaderButton = findViewById(R.id.addHeaderButton)
        addDataRowButton = findViewById(R.id.addDataRowButton)
        fontSizeInput = findViewById(R.id.fontSizeInput)
        fontFamilySpinner = findViewById(R.id.fontFamilySpinner)
        boldCheckbox = findViewById(R.id.boldCheckbox)
        italicCheckbox = findViewById(R.id.italicCheckbox)
        alignmentSpinner = findViewById(R.id.alignmentSpinner)
        pageSizeSpinner = findViewById(R.id.pageSizeSpinner)
        orientationSpinner = findViewById(R.id.orientationSpinner)
        authorInput = findViewById(R.id.authorInput)
        subjectInput = findViewById(R.id.subjectInput)
        keywordsInput = findViewById(R.id.keywordsInput)
        descriptionInput = findViewById(R.id.descriptionInput)
        generateButton = findViewById(R.id.generateButton)
        cancelButton = findViewById(R.id.cancelButton)
    }

    private fun setupSpinners() {
        // File type spinner
        val fileTypes = arrayOf("PDF", "DOCX", "XLSX", "CSV", "TXT", "JSON", "XML")
        fileTypeSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, fileTypes)
        
        // Font family spinner
        val fontFamilies = arrayOf("Arial", "Times New Roman", "Helvetica", "Calibri", "Courier New")
        fontFamilySpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, fontFamilies)
        
        // Alignment spinner
        val alignments = arrayOf("LEFT", "CENTER", "RIGHT", "JUSTIFY")
        alignmentSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, alignments)
        
        // Page size spinner
        val pageSizes = arrayOf("A4", "LETTER", "LEGAL")
        pageSizeSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, pageSizes)
        
        // Orientation spinner
        val orientations = arrayOf("PORTRAIT", "LANDSCAPE")
        orientationSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, orientations)
        
        // Set default values
        fontSizeInput.setText("12")
        authorInput.setText("Qwin AI")
    }

    private fun setupRecyclerViews() {
        headerAdapter = HeaderAdapter(headers) { position ->
            headers.removeAt(position)
            headerAdapter.notifyItemRemoved(position)
            updateDataRowsForHeaders()
        }
        
        dataAdapter = DataRowAdapter(dataRows, headers) { position ->
            dataRows.removeAt(position)
            dataAdapter.notifyItemRemoved(position)
        }
        
        dataRecyclerView.layoutManager = LinearLayoutManager(context)
        dataRecyclerView.adapter = dataAdapter
    }

    private fun setupListeners() {
        addHeaderButton.setOnClickListener {
            val headerText = headerInput.text.toString().trim()
            if (headerText.isNotEmpty() && !headers.contains(headerText)) {
                headers.add(headerText)
                headerAdapter.notifyItemInserted(headers.size - 1)
                headerInput.text?.clear()
                updateDataRowsForHeaders()
            }
        }

        addDataRowButton.setOnClickListener {
            if (headers.isNotEmpty()) {
                val newRow = mutableMapOf<String, Any>()
                headers.forEach { header ->
                    newRow[header] = ""
                }
                dataRows.add(newRow)
                dataAdapter.notifyItemInserted(dataRows.size - 1)
            } else {
                Toast.makeText(context, "Please add headers first", Toast.LENGTH_SHORT).show()
            }
        }

        generateButton.setOnClickListener {
            generateFile()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun updateDataRowsForHeaders() {
        dataRows.forEach { row ->
            val currentKeys = row.keys.toList()
            currentKeys.forEach { key ->
                if (!headers.contains(key)) {
                    row.remove(key)
                }
            }
            headers.forEach { header ->
                if (!row.containsKey(header)) {
                    row[header] = ""
                }
            }
        }
        dataAdapter.updateHeaders(headers)
        dataAdapter.notifyDataSetChanged()
    }

    private fun generateFile() {
        val request = DocumentRequest(
            type = fileTypeSpinner.selectedItem.toString().lowercase(),
            title = titleInput.text.toString(),
            content = contentInput.text.toString(),
            data = dataRows.map { it.toMap() },
            headers = headers.toList(),
            formatting = DocumentFormatting(
                fontSize = fontSizeInput.text.toString().toFloatOrNull() ?: 12f,
                fontFamily = fontFamilySpinner.selectedItem.toString(),
                bold = boldCheckbox.isChecked,
                italic = italicCheckbox.isChecked,
                alignment = alignmentSpinner.selectedItem.toString(),
                pageSize = pageSizeSpinner.selectedItem.toString(),
                orientation = orientationSpinner.selectedItem.toString()
            ),
            metadata = DocumentMetadata(
                author = authorInput.text.toString(),
                subject = subjectInput.text.toString(),
                keywords = keywordsInput.text.toString(),
                description = descriptionInput.text.toString()
            )
        )
        
        onGenerate(request)
        dismiss()
    }

    // Header adapter
    private class HeaderAdapter(
        private val headers: MutableList<String>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<HeaderAdapter.HeaderViewHolder>() {

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val headerText: TextView = view.findViewById(R.id.headerText)
            val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_header, parent, false)
            return HeaderViewHolder(view)
        }

        override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
            holder.headerText.text = headers[position]
            holder.deleteButton.setOnClickListener {
                onDelete(position)
            }
        }

        override fun getItemCount() = headers.size
    }

    // Data row adapter
    private class DataRowAdapter(
        private val dataRows: MutableList<MutableMap<String, Any>>,
        private var headers: List<String>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<DataRowAdapter.DataRowViewHolder>() {

        class DataRowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val container: LinearLayout = view.findViewById(R.id.container)
            val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataRowViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_data_row, parent, false)
            return DataRowViewHolder(view)
        }

        override fun onBindViewHolder(holder: DataRowViewHolder, position: Int) {
            val dataRow = dataRows[position]
            holder.container.removeAllViews()

            headers.forEach { header ->
                val editText = EditText(holder.itemView.context).apply {
                    hint = header
                    setText(dataRow[header]?.toString() ?: "")
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        setMargins(8, 8, 8, 8)
                    }
                    setOnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus) {
                            dataRow[header] = text.toString()
                        }
                    }
                }
                holder.container.addView(editText)
            }

            holder.deleteButton.setOnClickListener {
                onDelete(position)
            }
        }

        override fun getItemCount() = dataRows.size

        fun updateHeaders(newHeaders: List<String>) {
            headers = newHeaders
        }
    }
}