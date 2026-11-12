package app.tgdrive.rebuild

import com.infinity.drive.core.security.AppLockManager
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class HiddenAppLockCoordinationTest {
    private fun manager():AppLockManager {
        val prefs=MutableStateFlow(UserPreferences(appLockEnabled=true,autoLockTimeoutMinutes=0))
        val settings=Proxy.newProxyInstance(SettingsRepository::class.java.classLoader,arrayOf(SettingsRepository::class.java)){_,method,_->
            if(method.name=="getPreferences")prefs else error("Unexpected settings call")
        } as SettingsRepository
        return AppLockManager(settings)
    }
    @Test fun successfulNativeAuthenticationDoesNotLaunchASecondAppLockOnCredentialReturn()=runTest{
        val lock=manager();lock.onAppStarted();lock.unlock()
        val native=lock.beginDeviceAuthentication()
        lock.onAppStopped();lock.onAppStarted()
        assertFalse(lock.locked.value)
        assertTrue(lock.finishDeviceAuthentication(native,true));assertFalse(lock.locked.value)
        lock.onAppStopped();lock.onAppStarted();assertTrue(lock.locked.value)
    }
    @Test fun cancellingTheNativePromptStillEnforcesTheDeferredAppLock()=runTest{
        val lock=manager();lock.onAppStarted();lock.unlock()
        val native=lock.beginDeviceAuthentication()
        lock.onAppStopped();lock.onAppStarted()
        assertTrue(lock.finishDeviceAuthentication(native,false));assertTrue(lock.locked.value)
        assertFalse(lock.finishDeviceAuthentication(native,true));assertTrue(lock.locked.value)
    }
    @Test fun explicitlyLockingTheAppRejectsAnOlderNativeResult()=runTest{
        val lock=manager();lock.onAppStarted();lock.unlock()
        val native=lock.beginDeviceAuthentication();lock.lockNow()
        assertFalse(lock.finishDeviceAuthentication(native,true));assertTrue(lock.locked.value)
    }

}
