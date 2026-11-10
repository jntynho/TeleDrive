package app.tgdrive.rebuild

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.core.net.toUri
import com.infinity.drive.presentation.platform.AndroidTelegramLinkOpener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class TelegramAndroidActionsTest {
    @Test fun privatePostOpensTheNativeMessageUri(){
        val context=RuntimeEnvironment.getApplication()
        assertTrue(AndroidTelegramLinkOpener(context).open("https://t.me/c/12345/321"))
        assertEquals("tg://privatepost?channel=12345&post=321",shadowOf(context).nextStartedActivity.dataString)
    }
    @Test fun nativeOriginalSharingReportsMissingTelegramInsteadOfPretendingToSend(){
        assertFalse(AndroidTelegramLinkOpener(RuntimeEnvironment.getApplication()).shareFile("content://test/original","image/jpeg","Telegram"))
    }
    @Test fun originalSharingTargetsTelegramAndGrantsTheContentUri(){
        val context=RuntimeEnvironment.getApplication();val pm=shadowOf(context.packageManager)
        val pkg="org.telegram.messenger"
        val resolved=ResolveInfo().apply{activityInfo=ActivityInfo().apply{
            packageName=pkg;name="org.telegram.ui.LaunchActivity";exported=true
            applicationInfo=ApplicationInfo().apply{packageName=pkg}
        }}
        pm.addResolveInfoForIntent(Intent(Intent.ACTION_VIEW,"tg://resolve?domain=telegram".toUri()),resolved)
        pm.addResolveInfoForIntent(Intent(Intent.ACTION_SEND).setType("image/jpeg").setPackage(pkg),resolved)
        assertTrue(AndroidTelegramLinkOpener(context).shareFile("content://test/original","image/jpeg","Telegram"))
        val started=shadowOf(context).nextStartedActivity
        assertEquals(pkg,started.`package`)
        assertEquals(Intent.ACTION_SEND,started.action)
        assertEquals("content://test/original",started.clipData!!.getItemAt(0).uri.toString())
        assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
