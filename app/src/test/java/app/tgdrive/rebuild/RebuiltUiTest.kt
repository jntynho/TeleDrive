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
        compose.onNodeWithText("Search photos and videos").assertIsDisplayed()
        capture("gallery")
        compose.onNodeWithContentDescription("Sort gallery").performClick()
        compose.onNodeWithText("Size").performClick()
        compose.onNodeWithTag("navigation-audio").performClick()
        compose.onNodeWithText("Search audio").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Song",substring=false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Song",substring=false).assertIsDisplayed()
        capture("audio")
        compose.onNodeWithText("Report.pdf").assertDoesNotExist()
        compose.onNodeWithText("Photo.jpg").assertDoesNotExist()
        compose.onNodeWithTag("navigation-settings").performClick()
        compose.onNodeWithText("Search settings").assertIsDisplayed()
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
        compose.onNodeWithText("Sort",substring=false).performClick()
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
        compose.onNodeWithText("Upload",substring=false).assertIsDisplayed()
        listOf("gallery","audio","settings").forEach {
            compose.onNodeWithTag("navigation-$it").assertIsDisplayed().performClick()
            compose.onNode(hasSetTextAction()).assertIsDisplayed()
        }
        runBlocking { GlobalContext.get().get<SettingsRepository>().update { it.copy(onboardingComplete=false) } }
        compose.waitUntil(10_000){compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Get started").performScrollTo().performClick()
        compose.onNodeWithText("Continue",substring=false).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        capture("api-large-text")
    }

    private fun capture(name:String) {
        compose.waitForIdle()
        val file=File("build/design-previews/refreshed-$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }

}
