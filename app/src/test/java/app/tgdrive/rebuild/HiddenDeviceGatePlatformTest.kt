package app.tgdrive.rebuild

import android.app.Application
import android.app.KeyguardManager
import androidx.fragment.app.FragmentActivity
import com.infinity.drive.presentation.applock.requireDeviceOwner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
class HiddenDeviceGatePlatformTest {
    @Test fun hiddenNeverConfirmsWithoutAnAuthenticationHost(){
        var success=false;var denied=false
        requireDeviceOwner(null,"Hidden","",{denied=true},allowUnsecured=false){success=true}
        assertTrue(denied);assertFalse(success)
    }
    @Test fun hiddenNeverConfirmsWhenThePhoneHasNoSecureLock(){
        val host=Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            shadowOf(host.get().getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(false)
            var success=false;var denied=false
            requireDeviceOwner(host.get(),"Hidden","",{denied=true},allowUnsecured=false){success=true}
            assertTrue(denied);assertFalse(success)
        }finally{host.pause().stop().destroy()}
    }
}
