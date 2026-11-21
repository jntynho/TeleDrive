// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.infinity.drive.core.common.NotificationDestinations
import com.infinity.drive.core.files.PendingShare
import androidx.compose.ui.platform.LocalLocaleList
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import com.infinity.drive.presentation.navigation.showsNavigationChrome
import com.infinity.drive.presentation.platform.LocalLiquidNavigation
import com.infinity.drive.domain.model.AppLanguage
import com.infinity.drive.domain.model.AppTheme
import com.infinity.drive.presentation.applock.LockScreen
import com.infinity.drive.presentation.platform.LocalAppVersion
import com.infinity.drive.presentation.platform.LocalUrlOpener
import com.infinity.drive.presentation.navigation.AppNavHost
import com.infinity.drive.presentation.navigation.BottomBarHeight
import com.infinity.drive.presentation.navigation.LocalBottomBarHeight
import com.infinity.drive.presentation.navigation.Route
import com.infinity.drive.presentation.navigation.TopLevelDestination
import com.infinity.drive.presentation.navigation.navigateTopLevelOnce
import com.infinity.drive.presentation.navigation.navigateOnce
import com.infinity.drive.presentation.theme.DriveInfinityTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import com.infinity.drive.presentation.components.UpdateDialog
import com.infinity.drive.presentation.components.SessionBrokenDialog

