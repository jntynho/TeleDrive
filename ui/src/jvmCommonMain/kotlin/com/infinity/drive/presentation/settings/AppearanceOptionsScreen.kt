package com.infinity.drive.presentation.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.LibraryDashboard
import app.tgdrive.ui.LibraryOverview
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import com.infinity.drive.domain.model.DashboardStyle
import com.infinity.drive.presentation.common.AppBackHandler
import com.infinity.drive.presentation.components.DriveBottomSheet
import com.infinity.drive.presentation.components.PageHeader
import com.infinity.drive.presentation.navigation.LocalBottomBarInset
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.settings_dashboard_style
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val sampleLibrary = LibraryOverview(
    1284, 26_628_797_235, listOf(9_450_000_000, 11_350_000_000, 3_200_000_000, 2_628_797_235),
    offlineBytes = 3_621_451_776
)

@Composable
fun DashboardStylesScreen(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    AppBackHandler { onBack() }
    Scaffold(topBar = {
        PageHeader(
            title = { Text(stringResource(Res.string.settings_dashboard_style)) },
            navigationIcon = { IconButton(onBack) { Icon(DriveIcons.ArrowBack, "Back") } }
        )
    }) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).padding(bottom = LocalBottomBarInset.current),
            contentAlignment = Alignment.TopCenter
        ) {
            DashboardStylePreferences(viewModel, sampleLibrary, "Updated just now", sample = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardStylesSheet(
    usage: LibraryOverview,
    updatedLabel: String,
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    DriveBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical) }
    ) {
        Column(Modifier.fillMaxWidth().testTag("dashboard-style-sheet"), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(Res.string.settings_dashboard_style),
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleLarge
            )
            DashboardStylePreferences(viewModel, usage, updatedLabel)
        }
    }
}

@Composable
private fun DashboardStylePreferences(
    viewModel: SettingsViewModel,
    usage: LibraryOverview,
    updatedLabel: String,
    sample: Boolean = false
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.loading) return
    val prefs = state.preferences
    DashboardStyleSelector(usage, updatedLabel, prefs.dashboardStyle, prefs.dashboardDarkStyles, sample = sample) { style, dark ->
        viewModel.update { current ->
            current.copy(
                dashboardStyle = style,
                dashboardDarkStyles = if (dark) current.dashboardDarkStyles + style.name else current.dashboardDarkStyles - style.name
            )
        }
    }
}

@Composable
fun DashboardStyleSelector(
    usage: LibraryOverview,
    updatedLabel: String,
    selectedStyle: DashboardStyle,
    darkStyles: Set<String>,
    modifier: Modifier = Modifier,
    sample: Boolean = false,
    onApply: (DashboardStyle, Boolean) -> Unit
) {
    val styles = DashboardStyle.entries
    val pager = rememberPagerState(initialPage = selectedStyle.ordinal) { styles.size }
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var previewTones by rememberSaveable(darkStyles) { mutableStateOf(hashMapOf<String, Boolean>()) }
    val page = pager.settledPage
    val active = styles[page]
    val appliedTone = active.name in darkStyles
    val activeTone = previewTones[active.name] ?: appliedTone
    val applied = selectedStyle == active && activeTone == appliedTone

    Column(
        modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (sample) {
            Text("Sample library data", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().testTag("dashboard-style-carousel"),
            pageSpacing = 12.dp,
            key = { styles[it].name }
        ) { index ->
            val style = styles[index]
            val dark = previewTones[style.name] ?: (style.name in darkStyles)
            Column(
                Modifier.fillMaxWidth().testTag("dashboard-option-${style.name}"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(style.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        style.description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 1, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LibraryDashboard(
                    usage, {}, showAddAction = false, updatedLabel = updatedLabel, style = style.name, dark = dark,
                    headerAction = {
                        val ink = LocalContentColor.current
                        IconToggleButton(
                            checked = dark,
                            onCheckedChange = { previewTones = HashMap(previewTones).apply { put(style.name, it) } },
                            modifier = Modifier.testTag("dashboard-tone-${style.name}").semantics { contentDescription = "Dark preview" },
                            colors = IconButtonDefaults.iconToggleButtonColors(
                                containerColor = ink.copy(alpha = .09f), contentColor = ink,
                                checkedContainerColor = ink.copy(alpha = .12f), checkedContentColor = ink
                            )
                        ) {
                            Icon(if (dark) DriveIcons.DarkMode else DriveIcons.LightMode, null, Modifier.size(20.dp))
                        }
                    }
                )
            }
        }
        Button(
            onClick = { onApply(active, activeTone) },
            enabled = !applied && !pager.isScrollInProgress,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("apply-dashboard-${active.name}")
                .semantics { selected = applied; contentDescription = "Use ${active.label} dashboard" }
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (applied) {
                    Icon(DriveIcons.Check, null, Modifier.align(Alignment.CenterStart).size(20.dp))
                }
                Text(if (applied) "Applied" else "Use this style", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { scope.launch { pager.animateScrollToPage(page - 1, animationSpec = tween(240, easing = FastOutSlowInEasing)) } },
                enabled = page > 0 && !pager.isScrollInProgress
            ) {
                Icon(if (rtl) DriveIcons.ChevronRight else DriveIcons.ChevronLeft, "Previous dashboard style")
            }
            Text(
                "${page + 1} / ${styles.size}",
                style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalIconButton(
                onClick = { scope.launch { pager.animateScrollToPage(page + 1, animationSpec = tween(240, easing = FastOutSlowInEasing)) } },
                enabled = page < styles.lastIndex && !pager.isScrollInProgress
            ) {
                Icon(if (rtl) DriveIcons.ChevronLeft else DriveIcons.ChevronRight, "Next dashboard style")
            }
        }
    }
}
