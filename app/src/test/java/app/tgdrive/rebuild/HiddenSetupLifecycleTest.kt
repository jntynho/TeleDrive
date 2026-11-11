package app.tgdrive.rebuild

import androidx.lifecycle.ViewModelStore
import com.infinity.drive.core.security.*
import com.infinity.drive.presentation.hidden.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HiddenSetupLifecycleTest {
    private val dispatcher=UnconfinedTestDispatcher()
    private val models=ViewModelStore()
    @Before fun main(){Dispatchers.setMain(dispatcher)}
    @After fun close(){models.clear();Dispatchers.resetMain()}
    private class Store:HiddenLockStore {
        var value:HiddenLockRecord?=null
        override suspend fun read()=value
        override suspend fun write(record:HiddenLockRecord){value=record}
        override suspend fun clear(){value=null}
    }
    private fun enter(vm:HiddenLockViewModel,pin:String){pin.forEach{vm.input(vm.ui.value.entry+it)}}
    @Test fun savingTheFirstRecoveryKeyInAnotherAppKeepsThePendingKeyStable()=runTest(dispatcher){
        val manager=HiddenLockManager(Store(),1000,{100_000},backgroundScope);manager.awaitReady()
        val vm=HiddenLockViewModel(manager);models.put("vm",vm)
        enter(vm,"246810");enter(vm,"246810")
        assertEquals(HiddenStage.SAVE_KEY,vm.ui.value.stage)
        val key=vm.ui.value.recovery
        manager.lock()
        assertEquals(HiddenStage.SAVE_KEY,vm.ui.value.stage);assertEquals(key,vm.ui.value.recovery)
        vm.acknowledge(true);vm.save()
        vm.ui.first{it.stage==HiddenStage.UNLOCK&&!it.working}
        assertTrue(manager.state.value.configured)
        manager.lock();assertEquals(HiddenVerification.SUCCESS,manager.verifyRecovery(key))
    }
    @Test fun changingAnExistingPinResumesOnlyAfterReauthenticationWithoutChangingThePendingKey()=runTest(dispatcher){
        val manager=HiddenLockManager(Store(),1000,{100_000},backgroundScope);manager.awaitReady()
        manager.configure("246810".toCharArray(),"0123456789ABCDEF0123456789ABCDEF")
        val vm=HiddenLockViewModel(manager);models.put("vm",vm)
        vm.changePin();enter(vm,"135790");enter(vm,"135790")
        val key=vm.ui.value.recovery
        assertEquals(HiddenStage.SAVE_KEY,vm.ui.value.stage)
        manager.lock()
        vm.ui.first{it.stage==HiddenStage.UNLOCK}
        assertEquals(HiddenStage.UNLOCK,vm.ui.value.stage);assertFalse(manager.state.value.unlocked)
        enter(vm,"246810")
        vm.ui.first{it.stage==HiddenStage.SAVE_KEY&&!it.working}
        assertEquals(key,vm.ui.value.recovery)
        vm.acknowledge(true);vm.save();vm.ui.first{it.stage==HiddenStage.UNLOCK&&!it.working}
        manager.lock();assertEquals(HiddenVerification.SUCCESS,manager.verifyPin("135790".toCharArray()))
    }
}
