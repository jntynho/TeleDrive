package app.tgdrive.rebuild

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.infinity.drive.data.local.database.*
import com.infinity.drive.domain.model.TransferState
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class QueueMigrationTest {
    @Test fun `v11 migration preserves existing files and persists retry deadlines and offline protection`()=runTest {
        val context=RuntimeEnvironment.getApplication()
        val name="queue-migration-${System.nanoTime()}.db"
        val path=context.getDatabasePath(name).apply{parentFile?.mkdirs()}
        val schema=listOf(File("../shared/schemas/com.infinity.drive.data.local.database.DriveInfinityDatabase/11.json"),File("shared/schemas/com.infinity.drive.data.local.database.DriveInfinityDatabase/11.json")).first{it.isFile}
        val entities=JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities")
        SQLiteDatabase.openOrCreateDatabase(path,null).use { old ->
            for(i in 0 until entities.length()){
                val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\u0024{TABLE_NAME}",table))
                val indices=entity.optJSONArray("indices")?:org.json.JSONArray()
                for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\u0024{TABLE_NAME}",table))
                if(table !in listOf("files","transfers"))continue
                val values=ContentValues();val fields=entity.getJSONArray("fields")
                for(j in 0 until fields.length()){
                    val field=fields.getJSONObject(j)
                    if(!field.optBoolean("notNull",false))continue
                    val column=field.getString("columnName")
                    if(field.getString("affinity")=="INTEGER")values.put(column,0L)else values.put(column,"")
                }
                values.put("id",if(table=="files")"original-file"else"original-transfer")
                if(table=="files"){
                    values.put("name","Original.jpg");values.put("mimeType","image/jpeg");values.put("category","IMAGE");values.put("backupState","NONE")
                }else{
                    values.put("displayName","Original.jpg");values.put("type","UPLOAD");values.put("state","QUEUED");values.put("fileId","original-file")
                }
                old.insertOrThrow(table,null,values)
            }
            old.version=11
        }
        fun open()=Room.databaseBuilder(context,DriveInfinityDatabase::class.java,name).addMigrations(MIGRATION_11_12).allowMainThreadQueries().build()
        try {
            val first=open()
            try {
                val db=first
                val file=db.fileDao().byId("original-file")!!
                assertEquals("Original.jpg",file.name);assertFalse(file.offlinePinned);assertNull(file.importReference);assertTrue(file.uploadRequested)
                assertEquals(0,db.transferDao().byId("original-transfer")!!.retryCount)
                assertEquals(1,db.transferDao().claim("original-transfer",100))
                assertEquals(0,db.transferDao().claim("original-transfer",100))
                db.transferDao().defer("original-transfer",161_000,0,"Waiting for Telegram",100)
                db.fileDao().setUploadRequested(listOf("original-file"),false)
                db.fileDao().setLocalPath("original-file","new-private-copy")
                db.fileDao().setOfflinePinned("original-file",true)
                assertEquals(0,db.fileDao().clearLocalPathIfMatches("original-file","old-source",true))
                assertEquals(0,db.fileDao().clearLocalPathIfMatches("original-file","new-private-copy"))
                assertEquals("new-private-copy",db.fileDao().byId("original-file")!!.localPath)
            }finally{first.close()}
            val second=open()
            try {
                val db=second
                val task=db.transferDao().byId("original-transfer")!!
                assertEquals(TransferState.QUEUED,task.state);assertEquals(161_000,task.nextAttemptAt);assertEquals(0,task.retryCount)
                assertTrue(db.fileDao().byId("original-file")!!.offlinePinned)
                assertFalse(db.fileDao().byId("original-file")!!.uploadRequested)
                assertTrue(db.fileDao().localOnlyFileIds(null).isEmpty())
            }finally{second.close()}
        }finally{context.deleteDatabase(name)}
    }
}
