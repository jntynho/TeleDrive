package app.tgdrive.rebuild

import android.app.Activity
import android.app.Application
import com.infinity.drive.core.security.CollectionLockLifecycle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class CollectionLockLifecycleTest {
    @Test fun internalActivitiesDoNotCauseAnAppExitAndReturningStartsOneSession(){
        var entered=0;var left=0
        val observer=CollectionLockLifecycle({entered++},{left++})
        val main=Activity();val capture=Activity()
        observer.onActivityStarted(main);observer.onActivityStarted(capture);observer.onActivityStopped(main)
        assertEquals(1,entered);assertEquals(0,left)
        observer.onActivityStopped(capture);assertEquals(1,left)
        observer.onActivityStarted(main);assertEquals(2,entered)
        observer.onActivityPaused(main);assertEquals(1,left)
    }
    @Test fun rotationAndDuplicateCallbacksDoNotResetTheUnlockWindow(){
        var entered=0;var left=0
        val observer=CollectionLockLifecycle({entered++},{left++})
        val old=Activity();val replacement=Activity()
        observer.onActivityStarted(old)
        ReflectionHelpers.setField(old,"mChangingConfigurations",true)
        observer.onActivityStopped(old);observer.onActivityDestroyed(old)
        observer.onActivityStarted(replacement);observer.onActivityStarted(replacement)
        assertEquals(1,entered);assertEquals(0,left)
        observer.onActivityStopped(replacement);observer.onActivityStopped(replacement)
        assertEquals(1,left)
    }
}
