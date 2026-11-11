package com.infinity.drive.presentation.input

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import app.tgdrive.ui.DriveIcons

/** Selection-aware editing without an IME. Backspace removes whole Unicode code points. */
fun editInlineText(value:TextFieldValue,insertion:String?=null,backspace:Boolean=false):TextFieldValue {
    val start=value.selection.min.coerceIn(0,value.text.length)
    val end=value.selection.max.coerceIn(start,value.text.length)
    val from=if(backspace&&start==end&&start>0)value.text.offsetByCodePoints(start,-1)else start
    val text=insertion.orEmpty()
    return TextFieldValue(value.text.replaceRange(from,end,text),TextRange(from+text.length))
}

enum class InlineInputMode { NUMBER, PHONE, TEXT, HEX }
class InlineTarget(val id:String,val mode:InlineInputMode,val read:()->TextFieldValue,
    val write:(TextFieldValue)->Unit,val enabled:()->Boolean,val paste:((String)->Unit)?=null)
class InlineAction(val label:String,val enabled:Boolean,val working:Boolean,val invoke:()->Unit)
@Stable class InlineInputController {
    var target by mutableStateOf<InlineTarget?>(null)
    var action by mutableStateOf<InlineAction?>(null)
    fun insert(text:String){target?.takeIf{it.enabled()}?.let{it.write(editInlineText(it.read(),text))}}
    fun backspace(){target?.takeIf{it.enabled()}?.let{it.write(editInlineText(it.read(),backspace=true))}}
    fun paste(text:String){target?.takeIf{it.enabled()}?.let{if(it.paste!=null)it.paste.invoke(text)else it.write(editInlineText(it.read(),text))}}
}
val LocalInlineInput=staticCompositionLocalOf<InlineInputController?>{null}

