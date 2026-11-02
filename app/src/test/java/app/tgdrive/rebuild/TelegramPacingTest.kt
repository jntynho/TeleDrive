package app.tgdrive.rebuild

import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.core.telegram.TelegramPacer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramPacingTest {
    @Test fun `burst starts without per-file sleeps and later calls respect shared budget`()=runTest {
        val pacer=TelegramPacer { testScheduler.currentTime }
        val starts=mutableListOf<Long>()
        repeat(6) { backgroundScope.launch { pacer.paced { starts+=testScheduler.currentTime } } }
        runCurrent();assertEquals(List(5){0L},starts)
        advanceTimeBy(3000);runCurrent();assertEquals(3000L,starts.last());assertEquals(6,starts.size)
    }
    @Test fun `flood wait from one operation blocks every other operation`()=runTest {
        val pacer=TelegramPacer { testScheduler.currentTime }
        runCatching { pacer.paced { throw TelegramException.from(429,"Too Many Requests: retry after 160") } }
        var sent=false
        backgroundScope.launch { pacer.paced { sent=true } }
        runCurrent();assertFalse(sent)
        advanceTimeBy(160_999);runCurrent();assertFalse(sent)
        advanceTimeBy(1);runCurrent();assertTrue(sent)
    }
}
