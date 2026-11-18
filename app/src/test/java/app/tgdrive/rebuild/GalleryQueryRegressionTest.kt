package app.tgdrive.rebuild

import android.app.Application
import androidx.room.Room
import com.infinity.drive.data.local.FileQueryBuilder
import com.infinity.drive.data.local.database.DriveInfinityDatabase
import com.infinity.drive.data.local.entity.AudioTagEntity
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.local.entity.FolderEntity
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

    @Test fun albumCoverVersionTracksNewArtworkWithoutChangingTheFileTimestamp()=runTest {
        val db=database()
        try {
            val dao=db.fileDao();val row=file("cover").copy(iconFileId="old-thumb",contentHash="hash-one")
            dao.upsert(row)
            val old=dao.observeAlbumsScoped(false,false,null).first().single().coverRevision
            dao.upsert(row.copy(iconFileId="new-thumb",contentHash="hash-two"))
            val next=dao.observeAlbumsScoped(false,false,null).first().single().coverRevision
            assertNotEquals(old,next)
            assertEquals(com.infinity.drive.core.media.thumbnailModel(row.copy(iconFileId="new-thumb",contentHash="hash-two").toDomain()).revision,next)
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
    @Test fun recursiveFolderStatisticsHonorDriveAndLocks() = runTest {
        val db = database()
        try {
            val stats = db.folderDao()
            fun folder(id: String, parent: String?, chat: Long = 1, hidden: Boolean = false, trash: Long? = null) =
                FolderEntity(id, chat, parent, id, isHidden = hidden, trashedAt = trash, createdAt = 1, modifiedAt = 1)
            stats.upsert(folder("root", null))
            stats.upsert(folder("level-one", "root"))
            stats.upsert(folder("level-two", "level-one"))
            stats.upsert(folder("hidden", "root", hidden = true))
            stats.upsert(folder("trashed", "root", trash = 1))
            stats.upsert(folder("other-drive", "root", chat = 2))
            val files = db.fileDao()
            files.upsert(file("both", chat = 1).copy(
                sizeBytes = 15, folderId = "level-two", localPath = "/local",
                messageId = 1, backupState = BackupState.BACKED_UP
            ))
            files.upsert(file("local", chat = 1).copy(sizeBytes = 4, folderId = "root", localPath = "/local-2"))
            files.upsert(file("hidden file", chat = 1).copy(folderId = "hidden", sizeBytes = 70))
            files.upsert(file("note", "secret.md", chat = 1).copy(folderId = "root", sizeBytes = 100))
            files.upsert(file("different drive", chat = 2).copy(folderId = "other-drive", sizeBytes = 400))
            files.upsert(file("trashed file", chat = 1).copy(folderId = "trashed", sizeBytes = 800))
            assertEquals(FolderStatistics(2, 2, 19, 19, 15), stats.observeStatistics("root", 1, false, false).first())
            assertEquals(FolderStatistics(4, 3, 189, 19, 15), stats.observeStatistics("root", 1, true, true).first())
            assertEquals(FolderStatistics(0, 0, 0, 0, 0), stats.observeStatistics("root", 2, true, true).first())
        } finally {
            db.close()
        }
    }
    @Test fun duplicateIndexUsesBytesOrStableTelegramIdAndExcludesLockedItems() = runTest {
        val db = database()
        try {
            val dao = db.fileDao()
            val hash = "ab".repeat(32)
            db.folderDao().upsert(FolderEntity(
                "protected", 1, null, "Protected", isHidden = true, createdAt = 1, modifiedAt = 1
            ))
            val equalA = file("a", chat = 1).copy(contentHash = hash, sizeBytes = 30)
            val equalB = file("b", chat = 1).copy(contentHash = hash.uppercase(), sizeBytes = 30)
            dao.upsert(equalA)
            dao.upsert(equalB)
            dao.upsert(equalA.copy(id = "different-size", sizeBytes = 20))
            dao.upsert(equalA.copy(id = "removed", trashedAt = 1))
            dao.upsert(equalA.copy(id = "other", chatId = 2))
            dao.upsert(equalA.copy(id = "hidden", folderId = "protected"))
            dao.upsert(equalA.copy(id = "note", name = "Pinned.md"))
            dao.upsert(file("remote-a", chat = 1).copy(remoteUniqueId = "shared"))
            dao.upsert(file("remote-b", chat = 1).copy(remoteUniqueId = "shared"))
            assertEquals(
                setOf("a", "b", "remote-a", "remote-b"),
                dao.observeDuplicateFiles(1, includeHidden = false, includeNotes = false).first().map { it.id }.toSet()
            )
            assertEquals(
                setOf("a", "b", "hidden", "note", "remote-a", "remote-b"),
                dao.observeDuplicateFiles(1, includeHidden = true, includeNotes = true).first().map { it.id }.toSet()
            )
            assertTrue(dao.observeDuplicateFiles(2, includeHidden = true, includeNotes = true).first().isEmpty())
        } finally {
            db.close()
        }
    }
    @Test fun naturalAndExtensionSortStayConsistentAfterRenameAndAcrossPreviewPages() = runTest {
        val db = database()
        try {
            val dao = db.fileDao()
            dao.upsert(file("1", "take10.MP3"))
            dao.upsert(file("2", "take2.mp3"))
            dao.upsert(file("3", "take100000000000000000000.png"))
            dao.upsert(file("4", "take1.JPG"))
            dao.upsert(file("5", ".dotfile"))
            val natural = FileQuerySpec(sortField = FileSortField.NATURAL_NAME)
            val extension = FileQuerySpec(sortField = FileSortField.EXTENSION)
            assertEquals(listOf("5", "4", "2", "1", "3"), dao.idList(FileQueryBuilder.buildIds(natural)))
            assertEquals(listOf("5", "4", "2", "1", "3"), dao.idList(FileQueryBuilder.buildIds(extension)))
            dao.rename("1", "take3.gif", 2)
            dao.upsert(dao.byId("2")!!.copy(name = "take0.bmp"))
            assertEquals(listOf("5", "2", "4", "1", "3"), dao.idList(FileQueryBuilder.buildIds(natural)))
            assertEquals(listOf("5", "2", "1", "4", "3"), dao.idList(FileQueryBuilder.buildIds(extension)))
            for (field in listOf(FileSortField.NATURAL_NAME, FileSortField.EXTENSION)) {
                for (direction in SortDirection.entries) {
                    val spec = FileQuerySpec(sortField = field, sortDirection = direction)
                    val order = dao.idList(FileQueryBuilder.buildIds(spec))
                    for ((index, id) in order.withIndex()) {
                        val file = dao.byId(id)!!.toDomain()
                        assertEquals(index, dao.queryCount(FileQueryBuilder.rank(spec, file)))
                        assertEquals(id, dao.observeList(FileQueryBuilder.buildWindow(spec, index, 1)).first().single().id)
                    }
                }
            }
        } finally {
            db.close()
        }
    }
    @Test fun tagSearchUsesExactValuesAndTreatsWildcardsAsCharacters() = runTest {
        val db = database()
        try {
            val dao = db.fileDao()
            dao.upsert(file("1", "regular.jpg").copy(tagsEncoded = "|travel|50%|"))
            dao.upsert(file("2", "other.jpg").copy(tagsEncoded = "|travel plans|500|"))
            dao.upsert(file("3", "hidden.jpg").copy(tagsEncoded = "|travel|", isHidden = true))
            assertEquals(listOf("1"), dao.idList(FileQueryBuilder.buildIds(FileQuerySpec(tag = "travel"))))
            assertEquals(listOf("1"), dao.idList(FileQueryBuilder.buildIds(FileQuerySpec(tag = "50%"))))
            assertEquals(listOf("2"), dao.idList(FileQueryBuilder.buildIds(FileQuerySpec(tag = "travel plans"))))
        } finally {
            db.close()
        }
    }
}
