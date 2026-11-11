package app.tgdrive.rebuild

import com.infinity.drive.core.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HiddenLockTest {
    private class Store:HiddenLockStore {
        var record:HiddenLockRecord?=null
        var entered:CompletableDeferred<Unit>?=null
        var resume:CompletableDeferred<Unit>?=null
        override suspend fun read()=record
        override suspend fun write(record:HiddenLockRecord){entered?.complete(Unit);resume?.await();this.record=record}
        override suspend fun clear(){record=null}
    }
    private val key="0123456789ABCDEF0123456789ABCDEF"
    private suspend fun prepared(store:Store,scope:CoroutineScope,clock:()->Long={100_000L}):HiddenLockManager {
        val manager=HiddenLockManager(store,1000,clock,scope);manager.awaitReady()
        manager.configure("246810".toCharArray(),key);manager.lock();return manager
    }
    @Test fun pinIsDerivedNotStoredAndNewProcessStartsLocked()=runTest {
        val store=Store();val manager=prepared(store,backgroundScope)
        assertNotEquals("246810",store.record!!.pinHash);assertNotEquals(key,store.record!!.recoveryHash)
        val pin="246810".toCharArray()
        assertEquals(HiddenVerification.SUCCESS,manager.verifyPin(pin));assertTrue(manager.state.value.unlocked)
        assertTrue(pin.all{it=='\u0000'})
        val reopened=HiddenLockManager(store,1000,{100_000},backgroundScope);reopened.awaitReady()
        assertTrue(reopened.state.value.restricted);assertFalse(reopened.state.value.unlocked)
    }
    @Test fun retryDelaySurvivesRestartAndRejectsEvenCorrectPinUntilItExpires()=runTest {
        var now=100_000L;val store=Store();val manager=prepared(store,backgroundScope){now}
        repeat(5){assertEquals(HiddenVerification.WRONG,manager.verifyPin("000000".toCharArray()))}
        assertEquals(30L,manager.remainingDelaySeconds())
        val reopened=HiddenLockManager(store,1000,{now},backgroundScope);reopened.awaitReady()
        assertEquals(HiddenVerification.WAIT,reopened.verifyPin("246810".toCharArray()))
        now-=60_000
        assertEquals(30L,reopened.remainingDelaySeconds())
        now=130_001
        assertEquals(HiddenVerification.SUCCESS,reopened.verifyPin("246810".toCharArray()))
        assertEquals(0,store.record!!.failures)
    }
    @Test fun recoveryAuthorizesReplacementWithoutUnlockingOldContentAndRotatesTheKey()=runTest {
        val store=Store();val manager=prepared(store,backgroundScope)
        assertEquals(HiddenVerification.SUCCESS,manager.verifyRecovery(key.chunked(4).joinToString("-").lowercase()))
        assertFalse(manager.state.value.unlocked)
        val replacement="FEDCBA9876543210FEDCBA9876543210"
        manager.configure("135790".toCharArray(),replacement);manager.lock()
        assertEquals(HiddenVerification.WRONG,manager.verifyPin("246810".toCharArray()))
        assertEquals(HiddenVerification.WRONG,manager.verifyRecovery(key))
        assertEquals(HiddenVerification.SUCCESS,manager.verifyPin("135790".toCharArray()))
    }
    @Test fun leavingDuringVerificationCannotGrantAStaleUnlock()=runTest {
        val store=Store();val manager=prepared(store,backgroundScope)
        store.entered=CompletableDeferred();store.resume=CompletableDeferred()
        val verification=async{manager.verifyPin("246810".toCharArray())}
        store.entered!!.await();manager.lock();store.resume!!.complete(Unit)
        assertEquals(HiddenVerification.CANCELLED,verification.await())
        assertFalse(manager.state.value.unlocked)
    }
    @Test fun aRecoveredIdentityPermitIsRevokedWhenTheAppLeaves()=runTest {
        val store=Store();val manager=prepared(store,backgroundScope)
        assertEquals(HiddenVerification.SUCCESS,manager.verifyRecovery(key));manager.lock()
        try{manager.configure("135790".toCharArray(),key);fail("A stale recovery must not change the PIN")}
        catch(_:IllegalStateException){}
        assertEquals(HiddenVerification.SUCCESS,manager.verifyPin("246810".toCharArray()))
    }
    @Test fun corruptOrUnusableStoreFailsClosedInsteadOfOfferingANewUnprotectedSetup()=runTest {
        val store=object:HiddenLockStore {
            override suspend fun read():HiddenLockRecord?=error("Authentication tag failed")
            override suspend fun write(record:HiddenLockRecord)=error("Must not overwrite")
            override suspend fun clear(){}
        }
        val manager=HiddenLockManager(store,1000,{100_000},backgroundScope);manager.awaitReady()
        assertTrue(manager.state.value.error);assertTrue(manager.state.value.configured);assertTrue(manager.state.value.restricted)
        try{manager.configure("135790".toCharArray(),key);fail("Must fail closed")}
        catch(_:IllegalStateException){}
    }
    @Test fun biometricRequiresTheExactChallengeAndCurrentEpoch()=runTest {
        val store=Store();val manager=prepared(store,backgroundScope)
        manager.verifyPin("246810".toCharArray())
        val challenge=ByteArray(32){it.toByte()}
        manager.enrollBiometric(ByteArray(12),ByteArray(48),challenge.copyOf());manager.lock()
        val ticket=manager.state.value.epoch
        assertFalse(manager.verifyBiometric(ByteArray(32){99},ticket))
        assertFalse(manager.state.value.unlocked)
        manager.lock()
        assertFalse(manager.verifyBiometric(challenge.copyOf(),ticket))
        assertTrue(manager.verifyBiometric(challenge.copyOf(),manager.state.value.epoch,true))
        assertFalse(manager.state.value.unlocked)
        manager.configure("135790".toCharArray(),key)
        assertFalse(manager.state.value.biometrics)
    }
    @Test fun recoveryKeyHas128RandomBitsAndIsNotARepeatedConstant()=runTest {
        val manager=HiddenLockManager(Store(),1000,{100_000},backgroundScope);manager.awaitReady()
        val keys=(1..100).map{manager.newRecoveryKey()}
        assertTrue(keys.all{it.matches(Regex("[0-9A-F]{32}"))});assertEquals(100,keys.toSet().size)
    }
}
