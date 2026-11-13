// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.navigation

import androidx.navigation.NavDestination.Companion.hasRoute

import com.infinity.drive.presentation.navigation.NavigationTransitions.forward
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.staticCompositionLocalOf

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import com.infinity.drive.presentation.hidden.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.album_not_in_folder
import com.infinity.drive.presentation.channels.ChannelsScreen
import com.infinity.drive.presentation.collection.CollectionScreen
import com.infinity.drive.presentation.files.FilesScreen
import com.infinity.drive.presentation.gallery.GalleryScreen
import com.infinity.drive.presentation.home.HomeScreen
import com.infinity.drive.presentation.platform.LocalPlatformScreens
import com.infinity.drive.presentation.note.NoteEditorScreen
import com.infinity.drive.presentation.onboarding.OnboardingScreen
import com.infinity.drive.presentation.search.SearchScreen
import com.infinity.drive.presentation.proxy.ProxyScreen
import com.infinity.drive.presentation.settings.ExclusionsScreen
import com.infinity.drive.presentation.settings.SettingsScreen
import com.infinity.drive.presentation.settings.SettingsSectionScreen
import com.infinity.drive.presentation.settings.SettingsSectionType
import com.infinity.drive.presentation.transfers.TransfersScreen
import com.infinity.drive.presentation.trash.TrashScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: Route,
    modifier: Modifier = Modifier
) {
    NavigationCoordinator(navController)
    val entry by navController.currentBackStackEntryAsState()
    val folderHistory=androidx.compose.runtime.saveable.rememberSaveable(saver=androidx.compose.runtime.saveable.listSaver(
        save={it:com.infinity.drive.presentation.components.FolderNavigationHistory->it.snapshot()},
        restore={com.infinity.drive.presentation.components.FolderNavigationHistory(it)}
    )){com.infinity.drive.presentation.components.FolderNavigationHistory()}
    val folderMotion=androidx.compose.runtime.remember(entry?.id){folderHistory.visit(entry?.id,entry?.destination?.hasRoute<Route.Files>()==true)}
    val hidden=org.koin.compose.koinInject<com.infinity.drive.core.security.HiddenContentAccess>()
    val hiddenState by hidden.lock.state.collectAsStateWithLifecycle()
    if(hiddenState.unlocked)SensitiveScreen()
    val collectionLocks=org.koin.compose.koinInject<com.infinity.drive.core.security.CollectionLocks>()
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner,collectionLocks){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)collectionLocks.lockAll()}
        owner.lifecycle.addObserver(observer)
        onDispose{owner.lifecycle.removeObserver(observer)}
    }
    val rawFiles=org.koin.compose.koinInject<com.infinity.drive.domain.repository.FileRepository>()
    LaunchedEffect(entry?.id){
        val current=entry?:return@LaunchedEffect
        val inside=when {
            current.destination.hasRoute<Route.HiddenSettings>()->true
            current.destination.hasRoute<Route.Collection>()->current.toRoute<Route.Collection>().type==com.infinity.drive.presentation.collection.CollectionType.HIDDEN.name
            current.destination.hasRoute<Route.Preview>()->rawFiles.getFile(current.toRoute<Route.Preview>().fileId)?.let{hidden.protectedFile(it)}==true
            current.destination.hasRoute<Route.NoteEditor>()->current.toRoute<Route.NoteEditor>().let{route->route.fileId?.let{rawFiles.getFile(it)?.let{file->hidden.protectedFile(file)}}?:hidden.protectedFolder(route.folderId)}
            current.destination.hasRoute<Route.Files>()->current.toRoute<Route.Files>().let{route->hidden.protectedFolder(route.folderId)||route.focusFileId?.let{rawFiles.getFile(it)?.let{file->hidden.protectedFile(file)}}==true}
            else->false
        }
        // Also invalidate verification started inside Trash or a guard after a policy mutation.
        // Public content stays mounted: guards and thumbnails no longer reset on this epoch.
        if(!inside)hidden.lock.lock()
        val collection=when {
            current.destination.hasRoute<Route.Collection>()->current.toRoute<Route.Collection>().type
            current.destination.hasRoute<Route.Trash>()->"TRASH"
            current.destination.hasRoute<Route.CollectionSettings>()->current.toRoute<Route.CollectionSettings>().type
            current.destination.hasRoute<Route.Preview>()->return@LaunchedEffect
            else->null
        }
        collectionLocks.leaveExcept(collection)
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        sizeTransform={androidx.compose.animation.SizeTransform(clip=true){_,_->androidx.compose.animation.core.snap()}},
        enterTransition = { NavigationTransitions.enter(forward(true)) },
        exitTransition = { NavigationTransitions.exit(forward(true)) },
        popEnterTransition = { NavigationTransitions.enter(forward(false)) },
        popExitTransition = { NavigationTransitions.exit(forward(false)) }
    ) {
        stableComposable<Route.Onboarding>(navController) {
            OnboardingScreen(
                onOpenProxy = { navController.navigateOnce(Route.Proxy) },
                onFinished = {
                    navController.navigateOnce(Route.Home) {
                        popUpTo(Route.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        stableComposable<Route.Home>(navController) {
            val openHidden=rememberCollectionEntry(hidden.lock,"Hidden"){navController.navigateOnce(Route.Collection("HIDDEN"))}
            val openFavorites=rememberCollectionEntry(collectionLocks.manager("FAVORITES"),"Favorites"){navController.navigateOnce(Route.Collection("FAVORITES"))}
            val openArchived=rememberCollectionEntry(collectionLocks.manager("ARCHIVED"),"Archived"){navController.navigateOnce(Route.Collection("ARCHIVED"))}
            val openTrash=rememberCollectionEntry(collectionLocks.manager("TRASH"),"Trash"){navController.navigateOnce(Route.Trash)}
            UnderBottomBar {
                HomeScreen(
                    onOpenFile = { id, sequence ->
                        navController.navigateOnce(sequence.routeFor(id))
                    },
                    onOpenFolder = { navController.navigateOnce(Route.Files(it)) },
                    onOpenFiles={navController.navigateOnce(Route.Files())},
                    onBrowseCategory={index->
                        when(index){
                            0->navController.navigateOnce(Route.Gallery(initialTab="PHOTOS"))
                            1->navController.navigateOnce(Route.Gallery(initialTab="VIDEOS"))
                            2->navController.navigateOnce(Route.Audio(true))
                            else->navController.navigateOnce(Route.Files())
                        }
                    },
                    onOpenTransfers = { navController.navigateOnce(Route.Transfers) },
                    onOpenTrash = openTrash,
                    onOpenChannels = { navController.navigateOnce(Route.Channels) },
                    onOpenCollection = { when(it.name){"HIDDEN"->openHidden();"ARCHIVED"->openArchived();else->openFavorites()} },
                    onOpenBackupSettings = {
                        navController.navigateOnce(
                            Route.SettingsSection(SettingsSectionType.BACKUP.name)
                        )
                    }
                )
            }
        }
        stableComposable<Route.Files>(navController,
            enterTransition={if(initialState.destination.hasRoute<Route.Files>())androidx.compose.animation.EnterTransition.None else null},
            exitTransition={if(targetState.destination.hasRoute<Route.Files>())androidx.compose.animation.ExitTransition.None else null}
        ) { entry ->
            val bodyMotion=if(folderMotion.enabled)Modifier.animateEnterExit(
                enter=com.infinity.drive.presentation.components.FolderContentTransitions.enter(folderMotion.forward),
                exit=com.infinity.drive.presentation.components.FolderContentTransitions.exit(folderMotion.forward)
            )else Modifier
            UnderBottomBar {
                val isRoot = entry.toRoute<Route.Files>().folderId == null
                HiddenContentGuard(folderId=entry.toRoute<Route.Files>().folderId,onBack={navController.popBackStackOnce()}){
                FilesScreen(
                    requestUpload=entry.toRoute<Route.Files>().upload,
                    contentMotion=bodyMotion,
                onOpenFolder = { navController.navigateOnce(Route.Files(it)) },
                    onOpenCrumb = { folderId ->
                        val route = Route.Files(folderId)
                        if (!navController.popBackStack(route, inclusive = false)) {
                            navController.navigateOnce(route)
                        }
                    },
                    onOpenFile = { id, sequence ->
                        navController.navigateOnce(sequence.routeFor(id))
                    },
                    onOpenSearch = { navController.navigateOnce(Route.Search) },
                    onNewNote = { folderId ->
                        navController.navigateOnce(Route.NoteEditor(folderId = folderId))
                    },
                    onEditNote = { fileId, title ->
                        navController.navigateOnce(
                            Route.NoteEditor(fileId = fileId, title = title)
                        )
                    },
                    onBack = { navController.popBackStackOnce() }
                )
                }
            }
        }
        stableComposable<Route.Audio>(navController) {
            UnderBottomBar {
                com.infinity.drive.presentation.audio.AudioScreen(onOpenFile={id,sequence->navController.navigateOnce(sequence.routeFor(id))})
            }
        }
        stableComposable<Route.Gallery>(navController) {
            UnderBottomBar {
                val unfiledAlbumName = stringResource(Res.string.album_not_in_folder)
                GalleryScreen(
                    onOpenFile = { id, sequence ->
                        navController.navigateOnce(sequence.routeFor(id))
                    },
                    onOpenAlbum = { album ->
                        navController.navigateOnce(
                            Route.GalleryAlbum(album.folderId, album.name ?: unfiledAlbumName)
                        )
                    }
                )
            }
        }
        stableComposable<Route.GalleryAlbum>(navController) {
            GalleryScreen(
                onOpenFile = { id, sequence ->
                    navController.navigateOnce(sequence.routeFor(id))
                },
                onBack = { navController.popBackStackOnce() }
            )
        }
        stableComposable<Route.Preview>(navController,
            enterTransition = { NavigationTransitions.enter(forward(true)) },
            exitTransition = { NavigationTransitions.exit(forward(true)) },
            popEnterTransition = { NavigationTransitions.enter(forward(false)) },
            popExitTransition = { NavigationTransitions.exit(forward(false)) }
        ) { entry ->
            HiddenContentGuard(fileId=entry.toRoute<Route.Preview>().fileId,onBack={navController.popBackStackOnce()}){
            LocalPlatformScreens.current.Preview(
                onBack = { navController.popBackStackOnce() },
                onEditNote = { fileId, title ->
                    navController.navigateOnce(
                        Route.NoteEditor(fileId = fileId, title = title)
                    )
                }
            )
            }
        }
        stableComposable<Route.Transfers>(navController) {
            TransfersScreen(onBack={navController.popBackStackOnce()},onOpenFile={id,sequence->navController.navigateOnce(sequence.routeFor(id))})
        }
        stableComposable<Route.Search>(navController) {
            SearchScreen(
                onBack = { navController.popBackStackOnce() },
                onOpenFile = { id, sequence ->
                    navController.navigateOnce(sequence.routeFor(id))
                },
                onOpenFolder = { navController.navigateOnce(Route.Files(it)) }
            )
        }
        stableComposable<Route.Collection>(navController) { entry ->
            val back:()->Unit={navController.popBackStackOnce()}
            val open:(String,com.infinity.drive.presentation.preview.PreviewSequence)->Unit={id,sequence->navController.navigateOnce(sequence.routeFor(id))}
            if(entry.toRoute<Route.Collection>().type==com.infinity.drive.presentation.collection.CollectionType.HIDDEN.name)HiddenCollectionGate(back,open){navController.navigateOnce(Route.HiddenSettings)}
            else CollectionAccessGate(entry.toRoute<Route.Collection>().type,back){CollectionScreen(onBack=back,onOpenFile=open,onManageHidden={navController.navigateOnce(Route.CollectionSettings(entry.toRoute<Route.Collection>().type))})}
        }
        stableComposable<Route.CollectionSettings>(navController){entry->CollectionSettingsScreen(entry.toRoute<Route.CollectionSettings>().type){navController.popBackStackOnce()}}
        stableComposable<Route.HiddenSettings>(navController){HiddenSettingsScreen{navController.popBackStackOnce()}}
        stableComposable<Route.NoteEditor>(navController) { entry ->
            val route=entry.toRoute<Route.NoteEditor>()
            HiddenContentGuard(fileId=route.fileId,folderId=route.folderId,onBack={navController.popBackStackOnce()}){
                NoteEditorScreen(onBack={navController.popBackStackOnce()})
            }
        }
        stableComposable<Route.Channels>(navController) {
            ChannelsScreen(onBack = { navController.popBackStackOnce() })
        }
        stableComposable<Route.Trash>(navController) {
            CollectionAccessGate("TRASH",{navController.popBackStackOnce()}){TrashScreen(onBack={navController.popBackStackOnce()},onManageLock={navController.navigateOnce(Route.CollectionSettings("TRASH"))},onOpenFile={id->navController.navigateOnce(Route.Preview(fileId=id,fromTrash=true))})}
        }
        stableComposable<Route.Settings>(navController) {
            UnderBottomBar {
                SettingsScreen(
                    onBack = null,
                    onOpenActivity={navController.navigateOnce(Route.Transfers)},
                    onOpenSetting={section,title->navController.navigateOnce(Route.SettingsSection(section.name,title))},
                    onOpenSection = { section ->
                        navController.navigateOnce(Route.SettingsSection(section.name))
                    }
                )
            }
        }
        stableComposable<Route.SettingsSection>(navController) { entry ->
            val route = entry.toRoute<Route.SettingsSection>()
            val section = SettingsSectionType.entries
                .firstOrNull { it.name == route.section }
                ?: SettingsSectionType.ACCOUNT
            CompositionLocalProvider(com.infinity.drive.presentation.settings.LocalSettingsSearchTarget provides route.focusTitle) {
            SettingsSectionScreen(
                section = section,
                onOpenHidden={navController.navigateOnce(Route.Collection(com.infinity.drive.presentation.collection.CollectionType.HIDDEN.name))},
                onOpenChannels = { navController.navigateOnce(Route.Channels) },
                onOpenPreviousLibrary = { navController.navigateOnce(Route.PreviousLibrary) },
                onBack = { navController.popBackStackOnce() },
                onOpenExclusions = { navController.navigateOnce(Route.Exclusions) },
                onOpenProxy = { navController.navigateOnce(Route.Proxy) },
                onLoggedOut = {
                    navController.navigateOnce(Route.Onboarding) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
            }
        }
        stableComposable<Route.PreviousLibrary>(navController) {
            LocalPlatformScreens.current.PreviousLibrary(onBack = { navController.popBackStackOnce() })
        }
        stableComposable<Route.Exclusions>(navController) {
            ExclusionsScreen(onBack = { navController.popBackStackOnce() })
        }
        stableComposable<Route.Proxy>(navController) {
            ProxyScreen(onBack = { navController.popBackStackOnce() })
        }
    }
}

/**
 * Screens that sit under the bottom bar reserve its height for their whole
 * lifetime. Reading it per screen rather than from the current destination
 * keeps an outgoing screen's padding steady while it animates away.
 */
@Composable
private fun UnderBottomBar(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBottomBarInset provides LocalBottomBarHeight.current,
        content = content
    )
}

/** Disable gesture-progress previews while retaining the ordinary committed pop transition. */
private inline fun <reified T:Any> androidx.navigation.NavGraphBuilder.stableComposable(
    controller:NavHostController,
    noinline enterTransition:(androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.()->androidx.compose.animation.EnterTransition?)?=null,
    noinline exitTransition:(androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.()->androidx.compose.animation.ExitTransition?)?=null,
    noinline popEnterTransition:(androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.()->androidx.compose.animation.EnterTransition?)?=enterTransition,
    noinline popExitTransition:(androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.()->androidx.compose.animation.ExitTransition?)?=exitTransition,
    noinline content:@Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry)->Unit
) {
    composable<T>(enterTransition=enterTransition,exitTransition=exitTransition,popEnterTransition=popEnterTransition,popExitTransition=popExitTransition){entry->
        com.infinity.drive.presentation.common.AppBackHandler(controller.previousBackStackEntry!=null){controller.popBackStackOnce()}
        val chrome = T::class !in setOf<kotlin.reflect.KClass<*>>(Route.Onboarding::class, Route.Preview::class, Route.NoteEditor::class, Route.Search::class)
        // Every page draws behind overlay chrome; reserve occlusion in scroll padding, never the viewport.
        CompositionLocalProvider(LocalBottomBarInset provides if(chrome)LocalBottomBarHeight.current else 0.dp){
            Box(Modifier.fillMaxSize().padding(start=if(chrome)LocalNavigationRailWidth.current else 0.dp)){
                content(entry)
            }
        }
    }
}

/** Fixed rail width is applied per entry, never to the animated host itself. */
val LocalNavigationRailWidth = staticCompositionLocalOf { 0.dp }
