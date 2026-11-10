package app.tgdrive.rebuild

import android.app.Application
import androidx.room.Room
import com.infinity.drive.data.local.FileQueryBuilder
import com.infinity.drive.data.local.database.DriveInfinityDatabase
import com.infinity.drive.data.local.entity.AudioTagEntity
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.mapper.toDomain
import com.infinity.drive.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class GalleryQueryRegressionTest {
    private fun file(id:String,name:String=id,chat:Long?=null,modified:Long=1)=FileEntity(
        id=id,folderId=null,name=name,sizeBytes=1,mimeType="image/jpeg",category=FileCategory.IMAGE,
        localPath=null,contentHash=null,chatId=chat,messageId=null,remoteFileId=null,remoteUniqueId=null,
        backupState=BackupState.NONE,createdAt=1,modifiedAt=modified,addedAt=1)
    private fun database()=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),DriveInfinityDatabase::class.java).allowMainThreadQueries().build()

    @Test fun albumCoverAndNullChannelQueriesStayInsideTheirChannel()=runTest {
        val db=database()
        try {
            val dao=db.fileDao()
            dao.upsert(file("local"));dao.upsert(file("channel-1",chat=1,modified=10));dao.upsert(file("channel-2",chat=2,modified=100))
            assertEquals("channel-1",dao.observeAlbumsScoped(false,false,1).first().single().coverFileId)
            assertEquals("local",dao.observeAlbumsScoped(false,false,null).first().single().coverFileId)
            assertEquals(listOf("local"),dao.idList(FileQueryBuilder.buildIds(FileQuerySpec(scopeToChat=true)) ))
        }finally{db.close()}
    }

    @Test fun previewRankUsesSameCaseInsensitiveOrderingAndStableTieBreakAsPaging()=runTest {
        val db=database()
        try {
            val dao=db.fileDao()
            val rows=listOf(file("a","alpha"),file("b","Beta"),file("c","ALPHA"),file("d","zeta"))
            rows.forEach{dao.upsert(it)}
            for(direction in SortDirection.entries){
                val spec=FileQuerySpec(sortField=FileSortField.NAME,sortDirection=direction,scopeToChat=true)
                val sorted=dao.idList(FileQueryBuilder.buildIds(spec))
                for((index,id) in sorted.withIndex()){
                    assertEquals(index,dao.queryCount(FileQueryBuilder.rank(spec,rows.first{it.id==id}.toDomain())))
                    assertEquals(id,dao.observeList(FileQueryBuilder.buildWindow(spec,index,1)).first().single().id)
                }
            }
        }finally{db.close()}
    }

    @Test fun audioSearchUsesVersionedDisplayedTagsAndEscapesLiteralWildcards()=runTest {
        val db=database()
        try {
            val dao=db.fileDao();val row=file("song","recording.mp3").copy(category=FileCategory.AUDIO,mimeType="audio/mpeg")
            dao.upsert(row);dao.storeAudioTags(AudioTagEntity(row.id,row.modifiedAt,row.localPath,"100% calm","M83"))
            fun query(text:String)=FileQueryBuilder.buildIds(FileQuerySpec(nameQuery=text,scopeToChat=true))
            assertEquals(listOf("song"),dao.idList(query("M83")))
            assertEquals(listOf("song"),dao.idList(query("100%")))
            assertTrue(dao.idList(query("100_" )).isEmpty())
            dao.upsert(row.copy(modifiedAt=2))
            assertTrue(dao.idList(query("M83")).isEmpty())
        }finally{db.close()}
    }
}
