package com.infinity.drive.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tgdrive.ui.elasticVerticalScroll as verticalScroll

internal fun parseBackupSizeMb(text:String):Int? {
    val raw=text.trim()
    if(raw.isEmpty())return null
    val digits=buildString{for(char in raw){val d=Character.digit(char,10);if(d<0)return null;append('0'+d)}}
    return digits.toIntOrNull()?.takeIf{it>=0}
}

@Composable
fun BackupSizeDialog(minimum:Boolean,selected:Int,otherLimit:Int,onSelect:(Int)->Unit,onDismiss:()->Unit) {
    val presets=if(minimum)listOf(0,1,5,10,25,50,100)else listOf(0,100,500,1000,2000,4000)
    var custom by rememberSaveable{mutableStateOf(selected !in presets)}
    var input by rememberSaveable(stateSaver=androidx.compose.ui.text.input.TextFieldValue.Saver){
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(selected.toString(),androidx.compose.ui.text.TextRange(0,selected.toString().length)))
    }
    val text=input.text
    fun valid(value:Int)=if(minimum)otherLimit==0||value<=otherLimit else value==0||value>=otherLimit
    val parsed=parseBackupSizeMb(text)
    val error=when {
        parsed==null->"Enter a whole number from 0 to 2147483647."
        !valid(parsed)->if(minimum)"Minimum cannot exceed the $otherLimit MB maximum."else"Maximum cannot be below the $otherLimit MB minimum."
        else->null
    }
    androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss) {
        Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier=Modifier.fillMaxWidth().heightIn(max=560.dp)) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(if(minimum)"Skip files smaller than"else"Skip files larger than",style=MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if(custom) {
                Text("Size in MB",style=MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicTextField(value=input,onValueChange={input=it},singleLine=true,
                    textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),
                    cursorBrush=androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=androidx.compose.ui.text.input.ImeAction.Done,autoCorrectEnabled=false),
                    keyboardActions=androidx.compose.foundation.text.KeyboardActions(onDone={parseBackupSizeMb(input.text)?.takeIf(::valid)?.let(onSelect)}),
                    modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).border(1.dp,if(error!=null)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .padding(16.dp).semantics{contentDescription="Size in MB"})
                Spacer(Modifier.height(8.dp))
                Text(error?:"0 means no limit. Files exactly at the limit are included.",style=MaterialTheme.typography.bodySmall,
                    color=if(error!=null)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({custom=false}){Text("Show suggested sizes")}
            } else {
                presets.forEach { value->
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected==value,enabled=valid(value),role=Role.RadioButton,onClick={onSelect(value)}).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected==value,onClick=null,enabled=valid(value));Spacer(Modifier.width(8.dp))
                        Text(if(value==0)"No limit"else"$value MB",color=if(valid(value))MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton({custom=true},modifier=Modifier.fillMaxWidth()){Text("Custom size…")}
            }
        }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                TextButton(onDismiss){Text("Cancel")}
                if(custom)TextButton({parseBackupSizeMb(input.text)?.takeIf(::valid)?.let(onSelect)},enabled=error==null){Text("Save")}
            }
        }
        }
    }
}
