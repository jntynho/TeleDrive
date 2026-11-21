package com.infinity.drive.presentation.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.infinity.drive.domain.model.NavigationBarStyle
import com.infinity.drive.domain.model.UserPreferences

internal val fullScreenRoutes = setOf<kotlin.reflect.KClass<*>>(
    Route.Onboarding::class,
    Route.Preview::class,
    Route.NoteEditor::class,
    Route.Search::class,
    Route.RecoveryExport::class
)

internal fun NavDestination.showsNavigationChrome(): Boolean = fullScreenRoutes.none { hasRoute(it) }

internal fun NavigationBarStyle.canBlur(supportsGlass: Boolean, supportsBackdropBlur: Boolean): Boolean =
    when (this) {
        NavigationBarStyle.STANDARD -> supportsBackdropBlur
        NavigationBarStyle.FLOATING -> supportsGlass
        NavigationBarStyle.MIUIX -> false
    }

internal fun UserPreferences.activeNavigationStyle(): NavigationBarStyle =
    if (floatingNavigationBar) navigationBarStyle else NavigationBarStyle.STANDARD
