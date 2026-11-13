package app.tgdrive

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.infinity.drive.presentation.settings.BackupSizeDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native popup focus and IME interaction are exercised on Android, rather than a JVM window shadow. */
@RunWith(AndroidJUnit4::class)
class BackupSizeDeviceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun customMaximumCanBeTypedAndSavedWithTheNativeEditor(){
        var open by mutableStateOf(true);var saved:Int?=null
        compose.setContent{MaterialTheme{if(open)BackupSizeDialog(false,0,0,{saved=it;open=false},{open=false})}}
        compose.onNodeWithText("Custom size…",substring=false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Size in MB").performTextReplacement("2048")
        compose.onNodeWithText("Save",substring=false).performClick()
        compose.waitForIdle();assertEquals(2048,saved)
        compose.onNodeWithContentDescription("Size in MB").assertDoesNotExist()
    }
    @Test fun customMinimumRejectsInvalidBoundsAndAcceptsLocalDecimalDigits(){
        var open by mutableStateOf(true);var saved:Int?=null
        compose.setContent{MaterialTheme{if(open)BackupSizeDialog(true,0,50,{saved=it;open=false},{open=false})}}
        compose.onNodeWithText("Custom size…",substring=false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Size in MB").performTextReplacement("51")
        compose.onNodeWithText("Save",substring=false).assertIsNotEnabled()
        compose.onNodeWithText("Minimum cannot exceed the 50 MB maximum.").assertIsDisplayed()
        listOf("-1","2147483648","").forEach{invalid->
            compose.onNodeWithContentDescription("Size in MB").performTextReplacement(invalid)
            compose.onNodeWithText("Save",substring=false).assertIsNotEnabled()
        }
        compose.onNodeWithContentDescription("Size in MB").performTextReplacement("٢٥")
        compose.onNodeWithText("Save",substring=false).performClick()
        compose.waitForIdle();assertEquals(25,saved)
    }
}
