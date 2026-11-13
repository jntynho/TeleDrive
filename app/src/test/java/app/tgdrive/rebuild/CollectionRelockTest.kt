package app.tgdrive.rebuild

import com.infinity.drive.core.security.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CollectionRelockTest {
    private class Store:HiddenLockStore{
        var value:HiddenLockRecord?=null
        override suspend fun read()=value
        override suspend fun write(record:HiddenLockRecord){value=record}
        override suspend fun clear(){value=null}
    }
    private suspend fun unlocked(store:Store,scope:TestScope):HiddenLockManager {
        val manager=HiddenLockManager(store,ownerScope=scope.backgroundScope,elapsedClock={scope.testScheduler.currentTime})
        manager.awaitReady()
        if(!manager.state.value.configured)assertTrue(manager.setDeviceLock(true,manager.state.value.epoch))
        assertTrue(manager.unlockWithDevice(manager.state.value.epoch))
        return manager
    }
    @Test fun exitIsTheDefaultAndLocksImmediately()=runTest{
        val manager=unlocked(Store(),this)
        assertEquals(0,manager.state.value.autoLockMinutes)
        manager.onAppStopped();assertTrue(manager.state.value.restricted)
        manager.onAppStarted();assertTrue(manager.state.value.restricted)
    }
    @Test fun timedAccessExpiresAtTheBoundaryAndKeepsItsPolicyOnRestart()=runTest{
        val store=Store();val manager=unlocked(store,this)
        assertTrue(manager.setAutoLockMinutes(3,manager.state.value.epoch))
        manager.onAppStopped();advanceTimeBy(179_999);runCurrent();assertTrue(manager.state.value.unlocked)
        advanceTimeBy(1);runCurrent();assertTrue(manager.state.value.restricted)
        val restarted=HiddenLockManager(store,ownerScope=backgroundScope,elapsedClock={testScheduler.currentTime})
        restarted.awaitReady();assertEquals(3,restarted.state.value.autoLockMinutes);assertTrue(restarted.state.value.restricted)
    }
    @Test fun returningBeforeTheDeadlineCancelsTheOldTimerAndTheNextExitStartsAFreshWindow()=runTest{
        val manager=unlocked(Store(),this);manager.setAutoLockMinutes(5,manager.state.value.epoch)
        manager.onAppStopped();advanceTimeBy(60_000);manager.onAppStarted()
        advanceTimeBy(600_000);runCurrent();assertTrue(manager.state.value.unlocked)
        manager.onAppStopped();advanceTimeBy(299_999);runCurrent();assertTrue(manager.state.value.unlocked)
        advanceTimeBy(1);runCurrent();assertTrue(manager.state.value.restricted)
    }
    @Test fun neverKeepsOnlyTheCurrentProcessSessionOpenAndManualLockStillWorks()=runTest{
        val store=Store();val manager=unlocked(store,this);manager.setAutoLockMinutes(-1,manager.state.value.epoch)
        manager.onAppStopped();advanceTimeBy(86_400_000);runCurrent();manager.onAppStarted()
        assertTrue(manager.state.value.unlocked)
        val restarted=HiddenLockManager(store,ownerScope=backgroundScope);restarted.awaitReady()
        assertTrue(restarted.state.value.restricted);assertEquals(-1,restarted.state.value.autoLockMinutes)
        manager.lock();assertTrue(manager.state.value.restricted)
    }
    @Test fun elapsedDeadlineIsCheckedOnReturnEvenIfTheBackgroundTimerDidNotRun()=runTest{
        var elapsed=0L
        val store=Store();val manager=HiddenLockManager(store,ownerScope=backgroundScope,elapsedClock={elapsed})
        manager.awaitReady();manager.setDeviceLock(true,manager.state.value.epoch);manager.unlockWithDevice(manager.state.value.epoch)
        manager.setAutoLockMinutes(10,manager.state.value.epoch);manager.onAppStopped()
        elapsed=600_000;manager.onAppStarted();assertTrue(manager.state.value.restricted)
    }
    @Test fun changingTheDelayRequiresAnUnlockedCurrentAuthentication()=runTest{
        val manager=unlocked(Store(),this);val stale=manager.state.value.epoch
        manager.lock();assertFalse(manager.setAutoLockMinutes(-1,stale));assertEquals(0,manager.state.value.autoLockMinutes)
        assertFalse(manager.setAutoLockMinutes(3,manager.state.value.epoch))
        manager.unlockWithDevice(manager.state.value.epoch)
        assertTrue(manager.setAutoLockMinutes(30,manager.state.value.epoch))
        try{manager.setAutoLockMinutes(2,manager.state.value.epoch);fail("Unsupported delays must not be persisted")}catch(_:IllegalArgumentException){}
    }
}
