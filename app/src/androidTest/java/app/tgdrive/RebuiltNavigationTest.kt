package app.tgdrive

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.infinity.drive.core.files.PendingShare
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
import org.koin.core.context.GlobalContext

/** Empty local fixtures exercise real repositories/navigation; no Telegram account. */
@RunWith(AndroidJUnit4::class)
class RebuiltNavigationTest {
    @get:Rule val compose=createComposeRule()
    private val settings get()=GlobalContext.get().get<SettingsRepository>()
    private var previous=UserPreferences()
    @Before fun prepare()=runBlocking {
        previous=settings.preferences.first()
        settings.update { UserPreferences(onboardingComplete=true,updateCheckEnabled=false) }
    }
    @After fun restore()=runBlocking { settings.update { previous };Unit }
    @Test fun fourTabsSearchSortingAndPreviousLibraryAreReachable() {
        val pending=GlobalContext.get().get<PendingShare>()
        compose.setContent { ProvidePlatformActions { DriveInfinityApp(pending) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your library").assertIsDisplayed()
        compose.onNodeWithText("Gallery").performClick()
        compose.onNodeWithText("Search photos and videos").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sort gallery").performClick()
        compose.onNodeWithText("Size").performClick()
        compose.onNodeWithText("Audio").performClick()
        compose.onNodeWithText("Search audio").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Search settings").assertIsDisplayed()
        compose.onNodeWithText("Account",substring=false).performClick()
        compose.onNodeWithText("Previous library").performClick()
        compose.onNodeWithText("Previous library").assertIsDisplayed()
    }
}
