package app.tgdrive.rebuild
import com.infinity.drive.core.update.UpdateChecker
import org.junit.Test
import org.junit.Assert.*
class UpdateVersionTest {
    @Test fun buildTagsCannotOfferTheInstalledOrOlderBuild(){
        val checker=UpdateChecker("1.0.0",34)
        val method=UpdateChecker::class.java.getDeclaredMethod("isNewer",String::class.java,String::class.java).apply{isAccessible=true}
        fun newer(candidate:String)=method.invoke(checker,candidate,"1.0.0-build.34") as Boolean
        assertFalse(newer("1.0.0-build.34"));assertFalse(newer("1.0.0-build.33"))
        assertTrue(newer("1.0.0-build.35"));assertTrue(newer("1.0.1"));assertFalse(newer("1.0.0"))
    }
}
