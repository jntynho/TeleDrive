package com.infinity.drive.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class SheetHost(val dismiss:()->Unit)
val LocalSheetCloseThen=staticCompositionLocalOf<((()->Unit)->Unit)>{{it()}}
private val LocalSheetHost=staticCompositionLocalOf<SheetHost?>{null}

/** Bounded, non-overshooting motion also respects the system animator duration scale. */
private object SheetMotion:MotionScheme {
    override fun <T> defaultSpatialSpec():FiniteAnimationSpec<T> = tween(300,easing=FastOutSlowInEasing)
    override fun <T> fastSpatialSpec():FiniteAnimationSpec<T> = tween(240,easing=FastOutSlowInEasing)
    override fun <T> slowSpatialSpec():FiniteAnimationSpec<T> = tween(340,easing=FastOutSlowInEasing)
    override fun <T> defaultEffectsSpec():FiniteAnimationSpec<T> = tween(220)
    override fun <T> fastEffectsSpec():FiniteAnimationSpec<T> = tween(160)
    override fun <T> slowEffectsSpec():FiniteAnimationSpec<T> = tween(260)
}

/** Keep the native modal/window, drag state and scrim while navigating between its pages. */
@Composable
fun <T> DriveSheetPage(page:T,content:@Composable (T)->Unit){
    AnimatedContent(page,transitionSpec={
        (fadeIn(tween(220,40))+slideInHorizontally(tween(280, easing=FastOutSlowInEasing)){it/16}) togetherWith
            (fadeOut(tween(140))+slideOutHorizontally(tween(220, easing=FastOutSlowInEasing)){-it/24}) using
            SizeTransform(clip=true){_,_->tween(280,easing=FastOutSlowInEasing)}
    },label="sheetPage"){content(it)}
}

/** One modal surface; each purpose keeps its own scrolling and selection semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    content: @Composable ColumnScope.(dismiss:()->Unit) -> Unit
) {
    val parent=LocalSheetHost.current
    if(parent!=null){Column{content(onDismissRequest)};return}
    val colors = MaterialTheme.colorScheme
    val surface = colors.surfaceContainerLow
    val scope=rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    var closing by remember{mutableStateOf(false)}
    val closeThen:((()->Unit)->Unit)={action->if(!closing){closing=true;scope.launch{
        try{sheetState.hide();latestDismiss();action()}finally{closing=false}
    }}}
    val dismiss:()->Unit={closeThen{}}
    MaterialTheme(motionScheme=SheetMotion){
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = surface,
        contentColor = colors.onSurface,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = .48f),
        contentWindowInsets = contentWindowInsets,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 36.dp, height = 4.dp,
                color = colors.onSurfaceVariant.copy(alpha = .35f)
            )
        }
    ) {
        val scope = this
        // Default ListItems must blend into the modal rather than painting rectangular patches.
        MaterialTheme(colorScheme = colors.copy(surface = surface, surfaceContainer = surface,
            surfaceContainerLow = surface, surfaceContainerHigh = surface)) {
            CompositionLocalProvider(LocalSheetHost provides SheetHost(dismiss),LocalSheetCloseThen provides closeThen){content(scope,dismiss)}
        }
    }
    }
}

@Composable
fun SheetActionRow(label: String, icon: ImageVector, enabled: Boolean = true, detail:String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if(enabled)1f else .38f)
        Icon(icon, null, Modifier.size(24.dp), tint = color)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
            if(detail!=null)Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
