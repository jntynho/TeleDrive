package com.infinity.drive.presentation.upload

import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.files.*
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.ZipFile

/** Reads Google Takeout ZIPs locally, one original at a time; never extracts archive paths. */
class TakeoutImporter(private val importer:FileImporter,private val paths:AppStoragePaths,
    private val files:FileRepository,private val transfers:TransferRepository,private val storage:StorageInspector,private val settings:SettingsRepository) {
    data class State(val running:Boolean=false,val processed:Int=0,val imported:Int=0,val skipped:Int=0,
        val failed:Int=0,val current:String="",val error:String?=null)
    private val mutable=MutableStateFlow(State())
    val state=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job:Job?=null
    fun cancel(){job?.cancel()}
    fun start(references:List<String>,parentFolder:String?=null){
        if(mutable.value.running||references.isEmpty())return
        mutable.value=State(running=true)
        job=scope.launch {
            try {
                val context=settings.preferences.first().let{it.storageChatId to it.onboardingComplete}
                for(reference in references){
                    ensureActive()
                    val source=importer.import(reference)?:error("Couldn't read an export part. Download the ZIP to this phone first.")
                    try { ZipFile(source.path).use { zip ->
                        val sidecars=mutableMapOf<String,JsonObject>()
                        val albumNames=mutableMapOf<String,String>()
                        val entries=zip.entries().asSequence().toList()
                        // Sidecars are small; media remains in the archive until its turn.
                        entries.filter{!it.isDirectory&&it.name.endsWith(".json",true)&&it.size in 1..1_048_576}.forEach{entry ->
                            ensureActive()
                            val data=runCatching { zip.getInputStream(entry).bufferedReader().use{Json.parseToJsonElement(it.readText()).jsonObject} }.getOrNull()?:return@forEach
                            val folder=entry.name.substringBeforeLast('/',"")
                            val title=data["title"]?.jsonPrimitive?.contentOrNull
                            if(data.containsKey("photoTakenTime")||data.containsKey("creationTime")){
                                if(!title.isNullOrBlank())sidecars["$folder/$title"]=data
                                sidecars[entry.name.removeSuffix(".supplemental-metadata.json").removeSuffix(".json")]=data
                            }else if(entry.name.endsWith("metadata.json")){
                                val album=data["albumData"] as? JsonObject
                                val name=(album?.get("title")?:data["title"])?.jsonPrimitive?.contentOrNull
                                if(!name.isNullOrBlank())albumNames[folder]=FileNameUtils.sanitize(name)
                            }
                        }
                        for(entry in entries){
                            ensureActive()
                            val category=FileCategory.fromMimeType(MimeTypes.fromFileName(entry.name))
                            if(entry.isDirectory||category !in listOf(FileCategory.IMAGE,FileCategory.VIDEO))continue
                            var staged:ImportedFile?=null
                            try {
                                val name=FileNameUtils.sanitize(entry.name.substringAfterLast('/'))
                                mutable.update{it.copy(current=name)}
                                require(entry.size>=0&&entry.size+16L*1024*1024<=storage.availableBytes()){"Not enough free space to prepare $name. Free some space and select the export parts again."}
                                val directory=File(paths.filesDir,"imports/${java.util.UUID.randomUUID()}").apply{mkdirs()}
                                val target=File(directory,name)
                                importer.reserve(target.absolutePath)
                                staged=ImportedFile(target.absolutePath,name,entry.size)
                                val digest=java.security.MessageDigest.getInstance("SHA-256")
                                zip.getInputStream(entry).use{input->target.outputStream().buffered().use{output->
                                    val buffer=ByteArray(256*1024);var written=0L
                                    while(true){ensureActive();val count=input.read(buffer);if(count<0)break;written+=count
                                        check(written<=entry.size){"An export entry is larger than declared"};output.write(buffer,0,count);digest.update(buffer,0,count)}
                                    check(written==entry.size){"An export entry is incomplete"}
                                }}
                                val hash=digest.digest().joinToString(""){"%02x".format(it)}
                                val metadata=sidecars[entry.name]
                                val seconds=(metadata?.get("photoTakenTime") as? JsonObject)?.get("timestamp")?.jsonPrimitive?.longOrNull
                                    ?:(metadata?.get("creationTime") as? JsonObject)?.get("timestamp")?.jsonPrimitive?.longOrNull
                                target.setLastModified(seconds?.takeIf{it>0&&it<Long.MAX_VALUE/1000}?.times(1000)?:entry.time.takeIf{it>0}?:System.currentTimeMillis())
                                if(settings.preferences.first().let{it.storageChatId to it.onboardingComplete}!=context)throw CancellationException("Drive changed")
                                val parent=entry.name.substringBeforeLast('/',"")
                                val relative=albumNames[parent]?:parent.substringAfter("Google Photos/",parent.substringAfterLast('/'))
                                val destination=files.resolveImportFolder("Google Photos/${FileNameUtils.relativeFolderPath(relative).orEmpty()}",parentFolder)
                                val existing=files.findDuplicate(target.absolutePath,hash)
                                if(existing!=null){
                                    if(existing.folderId!=destination&&files.findNameClash(name,destination)?.contentHash!=existing.contentHash){
                                        // Wait for the first copy, then create an album copy server-side, never upload its bytes again.
                                        val backed=if(existing.hasRemoteCopy)existing else withTimeoutOrNull(30*60_000L){
                                            files.observeFile(existing.id).filterNotNull().first{it.hasRemoteCopy||it.backupState==BackupState.FAILED}
                                        }
                                        if(backed?.hasRemoteCopy==true){check(files.copyFiles(listOf(backed.id),destination) is AppResult.Success)}
                                        else error("The original is not backed up yet; select these export parts again after it finishes")
                                    }
                                    mutable.update{it.copy(processed=it.processed+1,skipped=it.skipped+1)}
                                    continue
                                }
                                val clash=files.findNameClash(name,destination)
                                val displayName=if(clash==null)name else {
                                    val names=files.observeFiles(FileQuerySpec(folderId=destination,filterByFolder=true,showHidden=true,showArchived=true)).first().map{it.name.lowercase()}.toSet()
                                    FileNameUtils.uniqueName(name){it.lowercase() in names}
                                }
                                val result=files.importLocalFile(target.absolutePath,destination,displayName)
                                check(result is AppResult.Success){"Couldn't index $name"}
                                staged=null
                                files.rememberContentHash(result.value.id,hash)
                                if(metadata?.get("favorited")?.jsonPrimitive?.booleanOrNull==true)files.setFilesFavorite(listOf(result.value.id),true)
                                check(transfers.enqueueUploads(listOf(result.value.id)) is AppResult.Success){"Couldn't queue $name"}
                                mutable.update{it.copy(processed=it.processed+1,imported=it.imported+1)}
                            }catch(cancel:CancellationException){throw cancel}
                            catch(error:Exception){mutable.update{it.copy(processed=it.processed+1,failed=it.failed+1,error=error.message?:"Couldn't import one photo")}}
                            finally{staged?.let(importer::discard)}
                        }
                    }}finally{importer.discard(source)}
                }
            }catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){mutable.update{it.copy(error=error.message?:"Couldn't read the Takeout ZIP. Choose the original downloaded ZIP parts.")}}
            finally{mutable.update{it.copy(running=false,current="")}}
        }
    }
}
