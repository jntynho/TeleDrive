package app.tgdrive.rebuild

import com.infinity.drive.core.telegram.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramPacingTest {
    @Test fun `hundreds of upload submissions have no artificial per-file delay`()=runTest {
        val pacer=TelegramPacer{testScheduler.currentTime}
        val starts=mutableListOf<Long>()
        repeat(500){pacer.upload{starts+=testScheduler.currentTime}}
        assertEquals(500,starts.size);assertTrue(starts.all{it==0L})
    }
    @Test fun `copy and mutation pacing does not consume an upload slot`()=runTest {
        val pacer=TelegramPacer{testScheduler.currentTime}
        val starts=mutableListOf<Long>()
        val job=launch{repeat(3){pacer.paced{starts+=testScheduler.currentTime}}}
        runCurrent()
        pacer.upload{assertEquals(0L,testScheduler.currentTime)}
        advanceUntilIdle();job.join()
        assertEquals(listOf(0L,400L,800L),starts)
    }
    @Test fun `flood wait fails upload fast and is restored after process recreation`()=runTest {
        var deadline=0L
        val pacer=TelegramPacer(persistDeadline={deadline=it},now={testScheduler.currentTime})
        runCatching{pacer.upload{throw TelegramException.from(429,"FLOOD_WAIT_160")}}
        assertEquals(161_000L,deadline)
        val restored=TelegramPacer(initialDeadline=deadline,now={testScheduler.currentTime})
        var sent=false
        val rejected=runCatching{restored.upload{sent=true}}.exceptionOrNull() as TelegramException
        assertFalse(sent);assertEquals(161,rejected.retryAfterSeconds)
        advanceTimeBy(161_000);restored.upload{sent=true};assertTrue(sent)
    }
    @Test fun `a server wait blocks metadata mutations too`()=runTest {
        val pacer=TelegramPacer{testScheduler.currentTime}
        pacer.recordFloodWait(160)
        var sent=false
        val job=launch{pacer.paced{sent=true}}
        advanceTimeBy(160_999);runCurrent();assertFalse(sent)
        advanceTimeBy(1);runCurrent();assertTrue(sent);job.join()
    }
}
