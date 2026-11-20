package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.PdfExportOptions
import com.example.data.model.PdfOrientation
import com.example.data.model.PdfPageSize
import com.example.localization.LocalStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfExportConfigDialog(
    initialTitle: String,
    defaultPageSize: PdfPageSize,
    defaultQuality: Int,
    onDismiss: () -> Unit,
    onConfirm: (PdfExportOptions) -> Unit
) {
    val strings = LocalStrings.current
    var title by remember { mutableStateOf(initialTitle) }
    var author by remember { mutableStateOf("ScanPro PDF") }
    var pageSize by remember { mutableStateOf(defaultPageSize) }
    var orientation by remember { mutableStateOf(PdfOrientation.PORTRAIT) }
    var customW by remember { mutableStateOf("210") }
    var customH by remember { mutableStateOf("297") }
    var marginMm by remember { mutableFloatStateOf(8f) }
    var quality by remember { mutableIntStateOf(defaultQuality) }
    var enablePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var watermark by remember { mutableStateOf("") }
    var includePageNumbers by remember { mutableStateOf(true) }
    var searchableOcr by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.pdfCreateTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.pdfDocTitle) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pdf_title_input")
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(strings.pdfAuthor) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(strings.pdfPageSize, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PdfPageSize.entries.forEach { size ->
                        FilterChip(
                            selected = pageSize == size,
                            onClick = { pageSize = size },
                            label = { Text(strings.pageSizeLabel(size)) }
                        )
                    }
                }

                if (pageSize == PdfPageSize.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customW,
                            onValueChange = { customW = it },
                            label = { Text(strings.pdfCustomWidthMm) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customH,
                            onValueChange = { customH = it },
                            label = { Text(strings.pdfCustomHeightMm) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(strings.pdfOrientation, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = orientation == PdfOrientation.PORTRAIT,
                        onClick = { orientation = PdfOrientation.PORTRAIT },
                        label = { Text(strings.pdfPortrait) }
                    )
                    FilterChip(
                        selected = orientation == PdfOrientation.LANDSCAPE,
                        onClick = { orientation = PdfOrientation.LANDSCAPE },
                        label = { Text(strings.pdfLandscape) }
                    )
                }

                Text("${strings.pdfMarginMm}: ${marginMm.toInt()} mm", style = MaterialTheme.typography.labelMedium)
                Slider(value = marginMm, onValueChange = { marginMm = it }, valueRange = 0f..25f)

                Text("${strings.pdfQuality}: $quality%", style = MaterialTheme.typography.labelMedium)
                Slider(value = quality.toFloat(), onValueChange = { quality = it.toInt() }, valueRange = 35f..100f)

                OutlinedTextField(
                    value = watermark,
                    onValueChange = { watermark = it },
                    label = { Text(strings.pdfWatermark) },
                    placeholder = { Text(strings.pdfWatermarkHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.pdfPageNumbers, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = includePageNumbers, onCheckedChange = { includePageNumbers = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.pdfSearchableLayer, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = searchableOcr, onCheckedChange = { searchableOcr = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.pdfPasswordProtect, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enablePassword, onCheckedChange = { enablePassword = it })
                }
                if (enablePassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(strings.pdfPasswordHint) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_password_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        PdfExportOptions(
                            title = title.ifBlank { initialTitle },
                            author = author.ifBlank { "ScanPro PDF" },
                            pageSize = pageSize,
                            orientation = orientation,
                            customWidthMm = customW.toFloatOrNull() ?: 210f,
                            customHeightMm = customH.toFloatOrNull() ?: 297f,
                            marginMm = marginMm,
                            quality = quality,
                            password = if (enablePassword) password else "",
                            watermarkText = watermark,
                            includePageNumbers = includePageNumbers,
                            searchableOcr = searchableOcr
                        )
                    )
                },
                modifier = Modifier.testTag("confirm_generate_pdf_button")
            ) {
                Text(strings.pdfGenerateAndSave)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
