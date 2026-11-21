package app.tgdrive.rebuild

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.infinity.drive.core.files.NoteDraft
import com.infinity.drive.core.files.NoteDraftStore
import com.infinity.drive.domain.model.ViewMode
import com.infinity.drive.presentation.note.NoteEditorScreen
import com.infinity.drive.presentation.note.NoteEditorViewModel
import com.infinity.drive.presentation.theme.DriveInfinityTheme
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.infinity.drive.core.crypto.CredentialCipher
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.data.mapper.toDomain
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.BackupState
import org.koin.core.context.GlobalContext
import com.infinity.drive.core.files.PendingShare
import com.infinity.drive.di.appModules
import com.infinity.drive.domain.model.UserPreferences
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.presentation.DriveInfinityApp
import com.infinity.drive.presentation.platform.ProvidePlatformActions
import com.infinity.drive.presentation.preview.PreviewContent
import com.infinity.drive.presentation.preview.PreviewContentResolver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowContentResolver

/** Real modules, database, view models and navigation, with device key wrapping faked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class RebuiltUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    @Before fun start() {
        val context=RuntimeEnvironment.getApplication()
        val app=startKoin {
            androidContext(context)
            workManagerFactory()
            modules(appModules+module {
                // Keep the manual-credentials fallback deterministic on release CI too.
                // NativeSignInUiTest covers phone-first sign-in with credentials present.
                // Tests must never start a live TDLib session using build-time secrets.
                single<com.infinity.drive.core.telegram.TelegramAppCredentials> {
                    com.infinity.drive.core.telegram.TelegramAppCredentials { null }
                }
                single<CredentialCipher> { object:CredentialCipher {
                    override fun encrypt(plaintext:ByteArray)=plaintext.copyOf()
                    override fun decrypt(ciphertext:ByteArray)=ciphertext.copyOf()
                } }
            })
        }
        runBlocking {
            app.koin.get<SettingsRepository>().update { UserPreferences(onboardingComplete=true,updateCheckEnabled=false) }
            val dao=app.koin.get<FileDao>()
            listOf("Photo.jpg" to FileCategory.IMAGE,"Song.mp3" to FileCategory.AUDIO,"Report.pdf" to FileCategory.DOCUMENT).forEach { (name,category) ->
                dao.upsert(FileEntity(id=name,folderId=null,name=name,sizeBytes=123,mimeType=when(category){FileCategory.IMAGE->"image/jpeg";FileCategory.AUDIO->"audio/mpeg";else->"application/pdf"},category=category,localPath=null,contentHash=null,chatId=null,messageId=null,remoteFileId=null,remoteUniqueId=null,backupState=BackupState.NONE,createdAt=1,modifiedAt=1,addedAt=1))
            }
        }
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val share=app.koin.get<PendingShare>()
        activity.get().setContent { ProvidePlatformActions { DriveInfinityApp(share) } }
    }
    @After fun stop() {
        if(::activity.isInitialized)activity.pause().stop().destroy()
        stopKoin()
    }
    @Test fun selectingTheSameLocalFileInSeparateDrivesCreatesIndependentRows() = runBlocking {
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsRepository>()
        val files = koin.get<com.infinity.drive.domain.repository.FileRepository>()
        val source = File(RuntimeEnvironment.getApplication().cacheDir, "per-drive-import.txt")
        source.writeText("A file that belongs to two separate drives")
        try {
            settings.update { it.copy(storageChatId = -1001L) }
            val first = files.importLocalFile(source.absolutePath, null)
                as com.infinity.drive.core.common.AppResult.Success
            settings.update { it.copy(storageChatId = -1002L) }
            org.junit.Assert.assertNull(files.findDuplicate(source.absolutePath))
            val second = files.importLocalFile(source.absolutePath, null)
                as com.infinity.drive.core.common.AppResult.Success
            org.junit.Assert.assertEquals(-1002L, second.value.chatId)
            org.junit.Assert.assertNotEquals(first.value.id, second.value.id)
        } finally {
            source.delete()
        }
    }

    @Test fun settingsSearchHidesGridColumnsWhileGalleryUsesListMode() {
        val settings = GlobalContext.get().get<SettingsRepository>()
        runBlocking { settings.update { it.copy(galleryViewMode = ViewMode.LIST) } }
        compose.waitUntil(15_000) { compose.onNodeWithText("Your library").isDisplayed() }
        compose.onNodeWithTag("navigation-settings").performClick()
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("grid columns")
        compose.onNodeWithText("Gallery grid columns", substring = false).assertDoesNotExist()
        compose.onNodeWithText("No settings found").assertIsDisplayed()

        runBlocking { settings.update { it.copy(galleryViewMode = ViewMode.GRID) } }
        val result = compose.onNodeWithText("Gallery grid columns", substring = false)
        compose.waitUntil(5_000) { result.isDisplayed() }
        result.performClick()
        result.assertIsDisplayed()
    }

    @Test fun matchingLocalBytesDoNotInventTheHashOfAnUnverifiedRemoteFile() = runBlocking {
        val koin = GlobalContext.get()
        val dao = koin.get<FileDao>()
        val files = koin.get<com.infinity.drive.domain.repository.FileRepository>()
        val settings = koin.get<SettingsRepository>()
        val cache = RuntimeEnvironment.getApplication().cacheDir
        val original = File.createTempFile("original-", ".txt", cache)
        val incoming = File.createTempFile("incoming-", ".txt", cache)
        try {
            original.writeText("current bytes")
            incoming.writeText("current bytes")
            settings.update { it.copy(storageChatId = -1001L) }
            val local = files.importLocalFile(original.path, null)
                as com.infinity.drive.core.common.AppResult.Success
            val row = requireNotNull(dao.byId(local.value.id))
            dao.upsert(row.copy(messageId = 42, remoteFileId = "remote", backupState = BackupState.BACKED_UP))
            org.junit.Assert.assertNull(files.findDuplicate(incoming.path))
            org.junit.Assert.assertNull(dao.byId(row.id)?.contentHash)
        } finally {
            original.delete()
            incoming.delete()
        }
    }

    @Test fun aDownloadedPdfWithAnAndroidContentUriDoesNotAskToDownloadAgain() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val authority = "saved.download.test"
        val uri = Uri.parse("content://$authority/42")
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "application/pdf"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?) =
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
                    .apply { addRow(arrayOf<Any>("Report.pdf", 123)) }
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?,
                selectionArgs: Array<out String>?) = 0
        }.apply { attachInfo(context, ProviderInfo().apply { this.authority = authority }) }
        ShadowContentResolver.registerProviderInternal(authority, provider)
        val koin = GlobalContext.get()
        val dao = koin.get<FileDao>()
        val file = requireNotNull(dao.byId("Report.pdf")).copy(
            localPath = uri.toString(), messageId = 42L, remoteFileId = "downloaded",
            backupState = BackupState.BACKED_UP
        )
        dao.upsert(file)
        val result = koin.get<PreviewContentResolver>().resolve(file.toDomain()).last()
        org.junit.Assert.assertEquals(PreviewContent.Pdf(uri.toString()), result)
    }

    @Test fun leavingARevertedNoteClearsItsPersistedDraftBeforeClosing() {
        val koin = GlobalContext.get()
        val files = koin.get<com.infinity.drive.domain.repository.FileRepository>()
        val settings = koin.get<SettingsRepository>()
        val drafts = koin.get<NoteDraftStore>()
        val drive = -1001L
        val id = runBlocking {
            settings.update { it.copy(storageChatId = drive) }
            val saved = files.saveNote(null, null, "Audit note", "Original text")
                as com.infinity.drive.core.common.AppResult.Success
            saved.value
        }
        val slot = "$drive:note:$id"
        runBlocking { drafts.write(slot, NoteDraft("Audit note", "Changed draft")) }
        val lifetime = ViewModelStore()
        val closed = mutableStateOf(false)
        val model = NoteEditorViewModel(
            files, koin.get(), SavedStateHandle(mapOf("fileId" to id)),
            draftStore = drafts, settings = settings
        ).also { lifetime.put("editor", it) }
        try {
            activity.get().setContent {
                DriveInfinityTheme {
                    if (closed.value) Text("Closed editor")
                    else NoteEditorScreen(viewModel = model, onBack = {
                        lifetime.clear()
                        closed.value = true
                    })
                }
            }
            compose.waitUntil(5_000) { compose.onNodeWithText("Changed draft", substring = false).isDisplayed() }
            compose.onNode(hasText("Changed draft") and hasSetTextAction()).performTextReplacement("Original text")
            compose.runOnIdle { org.junit.Assert.assertFalse(model.uiState.value.dirty) }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitUntil(5_000) { compose.onNodeWithText("Closed editor", substring = false).isDisplayed() }
            org.junit.Assert.assertNull(runBlocking { drafts.read(slot) })
        } finally {
            lifetime.clear()
        }
    }

    @Test fun hiddenLockStaysInHiddenAndRetiredActionsAreAbsentFromSettings(){
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Hidden",substring=false).performClick()
        compose.onNodeWithContentDescription("Hidden settings").performClick()
        compose.onNodeWithText("Hidden settings",substring=false).assertIsDisplayed()
        compose.onNodeWithText("Lock Hidden",substring=false).assertIsDisplayed()
        compose.onNodeWithTag("navigation-settings").performClick()
        compose.onNodeWithText("Security",substring=false).performScrollTo().performClick()
        for (label in listOf("Lock Hidden", "Encrypt uploads", "Encrypt thumbnails", "Verify Telegram key backup",
            "Restore encryption key", "Export content key", "Import content key")) {
            compose.onNodeWithText(label, substring = false).assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Telegram account", substring = false).performClick()
        for (label in listOf("Rebuild index from Telegram", "Restore library", "Check drive integrity",
            "Team workspaces", "Public links", "Telegram accounts", "Connected")) {
            compose.onNodeWithText(label, substring = false).assertDoesNotExist()
        }
    }
    @Test fun galleryReturnsFromAlbumsToAllMediaAfterFilteringPhotos() {
        compose.waitUntil(15000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation-gallery").performClick()
        compose.onNodeWithText("Photos",substring=false).performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("gallery-file-Photo.jpg").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Photo history").assertDoesNotExist()
        compose.onNodeWithContentDescription("Sort gallery").performClick()
        compose.onNodeWithText("Albums",substring=false).performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("Not in a folder",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Sort gallery").performClick()
        compose.onNodeWithText("All media",substring=false).performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("gallery-file-Photo.jpg").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("gallery-file-Photo.jpg").assertIsDisplayed()
    }

    @Test fun realEmptyLibraryNavigatesAcrossAllFourTabs() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your library").assertIsDisplayed()
        capture("home")
        compose.onNodeWithTag("navigation-gallery").performClick()
        compose.onNodeWithContentDescription("Search gallery").performClick()
        compose.onNodeWithText("Search photos and videos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close search").performClick()
        capture("gallery")
        compose.onNodeWithContentDescription("Sort gallery").performClick()
        compose.onNodeWithText("Size").performClick()
        compose.onNodeWithTag("navigation-audio").performClick()
        compose.onNodeWithContentDescription("Search audio").performClick()
        compose.onNodeWithText("Search audio").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Song",substring=false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Song",substring=false).assertIsDisplayed()
        compose.onNodeWithContentDescription("Playlists").assertDoesNotExist()
        capture("audio")
        compose.onNodeWithText("Report.pdf").assertDoesNotExist()
        compose.onNodeWithText("Photo.jpg").assertDoesNotExist()
        compose.onNodeWithTag("navigation-settings").performClick()
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.onNodeWithText("Search settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close search").performClick()
        capture("settings")
        compose.onNodeWithText("Telegram account").performClick()
        compose.onNodeWithText("Previous library").assertDoesNotExist()
        runBlocking { GlobalContext.get().get<SettingsRepository>().update { it.copy(onboardingComplete=false) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Get started").performClick()
        compose.waitUntil(10_000){compose.onNodeWithText("API access").isDisplayed()}
        compose.onNodeWithText("API access").assertIsDisplayed()
        capture("api-setup")
        compose.onNodeWithText("Continue",substring=false).assertIsNotEnabled()
    }
    @Test fun floatingNavigationPreferencesPersistAndTabsRemainUsable(){
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation-settings").performClick()
        compose.onNodeWithText("Appearance",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Floating navigation bar",substring=false).performScrollTo().performClick()
        compose.onAllNodesWithText("Floating navigation bar",substring=false).onFirst().assertIsDisplayed()
        compose.onNodeWithText("Navigation bar style", substring=false).assertDoesNotExist()
        compose.onNodeWithText("Enable custom navigation",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Blur background",substring=false).assertIsNotEnabled()
        compose.onNodeWithTag("navigation-settings").performClick().assertIsSelected()
        val repository=GlobalContext.get().get<SettingsRepository>()
        compose.onNodeWithText("Show labels",substring=false).performClick()
        compose.waitUntil(5000){compose.waitForIdle();!runBlocking{repository.preferences.first().navigationBarLabels}}
        capture("floating-navigation-settings")
        for(tab in listOf("home","gallery","audio","settings","audio","home")){
            compose.onNodeWithTag("navigation-$tab").performClick()
            // Selection follows the committed destination, including a queued tap during a transition.
            compose.waitUntil(5000){
                compose.waitForIdle()
                compose.onNodeWithTag("navigation-$tab").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Selected]
            }
            compose.onNodeWithTag("navigation-$tab").assertIsSelected()
        }
        assertTrue(!runBlocking{repository.preferences.first().navigationBarLabels})
    }

    @Test fun fileActionsUseHeaderDetailsAndHideActionsForAnOpenFile(){
        val file = runBlocking {
            val koin = GlobalContext.get()
            koin.get<FileDao>().setRemoteMapping("Report.pdf", 500L, 100L, "report-remote", "report-unique", BackupState.BACKED_UP)
            requireNotNull(koin.get<com.infinity.drive.domain.repository.FileRepository>().getFile("Report.pdf"))
        }
        assertTrue(file.hasRemoteCopy)
        compose.runOnUiThread{activity.get().setContent{ProvidePlatformActions{
            com.infinity.drive.presentation.theme.DriveInfinityTheme{
                com.infinity.drive.presentation.components.FileActionsSheet(file,onDismiss={},onOpen={},isOpen=true)
            }
        }}}
        compose.onNodeWithText("Open",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Make available offline",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Version history", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Download",substring=false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("File details").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Back to file actions").assertIsDisplayed()
    }

    @Test fun audioFileActionsExcludePlaylists() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("navigation-audio").performClick()
        val options = compose.onNodeWithContentDescription("Audio options for Song.mp3")
        compose.waitUntil(10_000) { options.isDisplayed() }
        options.performClick()
        compose.onNodeWithText("Download", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add to playlist", substring = false).assertDoesNotExist()
    }

    @Test fun fileFavoriteActionUpdatesItsVisibleStateWithoutClosingTheSheet(){
        val file=runBlocking{GlobalContext.get().get<com.infinity.drive.domain.repository.FileRepository>().getFile("Report.pdf")}!!
        compose.runOnUiThread{activity.get().setContent{ProvidePlatformActions{
            com.infinity.drive.presentation.theme.DriveInfinityTheme{com.infinity.drive.presentation.components.FileActionsSheet(file,onDismiss={},onOpen={})}
        }}}
        compose.onNodeWithText("Add To Favourite",substring=false).performScrollTo().performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText("Remove from Favourite",substring=false).fetchSemanticsNodes().isNotEmpty()}
        assertTrue(runBlocking{GlobalContext.get().get<FileDao>().byId("Report.pdf")!!.isFavorite})
    }

    @Test fun activityUsesInlineFiltersAndTheSharedFileActionSheet(){
        prepareActivityChannel()
        runBlocking{GlobalContext.get().get<com.infinity.drive.data.local.dao.TransferDao>().upsert(
            com.infinity.drive.data.local.entity.TransferEntity(id="done-task",type=com.infinity.drive.domain.model.TransferType.UPLOAD,
                fileId="Report.pdf",displayName="Report.pdf",localPath=null,chatId=-100701L,messageId=null,remoteFileId=null,sizeBytes=123,
                state=com.infinity.drive.domain.model.TransferState.COMPLETED,createdAt=1,updatedAt=1,completedAt=1))}
        compose.waitUntil(15000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Activity").performClick()
        compose.onNodeWithText("All",substring=false).assertIsDisplayed()
        compose.onNode(hasText("Finished",substring=false) and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5000){compose.onAllNodesWithContentDescription("Transfer options").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Transfer options").performClick()
        compose.waitUntil(10000){compose.onNodeWithText("Open",substring=false).isDisplayed()}
        compose.onNodeWithText("Open",substring=false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Download",substring=false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Send in Telegram",substring=false).performScrollTo().assertIsDisplayed()
    }

    private fun prepareActivityChannel() = runBlocking {
        val koin = GlobalContext.get()
        koin.get<SettingsRepository>().update { it.copy(storageChatId = -100701L) }
        val files = koin.get<FileDao>()
        files.byId("Report.pdf")?.let { files.upsert(it.copy(chatId = -100701L)) }
    }

    @Test fun activitySwitchesHistoryAndShowsContextualUploadOptions() {
        prepareActivityChannel()
        val koin = GlobalContext.get()
        val dao = koin.get<com.infinity.drive.data.local.dao.TransferDao>()
        runBlocking {
            val running = com.infinity.drive.data.local.entity.TransferEntity(
                "first-channel-upload", com.infinity.drive.domain.model.TransferType.UPLOAD, null,
                "First channel.docx", null, -100701L, null, null, sizeBytes = 100,
                state = com.infinity.drive.domain.model.TransferState.RUNNING, createdAt = 1, updatedAt = 1
            )
            dao.upsert(running)
            dao.upsert(running.copy(id = "second-channel-upload", chatId = -100702L, displayName = "Second channel.zip"))
        }
        compose.waitUntil(15000) { compose.onNodeWithText("Your library").isDisplayed() }
        compose.onNodeWithContentDescription("Activity").performClick()
        compose.onNodeWithText("First channel.docx").assertIsDisplayed()
        compose.onNodeWithText("Second channel.zip").assertDoesNotExist()
        compose.onNodeWithContentDescription("More actions", substring = false).performClick()
        compose.onNodeWithText("Uploads in progress").assertIsDisplayed()
        compose.onNodeWithText("Pause active transfers").assertIsDisplayed()
        runBlocking { koin.get<SettingsRepository>().update { it.copy(storageChatId = -100702L) } }
        compose.waitUntil(5000) { compose.onNodeWithText("Second channel.zip").isDisplayed() }
        compose.onNodeWithText("First channel.docx").assertDoesNotExist()
        compose.onNodeWithText("Uploads in progress").assertDoesNotExist()
        compose.onNodeWithContentDescription("Transfer options").performClick()
        compose.onNodeWithText("Pause upload").assertIsDisplayed()
        compose.onNodeWithText("Cancel upload").assertIsDisplayed()
    }

    @Test fun showInFolderRevealsTheRequestedFileBeyondTheFirstPage(){
        val dao=GlobalContext.get().get<FileDao>()
        runBlocking{
            val template=dao.byId("Report.pdf")!!
            (0 until 85).forEach{dao.upsert(template.copy(id="page-file-$it",name="Document %03d.pdf".format(it),addedAt=2))}
            dao.upsert(template.copy(id="deep-target",name="Z target.pdf",addedAt=System.currentTimeMillis()))
        }
        compose.waitUntil(15000){compose.onAllNodesWithContentDescription("Options for Z target.pdf").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Options for Z target.pdf").performClick()
        compose.onNodeWithText("Show in folder",substring=false).performScrollTo().performClick()
        compose.waitUntil(10000){compose.onAllNodes(hasStateDescription("Located file")).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Z target.pdf",substring=false).assertIsDisplayed()
    }

    @Test fun allFilesKeepsACleanHeaderAndAnchorsSortingToItsButton(){
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("All files",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("My drive",substring=false).assertDoesNotExist()
        compose.onNodeWithText("loaded files",substring=true).assertDoesNotExist()
        fun screenBounds(node:androidx.compose.ui.semantics.SemanticsNode):androidx.compose.ui.geometry.Rect{
            val c=node.layoutInfo.coordinates
            return androidx.compose.ui.geometry.Rect(c.localToScreen(androidx.compose.ui.geometry.Offset.Zero),androidx.compose.ui.geometry.Size(c.size.width.toFloat(),c.size.height.toFloat()))
        }
        val button=screenBounds(compose.onNodeWithContentDescription("Sort").fetchSemanticsNode())
        compose.onNodeWithContentDescription("Sort").performClick()
        val menu=screenBounds(compose.onNodeWithText("Name",substring=false).assertIsDisplayed().fetchSemanticsNode())
        capture("all-files-sort-anchor")
        assertTrue("Sort menu should align with Sort rather than the adjacent More button",menu.right<=button.right+20)
        assertTrue("Sort options should appear below the top bar",menu.top>=button.bottom)
        capture("all-files-sort-anchor")
    }

    @Test fun settingsSearchFindsControlsAndExplainsNoResults() {
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation-settings").performClick()
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("background")
        compose.onNodeWithText("Background playback",substring=false).assertIsDisplayed().performClick()
        compose.onNodeWithText("Background playback",substring=false).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("zzzz-no-such-setting")
        compose.onNodeWithText("No settings found").assertIsDisplayed()
        compose.onNodeWithText("Clear search",substring=false).performClick()
        compose.onNodeWithText("Telegram account").assertIsDisplayed()
    }

    @Test fun audioSortDoesNotChangeTheFileBrowserOrder() {
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation-audio").performClick()
        compose.onNodeWithContentDescription("Sort audio").performClick()
        compose.onNodeWithText("Size",substring=false).performClick()
        compose.waitUntil(10_000){compose.waitForIdle();runBlocking{GlobalContext.get().get<SettingsRepository>().preferences.first().audioSortField==com.infinity.drive.domain.model.FileSortField.SIZE}}
        val prefs=runBlocking{GlobalContext.get().get<SettingsRepository>().preferences.first()}
        org.junit.Assert.assertEquals(com.infinity.drive.domain.model.FileSortField.NAME,prefs.sortField)
        compose.onNodeWithText("Favorites",substring=false).performClick()
        // Room/Paging runs outside Compose's idling clock; wait for the filtered result.
        compose.waitUntil(10_000){compose.onNodeWithText("No favorites yet").isDisplayed()}
        compose.onNodeWithText("No favorites yet").assertIsDisplayed()
        compose.onNodeWithText("On device",substring=false).performClick()
        compose.waitUntil(10_000){compose.onNodeWithText("No audio on this device").isDisplayed()}
        compose.onNodeWithText("No audio on this device").assertIsDisplayed()
    }

    @Test @Config(qualifiers="w320dp-h640dp-xhdpi")
    fun narrowScreenWithLargeTextKeepsNavigationSearchAndSignInReachable() {
        val pending=GlobalContext.get().get<PendingShare>()
        compose.runOnUiThread {
            activity.get().setContent { ProvidePlatformActions {
                val density=androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,2f)
                ){DriveInfinityApp(pending)}
            } }
        }
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Upload").assertIsDisplayed()
        listOf("gallery","audio","settings").forEach {
            compose.onNodeWithTag("navigation-$it").assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Search $it").performClick()
            compose.onNode(hasSetTextAction()).assertIsDisplayed()
            compose.onNodeWithContentDescription("Close search").performClick()
        }
        runBlocking { GlobalContext.get().get<SettingsRepository>().update { it.copy(onboardingComplete=false) } }
        compose.waitUntil(10_000){compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.onNodeWithText("Continue",substring=false).assertIsDisplayed().assertIsNotEnabled()
        capture("api-large-text")
    }


    @Test fun backGestureCommitsOnceAndSearchClosesBeforeLeavingThePage() {
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("All files",substring=false).performClick()
        compose.onNodeWithText("All files",substring=false).assertIsDisplayed()
        compose.runOnUiThread {
            val dispatcher=activity.get().onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f,100f,0f,0))
            dispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(160f,100f,.6f,0))
        }
        compose.onNodeWithText("All files",substring=false).assertIsDisplayed()
        compose.onNodeWithText("Your library").assertDoesNotExist()
        compose.runOnUiThread{activity.get().onBackPressedDispatcher.dispatchOnBackCancelled()}
        compose.onNodeWithContentDescription("Search all files").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Photo")
        compose.runOnUiThread{activity.get().onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithContentDescription("Search all files").assertIsDisplayed()
        compose.runOnUiThread{activity.get().onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithText("Your library").assertIsDisplayed()
    }

    @Test fun sixLibraryDestinationsRemainReachableOutsideTheSummaryCard() {
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Stored in Telegram").assertIsDisplayed()
        listOf("All files", "Note", "Favorites", "Archived", "Hidden", "Trash").forEach {
            compose.onNodeWithText(it, substring=false).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Show collections").assertDoesNotExist()
        compose.onNodeWithContentDescription("Activity").assertIsDisplayed()
        capture("library-shortcuts")
        compose.onNodeWithText("Note", substring=false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Add note").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Hidden", substring=false).performClick()
        compose.onNodeWithText("Hidden", substring=false).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Your library").assertIsDisplayed()
    }

    @Test fun homeUploadOpensOnlyTheRequestedPickerWithoutChangingDestination() {
        var filePicks=0
        var folderPicks=0
        var mediaPicks=0
        var scans=0
        var photos=0
        val pending=GlobalContext.get().get<PendingShare>()
        compose.runOnUiThread {
            activity.get().setContent { ProvidePlatformActions {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.infinity.drive.presentation.platform.LocalMultiFilePicker provides com.infinity.drive.presentation.platform.MultiFilePicker { callback->filePicks++;callback(emptyList()) },
                    com.infinity.drive.presentation.platform.LocalFolderPicker provides com.infinity.drive.presentation.platform.FolderPicker { callback->folderPicks++;callback(com.infinity.drive.presentation.platform.PickResult.Canceled) },
                    com.infinity.drive.presentation.platform.LocalDeviceImportActions provides object:com.infinity.drive.presentation.platform.DeviceImportActions{
                        override fun pickMedia(onPicked:(List<String>)->Unit){mediaPicks++;onPicked(emptyList())}
                        override fun scanDocument(onResult:(com.infinity.drive.presentation.platform.CaptureResult)->Unit){scans++;onResult(com.infinity.drive.presentation.platform.CaptureResult.Cancelled)}
                        override fun takePhoto(onResult:(com.infinity.drive.presentation.platform.CaptureResult)->Unit){photos++;onResult(com.infinity.drive.presentation.platform.CaptureResult.Failed("Test camera unavailable"))}
                    }
                ){ DriveInfinityApp(pending) }
            } }
        }
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Upload",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Upload files",substring=false).assertIsDisplayed()
        compose.onNodeWithText("Upload folder",substring=false).assertIsDisplayed()
        compose.onNodeWithText("New note",substring=false).assertDoesNotExist()
        capture("upload-sheet")
        compose.onNodeWithText("Upload files",substring=false).performClick()
        compose.waitUntil(5_000){filePicks==1}
        compose.onNodeWithTag("navigation-home").assertIsSelected()
        compose.onNodeWithText("Your library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Upload").performClick()
        compose.onNodeWithText("Upload folder",substring=false).performClick()
        compose.waitUntil(5_000){folderPicks==1}
        compose.onNodeWithTag("navigation-home").assertIsSelected()
        fun pick(label:String){compose.onNodeWithContentDescription("Upload").performClick();compose.onNodeWithText(label,substring=false).performScrollTo().performClick()}
        pick("Upload photos and videos")
        compose.waitUntil(5_000){mediaPicks==1}
        pick("Scan a document")
        compose.waitUntil(5_000){scans==1}
        pick("Take a photo")
        compose.waitUntil(5_000){photos==1}
        compose.onNodeWithText("Test camera unavailable").assertIsDisplayed()
        compose.onNodeWithText("OK",substring=false).performClick()
        pick("Import from Google Photos")
        compose.onNodeWithText("Choose downloaded ZIP parts").performScrollTo().performClick()
        compose.waitUntil(5_000){filePicks==2}
        compose.onNodeWithContentDescription("Back to add options").performClick()
        compose.onNodeWithText("Save from a Telegram chat",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Search your chats").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close chat picker").performClick()
        compose.onNodeWithText("New folder",substring=false).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Test folder")
        compose.onNodeWithText("Create",substring=false).performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithText("Folder name",substring=false).fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("navigation-home").assertIsSelected()
        compose.onNodeWithTag("navigation-gallery").performClick()
        compose.onNodeWithContentDescription("Upload").assertDoesNotExist()
        compose.onNodeWithTag("navigation-audio").performClick()
        compose.onNodeWithContentDescription("Upload").assertDoesNotExist()
    }

    @Test fun galleryAndAudioFiltersFillTheirAvailableWidthEqually() {
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation-gallery").performClick()
        fun equalTabs(labels:List<String>){
            val tabs=labels.map{compose.onNode(hasText(it) and isSelectable()).fetchSemanticsNode().boundsInRoot}
            tabs.zipWithNext().forEach{(a,b)->
                org.junit.Assert.assertEquals(a.width,b.width,1f)
                org.junit.Assert.assertEquals(a.right,b.left,1f)
            }
        }
        equalTabs(listOf("All","Photos","Videos"))
        compose.onNodeWithContentDescription("Sort gallery").performClick()
        compose.onNodeWithText("Albums",substring=false).assertIsDisplayed()
        compose.onNodeWithText("All media",substring=false).performClick()
        compose.onNodeWithTag("navigation-audio").performClick()
        equalTabs(listOf("All","Favorites","On device"))
        compose.onNodeWithText("Play all",substring=false).assertDoesNotExist()
    }

    @Test fun expandedAudioControlsUseTheSamePlaybackStateAsTheMiniPlayer() {
        runBlocking {
            val dao=GlobalContext.get().get<FileDao>()
            dao.upsert(dao.byId("Song.mp3")!!.copy(localPath=File("src/androidTest/assets/cover-song.mp3").absolutePath))
        }
        val file=runBlocking{GlobalContext.get().get<com.infinity.drive.domain.repository.FileRepository>().getFile("Song.mp3")}!!
        val playback=kotlinx.coroutines.flow.MutableStateFlow(com.infinity.drive.presentation.platform.AudioPlaybackState(
            file=file,playing=true,metadata=com.infinity.drive.presentation.platform.AudioMetadata("Midnight City","M83",lyrics="[00:00]First lyric\n[00:30]Second lyric\n[01:30]Third lyric"),durationMs=240_000,positionMs=60_000,canSkipPrevious=true,canSkipNext=true))
        var previous=0
        var next=0
        val player=object:com.infinity.drive.presentation.platform.AudioPlayback {
            override val state=playback
            override fun play(file:com.infinity.drive.domain.model.DriveFile){playback.value=playback.value.copy(file=file,playing=true)}
            override fun setQueue(ids:List<String>){}
            override fun toggle(){playback.value=playback.value.copy(playing=!playback.value.playing)}
            override fun pause(){playback.value=playback.value.copy(playing=false)}
            override fun previous(){previous++}
            override fun next(){next++}
            override fun stop(){playback.value=com.infinity.drive.presentation.platform.AudioPlaybackState()}
            override fun seek(positionMs:Long){playback.value=playback.value.copy(positionMs=positionMs)}
            override suspend fun metadata(file:com.infinity.drive.domain.model.DriveFile)=playback.value.metadata
        }
        val pending=GlobalContext.get().get<PendingShare>()
        compose.runOnUiThread{activity.get().setContent{ProvidePlatformActions{
            androidx.compose.runtime.CompositionLocalProvider(com.infinity.drive.presentation.platform.LocalAudioPlayback provides player){DriveInfinityApp(pending)}
        }}}
        compose.waitUntil(15_000){compose.onAllNodesWithText("Midnight City",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.runOnUiThread{playback.value=playback.value.copy(playing=true)}
        compose.onNodeWithText("Midnight City",substring=false).performClick()
        try {compose.waitUntil(10_000){runCatching{compose.onNodeWithContentDescription("Back",useUnmergedTree=false).assertIsDisplayed();true}.getOrDefault(false)}}
        catch(error:Throwable){capture("audio-route-failure");throw error}
        capture("now-playing")
        compose.onNodeWithContentDescription("Open lyrics").assertDoesNotExist()
        compose.onNodeWithText("First lyric",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Third lyric",substring=true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Seek audio").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress
        ){change->assertTrue(change(90_000f))}
        org.junit.Assert.assertEquals(90_000L,playback.value.positionMs)
        compose.onNodeWithContentDescription("Previous audio").performClick()
        compose.onAllNodesWithContentDescription("Next audio").onLast().performClick()
        org.junit.Assert.assertEquals(1,previous)
        org.junit.Assert.assertEquals(1,next)
        compose.onAllNodesWithContentDescription("Pause audio").onLast().performClick()
        org.junit.Assert.assertFalse(playback.value.playing)
        compose.onNodeWithContentDescription("Playback options").assertDoesNotExist()
        compose.onNodeWithContentDescription("Sleep timer").assertDoesNotExist()
        // Stop can also arrive from the media notification/session after the old menu was removed.
        compose.runOnUiThread{player.stop()}
        compose.waitUntil(5_000){playback.value.file==null}
        compose.onNodeWithText("Your library").assertIsDisplayed()
        compose.onNodeWithText("Midnight City",substring=false).assertDoesNotExist()
    }

    @Test fun mediaTabsRestoreFiltersAndSearchAfterSwitching() {
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation-audio").performClick()
        compose.onNodeWithText("Favorites",substring=false).performClick()
        compose.onNodeWithTag("navigation-gallery").performClick()
        compose.onNodeWithText("Photos",substring=false).performClick()
        compose.onNodeWithTag("navigation-audio").performClick()
        compose.onNode(hasText("Favorites") and isSelectable()).assertIsSelected()
        compose.onNodeWithTag("navigation-gallery").performClick()
        compose.onNode(hasText("Photos") and isSelectable()).assertIsSelected()
        compose.onNodeWithContentDescription("Search gallery").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Photo")
        compose.onNodeWithTag("navigation-audio").performClick()
        compose.onNodeWithTag("navigation-gallery").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Photo")
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithTag("navigation-home").performClick()
        compose.onNodeWithText("Your library").assertIsDisplayed()
    }

    @Test fun audioScrollPositionSurvivesGalleryRoundTrip() {
        runBlocking {
            val dao=GlobalContext.get().get<FileDao>()
            val template=dao.byId("Song.mp3")!!
            repeat(40){index->val name="Track ${index.toString().padStart(2,'0')}.mp3";dao.upsert(template.copy(id=name,name=name))}
        }
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation-audio").performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("Song",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Track 25",substring=false))
        compose.onNodeWithText("Track 25",substring=false).assertIsDisplayed()
        compose.onNodeWithTag("navigation-gallery").performClick()
        compose.onNodeWithTag("navigation-audio").performClick()
        compose.onNodeWithText("Track 25",substring=false).assertIsDisplayed()
    }

    @Test fun allThreeThemesKeepSearchAndNavigationReachable(){
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        val settings=GlobalContext.get().get<SettingsRepository>()
        listOf(com.infinity.drive.domain.model.AppTheme.LIGHT,com.infinity.drive.domain.model.AppTheme.DARK,com.infinity.drive.domain.model.AppTheme.AMOLED).forEach{theme->
            runBlocking{settings.update{it.copy(theme=theme)}}
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Search home").performClick()
            compose.onNode(hasSetTextAction()).assertIsDisplayed()
            compose.onNodeWithContentDescription("Close search").performClick()
            compose.onNodeWithTag("navigation-gallery").performClick()
            compose.onNodeWithContentDescription("Search gallery").assertIsDisplayed()
            capture("gallery-${theme.name.lowercase()}")
            compose.onNodeWithTag("navigation-home").performClick()
            compose.onNodeWithText("Your library").assertIsDisplayed()
        }
    }

    @Test fun trashTapPreviewsWithoutRestoringAndLongPressSelects(){
        val dao=GlobalContext.get().get<FileDao>()
        runBlocking{dao.upsert(dao.byId("Report.pdf")!!.copy(trashedAt=System.currentTimeMillis()))}
        compose.waitUntil(15000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Trash",substring=false).performClick()
        compose.waitUntil(5000){compose.onNodeWithText("Report.pdf",substring=false).isDisplayed()}
        val more=compose.onNodeWithContentDescription("Trash settings").fetchSemanticsNode().boundsInRoot
        val empty=compose.onNodeWithContentDescription("Empty trash").fetchSemanticsNode().boundsInRoot
        assertTrue(more.left>=empty.right)
        compose.onNodeWithText("Report.pdf",substring=false).performClick()
        compose.waitUntil(5000){compose.onAllNodesWithContentDescription("File details").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Clear selection").assertDoesNotExist()
        assertTrue(runBlocking{dao.byId("Report.pdf")!!.trashedAt!=null})
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5000){compose.onNodeWithContentDescription("Trash settings").isDisplayed()}
        compose.onNodeWithText("Report.pdf",substring=false).performTouchInput{longClick()}
        compose.onNodeWithContentDescription("Clear selection").assertIsDisplayed()
        runBlocking{dao.upsert(dao.byId("Report.pdf")!!.copy(trashedAt=null))}
        compose.waitUntil(5000){compose.onAllNodesWithContentDescription("Clear selection").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithContentDescription("Trash settings").assertIsDisplayed()
    }

    @Test fun floatingBarKeepsTheActivityViewportFullHeight(){
        prepareActivityChannel()
        val settings=GlobalContext.get().get<SettingsRepository>()
        runBlocking{settings.update{it.copy(navigationBarBlur=true)}}
        runBlocking{GlobalContext.get().get<com.infinity.drive.data.local.dao.TransferDao>().upsert(
            com.infinity.drive.data.local.entity.TransferEntity(id="viewport-task",type=com.infinity.drive.domain.model.TransferType.UPLOAD,fileId="Report.pdf",displayName="Report.pdf",localPath=null,chatId=-100701L,messageId=null,remoteFileId=null,sizeBytes=123,state=com.infinity.drive.domain.model.TransferState.COMPLETED,createdAt=1,updatedAt=1,completedAt=1))}
        compose.waitUntil(15000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Activity").performClick()
        compose.waitUntil(5000){compose.onNode(hasText("Finished",substring=false) and hasClickAction()).isDisplayed()}
        val viewport=compose.onNodeWithTag("navigation-host").fetchSemanticsNode().boundsInRoot
        val list=compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("Activity viewport was shortened: $list / $viewport",kotlin.math.abs(list.bottom-viewport.bottom)<2f)
    }

    @Test fun folderPushAndPopKeepTheHeaderStationary(){
        runBlocking{GlobalContext.get().get<com.infinity.drive.data.local.dao.FolderDao>().upsert(
            com.infinity.drive.data.local.entity.FolderEntity("motion-folder",null,null,"Motion folder",createdAt=1,modifiedAt=1))}
        compose.waitUntil(15000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("All files",substring=false).performClick()
        compose.waitUntil(5000){compose.onNodeWithContentDescription("Open Motion folder").isDisplayed()}
        val before=compose.onNodeWithContentDescription("Search all files").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Open Motion folder").performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText("Motion folder",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithContentDescription("Search all files").fetchSemanticsNodes().forEach{org.junit.Assert.assertEquals(before,it.boundsInRoot)}
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5000){compose.onNodeWithContentDescription("Open Motion folder").isDisplayed()}
        org.junit.Assert.assertEquals(before,compose.onNodeWithContentDescription("Search all files").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun collectionsKeepSelectionAndTrashRestoreWithActivityRows() {
        val dao=GlobalContext.get().get<FileDao>()
        runBlocking {
            dao.upsert(dao.byId("Photo.jpg")!!.copy(isFavorite=true))
            dao.upsert(dao.byId("Song.mp3")!!.copy(isArchived=true))
            dao.upsert(dao.byId("Report.pdf")!!.copy(trashedAt=System.currentTimeMillis()))
        }
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        listOf("Favorites" to "Photo.jpg","Archived" to "Song.mp3","Trash" to "Report.pdf").forEach { (collection,name)->
            compose.onNodeWithText(collection,substring=false).performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithText(name,substring=false).fetchSemanticsNodes().isNotEmpty()}
            capture(collection.lowercase())
            compose.onNodeWithText(name,substring=false).performTouchInput{longClick()}
            compose.onNodeWithContentDescription("Clear selection").assertIsDisplayed()
            if(collection=="Trash"){
                compose.onNodeWithContentDescription("Restore").performClick()
                capture("trash-after-restore")
                compose.waitUntil(10_000){runBlocking{dao.byId("Report.pdf")?.trashedAt==null}}
            }else compose.onNodeWithContentDescription("Clear selection").performClick()
            compose.onNodeWithContentDescription("Back").performClick()
        }
    }

    @Test fun navigationHostKeepsItsBoundsAcrossTabsAndSecondaryPages() {
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        val bounds=compose.onNodeWithTag("navigation-host").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("navigation-settings").performClick()
        org.junit.Assert.assertEquals(bounds,compose.onNodeWithTag("navigation-host").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Appearance",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Appearance",substring=false).assertIsDisplayed()
        org.junit.Assert.assertEquals(bounds,compose.onNodeWithTag("navigation-host").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("navigation-gallery").performClick()
        org.junit.Assert.assertEquals(bounds,compose.onNodeWithTag("navigation-host").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun applicationLockRestoresTheMediaDestinationAndSearch(){
        val pending=GlobalContext.get().get<PendingShare>()
        compose.runOnUiThread{activity.get().setContent{ProvidePlatformActions{
            androidx.compose.runtime.CompositionLocalProvider(
                com.infinity.drive.presentation.platform.LocalDeviceOwnerGate provides com.infinity.drive.presentation.platform.DeviceOwnerGate { _,_,_,_-> }
            ){DriveInfinityApp(pending)}
        }}}
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation-gallery").performClick()
        compose.onNodeWithText("Photos",substring=false).performClick()
        compose.onNodeWithContentDescription("Search gallery").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Photo")
        val lock=GlobalContext.get().get<com.infinity.drive.core.security.AppLockManager>()
        compose.runOnUiThread{lock.lockNow()}
        compose.waitUntil(5_000){compose.onAllNodesWithText("Unlock",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation-gallery").assertDoesNotExist()
        compose.runOnUiThread{lock.unlock()}
        compose.waitUntil(5_000){compose.onAllNodesWithTag("navigation-gallery").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("navigation-gallery").assertIsSelected()
        compose.onNode(hasSetTextAction()).assertTextContains("Photo")
        compose.onNode(hasText("Photos") and isSelectable()).assertIsSelected()
    }

    @Test fun notesCanKeepAReadableLocalCopyWithoutChangingTelegramStatus() {
        val repo = GlobalContext.get().get<com.infinity.drive.domain.repository.FileRepository>()
        val id = runBlocking {
            (repo.saveNote(null, null, "Offline test note", "Text available locally")
                as com.infinity.drive.core.common.AppResult.Success).value
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Note", substring = false).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Keep note available offline").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Keep note available offline").performClick()
        compose.waitUntil(5_000) { runBlocking { repo.getFile(id)?.offlinePinned == true } }
        org.junit.Assert.assertEquals(BackupState.NONE, runBlocking { repo.getFile(id)?.backupState })
        org.junit.Assert.assertEquals("Text available locally", runBlocking {
            (repo.readNote(id) as com.infinity.drive.core.common.AppResult.Success).value
        })

        compose.onNodeWithContentDescription("Stop keeping note offline").performClick()
        compose.waitUntil(5_000) { runBlocking { repo.getFile(id)?.offlinePinned == false } }
        org.junit.Assert.assertEquals("Text available locally", runBlocking {
            (repo.readNote(id) as com.infinity.drive.core.common.AppResult.Success).value
        })
    }

    @Test fun dirtyNoteAsksBeforeLeavingAndCancelKeepsTheDraft(){
        val repo=GlobalContext.get().get<com.infinity.drive.domain.repository.FileRepository>()
        val id=runBlocking{(repo.saveNote(null,null,"Audit note","Original text") as com.infinity.drive.core.common.AppResult.Success).value}
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("All files",substring=false).performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithText("Audit note.md",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Audit note.md",substring=false).performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithContentDescription("Edit text").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Edit text").performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithText("Original text",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText("Original text") and hasSetTextAction()).performTextReplacement("Changed draft")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Save changes?",substring=false).assertIsDisplayed()
        compose.onNodeWithText("Cancel",substring=false).performClick()
        compose.onNodeWithText("Changed draft",substring=false).assertIsDisplayed()
        compose.runOnUiThread{activity.get().onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithText("Discard",substring=false).performClick()
        compose.waitUntil(5_000) {
            compose.onNodeWithContentDescription("Edit text").isDisplayed()
        }
        compose.onNodeWithContentDescription("Edit text").assertIsDisplayed()
        org.junit.Assert.assertEquals("Original text",runBlocking{(repo.readNote(id) as com.infinity.drive.core.common.AppResult.Success).value})
    }

    @Test fun audioOwnershipSurvivesUiReplacementAndOnlyRealLibraryChangesStopIt(){
        val pending=GlobalContext.get().get<PendingShare>()
        var current:com.infinity.drive.presentation.platform.AudioPlayback?=null
        fun install(){activity.get().setContent{ProvidePlatformActions{
            current=com.infinity.drive.presentation.platform.LocalAudioPlayback.current
            DriveInfinityApp(pending)
        }}}
        compose.runOnUiThread{install()}
        compose.waitUntil(15_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        val player=current!!
        val file=runBlocking{GlobalContext.get().get<com.infinity.drive.domain.repository.FileRepository>().getFile("Song.mp3")}!!
        compose.runOnUiThread{player.updateLibraryContext(null,true);player.play(file)}
        compose.waitUntil(5_000){player.state.value.file?.id==file.id}
        compose.runOnUiThread{activity.get().setContent{androidx.compose.material3.Text("Replacing UI")}}
        compose.onNodeWithText("Replacing UI").assertIsDisplayed()
        compose.runOnUiThread{install()}
        compose.waitUntil(5_000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        org.junit.Assert.assertSame(player,current)
        org.junit.Assert.assertEquals(file.id,player.state.value.file?.id)
        runBlocking{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default){player.updateLibraryContext(null,true)}}
        compose.waitForIdle()
        org.junit.Assert.assertEquals(file.id,player.state.value.file?.id)
        runBlocking{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default){player.updateLibraryContext(99L,true)}}
        compose.waitUntil(5_000){compose.waitForIdle();player.state.value.file==null}
    }


    @Test fun appearancePersistsAccentAndAllDashboardStylesAcrossNavigation(){
        val settings=GlobalContext.get().get<SettingsRepository>()
        compose.waitUntil(15000){compose.onNodeWithText("Your library",substring=false).isDisplayed()}
        compose.onNodeWithTag("navigation-settings").performClick()
        compose.onNodeWithText("Appearance",substring=false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Tidal accent").performScrollTo()
        compose.onNodeWithContentDescription("Tidal accent").performClick()
        compose.waitUntil(5000){compose.waitForIdle();runBlocking{settings.preferences.first().accentColour==com.infinity.drive.domain.model.AccentColour.TIDAL}}
        compose.onNodeWithText("Dashboard Style",substring=false).performScrollTo().performClick()
        val carousel = compose.onNodeWithTag("dashboard-style-carousel")
        val classicTone = compose.onNodeWithTag("dashboard-tone-CLASSIC")
        val styles = com.infinity.drive.domain.model.DashboardStyle.entries
        carousel.assertIsDisplayed()
        classicTone.assertIsOff().performClick()
        classicTone.assertIsOn()
        org.junit.Assert.assertFalse(runBlocking { "CLASSIC" in settings.preferences.first().dashboardDarkStyles })
        carousel.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("dashboard-option-ORBIT").assertIsDisplayed()
        carousel.performTouchInput { swipeRight() }
        classicTone.assertIsOn()

        styles.forEachIndexed { index, style ->
            if (index > 0) compose.onNodeWithContentDescription("Next dashboard style").performClick()
            val tone = compose.onNodeWithTag("dashboard-tone-${style.name}")
            val initiallyDark = style.name in runBlocking { settings.preferences.first().dashboardDarkStyles }
            if (!initiallyDark && index > 0) tone.performClick()
            tone.assertIsOn()
            capture("dashboard-preview-${style.name.lowercase()}")
            compose.onNodeWithTag("apply-dashboard-${style.name}").performScrollTo().performClick()
            compose.waitUntil(5000) {
                compose.waitForIdle()
                runBlocking { settings.preferences.first().let { it.dashboardStyle == style && style.name in it.dashboardDarkStyles } }
            }
        }
        compose.onNodeWithContentDescription("Back", substring = false).performClick()
        compose.onNodeWithTag("navigation-home").performClick()
        val library = compose.onNodeWithTag("library-card")
        library.assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithTag("dashboard-style-sheet").assertDoesNotExist()
        library.performTouchInput { longClick() }
        compose.onNodeWithTag("dashboard-style-sheet").assertIsDisplayed()
        val savedStyle = styles.last()
        compose.onNodeWithTag("dashboard-option-${savedStyle.name}").assertIsDisplayed()
        compose.onNode(hasText("0 B") and hasAnyAncestor(hasTestTag("dashboard-style-sheet"))).assertIsDisplayed()
        compose.onNodeWithTag("dashboard-tone-${savedStyle.name}").assertIsOn().performClick()
        compose.onNodeWithTag("apply-dashboard-${savedStyle.name}").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.waitForIdle()
            runBlocking { settings.preferences.first().let { it.dashboardStyle == savedStyle && savedStyle.name !in it.dashboardDarkStyles } }
        }
        capture("home-dashboard-style-sheet")
    }

    @Test fun iconStyleDefaultsToOutlineAndCanBeChangedInAppearance() {
        val settings = GlobalContext.get().get<SettingsRepository>()
        org.junit.Assert.assertEquals(com.infinity.drive.domain.model.AppIconStyle.OUTLINE,
            runBlocking { settings.preferences.first().iconStyle })
        compose.waitUntil(15_000) { compose.onNodeWithText("Your library", substring = false).isDisplayed() }
        compose.onNodeWithTag("navigation-settings").performClick()
        compose.onNodeWithText("Appearance", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Icon style", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Broken", substring = false).performClick()
        compose.waitUntil(5000) {
            compose.waitForIdle()
            runBlocking { settings.preferences.first().iconStyle == com.infinity.drive.domain.model.AppIconStyle.BROKEN }
        }
        compose.onNodeWithText("Broken", substring = false).assertIsDisplayed()
    }

    @Test fun appearanceOmitsAddingThemesAndKeepsExistingThemesEditable() {
        val settings = GlobalContext.get().get<SettingsRepository>()
        val theme = requireNotNull(com.infinity.drive.domain.model.AccentColour.TIDAL.themeDefinition())
            .copy(id = "legacy-theme", name = "Blue Hour")
        runBlocking { settings.update { it.copy(customThemes = listOf(theme), selectedCustomThemeId = theme.id) } }
        compose.waitUntil(15_000) { compose.onNodeWithText("Your library", substring = false).isDisplayed() }
        compose.onNodeWithTag("navigation-settings").performClick()
        compose.onNodeWithText("Appearance", substring = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Add Custom").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit custom theme").performScrollTo().performClick()
        compose.onNodeWithTag("custom-theme-name").performScrollTo().assertTextContains("Blue Hour")
        compose.onNodeWithText("Primary", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("custom-theme-colour-hex").performTextReplacement("#164F69")
        compose.onNodeWithText("Apply color", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Save theme", substring = false).performClick()
        compose.waitUntil(5000) {
            compose.waitForIdle()
            runBlocking { settings.preferences.first().selectedCustomTheme?.light?.primary == 0xFF164F69L }
        }
        compose.onNodeWithContentDescription("Blue Hour accent").assertIsSelected()
        compose.onNodeWithContentDescription("Edit custom theme").performScrollTo().performClick()
        compose.onNodeWithTag("custom-theme-name").performScrollTo().assertTextContains("Blue Hour")
        compose.onNodeWithContentDescription("Back", substring = false).performClick()
        compose.onNodeWithContentDescription("Delete custom theme").performScrollTo().performClick()
        compose.onNodeWithText("Delete theme", substring = false).performClick()
        compose.onNodeWithContentDescription("Blue Hour accent").assertDoesNotExist()
        compose.onNodeWithContentDescription("Default accent").assertIsSelected()
    }

    @Test fun leavingHiddenForHomeKeepsItsSessionUntilTheAppIsBackgrounded(){
        val hidden=GlobalContext.get().get<com.infinity.drive.core.security.HiddenLockManager>()
        runBlocking{hidden.awaitReady();hidden.setDeviceLock(true,hidden.state.value.epoch);hidden.unlockWithDevice(hidden.state.value.epoch)}
        compose.waitUntil(15000){compose.onNodeWithText("Your library",substring=false).isDisplayed()}
        compose.onNodeWithText("Hidden",substring=false).performClick()
        compose.waitUntil(5000){compose.onNodeWithContentDescription("Hidden settings").isDisplayed()}
        compose.onNodeWithContentDescription("Back",substring=false).performClick()
        compose.waitUntil(5000){compose.onNodeWithText("Your library",substring=false).isDisplayed()}
        org.junit.Assert.assertTrue(hidden.state.value.unlocked)
        compose.runOnIdle{hidden.onAppStopped()}
        org.junit.Assert.assertTrue(hidden.state.value.restricted)
    }

    private fun capture(name:String) {
        compose.waitForIdle()
        val file=File("build/design-previews/refreshed-$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }

}
