package app.tgdrive.rebuild

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.infinity.drive.domain.model.*
import com.infinity.drive.presentation.components.*
import com.infinity.drive.presentation.theme.DriveInfinityTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class FileRowsAppearanceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    @After fun close(){if(::activity.isInitialized)activity.pause().stop().destroy()}
    @Test fun rowsMatchRecentFilesAndUseThemeInkEvenWhenTheInheritedColourIsBlack(){
        val file=DriveFile("report",null,"Report.pdf",10,"application/pdf",FileCategory.DOCUMENT,null,null,null,null,null,null,BackupState.NONE,
            false,false,false,false,null,null,null,null,createdAt=1,modifiedAt=1,addedAt=1)
        val folder=DriveFolder("folder",null,"Documents",false,false,false,null,1,1)
        var theme by mutableIntStateOf(0)
        var selected by mutableStateOf(false)
        var compact by mutableStateOf(false)
        var expected=Color.Unspecified
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent{
            DriveInfinityTheme(darkTheme=theme!=1,amoled=theme==2){
                expected=if(selected)MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                CompositionLocalProvider(LocalContentColor provides Color.Black,LocalCompactLayout provides compact){
                    Column{
                        Box(Modifier.testTag("recent")){LibraryRecentRow(file)}
                        Box(Modifier.testTag("file")){FileListItem(file,selected,false,{},{})}
                        Box(Modifier.testTag("folder")){FolderRow(folder,{},selected=selected)}
                    }
                }
            }
        }
        for(mode in 0..2)for(dense in listOf(false,true))for(marked in listOf(false,true)){
            compose.runOnIdle{theme=mode;compact=dense;selected=marked}
            val reference=compose.onNodeWithTag("recent").fetchSemanticsNode()
            for((tag,name)in listOf("file" to "Report.pdf","folder" to "Documents")){
                val row=compose.onNodeWithTag(tag).fetchSemanticsNode()
                assertEquals("Row height must match Recent files",reference.size.height,row.size.height)
                val title=compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag(tag)),useUnmergedTree=true)
                val layouts=mutableListOf<TextLayoutResult>()
                title.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                assertEquals("Title must use the theme foreground",expected,layouts.single().layoutInput.style.color)
                val recentTitle=compose.onNode(hasText("Report.pdf") and hasAnyAncestor(hasTestTag("recent")),useUnmergedTree=true)
                assertEquals(recentTitle.fetchSemanticsNode().positionInRoot.x-reference.positionInRoot.x,
                    title.fetchSemanticsNode().positionInRoot.x-row.positionInRoot.x,.01f)
            }
        }
    }
}
