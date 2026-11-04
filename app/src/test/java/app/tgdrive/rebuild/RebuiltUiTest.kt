package app.tgdrive.rebuild

import android.app.Application
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
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.BackupState
import org.koin.core.context.GlobalContext
import com.infinity.drive.core.files.PendingShare
import com.infinity.drive.di.appModules
import com.infinity.drive.domain.model.UserPreferences
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.presentation.DriveInfinityApp
import com.infinity.drive.presentation.platform.ProvidePlatformActions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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

/** Real modules, database, view models and navigation; only device key wrapping is faked. */
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
        capture("audio")
        compose.onNodeWithText("Report.pdf").assertDoesNotExist()
        compose.onNodeWithText("Photo.jpg").assertDoesNotExist()
        compose.onNodeWithTag("navigation-settings").performClick()
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.onNodeWithText("Search settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close search").performClick()
        capture("settings")
        compose.onNodeWithText("Telegram account").performClick()
        compose.onNodeWithText("Previous library").assertIsDisplayed()
        runBlocking { GlobalContext.get().get<SettingsRepository>().update { it.copy(onboardingComplete=false) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("API access").assertIsDisplayed()
        capture("api-setup")
        compose.onNodeWithText("Continue").assertIsNotEnabled()
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
        compose.onNodeWithText("No favorites yet").assertIsDisplayed()
        compose.onNodeWithText("On device",substring=false).performClick()
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
        compose.onNodeWithText("Continue",substring=false).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
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
        listOf("All files", "Activity", "Favorites", "Archived", "Hidden", "Trash").forEach {
            compose.onNodeWithText(it, substring=false).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Show collections").assertDoesNotExist()
        capture("library-shortcuts")
        compose.onNodeWithText("Hidden", substring=false).performClick()
        compose.onNodeWithText("Hidden", substring=false).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Your library").assertIsDisplayed()
    }

    @Test fun homeUploadOpensOnlyTheRequestedPickerWithoutChangingDestination() {
        var filePicks=0
        var folderPicks=0
        val pending=GlobalContext.get().get<PendingShare>()
        compose.runOnUiThread {
            activity.get().setContent { ProvidePlatformActions {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.infinity.drive.presentation.platform.LocalMultiFilePicker provides com.infinity.drive.presentation.platform.MultiFilePicker { callback->filePicks++;callback(emptyList()) },
                    com.infinity.drive.presentation.platform.LocalFolderPicker provides com.infinity.drive.presentation.platform.FolderPicker { callback->folderPicks++;callback(com.infinity.drive.presentation.platform.PickResult.Canceled) }
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
        compose.onNodeWithContentDescription("Back to library").assertIsDisplayed()
        capture("now-playing")
        compose.onNodeWithContentDescription("Show lyrics").performClick()
        compose.onNodeWithContentDescription("Back to player").assertIsDisplayed()
        compose.onAllNodesWithText("Third lyric").onLast().performScrollTo().performClick()
        org.junit.Assert.assertEquals(90_000L,playback.value.positionMs)
        capture("synchronized-lyrics")
        compose.onNodeWithContentDescription("Back to player").performClick()
        compose.onNodeWithContentDescription("Previous audio").performClick()
        compose.onNodeWithContentDescription("Next audio").performClick()
        org.junit.Assert.assertEquals(1,previous)
        org.junit.Assert.assertEquals(1,next)
        compose.onAllNodesWithContentDescription("Pause audio").onLast().performClick()
        org.junit.Assert.assertFalse(playback.value.playing)
        compose.onNodeWithContentDescription("Playback options").performClick()
        compose.onNodeWithText("Stop playback",substring=false).performClick()
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
            runBlocking{settings.update{it.copy(theme=theme,dynamicColor=false)}}
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
        compose.onNodeWithText("Activity",substring=false).performClick()
        compose.onNodeWithText("Activity",substring=false).assertIsDisplayed()
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
        compose.onNodeWithText("Discard changes?",substring=false).assertIsDisplayed()
        compose.onNodeWithText("Cancel",substring=false).performClick()
        compose.onNodeWithText("Changed draft",substring=false).assertIsDisplayed()
        compose.runOnUiThread{activity.get().onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithText("Discard",substring=false).performClick()
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

    private fun capture(name:String) {
        compose.waitForIdle()
        val file=File("build/design-previews/refreshed-$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }

}
