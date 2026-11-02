package com.infinity.drive.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared secondary-page chrome, including selection mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageHeader(
    title:@Composable ()->Unit,
    modifier:Modifier=Modifier,
    navigationIcon:@Composable ()->Unit={},
    actions:@Composable RowScope.()->Unit={},
    colors:TopAppBarColors=TopAppBarDefaults.topAppBarColors(
        containerColor=MaterialTheme.colorScheme.background,
        scrolledContainerColor=MaterialTheme.colorScheme.background),
    scrollBehavior:TopAppBarScrollBehavior?=null,
    windowInsets:WindowInsets=TopAppBarDefaults.windowInsets
){
    TopAppBar(title={ProvideTextStyle(MaterialTheme.typography.titleLarge){title()}},modifier=modifier,
        navigationIcon=navigationIcon,actions=actions,colors=colors,scrollBehavior=scrollBehavior,
        windowInsets=windowInsets,expandedHeight=64.dp)
}
