package app.tgdrive.rebuild

import com.infinity.drive.core.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HiddenDeviceLockTest {
    private class Store:HiddenLockStore {
        var value:HiddenLockRecord?=null
        var clearing:CompletableDeferred<Unit>?=null
        var proceed:CompletableDeferred<Unit>?=null
        override suspend fun read()=value
        override suspend fun write(record:HiddenLockRecord){value=record}
        override suspend fun clear(){clearing?.complete(Unit);proceed?.await();value=null}
    }
    @Test fun deviceLockSurvivesRestartWithoutStoringPhoneCredentials()=runTest{
        val store=Store();val lock=HiddenLockManager(store,1000,ownerScope=backgroundScope);lock.awaitReady()
        assertTrue(lock.setDeviceLock(true,lock.state.value.epoch))
        assertTrue(lock.state.value.restricted);assertTrue(lock.state.value.deviceLock)
        assertEquals(HiddenVerification.WRONG,lock.verifyPin("246810".toCharArray()))
        val restored=HiddenLockManager(store,1000,ownerScope=backgroundScope);restored.awaitReady()
        assertTrue(restored.state.value.restricted)
        assertTrue(restored.unlockWithDevice(restored.state.value.epoch));assertTrue(restored.state.value.unlocked)
        assertTrue(store.value!!.pinHash.isEmpty());assertTrue(store.value!!.recoveryHash.isEmpty())
    }
    @Test fun aLegacyPinStaysProtectedUntilDeviceAuthenticationMigratesIt()=runTest{
        val store=Store();val lock=HiddenLockManager(store,1000,ownerScope=backgroundScope);lock.awaitReady()
        lock.configure("246810".toCharArray(),"0123456789ABCDEF0123456789ABCDEF");lock.lock()
        assertEquals(1,store.value!!.version);assertTrue(lock.state.value.restricted)
        assertTrue(lock.unlockWithDevice(lock.state.value.epoch));assertEquals(2,store.value!!.version)
        lock.lock();assertEquals(HiddenVerification.WRONG,lock.verifyPin("246810".toCharArray()))
    }
    @Test fun staleResultsCannotUnlockOrDisableTheLock()=runTest{
        val store=Store();val lock=HiddenLockManager(store,1000,ownerScope=backgroundScope);lock.awaitReady()
        lock.setDeviceLock(true,lock.state.value.epoch)
        val stale=lock.state.value.epoch;lock.lock()
        assertFalse(lock.unlockWithDevice(stale));assertFalse(lock.setDeviceLock(false,stale));assertTrue(lock.state.value.restricted)
        assertTrue(lock.setDeviceLock(false,lock.state.value.epoch));assertFalse(lock.state.value.configured)
    }
    @Test fun leavingDuringADisableWriteRestoresTheProtectedRecord()=runTest{
        val store=Store();val lock=HiddenLockManager(store,1000,ownerScope=backgroundScope);lock.awaitReady()
        lock.setDeviceLock(true,lock.state.value.epoch)
        store.clearing=CompletableDeferred();store.proceed=CompletableDeferred()
        val change=async{lock.setDeviceLock(false,lock.state.value.epoch)}
        store.clearing!!.await();lock.lock();store.proceed!!.complete(Unit)
        assertFalse(change.await());assertTrue(lock.state.value.restricted);assertNotNull(store.value)
    }
    @Test fun corruptRecordsCannotBeDisabledByAnAuthenticationCallback()=runTest{
        val store=object:HiddenLockStore{
            override suspend fun read():HiddenLockRecord?=error("Unreadable")
            override suspend fun write(record:HiddenLockRecord)=error("Must not write")
            override suspend fun clear()=error("Must not clear")
        }
        val lock=HiddenLockManager(store,1000,ownerScope=backgroundScope);lock.awaitReady()
        assertFalse(lock.unlockWithDevice(lock.state.value.epoch));assertFalse(lock.setDeviceLock(false,lock.state.value.epoch))
        assertTrue(lock.state.value.restricted)
    }
}
