package app.tgdrive.rebuild

import android.app.Application
import androidx.room.Room
import com.infinity.drive.data.local.database.DriveInfinityDatabase
import com.infinity.drive.data.local.entity.*
import com.infinity.drive.data.local.FileQueryBuilder
import com.infinity.drive.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class AudioTagDatabaseTest {
    @Test fun mergesPreserveKnownTagsAndIgnoreStaleContentWhileSearchSurvivesALocalPathChange()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),DriveInfinityDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao=db.fileDao()
            val file=FileEntity("audio",null,"Track.mp3",12,"audio/mpeg",FileCategory.AUDIO,"old-path","hash",null,null,null,null,backupState=BackupState.NONE,createdAt=1,modifiedAt=1,addedAt=1)
            dao.upsert(file)
            dao.mergeAudioTags(AudioTagEntity(file.id,1,"old-path","Title","Artist",12,"hash",1000,true))
            dao.mergeAudioTags(AudioTagEntity(file.id,1,"old-path",null,"",12,"hash",null,false))
            assertEquals("Artist",dao.audioTagsById(file.id)!!.artist);assertTrue(dao.audioTagsById(file.id)!!.inspected)
            dao.setLocalPath(file.id,"new-path")
            assertEquals(listOf("audio"),dao.idList(FileQueryBuilder.buildIds(FileQuerySpec(nameQuery="Artist"))))
            dao.upsert(file.copy(modifiedAt=2,contentHash="new-content"))
            dao.mergeAudioTags(AudioTagEntity(file.id,1,"old-path","Stale","Wrong",12,"hash",1000,true))
            assertEquals("Title",dao.audioTagsById(file.id)!!.title)
            assertTrue(dao.idList(FileQueryBuilder.buildIds(FileQuerySpec(nameQuery="Artist"))).isEmpty())
        } finally {db.close()}
    }
}
