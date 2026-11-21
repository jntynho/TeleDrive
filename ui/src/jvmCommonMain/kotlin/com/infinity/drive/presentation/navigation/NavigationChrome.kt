package com.infinity.drive.presentation.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

internal val fullScreenRoutes = setOf<kotlin.reflect.KClass<*>>(
    Route.Onboarding::class,
    Route.Preview::class,
    Route.NoteEditor::class,
    Route.Search::class,
    Route.RecoveryExport::class
)

internal fun NavDestination.showsNavigationChrome(): Boolean = fullScreenRoutes.none { hasRoute(it) }
