// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.navigation

import com.infinity.drive.presentation.navigation.NavigationTransitions.forward
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.staticCompositionLocalOf

import androidx.compose.runtime.Composable
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
            UnderBottomBar {
                HomeScreen(
                    onOpenFile = { id, sequence ->
                        navController.navigateOnce(sequence.routeFor(id))
                    },
                    onOpenFolder = { navController.navigateOnce(Route.Files(it)) },
                    onOpenFiles={navController.navigateOnce(Route.Files())},
                    onOpenTransfers = { navController.navigateOnce(Route.Transfers) },
                    onOpenTrash = { navController.navigateOnce(Route.Trash) },
                    onOpenChannels = { navController.navigateOnce(Route.Channels) },
                    onOpenCollection = { navController.navigateOnce(Route.Collection(it.name)) },
                    onOpenBackupSettings = {
                        navController.navigateOnce(
                            Route.SettingsSection(SettingsSectionType.BACKUP.name)
                        )
                    }
                )
            }
        }
        stableComposable<Route.Files>(navController) { entry ->
            UnderBottomBar {
                val isRoot = entry.toRoute<Route.Files>().folderId == null
                FilesScreen(
                    requestUpload=entry.toRoute<Route.Files>().upload,
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
        ) {
            LocalPlatformScreens.current.Preview(
                onBack = { navController.popBackStackOnce() },
                onEditNote = { fileId, title ->
                    navController.navigateOnce(
                        Route.NoteEditor(fileId = fileId, title = title)
                    )
                }
            )
        }
        stableComposable<Route.Transfers>(navController) {
            TransfersScreen(onBack = { navController.popBackStackOnce() })
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
        stableComposable<Route.Collection>(navController) {
            CollectionScreen(
                onBack = { navController.popBackStackOnce() },
                onOpenFile = { id, sequence ->
                    navController.navigateOnce(sequence.routeFor(id))
                }
            )
        }
        stableComposable<Route.NoteEditor>(navController) {
            NoteEditorScreen(onBack = { navController.popBackStackOnce() })
        }
        stableComposable<Route.Channels>(navController) {
            ChannelsScreen(onBack = { navController.popBackStackOnce() })
        }
        stableComposable<Route.Trash>(navController) {
            TrashScreen(onBack = { navController.popBackStackOnce() })
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
        val ownsBottomInset = T::class in setOf<kotlin.reflect.KClass<*>>(Route.Home::class, Route.Gallery::class, Route.Audio::class, Route.Settings::class, Route.Files::class)
        Box(Modifier.fillMaxSize().padding(start = if(chrome) LocalNavigationRailWidth.current else 0.dp,
            bottom = if(chrome && !ownsBottomInset) LocalBottomBarHeight.current else 0.dp)) {
            content(entry)
        }
    }
}

/** Fixed rail width is applied per entry, never to the animated host itself. */
val LocalNavigationRailWidth = staticCompositionLocalOf { 0.dp }