@Composable
fun InlineInputField(value:String,onChange:(String)->Unit,label:String,modifier:Modifier=Modifier,
    mode:InlineInputMode=InlineInputMode.TEXT,password:Boolean=false,enabled:Boolean=true,prefix:String?=null,
    supportingText:String?=null,onPaste:((String)->Unit)?=null,autoActivate:Boolean=true) {
    val controller=checkNotNull(LocalInlineInput.current){"InlineInputField needs an input controller"}
    val id=remember{java.util.UUID.randomUUID().toString()}
    val bring=remember{BringIntoViewRequester()}
    val scope=rememberCoroutineScope()
    var editor by remember{mutableStateOf(TextFieldValue(value,TextRange(value.length)))}
    var revealed by remember{mutableStateOf(false)}
    val currentChange by rememberUpdatedState(onChange)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentPaste by rememberUpdatedState(onPaste)
    if(editor.text!=value)editor=TextFieldValue(value,TextRange(value.length))
    val target=remember(id,mode){InlineTarget(id,mode,{editor},{next->
        val clean=when(mode){
            InlineInputMode.NUMBER->next.text.filter{it in '0'..'9'}.take(128)
            InlineInputMode.PHONE->next.text.filter{it in '0'..'9'||it=='+'}.take(32)
            InlineInputMode.HEX->next.text.uppercase(java.util.Locale.ROOT).filter{it in '0'..'9'||it in 'A'..'F'}.take(64)
            InlineInputMode.TEXT->(if(password)next.text else next.text.filterNot{it=='\n'||it=='\r'}).take(1024)
        }
        editor=if(clean==next.text)next else TextFieldValue(clean,TextRange(clean.length))
        currentChange(clean)
    },{currentEnabled},{text->if(currentPaste!=null)currentPaste!!.invoke(text)else controller.insert(text)})}
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    val navigating=app.tgdrive.ui.LocalNavigationInProgress.current
    LaunchedEffect(id,navigating){
        if(autoActivate&&controller.target==null)controller.target=target
        if(controller.target?.id==id&&!navigating){
            owner.lifecycle.currentStateFlow.first{it.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)}
            withFrameNanos{};bring.bringIntoView()
        }
    }
    DisposableEffect(id){onDispose{if(controller.target?.id==id)controller.target=null}}
    val active=controller.target?.id==id
    val border by animateColorAsState(if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,label="input-border")
    Column(modifier.bringIntoViewRequester(bring)){
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if(active)1.5.dp else 1.dp,border,RoundedCornerShape(18.dp))
            .clickable(enabled){controller.target=target}.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){
            if(prefix!=null){Text(prefix,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.width(14.dp))}
            BasicTextField(editor,{next->editor=next},readOnly=true,singleLine=true,enabled=enabled,
                textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),
                cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation=if(password&&!revealed)PasswordVisualTransformation()else VisualTransformation.None,
                modifier=Modifier.weight(1f).padding(vertical=16.dp).onFocusChanged{if(it.isFocused){controller.target=target;scope.launch{owner.lifecycle.currentStateFlow.first{it.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)};withFrameNanos{};bring.bringIntoView()}}}.semantics{
                    contentDescription=label
                    onClick{controller.target=target;true}
                    setText{if(enabled){controller.target=target;target.write(TextFieldValue(it.text,TextRange(it.length)));true}else false}
                })
            if(password)IconButton({revealed=!revealed}){Icon(if(revealed)DriveIcons.VisibilityOff else DriveIcons.Visibility,if(revealed)"Hide password"else"Show password",Modifier.size(21.dp))}
        }
        supportingText?.let{Text(it,Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable
fun InlineKeyboard(controller:InlineInputController,modifier:Modifier=Modifier,compact:Boolean=false,numericLeading:String?="paste",onLeadingAction:(()->Unit)?=null) {
    val target=controller.target?:return
    val clipboard=LocalClipboardManager.current
    val numeric=target.mode==InlineInputMode.NUMBER||target.mode==InlineInputMode.PHONE
    var shift by remember{mutableStateOf(false)}
    var symbols by remember{mutableIntStateOf(0)}
    var language by remember{mutableIntStateOf(0)}
    val enabled=target.enabled()
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(if(numeric)8.dp else 6.dp)){
        if(numeric){
            listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf(numericLeading.orEmpty(),"0","delete")).forEach{row->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    row.forEach{key->InputKey(key,enabled,Modifier.weight(1f).height(if(compact)48.dp else 56.dp)){
                        when(key){"fingerprint"->onLeadingAction?.invoke();"delete"->controller.backspace();"paste"->clipboard.getText()?.text?.let(controller::paste);else->controller.insert(key)}
                    }}
                }
            }
        }else{
            val rows=when {
                target.mode==InlineInputMode.HEX->listOf("012345","6789AB","CDEF")
                symbols==1->listOf("1234567890","@#%&*()-_=","+[]{}!?.,")
                symbols==2->listOf("~`|\\/<>:;","$€£¥^°•…'\"","áàâäéèêëçñ")
                language==1->if(shift)listOf("أإآٱپچڤگژ","ئؤءةى","لا")else listOf("ضصثقفغعهخحجد","شسيبلاتنمكط","ئءؤرلاىةوزظذ")
                language==2->listOf("ёйцукенгшщзхъ","фывапролджэ","ячсмитьбю")
                else->listOf("qwertyuiop","asdfghjkl","zxcvbnm")
            }
            rows.forEachIndexed{index,row->
                Row(Modifier.fillMaxWidth().padding(horizontal=if(index==1)10.dp else 0.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    if(index==rows.lastIndex&&symbols==0&&target.mode!=InlineInputMode.HEX)InputKey("shift",enabled,Modifier.weight(1.2f).height(if(compact)48.dp else 54.dp)){shift=!shift}
                    row.forEach{ch->val key=if(shift)ch.uppercaseChar().toString()else ch.toString()
                        InputKey(key,enabled,Modifier.weight(1f).height(if(compact)48.dp else 54.dp),small=true){controller.insert(key)}
                    }
                    if(index==rows.lastIndex)InputKey("delete",enabled,Modifier.weight(1.2f).height(if(compact)48.dp else 54.dp)){controller.backspace()}
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                if(target.mode!=InlineInputMode.HEX)InputKey(if(symbols==0)"123"else if(symbols==1)"#+="else"ABC",enabled,Modifier.weight(1.3f).height(if(compact)48.dp else 54.dp),small=true){symbols=(symbols+1)%3}
                if(target.mode!=InlineInputMode.HEX)InputKey("language",enabled,Modifier.weight(1f).height(if(compact)48.dp else 54.dp)){language=(language+1)%3;symbols=0}
                if(target.mode!=InlineInputMode.HEX)InputKey("space",enabled,Modifier.weight(4f).height(if(compact)48.dp else 54.dp)){controller.insert(" ")}
                InputKey("paste",enabled,Modifier.weight(1f).height(if(compact)48.dp else 54.dp)){clipboard.getText()?.text?.let(controller::paste)}
            }
        }
    }
}

@Composable
private fun InputKey(key:String,enabled:Boolean,modifier:Modifier=Modifier,small:Boolean=false,onClick:()->Unit){
    val label=when(key){"delete"->"Delete last character";"paste"->"Paste";"shift"->"Uppercase";"language"->"Change input language";"space"->"Space";"fingerprint"->"Unlock with fingerprint";else->key}
    if(key.isEmpty()){Spacer(modifier);return}
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(if(small)8.dp else 18.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier=modifier.semantics{contentDescription=label;role=Role.Button}){
        Box(contentAlignment=Alignment.Center){
            val icon=when(key){"delete"->DriveIcons.Backspace;"paste"->DriveIcons.ContentPaste;"shift"->DriveIcons.ArrowUpward;"language"->DriveIcons.Language;"fingerprint"->DriveIcons.Fingerprint;else->null}
            if(icon!=null)Icon(icon,null,Modifier.size(if(small)19.dp else 22.dp))
            else if(key!="space")BasicText(key,style=(if(small)MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall).copy(color=MaterialTheme.colorScheme.onSurface),maxLines=1,autoSize=TextAutoSize.StepBased(minFontSize=if(small)8.sp else 14.sp,maxFontSize=if(small)14.sp else 28.sp))
        }
    }
}

@Composable
fun InlineActionButton(controller:InlineInputController,fallbackLabel:String="Continue"){
    val action=controller.action?:InlineAction(fallbackLabel,false,false){}
    run{Button(onClick=action.invoke,enabled=action.enabled&&!action.working,
        shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
        if(action.working){CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp))}
        Text(action.label,style=MaterialTheme.typography.titleMedium)
    }}
}
