package app.tgdrive.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private data class SoftPalette(val glow: Long, val surface: Long, val ink: Long, val muted: Long)

private fun softPalette(style: String, dark: Boolean): SoftPalette = when (style) {
    "MIST" -> if (dark) SoftPalette(0xFF344A63, 0xFF1B2B3D, 0xFFECF4FF, 0xFFBDCDE0)
        else SoftPalette(0xFFC5DCF7, 0xFFEAF2FB, 0xFF203348, 0xFF4E647B)
    "BLOOM" -> if (dark) SoftPalette(0xFF2F3F36, 0xFF242E2B, 0xFFEDF5EF, 0xFFC0CEC5)
        else SoftPalette(0xFFE0EEE0, 0xFFF0F5EF, 0xFF26362E, 0xFF50645B)
    "TIDE" -> if (dark) SoftPalette(0xFF37324B, 0xFF292738, 0xFFF2EFFA, 0xFFC9C1D8)
        else SoftPalette(0xFFEAE1F3, 0xFFF4F0F9, 0xFF322944, 0xFF665975)
    else -> if (dark) SoftPalette(0xFF43372C, 0xFF302921, 0xFFFCF0E2, 0xFFDAC7B1)
        else SoftPalette(0xFFF5E7D6, 0xFFFAF4EB, 0xFF3F3026, 0xFF685547)
}

@Composable
internal fun SoftDashboard(
    style: String,
    usage: LibraryOverview,
    loading: Boolean,
    updated: String,
    dark: Boolean,
    modifier: Modifier,
    headerAction: (@Composable () -> Unit)?
) {
    val palette = softPalette(style, dark)
    val glow by animateColorAsState(Color(palette.glow), tween(220), label = "softDashboardGlow")
    val surface by animateColorAsState(Color(palette.surface), tween(220), label = "softDashboardSurface")
    val ink by animateColorAsState(Color(palette.ink), tween(220), label = "softDashboardInk")
    val muted by animateColorAsState(Color(palette.muted), tween(220), label = "softDashboardMuted")
    val brush = if (style == "MIST") Brush.radialGradient(listOf(glow, surface))
        else Brush.linearGradient(listOf(glow, surface))

    CompositionLocalProvider(LocalContentColor provides ink) {
        Box(modifier.clip(RoundedCornerShape(27.dp)).background(brush)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (style == "MIST") {
                    MistContents(usage, loading, updated, dark, muted)
                } else {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (style == "TIDE") "Storage share" else "Your library",
                            Modifier.weight(1f).skeleton(loading),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (headerAction != null) Spacer(Modifier.width(40.dp))
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                    ) {
                        when (style) {
                            "BLOOM" -> BloomContents(usage, loading, dark, muted)
                            "TIDE" -> TideContents(usage, loading, dark, muted)
                            "COVE" -> CoveContents(usage, loading, dark, muted)
                        }
                    }
                    if (style == "TIDE") {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = .12f)))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(fileCount(usage, loading), Modifier.skeleton(loading),
                            style = MaterialTheme.typography.labelMedium)
                        Text(updated, Modifier.weight(1f).skeleton(loading), color = muted,
                            style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End)
                    }
                }
            }
            if (headerAction != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 9.dp)) { headerAction() }
            }
        }
    }
}