@Composable
fun DriveInfinityApp(
    pendingShare: PendingShare,
    notificationDestination: String? = null,
    onDestinationHandled: () -> Unit = {},
    viewModel: AppViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val upgradeNotice by viewModel.upgradeNotice.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppStopped()
                Lifecycle.Event.ON_START -> viewModel.onAppStarted()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val darkTheme = when (state.theme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.AMOLED -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val pendingUpdate by viewModel.pendingUpdate.collectAsStateWithLifecycle()
    val sessionBroken by viewModel.sessionBroken.collectAsStateWithLifecycle()
    val urlOpener = LocalUrlOpener.current

    val systemLocaleList = LocalLocaleList.current
    val systemDirection = LocalLayoutDirection.current
    val targetDirection = when (state.language) {
        AppLanguage.SYSTEM -> systemDirection
        AppLanguage.ARABIC -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }
    val targetLocaleList = remember(state.language, systemLocaleList) {
        when (state.language) {
            AppLanguage.SYSTEM -> systemLocaleList
            AppLanguage.ENGLISH -> LocaleList(Locale("en"))
            AppLanguage.RUSSIAN -> LocaleList(Locale("ru"))
            AppLanguage.PORTUGUESE_BR -> LocaleList(Locale("pt-BR"))
            AppLanguage.ARABIC -> LocaleList(Locale("ar"))
        }
    }

    DisposableEffect(targetLocaleList){
        val previous=java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(targetLocaleList[0].toLanguageTag()))
        onDispose{java.util.Locale.setDefault(previous)}
    }
    val navigationState=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    LaunchedEffect(state.loading,state.onboardingComplete){if(!state.loading&&!state.onboardingComplete)navigationState.removeState("library")}

    DriveInfinityTheme(
        darkTheme = darkTheme,
        accentColour = state.accentColour,
        customTheme = state.customTheme,
        iconStyle = state.iconStyle,
        amoled = state.theme == AppTheme.AMOLED
    ) {
        CompositionLocalProvider(
            (LocalLocaleList as ProvidableCompositionLocal<LocaleList>) provides targetLocaleList,
            LocalLayoutDirection provides targetDirection,
            app.tgdrive.ui.LocalDashboardStyle provides state.dashboardStyle.name,
            app.tgdrive.ui.LocalDashboardDark provides state.dashboardDark
        ) {
            val startupFiles=org.koin.compose.koinInject<com.infinity.drive.domain.repository.FileRepository>()
            val startupMode=if(state.locked)"locked"else if(state.onboardingComplete)"library"else"sign-in"
            val startup=com.infinity.drive.presentation.components.rememberStartupPreparation(state.loading,startupMode,state.warmupGlass,viewModel.startupPreparation,startupFiles)
            val warmup=startup.visibility
            val preparing=warmup.currentState||warmup.targetState
            val fullyDrawn=com.infinity.drive.presentation.platform.LocalFullyDrawn.current
            LaunchedEffect(preparing,state.loading){if(!preparing&&!state.loading){withFrameNanos{};viewModel.firstContentDrawn();fullyDrawn()}}
            CompositionLocalProvider(com.infinity.drive.presentation.platform.LocalFirstLibraryFrame provides startup.onLibraryFrame){
            Box(Modifier.fillMaxSize()){
            Box(Modifier.fillMaxSize().then(if(preparing)Modifier.clearAndSetSemantics{}else Modifier)){
            when {
                state.loading -> Unit
                state.locked -> LockScreen(onUnlocked = viewModel::unlock)
                else -> navigationState.SaveableStateProvider(if(state.onboardingComplete)"library"else"sign-in") {
                MainScaffold(
                    pendingShare = pendingShare,
                    onboardingComplete = state.onboardingComplete,
                    notificationDestination = notificationDestination,
                    onDestinationHandled = onDestinationHandled,
                    onUpdateRequested = { viewModel.checkForUpdate(force = true) },
                    driveMissing = viewModel.driveMissing
                )
                }
            }
            }
            com.infinity.drive.presentation.components.StartupRenderProbe(startup)
            androidx.compose.animation.AnimatedVisibility(visibleState=warmup,enter=fadeIn(tween(200)),exit=fadeOut(tween(360))){com.infinity.drive.presentation.components.StartupSurface()}
            }
            }
            if(state.onboardingComplete&&!state.loading&&!state.locked&&!preparing)com.infinity.drive.presentation.upload.UploadConflictHost()
            if(upgradeNotice!=null&&!state.loading&&!state.locked&&!preparing)androidx.compose.material3.AlertDialog(onDismissRequest=viewModel::dismissUpgradeNotice,title={Text("Previous library preserved")},text={Text(upgradeNotice.orEmpty())},confirmButton={androidx.compose.material3.TextButton(viewModel::dismissUpgradeNotice){Text("Continue")}})
            if (sessionBroken&&!state.locked&&!preparing) {
                SessionBrokenDialog(onSignInAgain = viewModel::resetSession)
            }
            pendingUpdate?.takeIf { !state.loading && !state.locked&&!preparing }?.let { release ->
                UpdateDialog(
                    currentVersion = LocalAppVersion.current,
                    onOpenUrl = urlOpener::open,
                    release = release,
                    onDownload = {
                        viewModel.dismissUpdate()
                        urlOpener.open(release.pageUrl)
                    },
                    onDismiss = viewModel::dismissUpdate
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    pendingShare: PendingShare,
    onboardingComplete: Boolean,
    notificationDestination: String?,
    onDestinationHandled: () -> Unit,
    onUpdateRequested: () -> Unit,
    driveMissing: Flow<Unit>
) {
    val navigationSettings=org.koin.compose.koinInject<com.infinity.drive.domain.repository.SettingsRepository>()
    val navigationPreferences by navigationSettings.preferences.collectAsStateWithLifecycle(com.infinity.drive.domain.model.UserPreferences())
    val liquidNavigation = LocalLiquidNavigation.current
    val useGlass = navigationPreferences.navigationBarBlur && liquidNavigation?.supportsGlass == true
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val navigationFocus=androidx.compose.ui.platform.LocalFocusManager.current
    val navigationKeyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(backStackEntry?.id){navigationFocus.clearFocus();navigationKeyboard?.hide()}
    var selectedNavIndex by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val directTabIndex = TopLevelDestination.entries.indexOfFirst { currentDestination?.hasRoute(it.route::class) == true }
    val savedTabOwner = backStackEntry?.savedStateHandle?.get<Int>(TAB_OWNER)
        ?.takeIf { it in TopLevelDestination.entries.indices }
    val topLevelIndex = directTabIndex.takeIf { it >= 0 } ?: savedTabOwner ?: selectedNavIndex
    DisposableEffect(navController) {
        val updateTabOwner = NavController.OnDestinationChangedListener { controller, destination, _ ->
            val entry = controller.currentBackStackEntry ?: return@OnDestinationChangedListener
            val direct = TopLevelDestination.entries.indexOfFirst { destination.hasRoute(it.route::class) }
            // Record the owner at navigation time, even when a child opens before Compose redraws.
            val owner = direct.takeIf { it >= 0 }
                ?: entry.savedStateHandle.get<Int>(TAB_OWNER)?.takeIf { it in TopLevelDestination.entries.indices }
                ?: selectedNavIndex
            entry.savedStateHandle[TAB_OWNER] = owner
            selectedNavIndex = owner
        }
        navController.addOnDestinationChangedListener(updateTabOwner)
        onDispose { navController.removeOnDestinationChangedListener(updateTabOwner) }
    }
    val entryState=backStackEntry?.lifecycle?.currentStateFlow?.collectAsState()?.value
    val navigating=entryState!=Lifecycle.State.RESUMED


    val showChrome = onboardingComplete && currentDestination?.showsNavigationChrome() == true
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0

    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val useRail = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
    )

    val startDestination: Route = if (onboardingComplete) Route.Home else Route.Onboarding

    LaunchedEffect(Unit) {
        driveMissing.collect {
            val graphReady = withTimeoutOrNull(NAV_READY_TIMEOUT_MS.milliseconds) {
                while (navController.currentBackStackEntry == null) delay(NAV_READY_POLL_MS.milliseconds)
                true
            } == true
            if (graphReady) navController.navigate(Route.Channels)
        }
    }

    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete) return@LaunchedEffect
        val graphReady = withTimeoutOrNull(NAV_READY_TIMEOUT_MS.milliseconds) {
            while (navController.currentBackStackEntry == null) delay(NAV_READY_POLL_MS.milliseconds)
            true
        } == true
        if (!graphReady) return@LaunchedEffect
        if (navController.currentDestination?.hasRoute(Route.Onboarding::class) == true) {
            return@LaunchedEffect
        }
        navController.navigate(Route.Onboarding) { popUpTo(0) { inclusive = true } }
    }

    LaunchedEffect(notificationDestination, onboardingComplete) {
        val target = notificationDestination ?: return@LaunchedEffect
        if (!onboardingComplete) return@LaunchedEffect

        val graphReady = withTimeoutOrNull(NAV_READY_TIMEOUT_MS.milliseconds) {
            while (navController.currentBackStackEntry == null) delay(NAV_READY_POLL_MS.milliseconds)
            true
        } == true
        if (!graphReady) return@LaunchedEffect

        when (target) {
            NotificationDestinations.TRANSFERS -> navController.navigate(Route.Transfers)
            NotificationDestinations.FILES -> navController.navigate(Route.Files())
            NotificationDestinations.NOTE -> navController.navigate(
                Route.NoteEditor(sharedText = pendingShare.text.value)
            )

            NotificationDestinations.UPDATE -> onUpdateRequested()

            else -> Unit
        }
        onDestinationHandled()
    }

    val showBottomChrome = showChrome && !keyboardVisible
    val bottomVisibility = remember { MutableTransitionState(false) }.apply { targetState = showBottomChrome }
    val initialChromeHeight = if (useRail) 0.dp else BottomBarHeight
    var measuredChromeHeight by remember(useRail) { mutableStateOf(initialChromeHeight) }
    var bottomChromeHeight by remember(useRail) { mutableStateOf(initialChromeHeight) }
    LaunchedEffect(measuredChromeHeight, navigating) {
        if (!navigating) bottomChromeHeight = measuredChromeHeight
    }
    val chromeDensity = LocalDensity.current
    val systemBottom = WindowInsets.navigationBars.getBottom(chromeDensity)
    val direction = LocalLayoutDirection.current
    val startInset = if (direction == LayoutDirection.Ltr) WindowInsets.navigationBars.getLeft(chromeDensity, direction)
        else WindowInsets.navigationBars.getRight(chromeDensity, direction)
    val navigateToTab: (Int) -> Unit = { index ->
        TopLevelDestination.entries.getOrNull(index)?.let { navigateTopLevel(navController, it, index == topLevelIndex) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val railWidth = if (useRail && onboardingComplete) 80.dp + with(chromeDensity) { startInset.toDp() } else 0.dp
        CompositionLocalProvider(
            app.tgdrive.ui.LocalNavigationInProgress provides navigating,
            LocalBottomBarHeight provides if (onboardingComplete && !keyboardVisible) bottomChromeHeight else 0.dp,
            com.infinity.drive.presentation.components.LocalRevealFileNavigation provides { file:com.infinity.drive.domain.model.DriveFile -> navController.navigateOnce(Route.Files(file.folderId,focusFileId=file.id)) },
            com.infinity.drive.presentation.components.LocalFileFolderNavigation provides { id:String? -> navController.navigateOnce(Route.Files(id)) },
            com.infinity.drive.presentation.navigation.LocalNavigationRailWidth provides railWidth
        ) {
            val capture = useGlass && !useRail && (bottomVisibility.currentState || bottomVisibility.targetState)
            val host = Modifier.fillMaxSize()
            val recordedHost = liquidNavigation?.capture(host, capture) ?: host
            AppNavHost(navController, startDestination, modifier = recordedHost.testTag("navigation-host"))
        }
        if(useRail) AnimatedVisibility(visible=showChrome,
            enter=fadeIn(tween(180)),exit=fadeOut(tween(180)),modifier=Modifier.align(Alignment.CenterStart)) {
            NavigationRail(modifier=Modifier.width(railWidth).fillMaxHeight(),containerColor=MaterialTheme.colorScheme.surfaceContainer) {
                TopLevelDestination.entries.forEachIndexed { index, destination ->
                    val label = stringResource(destination.labelRes)
                    val showLabels = navigationPreferences.navigationBarLabels
                    NavigationRailItem(
                        modifier = Modifier.testTag("navigation-${destination.name.lowercase()}"),
                        selected = index == topLevelIndex, onClick = { navigateToTab(index) },
                        icon = { Icon(destination.icon, if (showLabels) null else label) },
                        label = if (showLabels) ({ Text(label, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }) else null
                    )
                }
            }
        }
        AnimatedVisibility(
            visibleState = bottomVisibility,
            enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)) +
                slideInVertically(tween(300, easing = LinearOutSlowInEasing)) { it / 3 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it / 4 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = railWidth)
        ) {
            androidx.compose.foundation.layout.Column(
                Modifier.onSizeChanged { size ->
                    if (showBottomChrome) {
                        measuredChromeHeight = with(chromeDensity) { (size.height - systemBottom).coerceAtLeast(0).toDp() }
                    }
                }.then(if (useRail) Modifier.navigationBarsPadding() else Modifier)
            ) {
                CompositionLocalProvider(com.infinity.drive.presentation.platform.LocalOpenAudioPlayer provides { id ->
                    navController.navigateOnce(Route.Preview(fileId=id,categories="AUDIO",sortField=navigationPreferences.audioSortField.name,sortDescending=navigationPreferences.audioSortDirection==com.infinity.drive.domain.model.SortDirection.DESCENDING))
                }){com.infinity.drive.presentation.audio.LibraryAudioMiniPlayer()}
                if (!useRail) {
                    BottomNavigationBar(useGlass, navigationPreferences.navigationBarLabels, topLevelIndex, navigateToTab)
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    glass: Boolean,
    showLabels: Boolean,
    selectedIndex: Int,
    onNavigate: (Int) -> Unit
) {
    val liquid = LocalLiquidNavigation.current
    if (liquid != null) {
        val items = TopLevelDestination.entries.map {
            com.infinity.drive.presentation.platform.LiquidNavigationItem(
                it.icon, stringResource(it.labelRes), "navigation-${it.name.lowercase()}"
            )
        }
        liquid.Bar(items, selectedIndex, showLabels, glass, onNavigate)
        return
    }
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = colors.surfaceContainerHigh,
            shadowElevation = 6.dp
        ) {
            val fontScale = LocalDensity.current.fontScale
            val height = 64.dp + 24.dp * if (showLabels) (fontScale - 1f).coerceAtLeast(0f) else 0f
            androidx.compose.material3.NavigationBar(
                modifier = Modifier.heightIn(min = height),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                TopLevelDestination.entries.forEachIndexed { index, destination ->
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        modifier = Modifier.testTag("navigation-${destination.name.lowercase()}")
                            .then(if (showLabels) Modifier else Modifier.semantics { contentDescription = label }),
                        selected = index == selectedIndex,
                        onClick = { onNavigate(index) },
                        icon = { Icon(destination.icon, null) },
                        label = if (showLabels) ({
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }) else null,
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.onPrimaryContainer,
                            selectedTextColor = colors.onSurface,
                            indicatorColor = colors.primaryContainer,
                            unselectedIconColor = colors.onSurfaceVariant,
                            unselectedTextColor = colors.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

private fun navigateTopLevel(
    navController: NavHostController,
    destination: TopLevelDestination,
    alreadySelected: Boolean
) {
    navController.navigateTopLevelOnce(destination.route, alreadySelected) {
        popUpTo(Route.Home) { saveState = true }
        restoreState = true
        launchSingleTop = true
    }
}

private const val TAB_OWNER = "navigation-tab-owner"
private const val NAV_READY_TIMEOUT_MS = 5_000L
private const val NAV_READY_POLL_MS = 50L
