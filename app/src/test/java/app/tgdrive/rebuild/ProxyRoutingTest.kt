package app.tgdrive.rebuild

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.crypto.CredentialCipher
import com.infinity.drive.core.dispatchers.DefaultDispatcherProvider
import com.infinity.drive.core.proxy.ProxyProbe
import com.infinity.drive.core.telegram.TelegramClient
import com.infinity.drive.core.telegram.TelegramException
import com.infinity.drive.core.telegram.TelegramProxy
import com.infinity.drive.core.telegram.TelegramProxyType
import com.infinity.drive.core.telegram.hasValidFields
import com.infinity.drive.data.local.database.DriveInfinityDatabase
import com.infinity.drive.data.repository.ProxyRepositoryImpl
import com.infinity.drive.data.repository.SettingsRepositoryImpl
import com.infinity.drive.domain.model.ProxyServer
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ProxyRoutingTest {
    @Test fun rejectedSelectionAndEditKeepTheActiveRouteAndFailoverSkipsUnusableServers() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DriveInfinityDatabase::class.java).build()
        try {
            var route: TelegramProxy? = null
            var reconnects = 0
            val unusedClient = Proxy.newProxyInstance(
                TelegramClient::class.java.classLoader, arrayOf(TelegramClient::class.java)
            ) { _, method, _ -> error("Unexpected Telegram call: " + method.name) } as TelegramClient
            val client = object : TelegramClient by unusedClient {
                override suspend fun applyProxy(proxy: TelegramProxy?) {
                    if (proxy?.host == "reject.example") throw TelegramException(400, "Invalid proxy")
                    route = proxy
                }

                override suspend fun reconnect() {
                    reconnects++
                }
            }
            val store = object : DataStore<Preferences> {
                override val data = MutableStateFlow<Preferences>(emptyPreferences())
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                    transform(data.value).also { data.value = it }
            }
            val cipher = object : CredentialCipher {
                override fun encrypt(plaintext: ByteArray) = plaintext.copyOf()
                override fun decrypt(ciphertext: ByteArray) = ciphertext.copyOf()
            }
            val settings = SettingsRepositoryImpl(store, cipher)
            val repository = ProxyRepositoryImpl(db.proxyDao(), settings, client, ProxyProbe(DefaultDispatcherProvider()))
            val original = ProxyServer("first", "First", TelegramProxyType.SOCKS5, "first.example", 1080)
            val rejected = original.copy(id = "bad", host = "reject.example")
            val next = original.copy(id = "next", host = "next.example")
            assertTrue(repository.save(original) is AppResult.Success)
            assertTrue(repository.save(rejected) is AppResult.Success)
            assertTrue(repository.save(next) is AppResult.Success)
            assertEquals(0, reconnects)

            assertTrue(repository.setEnabled(true) is AppResult.Success)
            assertEquals("first.example", route?.host)
            assertTrue(repository.select(rejected.id) is AppResult.Failure)
            assertTrue(repository.save(original.copy(host = rejected.host)) is AppResult.Failure)
            assertEquals(original.id, settings.preferences.first().activeProxyId)
            assertEquals(original.host, db.proxyDao().byId(original.id)?.host)
            assertEquals(original.host, route?.host)

            assertEquals(AppResult.Success(next.id), repository.rotate(original.id, setOf(original.id)))
            assertEquals(next.host, route?.host)
            assertEquals(AppResult.Success(null), repository.rotate(original.id, emptySet()))
            assertTrue(repository.setEnabled(false) is AppResult.Success)
            assertNull(route)
            assertFalse(settings.preferences.first().proxyEnabled)
            assertEquals(3, reconnects)
        } finally {
            db.close()
        }
    }

    @Test fun proxyFieldsRejectAnAddressWithCredentialsAndKeepValidIpv6() {
        assertFalse(TelegramProxy(TelegramProxyType.HTTP, "name@example.org", 80).hasValidFields())
        assertFalse(TelegramProxy(TelegramProxyType.SOCKS5, "example.org", 0).hasValidFields())
        assertTrue(TelegramProxy(TelegramProxyType.SOCKS5, "2001:db8::2", 1080).hasValidFields())
    }
}
