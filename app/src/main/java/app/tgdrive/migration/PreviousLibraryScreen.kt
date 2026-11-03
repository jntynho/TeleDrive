package app.tgdrive.migration

import app.tgdrive.ui.ElasticLazyColumn as LazyColumn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tgdrive.ui.DriveIcons
import com.infinity.drive.core.telegram.TelegramClient
import app.tgdrive.library.VaultDescriptor
import com.infinity.drive.presentation.common.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviousLibraryScreen(onBack:()->Unit) {
    val context=LocalContext.current
    val telegram=koinInject<TelegramClient>()
    val reader=remember(context,telegram) { PreviousLibraryReader(context,telegram) }
    val scope=rememberCoroutineScope()
    var listing by remember { mutableStateOf<PreviousLibraryReader.Listing?>(null) }
    var loading by remember{mutableStateOf(true)}
    var loadFailure by remember{mutableStateOf<String?>(null)}
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PreviousLibraryReader.Entry?>(null) }
    var unlocking by remember { mutableStateOf<VaultDescriptor?>(null) }
    var password by remember { mutableStateOf("") }
    var recovery by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    DisposableEffect(reader) { onDispose { reader.close() } }
    LaunchedEffect(reader,generation) {
        loading=true;loadFailure=null
        try { listing=reader.list() } catch(e:CancellationException) { throw e }
        catch(e:Exception) { loadFailure="The previous library could not be read. Its data is preserved." }
        finally{loading=false}
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val entry=pending;pending=null
        if(uri!=null&&entry!=null)scope.launch {
            busy="Preparing verified copy"
            try {
                val verified=reader.verifiedCopy(entry) { progress -> scope.launch { busy=progress } }
                try {
                    withContext(Dispatchers.IO) {
                        val output=context.contentResolver.openOutputStream(uri,"wt") ?: error("This location cannot be written.")
                        output.use { out -> verified.inputStream().use { copyCancellable(it,out) } }
                    }
                    error="Copy saved. The original is still in Saved Messages."
                }finally { verified.delete() }
            }catch(e:CancellationException) { throw e }
            catch(e:Exception) { error=e.message ?: "The copy could not be saved." }
            finally { busy=null }
        }
    }
    Scaffold(topBar={com.infinity.drive.presentation.components.PageHeader(title={Text("Previous library")},navigationIcon={IconButton(onBack){Icon(DriveIcons.ArrowBack,"Back")}})}) { padding ->
        LazyColumn(modifier=Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text("Your previous Saved Messages files stay untouched. Save a copy here, then add it to a private-channel drive when you are ready.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            if(loading)item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(loadFailure!=null)item { Column{Text(loadFailure!!);TextButton({generation++}){Text("Retry")}} }
            if(listing?.files?.isEmpty()==true&&listing?.locked?.isEmpty()==true)item { Text("No previous files were found in this installation for the signed-in Telegram account.") }
            listing?.locked?.let { locked -> items(locked,key={"vault-${it.id}"}) { vault ->
                OutlinedCard(onClick={if(busy==null){unlocking=vault;password="";recovery=false}},modifier=Modifier.fillMaxWidth()) {
                    ListItem(headlineContent={Text(vault.label)},supportingContent={Text("Unlock previous vault")},leadingContent={Icon(DriveIcons.Lock,null)})
                }
            } }
            listing?.files?.let { entries -> items(entries,key={it.item.id}) { entry ->
                Card(onClick={if(busy==null&&pending==null){pending=entry;save.launch(entry.item.name)}},modifier=Modifier.fillMaxWidth(),enabled=busy==null) {
                    ListItem(headlineContent={Text(entry.item.name,maxLines=2,overflow=TextOverflow.Ellipsis)},supportingContent={Text(Formatters.bytes(entry.item.size))},trailingContent={Icon(DriveIcons.Download,"Save a copy")})
                }
            } }
        }
    }
    if(busy!=null)AlertDialog(onDismissRequest={},title={Text("Saving a copy")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){LinearProgressIndicator(Modifier.fillMaxWidth());Text(busy.orEmpty())}},confirmButton={})
    if(error!=null)AlertDialog(onDismissRequest={error=null},text={Text(error.orEmpty())},confirmButton={TextButton({error=null}){Text("OK")}})
    if(unlocking!=null)AlertDialog(onDismissRequest={unlocking=null;password=""},title={Text("Unlock ${unlocking!!.label}")},text={Column {
        OutlinedTextField(value=password,onValueChange={password=it},label={Text(if(recovery)"Recovery key" else "Password")},singleLine=true,visualTransformation=PasswordVisualTransformation())
        Row { Checkbox(recovery,{recovery=it});Text("Use recovery key",modifier=Modifier.padding(top=12.dp)) }
    }},confirmButton={TextButton(onClick={val descriptor=unlocking!!;val secret=password.toCharArray();password="";unlocking=null;scope.launch {
        try { reader.unlock(descriptor,secret,recovery);generation++ }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{secret.fill('\u0000')}
    }},enabled=password.isNotEmpty()){Text("Unlock")}},dismissButton={TextButton({unlocking=null;password=""}){Text("Cancel")}})
}
