package app.tgdrive.rebuild

import android.app.Application
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.files.*
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.*
import com.infinity.drive.presentation.upload.TakeoutImporter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Proxy
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class TakeoutImportTest {
    private inline fun <reified T> fake(crossinline answer:(String,Array<out Any?>)->Any?):T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)){_,method,args->answer(method.name,args?:emptyArray())} as T
    @Test fun `duplicate originals in two albums upload once retain dates and stars and reimport without extra copies`()=runBlocking {
        org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowMimeTypeMap>(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg","image/jpeg")
        val root=java.nio.file.Files.createTempDirectory("takeout-test").toFile()
        val paths=object:AppStoragePaths {override val filesDir=File(root,"files").apply{mkdirs()};override val cacheDir=File(root,"cache").apply{mkdirs()};override val externalCacheDir:File?=null;override val externalStorageRoot:File?=null}
        val zip=File(root,"takeout.zip")
        val original="original photo bytes".toByteArray()
        ZipOutputStream(zip.outputStream()).use{out->
            fun entry(name:String,bytes:ByteArray){out.putNextEntry(ZipEntry(name));out.write(bytes);out.closeEntry()}
            for(album in listOf("First album","Second album")){
                val folder="Takeout/Google Photos/$album"
                entry("$folder/Photo.jpg",original)
                entry("$folder/Photo.jpg.supplemental-metadata.json","""{"title":"Photo.jpg","photoTakenTime":{"timestamp":"1700000000"},"favorited":true}""".toByteArray())
            }
        }
        val importer=AndroidFileImporter(RuntimeEnvironment.getApplication(),paths)
        val rows=linkedMapOf<String,DriveFile>()
        var uploads=0;var copies=0
        val repository=fake<FileRepository>{name,args->when(name){
            "resolveImportFolder"->args[0] as String
            "findDuplicate"->rows.values.firstOrNull{it.contentHash==args[1]}
            "findNameClash"->rows.values.firstOrNull{it.name==args[0]&&it.folderId==args[1]}
            "importLocalFile"->{
                val source=File(args[0] as String);val id="file${rows.size}"
                val file=DriveFile(id=id,folderId=args[1] as String?,name=args[2] as String,sizeBytes=source.length(),mimeType="image/jpeg",category=FileCategory.IMAGE,
                    localPath=source.absolutePath,contentHash=null,chatId=-1001,messageId=null,remoteFileId=null,remoteUniqueId=null,backupState=BackupState.NONE,
                    isHidden=false,isArchived=false,isFavorite=false,isEncrypted=false,width=null,height=null,durationMs=null,trashedAt=null,
                    createdAt=source.lastModified(),modifiedAt=source.lastModified(),addedAt=1)
                rows[id]=file;importer.registered(source.absolutePath);AppResult.Success(file)
            }
            "rememberContentHash"->{val id=args[0] as String;rows[id]=rows.getValue(id).copy(contentHash=args[1] as String);Unit}
            "setFilesFavorite"->{val id=(args[0] as List<*>).single() as String;rows[id]=rows.getValue(id).copy(isFavorite=args[1] as Boolean);Unit}
            "copyFiles"->{
                val source=rows.getValue((args[0] as List<*>).single() as String);assertTrue(source.hasRemoteCopy)
                val id="file${rows.size}";rows[id]=source.copy(id=id,folderId=args[1] as String?);copies++;AppResult.Success(1)
            }
            else->error(name)
        }}
        val queue=fake<TransferRepository>{name,args->when(name){
            "enqueueUploads"->{
                for(id in args[0] as List<String>){val row=rows.getValue(id);assertNotNull(row.contentHash);assertTrue(row.isFavorite)
                    rows[id]=row.copy(localPath=null,messageId=123,remoteFileId="remote",backupState=BackupState.BACKED_UP);uploads++}
                AppResult.Success((args[0] as List<*>).size)
            }
            else->error(name)
        }}
        val settings=fake<SettingsRepository>{name,_->when(name){"getPreferences"->flowOf(UserPreferences(onboardingComplete=true,storageChatId=-1001));else->error(name)}}
        val service=TakeoutImporter(importer,paths,repository,queue,StorageInspector(paths),settings)
        try {
            service.start(listOf(zip.absolutePath));val first=withTimeout(5000){service.state.first{!it.running}}
            assertNull(first.error);assertEquals("state=$first",0,first.failed);assertEquals("state=$first",1,uploads);assertEquals("state=$first",1,copies);assertEquals(2,rows.size)
            assertTrue(rows.values.all{it.createdAt==1_700_000_000_000&&it.isFavorite})
            assertEquals(setOf("Google Photos/First album","Google Photos/Second album"),rows.values.map{it.folderId}.toSet())
            service.start(listOf(zip.absolutePath));val second=withTimeout(5000){service.state.first{!it.running}}
            assertEquals(0,second.failed);assertEquals(2,second.skipped);assertEquals(1,uploads);assertEquals(1,copies)
            assertTrue(zip.exists())
        }finally{service.cancel();root.deleteRecursively()}
    }
}
