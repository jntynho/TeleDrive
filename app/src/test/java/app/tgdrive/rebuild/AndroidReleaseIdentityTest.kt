package app.tgdrive.rebuild

import com.infinity.drive.core.update.UpdateApkIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidReleaseIdentityTest {
    private val installed = UpdateApkIdentity("app.tgdrive.next", "1.5.5", 91, setOf("release-certificate"))
    private val update = installed.copy(versionName = "1.5.6", versionCode = 92)

    @Test fun acceptsOnlyANewerVersionOfTheSameSignedPackage() {
        assertTrue(installed.accepts(update, "1.5.6"))
        assertFalse(installed.accepts(update.copy(packageName = "app.tgdrive.other"), "1.5.6"))
        assertFalse(installed.accepts(update.copy(signers = setOf("other-certificate")), "1.5.6"))
        assertFalse(installed.accepts(update.copy(signers = update.signers + "additional-certificate"), "1.5.6"))
        assertFalse(installed.copy(signers = emptySet()).accepts(update.copy(signers = emptySet()), "1.5.6"))
        assertFalse(installed.accepts(update.copy(versionCode = 91), "1.5.6"))
        assertFalse(installed.accepts(update.copy(versionName = "1.5.7"), "1.5.6"))
    }

    @Test fun buildTaggedReleaseMustMatchTheExactBuildNumber() {
        assertTrue(installed.accepts(update, "1.5.6-build.92"))
        assertFalse(installed.accepts(update, "1.5.6-build.93"))
        assertFalse(installed.accepts(update.copy(versionName = "1.5.6-build.92"), "1.5.6-build.92"))
    }
}
