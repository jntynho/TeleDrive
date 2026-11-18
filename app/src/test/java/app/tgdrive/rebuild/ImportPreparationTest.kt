package app.tgdrive.rebuild

import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.infinity.drive.core.files.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class ImportPreparationTest {
    class OriginalProvider(val source:File):ContentProvider() {
        var opened=0
        override fun onCreate()=true
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).apply{addRow(arrayOf<Any>("Original.jpg",source.length()))}
        override fun getType(uri:Uri)="image/jpeg"
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {opened++;return ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY)}
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
    }
    private class Fixture:AutoCloseable {
        val context=RuntimeEnvironment.getApplication()
        val root=java.nio.file.Files.createTempDirectory("intake-test").toFile()
        val source=File(root,"outside/original.jpg").apply{parentFile?.mkdirs();writeBytes(ByteArray(900_000){(it%251).toByte()})}
        val paths=object:AppStoragePaths {override val filesDir=File(root,"app-files").apply{mkdirs()};override val cacheDir=File(root,"cache").apply{mkdirs()};override val externalCacheDir:File?=null;override val externalStorageRoot:File?=null}
        val importer=AndroidFileImporter(context,paths)
        val provider=OriginalProvider(source).apply{attachInfo(context,ProviderInfo().apply{authority="intake.fixture"})}
        val uri="content://intake.fixture/photo"
        init{ShadowContentResolver.registerProviderInternal("intake.fixture",provider)}
        override fun close(){root.deleteRecursively()}
    }
    @Test fun `picking an original reads only metadata and preparation is byte-for-byte`()=runTest {
        Fixture().use{f->
            val metadata=f.importer.describe(f.uri)!!
            assertEquals("Original.jpg",metadata.name);assertEquals(900_000L,metadata.sizeBytes);assertEquals(0,f.provider.opened)
            val prepared=f.importer.prepare(f.uri){}!!
            assertEquals(1,f.provider.opened);assertTrue(f.importer.isStaged(prepared.path))
            assertArrayEquals(f.source.readBytes(),File(prepared.path).readBytes())
            // A startup cleanup cannot delete a preparation before the database owns it.
            f.importer.sweepOrphans(emptySet());assertTrue(File(prepared.path).exists())
            f.importer.registered(prepared.path);f.importer.sweepOrphans(setOf(prepared.path));assertTrue(File(prepared.path).exists())
            f.importer.discard(prepared);assertFalse(File(prepared.path).exists());assertTrue(f.source.exists())
        }
    }
    @Test fun `canceling during preparation drops partial bytes and keeps the original`()=runTest {
        Fixture().use{f->
            var checks=0
            val canceled=runCatching{f.importer.prepare(f.uri){if(++checks==4)throw CancellationException("Pause")}}.exceptionOrNull()
            assertTrue(canceled is CancellationException);assertEquals(900_000L,f.source.length())
            assertEquals(0,File(f.paths.filesDir,"imports").walkTopDown().count{it.isFile})
        }
    }
    @Test fun `compressing selected originals creates one lossless archive with unique entry names`() = runTest {
        Fixture().use { fixture ->
            val local = File(fixture.root, "dir/Original.jpg").apply {
                parentFile!!.mkdirs()
                writeText("second file")
            }
            val archive = fixture.importer.prepareZip(
                listOf(fixture.uri, local.path, fixture.uri), "selected.zip"
            ) {}!!
            assertTrue(fixture.importer.isStaged(archive.path))
            java.util.zip.ZipFile(File(archive.path)).use { zip ->
                assertEquals(2, zip.size())
                assertArrayEquals(fixture.source.readBytes(), zip.getInputStream(zip.getEntry("Original.jpg")).readBytes())
                assertEquals("second file", zip.getInputStream(zip.getEntry("Original (1).jpg")).reader().readText())
            }
            fixture.importer.discard(archive)
            assertTrue(fixture.source.isFile)
            assertEquals("second file", local.readText())
        }
    }

    @Test fun `canceling a compressed import removes the partial archive`() = runTest {
        Fixture().use { fixture ->
            var checks = 0
            val failure = runCatching {
                fixture.importer.prepareZip(listOf(fixture.uri), "selected.zip") {
                    if (++checks == 3) throw CancellationException("Canceled")
                }
            }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertEquals(0, File(fixture.paths.filesDir, "imports").walkTopDown().count { it.isFile })
            assertEquals(900_000L, fixture.source.length())
        }
    }

    @Test fun `an offline pin owns a separate copy that survives cache and source deletion`() {
        Fixture().use{f->
            val retained=f.importer.retainOffline(f.uri,"track")!!
            val bytes=f.source.readBytes();assertTrue(f.source.delete())
            f.paths.cacheDir.deleteRecursively();f.importer.sweepOrphans(emptySet())
            assertArrayEquals(bytes,File(retained).readBytes());assertTrue(retained.contains("/offline/"))
        }
    }
}
