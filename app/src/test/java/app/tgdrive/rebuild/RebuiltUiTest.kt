package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
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
        compose.onNodeWithText("Gallery").performClick()
        compose.onNodeWithText("Search photos and videos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sort gallery").performClick()
        compose.onNodeWithText("Size").performClick()
        compose.onNodeWithText("Audio").performClick()
        compose.onNodeWithText("Search audio").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Song.mp3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Song.mp3").assertIsDisplayed()
        compose.onNodeWithText("Report.pdf").assertDoesNotExist()
        compose.onNodeWithText("Photo.jpg").assertDoesNotExist()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Search settings").assertIsDisplayed()
        compose.onNodeWithText("Account").performClick()
        compose.onNodeWithText("Previous library").assertIsDisplayed()
        runBlocking { GlobalContext.get().get<SettingsRepository>().update { it.copy(onboardingComplete=false) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("API access").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
    }
}