@Composable
private fun MistContents(usage: LibraryOverview, loading: Boolean, updated: String, dark: Boolean, muted: Color) {
    val accent = Color(if (dark) 0xFFBFD8F6 else 0xFF436888)
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(58.dp)) {
                listOf(26.dp, 20.dp, 14.dp).forEachIndexed { index, radius ->
                    drawCircle(accent.copy(alpha = .13f + index * .045f), radius.toPx(), style = Stroke(1.dp.toPx()))
                }
            }
            Icon(DriveIcons.CloudQueue, null, Modifier.size(24.dp).skeleton(loading), tint = accent)
        }
        Text("Stored in Telegram", Modifier.skeleton(loading), color = muted, style = MaterialTheme.typography.labelLarge)
        DashboardAmount(usage.bytes, loading, prominent = true)
        Text(
            fileCount(usage, loading),
            Modifier.skeleton(loading).clip(CircleShape)
                .background(accent.copy(alpha = .12f)).padding(horizontal = 15.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium
        )
        Text(updated, Modifier.skeleton(loading), color = muted, style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LibraryTotal(usage: LibraryOverview, loading: Boolean, muted: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DashboardAmount(usage.bytes, loading)
        Text("Stored in Telegram", Modifier.weight(1f).skeleton(loading), color = muted,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private val categoryNames = listOf("Photos", "Videos", "Audio", "Files")

@Composable
private fun BloomContents(usage: LibraryOverview, loading: Boolean, dark: Boolean, muted: Color) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val backgrounds = if (dark) listOf(0xFF345846, 0xFF423C55, 0xFF564738, 0xFF354653)
        else listOf(0xFFD7EADB, 0xFFE9E0F1, 0xFFF4E6D3, 0xFFDCEAF3)
    val tints = if (dark) listOf(0xFF9ED5B4, 0xFFCBB8F0, 0xFFEFC894, 0xFFA9CDEA)
        else listOf(0xFF3C7256, 0xFF795795, 0xFF8A5F31, 0xFF436B8C)
    val icons = listOf(DriveIcons.Photo, DriveIcons.Videocam, DriveIcons.MusicNote, DriveIcons.Description)
    LibraryTotal(usage, loading, muted)
    categoryNames.indices.chunked(if (largeText) 2 else 4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { index ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (largeText) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(tints[index])).skeleton(loading))
                    } else {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(17.dp))
                                .background(Color(backgrounds[index])).skeleton(loading),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icons[index], null, Modifier.size(19.dp), tint = Color(tints[index]))
                        }
                    }
                    Text(categoryNames[index], Modifier.skeleton(loading), color = muted,
                        style = MaterialTheme.typography.labelSmall)
                    Text(formatLibraryBytes(usage.groups.getOrElse(index) { 0L }), Modifier.skeleton(loading),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TideContents(usage: LibraryOverview, loading: Boolean, dark: Boolean, muted: Color) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val groups = categoryNames.indices.map { usage.groups.getOrElse(it) { 0L }.coerceAtLeast(0L).toDouble() }
    val total = groups.sum()
    val shares = groups.map { if (total > 0) (it / total).toFloat() else 0f }
    val scale = maxOf(.5f, shares.max())
    val track = Color(if (dark) 0xFF352E46 else 0xFFEEE6F5)
    val marks = if (dark) listOf(0xFFA69BDE, 0xFFC2ACE8, 0xFFD4A7C6, 0xFFA5BDE4)
        else listOf(0xFFA290CE, 0xFFB4A1D7, 0xFFBE94B4, 0xFF8FAACF)
    LibraryTotal(usage, loading, muted)
    categoryNames.indices.chunked(if (largeText) 2 else 4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { index ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(Modifier.widthIn(max = 56.dp).fillMaxWidth().height(if (largeText) 16.dp else 52.dp)
                        .clip(RoundedCornerShape(15.dp)).background(track).skeleton(loading)
                    ) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(shares[index] / scale)
                            .clip(RoundedCornerShape(15.dp)).background(Color(marks[index])))
                    }
                    Text("${(shares[index] * 100).roundToInt()}%", Modifier.skeleton(loading),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(categoryNames[index], Modifier.skeleton(loading), color = muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CoveContents(usage: LibraryOverview, loading: Boolean, dark: Boolean, muted: Color) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val accent = Color(if (dark) 0xFFEDC69E else 0xFF89603C)
    val iconSurface = Color(if (dark) 0xFF554435 else 0xFFF2E4D2)
    val cloud = Color(if (dark) 0xFF44382C else 0xFFFDF8F1)
    val device = Color(if (dark) 0xFF3D3228 else 0xFFF8EEE2)
    val storagePanel: @Composable (String, Long, Boolean, Color, Modifier) -> Unit = { label, bytes, local, background, modifier ->
        val panel = modifier.clip(RoundedCornerShape(19.dp)).background(background).padding(12.dp)
        val storageIcon: @Composable () -> Unit = {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(11.dp)).background(iconSurface).skeleton(loading),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (local) DriveIcons.PhoneAndroid else DriveIcons.CloudQueue, null,
                    Modifier.size(18.dp), tint = accent)
            }
        }
        val storageText: @Composable () -> Unit = {
            val value = if (loading) (if (local) "3.4 GB" else "24.8 GB") else formatLibraryBytes(bytes)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, Modifier.skeleton(loading), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, Modifier.skeleton(loading), color = muted, style = MaterialTheme.typography.labelMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (largeText) {
            Row(panel, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                storageIcon()
                storageText()
            }
        } else {
            Column(panel, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                storageIcon()
                storageText()
            }
        }
    }
    if (largeText) {
        storagePanel("Stored in Telegram", usage.bytes, false, cloud, Modifier.fillMaxWidth())
        storagePanel("On this device", usage.offlineBytes, true, device, Modifier.fillMaxWidth())
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            storagePanel("Stored in Telegram", usage.bytes, false, cloud, Modifier.weight(1.1f))
            storagePanel("On this device", usage.offlineBytes, true, device, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DashboardAmount(bytes: Long, loading: Boolean, prominent: Boolean = false) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Text(
        if (loading) "24.8 GB" else formatLibraryBytes(bytes), Modifier.skeleton(loading),
        style = when {
            largeText -> MaterialTheme.typography.headlineSmall
            prominent -> MaterialTheme.typography.displayMedium
            else -> MaterialTheme.typography.headlineMedium
        },
        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

private fun fileCount(usage: LibraryOverview, loading: Boolean): String =
    if (loading) "1,284 files" else "${usage.count} ${if (usage.count == 1) "file" else "files"}"
