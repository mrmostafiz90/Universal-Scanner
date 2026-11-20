package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.DocumentEntity
import com.example.localization.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareExportBottomSheet(
    doc: DocumentEntity,
    onDismiss: () -> Unit,
    onSharePdf: () -> Unit,
    onShareJpg: () -> Unit,
    onSharePng: () -> Unit,
    onOpenOcrExport: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onPrint: () -> Unit
) {
    val strings = LocalStrings.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "${strings.shareTitle}: ${doc.title}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SheetActionRow(
                icon = Icons.Filled.PictureAsPdf,
                title = strings.sharePdf,
                subtitle = strings.shareSystemSheet,
                testTag = "share_pdf_option",
                onClick = { onDismiss(); onSharePdf() }
            )
            SheetActionRow(
                icon = Icons.Filled.Image,
                title = strings.shareJpg,
                subtitle = strings.toolsPdfToImagesDesc,
                testTag = "share_jpg_option",
                onClick = { onDismiss(); onShareJpg() }
            )
            SheetActionRow(
                icon = Icons.Filled.Image,
                title = strings.sharePng,
                subtitle = strings.toolsPdfToImagesDesc,
                testTag = "share_png_option",
                onClick = { onDismiss(); onSharePng() }
            )
            SheetActionRow(
                icon = Icons.Filled.TextSnippet,
                title = "${strings.shareTxt} / ${strings.shareSearchablePdf}",
                subtitle = strings.ocrTitle,
                testTag = "share_ocr_option",
                onClick = { onDismiss(); onOpenOcrExport() }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SheetActionRow(
                icon = Icons.Filled.Download,
                title = strings.shareSaveToDownloads,
                subtitle = "Downloads/${doc.title}.pdf",
                testTag = "save_downloads_option",
                onClick = { onDismiss(); onSaveToDownloads() }
            )
            SheetActionRow(
                icon = Icons.Filled.Print,
                title = strings.shareSystemPrint,
                subtitle = strings.print,
                testTag = "print_pdf_option",
                onClick = { onDismiss(); onPrint() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick)
    )
}
