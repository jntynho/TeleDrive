package app.tgdrive.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun LibrarySearchField(value:String,onValueChange:(String)->Unit,placeholder:String,modifier:Modifier=Modifier) {
    OutlinedTextField(value=value,onValueChange=onValueChange,modifier=modifier.fillMaxWidth(),singleLine=true,
        shape=DriveStyle.PillShape,placeholder={Text(placeholder,style=MaterialTheme.typography.bodyMedium)},
        leadingIcon={Icon(DriveIcons.Search,null)},
        trailingIcon={if(value.isNotEmpty())IconButton(onClick={onValueChange("")}){Icon(DriveIcons.Close,"Clear search")}},
        colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Color.Transparent,unfocusedBorderColor=Color.Transparent,
            focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface))
}
