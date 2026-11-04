// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.infinity.drive.core.common.NotificationDestinations
import com.infinity.drive.core.files.PendingShare
import androidx.compose.ui.platform.LocalLocaleList
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import com.infinity.drive.domain.model.AppLanguage
import com.infinity.drive.domain.model.AppTheme
import com.infinity.drive.presentation.applock.LockScreen
import com.infinity.drive.presentation.components.LoadingState
import com.infinity.drive.presentation.components.LocalCompactLayout
import com.infinity.drive.presentation.platform.LocalAppVersion
import com.infinity.drive.presentation.platform.LocalUrlOpener
import com.infinity.drive.presentation.navigation.AppNavHost
import com.infinity.drive.presentation.navigation.BottomBarHeight
import com.infinity.drive.presentation.navigation.LocalBottomBarHeight
import com.infinity.drive.presentation.navigation.Route
import com.infinity.drive.presentation.navigation.TopLevelDestination
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
    val targetLocaleList = remember(state.language, systemLocaleList) {
        when (state.language) {
            AppLanguage.SYSTEM -> systemLocaleList
            AppLanguage.ENGLISH -> LocaleList(Locale("en"))
            AppLanguage.RUSSIAN -> LocaleList(Locale("ru"))
            AppLanguage.PORTUGUESE_BR -> LocaleList(Locale("pt-BR"))
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
        dynamicColor = state.dynamicColor,
        amoled = state.theme == AppTheme.AMOLED
    ) {
        CompositionLocalProvider(
            (LocalLocaleList as ProvidableCompositionLocal<LocaleList>) provides targetLocaleList,
            LocalCompactLayout provides state.compactLayout
        ) {
            var contentFrameReady by remember { mutableStateOf(false) }
            LaunchedEffect(state.loading){if(!state.loading){withFrameNanos{};withFrameNanos{};contentFrameReady=true}}
            val warmup=remember{androidx.compose.animation.core.MutableTransitionState(true)}
            warmup.targetState=state.loading||!contentFrameReady
            val preparing=warmup.currentState||warmup.targetState
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
            androidx.compose.animation.AnimatedVisibility(visibleState=warmup,enter=fadeIn(tween(200)),exit=fadeOut(tween(360))){com.infinity.drive.presentation.components.StartupSurface()}
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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val navigationFocus=androidx.compose.ui.platform.LocalFocusManager.current
    val navigationKeyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(backStackEntry?.id){navigationFocus.clearFocus();navigationKeyboard?.hide()}
    var requestedTab by remember { mutableStateOf<TopLevelDestination?>(null) }
    LaunchedEffect(requestedTab,backStackEntry) {
        val target=requestedTab ?: return@LaunchedEffect
        val entry=backStackEntry ?: return@LaunchedEffect
        entry.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        if(navController.currentDestination?.hasRoute(target.route::class)!=true)navigateTopLevel(navController,target)
        requestedTab=null
    }

    val showChrome=onboardingComplete&&currentDestination!=null&&
        currentDestination.hasRoute(Route.Onboarding::class).not()&&currentDestination.hasRoute(Route.Preview::class).not()&&
        currentDestination.hasRoute(Route.NoteEditor::class).not()&&currentDestination.hasRoute(Route.Search::class).not()
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

    val audioPlayer=com.infinity.drive.presentation.platform.LocalAudioPlayback.current
    var bottomChromeHeight by remember { mutableStateOf(BottomBarHeight) }
    val chromeDensity=androidx.compose.ui.platform.LocalDensity.current
    val systemBottom=WindowInsets.navigationBars.getBottom(chromeDensity)


    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val railWidth=if(useRail&&onboardingComplete)80.dp else 0.dp
        CompositionLocalProvider(
            LocalBottomBarHeight provides if(onboardingComplete)bottomChromeHeight else 0.dp,
            com.infinity.drive.presentation.components.LocalFileFolderNavigation provides { id:String? -> navController.navigateOnce(Route.Files(id)) },
            com.infinity.drive.presentation.navigation.LocalNavigationRailWidth provides railWidth
        ) {
            AppNavHost(navController=navController,startDestination=startDestination,modifier=Modifier.fillMaxSize().testTag("navigation-host"))
        }
        if(useRail) AnimatedVisibility(visible=showChrome&&!keyboardVisible,
            enter=fadeIn(tween(180)),exit=fadeOut(tween(180)),modifier=Modifier.align(Alignment.CenterStart)) {
            NavigationRail(modifier=Modifier.width(80.dp).fillMaxHeight(),containerColor=MaterialTheme.colorScheme.surfaceContainer) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected=currentDestination?.hasRoute(destination.route::class)==true
                    NavigationRailItem(selected=selected,onClick={if(!selected)requestedTab=destination},
                        icon={Icon(if(selected)destination.selectedIcon else destination.icon,contentDescription=stringResource(destination.labelRes))},
                        label={Text(stringResource(destination.labelRes))})
                }
            }
        }
        val topLevelIndex=TopLevelDestination.entries.indexOfFirst { currentDestination?.hasRoute(it.route::class)==true }
        var selectedNavIndex by remember { mutableIntStateOf(0) }
        LaunchedEffect(topLevelIndex){if(topLevelIndex>=0)selectedNavIndex=topLevelIndex}
        AnimatedVisibility(visible=showChrome,enter=fadeIn(tween(180)),exit=fadeOut(tween(180)),
            modifier=Modifier.align(Alignment.BottomCenter).padding(start=railWidth)) {
            androidx.compose.foundation.layout.Column(Modifier.onSizeChanged {
                bottomChromeHeight=with(chromeDensity){(it.height-if(useRail)0 else systemBottom).coerceAtLeast(0).toDp()}
            }) {
                com.infinity.drive.presentation.audio.LibraryAudioMiniPlayer()
                if(!useRail&&!keyboardVisible)FloatingNavigationBar(selectedIndex=selectedNavIndex,onNavigate={requestedTab=it})
            }
        }
    }
}

/** TG Drive's four native destinations, with stable icons and labels. */
@Composable
private fun FloatingNavigationBar(selectedIndex:Int,onNavigate:(TopLevelDestination)->Unit) {
    androidx.compose.material3.NavigationBar(containerColor=MaterialTheme.colorScheme.background,tonalElevation=0.dp){
        TopLevelDestination.entries.forEachIndexed{index,destination->
            NavigationBarItem(modifier=Modifier.testTag("navigation-${destination.name.lowercase()}"),selected=index==selectedIndex,onClick={onNavigate(destination)},icon={Icon(destination.icon,null)},label={Text(stringResource(destination.labelRes),style=MaterialTheme.typography.labelSmall)},
                colors=androidx.compose.material3.NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,indicatorColor=androidx.compose.ui.graphics.Color.Transparent,unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,unselectedTextColor=MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

private fun navigateTopLevel(
    navController: NavHostController,
    destination: TopLevelDestination
) {
    navController.navigateOnce(destination.route) {
        popUpTo(Route.Home) { saveState = true }
        restoreState = true
        launchSingleTop = true
    }
}

private const val NAV_READY_TIMEOUT_MS = 5_000L
private const val NAV_READY_POLL_MS = 50L
