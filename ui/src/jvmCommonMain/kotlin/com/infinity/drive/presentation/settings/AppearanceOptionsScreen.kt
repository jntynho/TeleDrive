package com.infinity.drive.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tgdrive.ui.DriveIcons
import app.tgdrive.ui.LibraryDashboard
import app.tgdrive.ui.LibraryOverview
import app.tgdrive.ui.elasticHorizontalScroll as horizontalScroll
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import com.infinity.drive.domain.model.AccentColour
import com.infinity.drive.domain.model.DashboardStyle
import com.infinity.drive.presentation.components.PageHeader
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun AccentColourPicker(selected:AccentColour,dynamic:Boolean,onSelect:(AccentColour)->Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp)){
        Text("Accent colour",Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodyLarge)
        Text(if(dynamic)"Wallpaper colours are on. Choose a colour to use it instead."else selected.label,
            Modifier.padding(start=16.dp,end=16.dp,top=4.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
            AccentColour.entries.forEach{colour->
                Box(Modifier.size(48.dp).clip(CircleShape).selectable(!dynamic&&selected==colour,role=Role.RadioButton,onClick={onSelect(colour)})
                    .semantics{contentDescription="${colour.label} accent"},contentAlignment=Alignment.Center){
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(colour.light))
                        .then(if(!dynamic&&selected==colour)Modifier.border(2.dp,MaterialTheme.colorScheme.onSurface,CircleShape)else Modifier),contentAlignment=Alignment.Center){
                        if(!dynamic&&selected==colour)Icon(DriveIcons.Check,null,Modifier.size(20.dp),tint=Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardStylesScreen(onBack:()->Unit,viewModel:SettingsViewModel=koinViewModel()){
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    com.infinity.drive.presentation.common.AppBackHandler{onBack()}
    val demo=remember{LibraryOverview(1284,26_628_797_235,listOf(9_450_000_000,11_350_000_000,3_200_000_000,2_628_797_235))}
    Scaffold(topBar={PageHeader(title={Text("Dashboard Style")},navigationIcon={IconButton(onBack){Icon(DriveIcons.ArrowBack,"Back")}})}){padding->
        Column(Modifier.fillMaxSize().padding(top=padding.calculateTopPadding(),bottom=padding.calculateBottomPadding()+com.infinity.drive.presentation.navigation.LocalBottomBarInset.current)
            .verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text("Preview each style in Light or Dark, then choose Use this style. Preview data is for illustration.",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            DashboardStyle.entries.forEach{style->
                DashboardStyleOption(style,demo,state.preferences.dashboardStyle==style,style.name in state.preferences.dashboardDarkStyles){dark->
                    viewModel.update{prefs->prefs.copy(dashboardStyle=style,dashboardDarkStyles=
                        if(dark)prefs.dashboardDarkStyles+style.name else prefs.dashboardDarkStyles-style.name)}
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Preview controls never apply a style accidentally; an explicit button saves style and tone together. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardStyleOption(style:DashboardStyle,usage:LibraryOverview,selectedStyle:Boolean,initialDark:Boolean,onApply:(Boolean)->Unit){
    var dark by rememberSaveable(style.name,initialDark){mutableStateOf(initialDark)}
    val applied=selectedStyle&&dark==initialDark
    val fontScale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    Surface(modifier=Modifier.fillMaxWidth().testTag("dashboard-option-${style.name}"),shape=RoundedCornerShape(28.dp),
        color=MaterialTheme.colorScheme.surfaceContainerLow,
        border=androidx.compose.foundation.BorderStroke(2.dp,if(applied)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)){
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Column(Modifier.fillMaxWidth().height((56*fontScale).dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(if(style==DashboardStyle.CLASSIC)"Classic · Default"else style.label,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(style.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            LibraryDashboard(usage,{},showAddAction=false,updatedLabel="Updated just now",style=style.name,dark=dark)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(maxOf(48.dp,(24*fontScale).dp))){
                listOf(false to "Light",true to "Dark").forEachIndexed{index,(value,label)->
                    SegmentedButton(selected=dark==value,onClick={dark=value},shape=SegmentedButtonDefaults.itemShape(index,2),
                        modifier=Modifier.weight(1f).testTag("dashboard-tone-${style.name}-$label")
                            .semantics{contentDescription="Preview ${style.label} in $label"}){Text(label,maxLines=1)}
                }
            }
            Button(onClick={onApply(dark)},enabled=!applied,modifier=Modifier.fillMaxWidth().height(maxOf(48.dp,(28*fontScale).dp))
                .testTag("apply-dashboard-${style.name}").semantics{selected=applied;contentDescription="Use ${style.label} dashboard"}){
                if(applied){Icon(DriveIcons.Check,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp))}
                Text(if(applied)"Applied"else"Use this style",maxLines=1)
            }
        }
    }
}
