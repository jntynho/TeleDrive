package app.tgdrive.benchmark

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.infinity.drive.data.local.database.DriveInfinityDatabase
import com.infinity.drive.data.local.entity.*
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext
import java.io.File
import java.security.MessageDigest

/** Exists in the isolated benchmark package only. Production APKs contain no fixture entry point. */
class SeedActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val status=TextView(this).apply{text="Preparing benchmark library"};setContentView(status)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO){
                    check(packageName.endsWith(".benchmark"))
                    getSharedPreferences("benchmark-control",MODE_PRIVATE).edit().putBoolean("visual",intent.getBooleanExtra("visual",true)).commit()
                    val koin=GlobalContext.get()
                    val settings=koin.get<SettingsRepository>()
                    settings.update{UserPreferences(onboardingComplete=true,instantBackupEnabled=false,updateCheckEnabled=false,
                        floatingNavigationBar=true,navigationBarBlur=true,folderOwnershipRepaired=true)}
                    val db=koin.get<DriveInfinityDatabase>();db.clearAllTables()
                    koin.get<com.infinity.drive.domain.repository.FileRepository>().clearStartupCache()
                    val root=File(filesDir,"benchmark-fixtures").apply{mkdirs()}
                    val cover=File(root,"cover.jpg")
                    Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).apply{
                        eraseColor(Color.rgb(44,92,142));cover.outputStream().use{compress(Bitmap.CompressFormat.JPEG,85,it)};recycle()
                    }
                    val thumb=cover.readBytes();val now=1_760_000_000_000L
                    repeat(1200){index->
                        val audio=index%4==0;val id="benchmark-$index"
                        val row=FileEntity(id=id,folderId=null,name=if(audio)"Track $index.mp3"else"Photo $index.jpg",sizeBytes=cover.length(),
                            mimeType=if(audio)"audio/mpeg"else"image/jpeg",category=if(audio)FileCategory.AUDIO else FileCategory.IMAGE,
                            localPath=cover.absolutePath,contentHash=null,chatId=null,messageId=null,remoteFileId=null,remoteUniqueId=null,
                            backupState=BackupState.NONE,uploadRequested=false,createdAt=now-index,modifiedAt=now-index,addedAt=now-index)
                        db.fileDao().upsert(row)
                        if(index<100){
                            val version="${row.id}:${row.modifiedAt}:${row.localPath}:null:null:null"
                            val hash=MessageDigest.getInstance("SHA-256").digest(version.toByteArray()).joinToString(""){"%02x".format(it)}
                            val path=File(cacheDir,"thumbnails/$hash.thumb").apply{parentFile?.mkdirs();writeBytes(thumb)}
                            db.thumbnailDao().upsert(ThumbnailEntity(id,path.absolutePath,false,256,256,thumb.size.toLong(),now,now))
                        }
                    }
                }
                status.text="Benchmark library ready"
            }catch(error:Exception){status.text="Benchmark setup failed: ${error.javaClass.simpleName}"}
        }
    }
}
