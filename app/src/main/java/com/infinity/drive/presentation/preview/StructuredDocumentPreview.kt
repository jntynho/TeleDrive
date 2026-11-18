package com.infinity.drive.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tgdrive.R

@Composable
internal fun StructuredDocumentPreview(document: StructuredDocumentReader.Document) {
    val columns = remember(document) { document.sections.maxOfOrNull { section ->
        section.rows.maxOfOrNull { it.size } ?: 0
    } ?: 0 }
    val cellWidth = 156.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = columns > 1
        val scroll = rememberScrollState()
        val tableWidth = maxOf(maxWidth, cellWidth * columns + 32.dp)
        val layout = if (wide) Modifier.horizontalScroll(scroll).width(tableWidth) else Modifier.fillMaxWidth()
        LazyColumn(
            modifier = layout,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (document.sections.all { it.rows.isEmpty() }) {
                item { Text(stringResource(R.string.document_preview_empty)) }
            }
            for (section in document.sections) {
                if (section.name.isNotBlank()) item {
                    Text(
                        section.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                itemsIndexed(section.rows) { index, row ->
                    if (wide) Row(
                        Modifier.fillMaxWidth().background(
                            if (index % 2 == 0) MaterialTheme.colorScheme.surfaceContainerLow
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        for (column in 0 until columns) {
                            Text(
                                row.getOrElse(column) { "" },
                                modifier = Modifier.width(cellWidth).padding(8.dp),
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Text(row.firstOrNull().orEmpty(), Modifier.padding(vertical = 4.dp))
                    }
                }
            }
            if (document.truncated) item {
                Text(
                    stringResource(R.string.document_preview_truncated),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
