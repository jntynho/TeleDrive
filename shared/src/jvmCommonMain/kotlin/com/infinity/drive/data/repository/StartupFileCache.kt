package com.infinity.drive.data.repository

import com.infinity.drive.domain.model.DriveFile
import com.infinity.drive.domain.model.FileQuerySpec

/** Small, one-shot presentation seeds. Room always follows them with a live refresh. */
class StartupFileCache(private val now:()->Long=System::nanoTime) {
    private data class Entry(val files:List<DriveFile>,val savedAt:Long)
    private val pages=linkedMapOf<FileQuerySpec,Entry>()
    private var recent:Pair<Long?,Entry>?=null
    private var assets=emptyList<DriveFile>()
    private var assetChannel:Long?=null
    @Synchronized fun putPage(spec:FileQuerySpec,files:List<DriveFile>){
        require(files.size<=40)
        if(pages.size>=2)pages.remove(pages.keys.first())
        pages[spec.copy(initialLoadIndex=null)]=Entry(files.toList(),now())
        assets=(assets+files).distinctBy{it.id}.take(92)
    }
    @Synchronized fun putRecent(chatId:Long?,files:List<DriveFile>){
        if(assetChannel!=chatId){clear();assetChannel=chatId}
        recent=chatId to Entry(files.take(12),now())
        assets=files.take(12)
    }
    @Synchronized fun takePage(spec:FileQuerySpec):List<DriveFile>? =
        pages.remove(spec.copy(initialLoadIndex=null))?.takeIf{now()-it.savedAt<60_000_000_000L}?.files
    @Synchronized fun takeRecent(chatId:Long?,limit:Int):List<DriveFile>? {
        val entry=recent?:return null
        recent=null
        return entry.takeIf{it.first==chatId&&limit<=12&&now()-it.second.savedAt<60_000_000_000L}?.second?.files?.take(limit)
    }
    @Synchronized fun assets(chatId:Long?):List<DriveFile> {
        if(assetChannel!=chatId)return emptyList()
        val gallery=assets.filter{it.category in listOf(com.infinity.drive.domain.model.FileCategory.IMAGE,com.infinity.drive.domain.model.FileCategory.VIDEO)}.take(10)
        val audio=assets.filter{it.category==com.infinity.drive.domain.model.FileCategory.AUDIO}.take(6)
        return buildList {addAll(assets.take(4));repeat(10){i->gallery.getOrNull(i)?.let(::add);audio.getOrNull(i)?.let(::add)}}.distinctBy{it.id}.take(24)
    }
    @Synchronized fun clear(){pages.clear();recent=null;assets=emptyList();assetChannel=null}
}
