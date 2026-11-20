package app.tgdrive.rebuild

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.infinity.drive.core.files.MediaStoreDownloadWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class MediaStoreDownloadWriterTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun refusesToReportAnUnpublishedDownloadAndRemovesThePendingEntry() {
        val target = temporary.newFile()
        val writer = writer(target, publishCount = 0)

        val result = writer.write("report.txt", "text/plain", null) { it.write("contents".toByteArray()) }

        assertNull(result)
        assertFalse(target.exists())
    }

    @Test
    fun returnsThePublishedUriWhenTheProviderCannotReturnAFilesystemPath() {
        val target = temporary.newFile()
        val writer = writer(target, publishCount = 1, refusePathLookup = true)

        val result = writer.write("report.txt", "text/plain", null) { it.write("contents".toByteArray()) }

        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        assertEquals(ContentUris.withAppendedId(collection, 1).toString(), result)
        assertEquals("contents", target.readText())
    }

    private fun writer(target: File, publishCount: Int, refusePathLookup: Boolean = false): MediaStoreDownloadWriter {
        val context = RuntimeEnvironment.getApplication()
        val provider = DownloadsProvider(target, publishCount, refusePathLookup).apply {
            attachInfo(context, ProviderInfo().apply { authority = MediaStore.AUTHORITY })
        }
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, provider)
        return MediaStoreDownloadWriter(context)
    }

    private class DownloadsProvider(
        val target: File,
        val publishCount: Int,
        val refusePathLookup: Boolean
    ) : ContentProvider() {
        override fun onCreate() = true

        override fun insert(uri: Uri, values: ContentValues?): Uri = ContentUris.withAppendedId(uri, 1)

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
            publishCount

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            if (refusePathLookup) throw SecurityException("The provider does not expose filesystem paths")
            return null
        }

        override fun getType(uri: Uri) = "text/plain"

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            if (target.delete()) 1 else 0
    }
}
