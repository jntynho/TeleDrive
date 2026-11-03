package app.tgdrive.rebuild

import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.telegram.*
import com.infinity.drive.data.repository.TelegramAuthRepositoryImpl
import com.infinity.drive.domain.repository.ProxyRepository
import com.infinity.drive.domain.repository.SettingsRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AppCredentialsTest {
    private inline fun <reified T> fake(crossinline call:(String,Array<out Any?>?)->Any?):T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)){_,method,args->call(method.name,args)} as T
    private fun repository(stored:TelegramCredentials?,wrapped:Boolean=stored!=null,bundled:TelegramCredentials?=null,
        started:(TelegramCredentials)->Unit={}):TelegramAuthRepositoryImpl {
        val settings=fake<SettingsRepository>{method,_->when(method){
            "getTelegramCredentials"->stored;"hasStoredTelegramCredentials"->wrapped
            else->error("Unexpected settings call: $method")
        }}
        val client=fake<TelegramClient>{method,args->when(method){
            "getAuthState"->MutableStateFlow<TelegramAuthState>(TelegramAuthState.Uninitialized)
            "getConnectionState"->MutableStateFlow(TelegramConnectionState.CONNECTING)
            "start"->{started(args!![0] as TelegramCredentials);Unit}
            else->error("Unexpected client call: $method")
        }}
        val proxy=fake<ProxyRepository>{method,_->if(method=="applyActive")Unit else error(method)}
        return TelegramAuthRepositoryImpl(client,settings,proxy,TelegramAppCredentials{bundled})
    }
    @Test fun firstInstallCanStartFromTheApplicationIdentity()=runTest {
        val app=TelegramCredentials(123,"a".repeat(32));var used:TelegramCredentials?=null
        val repo=repository(null,bundled=app){used=it}
        assertTrue(repo.hasCredentials().first())
        assertEquals(AppResult.Success(true),repo.startFromStoredCredentials())
        assertEquals(app,used)
    }
    @Test fun existingSessionAlwaysKeepsItsStoredIdentity()=runTest {
        val stored=TelegramCredentials(456,"b".repeat(32));var used:TelegramCredentials?=null
        repository(stored,bundled=TelegramCredentials(123,"a".repeat(32))){used=it}.startFromStoredCredentials()
        assertEquals(stored,used)
    }
    @Test fun unavailableWrappedCredentialsAreNotReplacedByBundledValues()=runTest {
        val result=repository(null,wrapped=true,bundled=TelegramCredentials(123,"a".repeat(32))){error("Must preserve the session")}.startFromStoredCredentials()
        assertEquals(AppResult.Failure(AppError.InvalidApiCredentials),result)
    }
    @Test fun buildsWithoutConfiguredCredentialsRetainManualSetup()=runTest {
        val repo=repository(null)
        assertFalse(repo.hasCredentials().first())
        assertEquals(AppResult.Success(false),repo.startFromStoredCredentials())
    }
}
