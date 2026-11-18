package com.infinity.drive.presentation.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.tgdrive.R
import app.tgdrive.ui.DriveIcons
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.presentation.components.FileListItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import java.text.DateFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun PhotoHistoryControl(onAlbum: Boolean, folder: String?, query: String, openFile: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton({ open = true }) {
        Icon(DriveIcons.History, stringResource(R.string.photo_history_title))
    }
    if (open) PhotoHistoryDialog(onAlbum, folder, query, { open = false }) {
        open = false
        openFile(it.id)
    }
}

@Composable
private fun PhotoHistoryDialog(
    onAlbum: Boolean,
    folder: String?,
    query: String,
    close: () -> Unit,
    openFile: (DriveFile) -> Unit
) {
    val history = koinInject<AndroidPhotoHistory>()
    var mapped by remember { mutableStateOf(false) }
    var inspectBackups by remember { mutableStateOf(false) }
    var limit by remember { mutableIntStateOf(100) }
    var photos by remember(onAlbum, folder, query) { mutableStateOf<PhotoHistory?>(null) }
    var scanning by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(history, folder, query, onAlbum, limit, inspectBackups) {
        try {
            history.observeGallery(onAlbum, folder, query).collectLatest { files ->
                scanning = true
                failed = false
                val revisions = files.associate { it.id to it.modifiedAt }
                photos = photos?.let { current ->
                    current.copy(photos = current.photos.filter { revisions[it.file.id] == it.file.modifiedAt })
                }
                photos = history.scan(files, limit, inspectBackups)
                scanning = false
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            scanning = false
            failed = true
        }
    }

    val outlines by produceState(emptyList<FloatArray>(), mapped) {
        if (mapped) {
            value = try { history.outlines() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { emptyList() }
        }
    }
    val located = remember(photos) { photos?.photos.orEmpty().filter { it.place != null } }
    val months = remember(photos) { photos?.photos.orEmpty().groupBy { captureMonth(it.date) } }
    val monthFormat = remember { DateTimeFormatter.ofPattern("LLLL yyyy") }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.photo_history_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!mapped, { mapped = false }, { Text(stringResource(R.string.photo_history_timeline)) })
                    FilterChip(mapped, { mapped = true }, { Text(stringResource(R.string.photo_history_map)) })
                }
                if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.photo_history_error))
                if (mapped) {
                    if (outlines.isNotEmpty()) {
                        PhotoOutlineMap(outlines, located, selected) { selected = it }
                    }
                    if (located.isEmpty() && !scanning) Text(stringResource(R.string.photo_history_no_locations))
                }
                val placed = located.firstOrNull { it.file.id == selected }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = if (mapped) 155.dp else 340.dp)) {
                    if (mapped) {
                        items(if (placed == null) located else listOf(placed), key = { it.file.id }) {
                            PhotoHistoryRow(it, dateFormat, openFile)
                        }
                    } else {
                        for ((month, entries) in months) {
                            item(key = "month-$month") {
                                Text(
                                    month.format(monthFormat), Modifier.padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            items(entries, key = { it.file.id }) { PhotoHistoryRow(it, dateFormat, openFile) }
                        }
                        if (!scanning && months.isEmpty()) {
                            item { Text(stringResource(R.string.photo_history_empty)) }
                        }
                    }
                }
                if (photos?.hasMore == true) {
                    TextButton(onClick = { limit += 100 }, enabled = !scanning) {
                        Text(stringResource(R.string.photo_history_more))
                    }
                }
                if (!inspectBackups) {
                    TextButton(onClick = { inspectBackups = true }, enabled = !scanning) {
                        Text(stringResource(R.string.photo_history_backups))
                    }
                }
            }
        },
        confirmButton = { TextButton(close) { Text(stringResource(android.R.string.ok)) } }
    )
}

@Composable
private fun PhotoHistoryRow(photo: DatedPhoto, formatter: DateFormat, open: (DriveFile) -> Unit) {
    Column {
        FileListItem(photo.file, selected = false, selectionMode = false,
            onClick = { open(photo.file) }, onLongClick = {})
        if (photo.capturedAt == null) {
            Text(stringResource(R.string.photo_history_file_date, formatter.format(java.util.Date(photo.date))),
                Modifier.padding(start = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelSmall)
        } else {
            Text(formatter.format(java.util.Date(photo.date)),
                Modifier.padding(start = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PhotoOutlineMap(
    map: List<FloatArray>,
    photos: List<DatedPhoto>,
    selected: String?,
    select: (String?) -> Unit
) {
    val tint = MaterialTheme.colorScheme
    val description = stringResource(R.string.photo_history_map)
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val clamped: (Offset, IntSize, Float) -> Offset = { offset, bounds, scale ->
        val x = bounds.width * (scale - 1) / 2
        val y = bounds.height * (scale - 1) / 2
        Offset(offset.x.coerceIn(-x, x), offset.y.coerceIn(-y, y))
    }
    Canvas(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(tint.surfaceContainerHigh)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, delta, factor, _ ->
                    val next = (zoom * factor).coerceIn(1f, 16f)
                    val relative = centroid - Offset(size.width / 2f, size.height / 2f)
                    pan = clamped(pan * (next / zoom) + relative * (1 - next / zoom) + delta, size, next)
                    zoom = next
                }
            }
            .pointerInput(photos, select) {
                detectTapGestures(
                    onTap = { point ->
                        val nearest = photos.map { photo ->
                            photo to (projectPlace(photo.place!!, size.width.toFloat(), size.height.toFloat(), zoom, pan) - point)
                                .getDistance()
                        }.filter { it.second < 20.dp.toPx() }.minByOrNull { it.second }
                        select(nearest?.first?.file?.id)
                    },
                    onDoubleTap = {
                        zoom = if (zoom > 1f) 1f else 4f
                        pan = Offset.Zero
                    }
                )
            }
    ) {
        for (polygon in map) {
            val path = Path()
            for (i in polygon.indices step 2) {
                val point = mapPoint(polygon[i], polygon[i + 1], size.width, size.height, zoom, pan)
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            path.close()
            drawPath(path, tint.secondaryContainer)
        }
        for (photo in photos) {
            val point = projectPlace(photo.place!!, size.width, size.height, zoom, pan)
            val active = photo.file.id == selected
            val radius = if (active) 7.dp.toPx() else 4.dp.toPx()
            drawCircle(tint.surface, radius + 1.5.dp.toPx(), point)
            drawCircle(if (active) tint.tertiary else tint.primary, radius, point)
        }
    }
}

internal fun captureMonth(time: Long, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
    YearMonth.from(Instant.ofEpochMilli(time).atZone(zone))

internal fun projectPlace(place: PhotoPlace, width: Float, height: Float, zoom: Float, pan: Offset): Offset =
    mapPoint(place.longitude.toFloat(), place.latitude.toFloat(), width, height, zoom, pan)

private fun mapPoint(longitude: Float, latitude: Float, width: Float, height: Float, zoom: Float, pan: Offset): Offset =
    Offset(width / 2 + longitude / 360 * width * zoom + pan.x, height / 2 - latitude / 180 * height * zoom + pan.y)
