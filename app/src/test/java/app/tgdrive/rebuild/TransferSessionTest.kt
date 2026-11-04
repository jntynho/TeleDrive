package app.tgdrive.rebuild

import com.infinity.drive.core.common.*
import com.infinity.drive.core.telegram.TelegramAuthState
import com.infinity.drive.core.transfer.awaitTransferSession
import com.infinity.drive.domain.repository.TelegramAuthRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class TransferSessionTest {
    private fun auth(state:StateFlow<TelegramAuthState>,start:()->AppResult<Boolean>):TelegramAuthRepository =
        Proxy.newProxyInstance(TelegramAuthRepository::class.java.classLoader,arrayOf(TelegramAuthRepository::class.java)){_,method,_->when(method.name){
            "getAuthState"->state
            "startFromStoredCredentials"->start()
            else->error(method.name)
        }} as TelegramAuthRepository
    @Test fun `a cold worker starts TDLib and waits for the restored account`()=runTest {
        val state=MutableStateFlow<TelegramAuthState>(TelegramAuthState.Initializing)
        var starts=0
        val session=auth(state){starts++;AppResult.Success(true)}
        val job=async{awaitTransferSession(session)}
        runCurrent();assertEquals(1,starts);assertFalse(job.isCompleted)
        state.value=TelegramAuthState.Ready;runCurrent();assertTrue(job.await())
    }
    @Test fun `missing credentials leave queued work for a later session instead of sending`()=runTest {
        val session=auth(MutableStateFlow(TelegramAuthState.Initializing)){AppResult.Success(false)}
        assertFalse(awaitTransferSession(session))
    }
    @Test fun `an initializing session is bounded and cannot hang a worker forever`()=runTest {
        val session=auth(MutableStateFlow(TelegramAuthState.Initializing)){AppResult.Success(true)}
        assertFalse(awaitTransferSession(session,5000));assertEquals(5000L,testScheduler.currentTime)
    }
}
