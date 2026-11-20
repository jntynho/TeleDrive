package app.tgdrive.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class IconsaxIconsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingIconStyleUpdatesTheRenderedGeometry() {
        val style = mutableStateOf(IconStyle.Outline)
        var rendered = emptyList<ImageVector>()
        compose.setContent {
            CompositionLocalProvider(LocalIconStyle provides style.value) {
                val vectors = listOf(DriveIcons.Home, DriveIcons.LightMode, DriveIcons.DarkMode)
                Column { vectors.forEach { Icon(it, contentDescription = null) } }
                SideEffect { rendered = vectors }
            }
        }
        val outline = compose.runOnIdle { rendered.also { checkVectors(it, "outline") } }
        compose.runOnIdle { style.value = IconStyle.Broken }
        compose.runOnIdle {
            checkVectors(rendered, "broken")
            outline.zip(rendered).forEach { (before, after) ->
                assertNotEquals(before.name, before.root, after.root)
            }
        }
    }

    @Test fun overflowKeepsThreeVerticalDotsInBothStyles() {
        val style = mutableStateOf(IconStyle.Outline)
        var rendered: ImageVector? = null
        compose.setContent {
            CompositionLocalProvider(LocalIconStyle provides style.value) {
                val overflow = DriveIcons.MoreVert
                Icon(overflow, contentDescription = "More actions")
                SideEffect { rendered = overflow }
            }
        }

        IconStyle.entries.forEach { choice ->
            compose.runOnIdle { style.value = choice }
            compose.runOnIdle {
                val icon = requireNotNull(rendered)
                assertEquals("iconsax:${choice.name.lowercase()}/more", icon.name)
                assertEquals(1, icon.root.size)
                val dots = icon.root[0] as VectorGroup
                assertEquals(3, dots.size)
                assertTrue(dots.all { it is VectorPath })
                assertEquals(90f, dots.rotation, 0f)
                assertEquals(12f, dots.pivotX, 0f)
                assertEquals(12f, dots.pivotY, 0f)
            }
        }
    }

    private fun checkVectors(vectors: List<ImageVector>, style: String) {
        assertEquals(listOf("home-2", "sun", "moon").map { "iconsax:$style/$it" }, vectors.map { it.name })
        vectors.forEach { icon ->
            assertEquals(24.dp, icon.defaultWidth)
            assertEquals(24.dp, icon.defaultHeight)
            assertEquals(24f, icon.viewportWidth)
            assertEquals(24f, icon.viewportHeight)
            assertTrue(icon.root.size > 0)
        }
    }
}
