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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        Column(Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text("Choose how Your library appears. Preview data is for illustration.",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            DashboardStyle.entries.forEach{style->
                val selected=state.preferences.dashboardStyle==style
                Surface(shape=RoundedCornerShape(28.dp),color=Color.Transparent,border=androidx.compose.foundation.BorderStroke(if(selected)2.dp else 1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)){
                    Column(Modifier.fillMaxWidth().selectable(selected,role=Role.RadioButton,onClick={viewModel.update{it.copy(dashboardStyle=style)}}).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f).padding(start=4.dp)){
                                Text(if(style==DashboardStyle.CLASSIC)"Classic · Default"else style.label,style=MaterialTheme.typography.titleMedium)
                                Text(style.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected,onClick=null)
                        }
                        LibraryDashboard(demo,{},showAddAction=false,updatedLabel="Updated just now",style=style.name)
                    }
                }
            }
            Spacer(Modifier.height(24.dp+padding.calculateBottomPadding()+com.infinity.drive.presentation.navigation.LocalBottomBarInset.current))
        }
    }
}
