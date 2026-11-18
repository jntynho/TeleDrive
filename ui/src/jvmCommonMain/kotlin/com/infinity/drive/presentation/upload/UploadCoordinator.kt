package com.infinity.drive.presentation.upload

import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.common.SafeLog
import com.infinity.drive.core.files.*
import com.infinity.drive.core.telegram.RemoteDocument
import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.repository.*
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.resources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One import pipeline for every picker. Preparation overlaps the durable two-slot upload queue. */
class UploadCoordinator(private val fileRepository:FileRepository,
    private val transferRepository:TransferRepository,private val fileImporter:FileImporter,
    private val trash:TrashRepository,private val settings:SettingsRepository) {
    enum class Choice { REPLACE, KEEP_BOTH, SKIP }
    data class Answer(val choice:Choice,val applyToAll:Boolean=false)
    data class Conflict(val name:String,val size:Long,val existing:DriveFile,val folderName:String,
        val bothName:String,val remaining:Int,val reply:CompletableDeferred<Answer>)
    private val _conflict=MutableStateFlow<Conflict?>(null)
    val conflict=_conflict.asStateFlow()
    private val _messages=MutableSharedFlow<UiText>(extraBufferCapacity=16)
    val messages=_messages.asSharedFlow()
    private val serial=Mutex()
    private val importScope=CoroutineScope(SupervisorJob()+Dispatchers.IO+CoroutineExceptionHandler { _,error ->
        SafeLog.e("UploadCoordinator","Import failed",error)
        _messages.tryEmit(UiText.Resource(Res.string.files_import_failed))
    })
    private suspend fun choice(name:String,size:Long,target:String?,remaining:Int,apply:Answer?):Pair<Answer?,String> {
        val existing=fileRepository.findNameClash(name,target)?:return null to name
        val siblings=fileRepository.observeFiles(com.infinity.drive.domain.model.FileQuerySpec(folderId=target,filterByFolder=true,respectHiddenLock=false,showHidden=true,showArchived=true)).first()
        val taken=siblings.map{it.name.lowercase()}.toSet()
        val alternate=FileNameUtils.uniqueName(name){it.lowercase() in taken}
        val answer=apply?:run {
            val request=Conflict(name,size,existing,target?.let{fileRepository.getFolder(it)?.name}?:"My Drive",alternate,remaining,CompletableDeferred())
            _conflict.value=request
            val context=settings.preferences.first().let{it.storageChatId to it.onboardingComplete}
            try{coroutineScope{
                val guard=launch{settings.preferences.first{(it.storageChatId to it.onboardingComplete)!=context};request.reply.cancel(CancellationException("Drive changed"))}
                try{request.reply.await()}finally{guard.cancel()}
            }}finally{_conflict.value=null}
        }
        if(answer.choice==Choice.REPLACE){
            // Trash keeps the previous remote copy; never delete it permanently to replace a name.
            val result=trash.moveFilesToTrash(listOf(existing.id))
            if(result is AppResult.Failure)error("Could not move the old copy to Trash")
        }
        return answer to if(answer.choice==Choice.KEEP_BOTH)alternate else name
    }
    fun answer(answer:Answer){_conflict.value?.reply?.complete(answer)}

    fun importAndUpload(uris:List<String>,target:String?=null,copyImmediately:Boolean=false) {
        if(uris.isEmpty())return
        importScope.launch {serial.withLock {
            val context=settings.preferences.first().let{it.storageChatId to it.onboardingComplete}
            _messages.tryEmit(UiText.Resource(Res.string.files_import_preparing))
            var imported=0;var skipped=0;var failed=0
            var apply:Answer?=null
            val pending=mutableListOf<String>()
            val batchId = java.util.UUID.randomUUID().toString()
            suspend fun flush(){
                if(pending.isEmpty())return
                when(val result=transferRepository.enqueueUploads(pending.toList(), batchId)){
                    is AppResult.Success -> {imported+=result.value;failed+=pending.size-result.value}
                    is AppResult.Failure -> failed+=pending.size
                }
                pending.clear()
            }
            // Don't expand an entire directory tree into copied bytes before the first upload starts.
            val sources=uris.flatMap{fileImporter.expand(it)}
            for((index,source) in sources.withIndex()){
                var staged:ImportedFile?=null
                try {
                    check(settings.preferences.first().let{it.storageChatId to it.onboardingComplete}==context){"Drive changed. Select the remaining files again."}
                    val destination=fileRepository.resolveImportFolder(source.relativeFolder,target)
                    staged=(if(copyImmediately)fileImporter.import(source.reference)else fileImporter.describe(source.reference))?:error("The file provider could not read this file")
                    if(settings.preferences.first().let{it.storageChatId to it.onboardingComplete}!=context)throw CancellationException("Drive changed")
                    val (answer,name)=choice(staged.name,staged.sizeBytes,destination,sources.size-index,apply)
                    if(answer?.applyToAll==true)apply=answer
                    if(answer?.choice==Choice.SKIP){fileImporter.discard(staged);staged=null;skipped++;continue}
                    when(val result=fileRepository.registerImport(staged,destination,name,allowDuplicate=answer!=null)){
                        is AppResult.Success -> {
                            // Registration owns staged bytes from this point; a failed enqueue can be recovered.
                            staged=null
                            if(!result.value.hasRemoteCopy)pending+=result.value.id
                        }
                        is AppResult.Failure -> error("Could not register this file")
                    }
                    if(pending.size>=8||transferRepository.observeActiveCount().first()<2)flush()
                } catch(cancel:CancellationException){throw cancel}
                catch(error:Exception){SafeLog.w("UploadCoordinator","Could not prepare one selected file",error);failed++}
                finally{staged?.let(fileImporter::discard)}
            }
            flush()
            _messages.tryEmit(when {
                failed>0 -> UiText.Resource(Res.string.files_import_uploading_failed,imported,failed)
                imported>0 -> UiText.Resource(Res.string.files_import_uploading,imported)
                skipped>0 -> UiText.Resource(Res.string.files_import_duplicates,skipped)
                else -> UiText.Resource(Res.string.files_import_failed)
            })
        }}
    }
    val canCompressImports get() = fileImporter.canPrepareZip

    fun importAsZip(references: List<String>, name: String, target: String?) {
        if (references.isEmpty() || !canCompressImports) return
        importScope.launch {
            serial.withLock {
                val drive = settings.preferences.first().takeIf { it.onboardingComplete }?.storageChatId
                    ?: return@withLock
                suspend fun verifyTarget() {
                    val prefs = settings.preferences.first()
                    check(prefs.onboardingComplete && prefs.storageChatId == drive)
                    if (target != null) {
                        val folder = fileRepository.getFolder(target)
                        check(folder != null && folder.trashedAt == null)
                    }
                }
                _messages.tryEmit(UiText.Resource(Res.string.files_zip_preparing))
                var temporary: ImportedFile? = null
                try {
                    verifyTarget()
                    val archive = coroutineScope {
                        val preparation = launch {
                            settings.preferences.first { !it.onboardingComplete || it.storageChatId != drive }
                            this@coroutineScope.cancel(CancellationException("Drive changed"))
                        }
                        try {
                            fileImporter.prepareZip(references, name) { ensureActive() }
                                ?.also { temporary = it }
                                ?: error("Could not compress the selected files")
                        } finally {
                            preparation.cancel()
                        }
                    }
                    verifyTarget()
                    val (answer, resolvedName) = choice(archive.name, archive.sizeBytes, target, 1, null)
                    if (answer?.choice == Choice.SKIP) return@withLock
                    verifyTarget()
                    val file = when (val result = fileRepository.registerImport(archive, target, resolvedName, allowDuplicate = true, expectedDrive = drive)) {
                        is AppResult.Success -> result.value
                        is AppResult.Failure -> error("Could not register the ZIP")
                    }
                    temporary = null
                    when (val result = transferRepository.enqueueUploads(listOf(file.id))) {
                        is AppResult.Success -> check(result.value == 1)
                        is AppResult.Failure -> error("Could not queue the ZIP")
                    }
                    _messages.tryEmit(UiText.Resource(Res.string.files_import_uploading, 1))
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    _messages.tryEmit(UiText.Resource(Res.string.files_zip_failed))
                } finally {
                    temporary?.let(fileImporter::discard)
                }
            }
        }
    }

    fun importFromTelegram(documents: List<RemoteDocument>, target: String?, expectedDrive: Long) {
        importScope.launch {serial.withLock {
            val context = settings.preferences.first()
            if (!context.onboardingComplete || context.storageChatId != expectedDrive || context.encryptFiles) {
                _messages.tryEmit(UiText.Resource(Res.string.files_import_failed))
                return@withLock
            }
            var apply:Answer?=null
            val ids=mutableListOf<String>()
            for((index,document)in documents.withIndex()){
                try {
                    if (settings.preferences.first().let { it.storageChatId != expectedDrive || !it.onboardingComplete || it.encryptFiles }) {
                        throw CancellationException("Drive or encryption changed")
                    }
                    val(answer,name)=choice(document.fileName,document.sizeBytes,target,documents.size-index,apply)
                    if(answer?.applyToAll==true)apply=answer
                    if(answer?.choice==Choice.SKIP)continue
                    val row=fileRepository.importTelegramFile(document,target,name,expectedDrive)
                    if(row is AppResult.Success)ids+=row.value.id
                }catch(cancel:CancellationException){throw cancel}
                catch(error:Exception){SafeLog.w("UploadCoordinator","Could not import Telegram file",error)}
            }
            val result=transferRepository.enqueueUploads(ids)
            _messages.tryEmit(if(result is AppResult.Success && result.value > 0)UiText.Resource(Res.string.files_import_uploading,result.value)else UiText.Resource(Res.string.files_import_failed))
        }}
    }
}
