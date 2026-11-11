package app.tgdrive.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A single, fixed-height toolbar. Opening search never resizes the content below it. */
@Composable
fun ExpandableLibraryHeader(
    title:String, query:String, onQueryChange:(String)->Unit, expanded:Boolean,
    onExpandedChange:(Boolean)->Unit, placeholder:String,
    navigationIcon:@Composable ()->Unit={}, actions:@Composable RowScope.()->Unit={},
    titleStyle:androidx.compose.ui.text.TextStyle?=null,
    requestSearchFocus:Boolean=true
) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val requester=remember{FocusRequester()}
    val owner=LocalLifecycleOwner.current
    LaunchedEffect(expanded){if(!expanded&&requestSearchFocus){focus.clearFocus();keyboard?.hide()}}
    fun close(){focus.clearFocus();keyboard?.hide();onQueryChange("");onExpandedChange(false)}
    Surface(color=MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp)) {
            AnimatedContent(expanded,modifier=Modifier.fillMaxSize(),transitionSpec={
                (fadeIn(tween(180)) togetherWith fadeOut(tween(180)))
                    .using(SizeTransform(clip=true){_,_->androidx.compose.animation.core.snap()})
            },label="toolbarSearch"){ searching ->
                Row(Modifier.fillMaxSize().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(searching){
                        IconButton(::close){Icon(DriveIcons.ArrowBack,"Close search")}
                        BasicTextField(query,onQueryChange,singleLine=true,
                            textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),
                            cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                            keyboardActions=KeyboardActions(onSearch={keyboard?.hide()}),
                            modifier=Modifier.weight(1f).heightIn(min=48.dp).focusRequester(requester).semantics{contentDescription=placeholder},
                            decorationBox={input->Box(contentAlignment=Alignment.CenterStart){if(query.isEmpty())Text(placeholder,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyLarge);input()}})
                        if(query.isNotEmpty())IconButton({onQueryChange("")}){Icon(DriveIcons.Close,"Clear search")}
                        LaunchedEffect(expanded,owner){
                             if(expanded&&requestSearchFocus){
                                 owner.lifecycle.currentStateFlow.first{it.isAtLeast(Lifecycle.State.RESUMED)}
                                 kotlinx.coroutines.delay(180)
                                 requester.requestFocus();keyboard?.show()
                             }
                         }
                    }else{
                        navigationIcon()
                        Text(title,Modifier.weight(1f).padding(start=8.dp),style=titleStyle?:MaterialTheme.typography.headlineLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                        IconButton({onExpandedChange(true)}){Icon(DriveIcons.Search,"Search ${title.lowercase()}")}
                        actions()
                    }
                }
            }
        }
    }
}
