package app.tgdrive.rebuild

import com.infinity.drive.core.security.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CollectionLocksTest {
    @Test fun locksAreIndependentOptInAndResetWithLocalData()=runBlocking {
        val locks=CollectionLocks{object:HiddenLockStore{
            var record:HiddenLockRecord?=null
            override suspend fun read()=record
            override suspend fun write(record:HiddenLockRecord){this.record=record}
            override suspend fun clear(){record=null}
        }}
        val names=listOf("FAVORITES","ARCHIVED","TRASH")
        names.forEach{locks.manager(it).awaitReady();assertFalse(locks.manager(it).state.value.configured)}
        val favorites=locks.manager("FAVORITES")
        assertTrue(favorites.setDeviceLock(true,favorites.state.value.epoch))
        assertTrue(favorites.unlockWithDevice(favorites.state.value.epoch))
        assertTrue(favorites.state.value.unlocked)
        assertFalse(locks.manager("ARCHIVED").state.value.configured)
        locks.leaveExcept("ARCHIVED")
        assertTrue(favorites.state.value.restricted)
        locks.clearAll()
        names.forEach{assertFalse(locks.manager(it).state.value.configured)}
    }
}
