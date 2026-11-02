package app.tgdrive.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun LibrarySearchField(value:String,onValueChange:(String)->Unit,placeholder:String,modifier:Modifier=Modifier) {
    OutlinedTextField(value=value,onValueChange=onValueChange,modifier=modifier.fillMaxWidth().semantics { contentDescription=placeholder },singleLine=true,
        shape=RoundedCornerShape(18.dp),placeholder={Text(placeholder,style=MaterialTheme.typography.bodyMedium)},
        leadingIcon={Icon(DriveIcons.Search,null)},
        trailingIcon={if(value.isNotEmpty())IconButton(onClick={onValueChange("")}){Icon(DriveIcons.Close,"Clear search")}},
        colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Color.Transparent,unfocusedBorderColor=Color.Transparent,
            focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface))
}
