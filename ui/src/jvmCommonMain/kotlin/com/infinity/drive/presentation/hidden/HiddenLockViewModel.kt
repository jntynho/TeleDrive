package com.infinity.drive.presentation.hidden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinity.drive.core.security.*
import com.infinity.drive.presentation.platform.HiddenBiometrics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class HiddenStage { UNLOCK, CHOOSE, CONFIRM, SAVE_KEY, RECOVER, RECOVERY_OPTIONS, MANAGE }
data class HiddenUiState(val stage:HiddenStage=HiddenStage.UNLOCK,val entry:String="",val recovery:String="",val saved:Boolean=false,
    val working:Boolean=false,val message:String?=null,val delaySeconds:Long=0)
class HiddenLockViewModel(val manager:HiddenLockManager):ViewModel(){
    private val mutable=MutableStateFlow(HiddenUiState())
    val ui=mutable.asStateFlow()
    private var firstPin:CharArray?=null
    private var suspendedSetup:HiddenUiState?=null
    private var retryJob:Job?=null
    private val initialEpoch=manager.state.value.epoch
    init {
        viewModelScope.launch {
            var lastEpoch=initialEpoch
            var wasConfigured=false
            manager.state.collect{state->
                if(wasConfigured&&!state.configured)clearSecrets()
                wasConfigured=state.configured
                if(state.epoch!=lastEpoch){
                    lastEpoch=state.epoch;retryJob?.cancel()
                    if(state.configured){
                        if(firstPin!=null&&mutable.value.stage in setOf(HiddenStage.CONFIRM,HiddenStage.SAVE_KEY))suspendedSetup=mutable.value.copy(entry="",working=false)
                        if(suspendedSetup==null)clearSecrets()
                        mutable.value=HiddenUiState(message=if(suspendedSetup!=null)"Unlock to finish changing your PIN."else null)
                    }
                    // An unfinished first setup has no existing PIN to verify. Keep its in-memory
                    // key stable while the owner switches out to save it; nothing is committed yet.
                }
                if(state.ready&&!state.configured&&!state.error&&mutable.value.stage==HiddenStage.UNLOCK)mutable.value=HiddenUiState(stage=HiddenStage.CHOOSE)
            }
        }
    }
    fun refreshDelay(){
        retryJob?.cancel()
        retryJob=viewModelScope.launch {
            do {
                val remaining=manager.remainingDelaySeconds()
                mutable.update{it.copy(delaySeconds=remaining)}
                if(remaining<=0)break
                delay(1000)
            }while(isActive)
        }
    }
    fun stopCountdown(){retryJob?.cancel()}
    private fun clearSecrets(){firstPin?.fill('\u0000');firstPin=null;suspendedSetup=null}
    private fun resumeSetup(recoveryVerified:Boolean=false):Boolean {
        val pending=suspendedSetup?:return false
        if(!recoveryVerified&&!manager.beginPinChange())return false
        suspendedSetup=null;mutable.value=pending.copy(working=false,message=null)
        return true
    }
    fun manage(){if(manager.state.value.unlocked)mutable.value=HiddenUiState(stage=HiddenStage.MANAGE)}
    fun changePin(){if(manager.beginPinChange())mutable.value=HiddenUiState(stage=HiddenStage.CHOOSE)}
    fun recoveryOptions(){mutable.value=HiddenUiState(stage=HiddenStage.RECOVERY_OPTIONS)}
    fun enterRecovery(){mutable.value=HiddenUiState(stage=HiddenStage.RECOVER)}
    fun acknowledge(saved:Boolean){mutable.update{it.copy(saved=saved)}}
    fun back():Boolean {
        if(mutable.value.working)return true
        val stage=mutable.value.stage
        clearSecrets()
        return when(stage){
            HiddenStage.CONFIRM,HiddenStage.SAVE_KEY->{mutable.value=HiddenUiState(stage=HiddenStage.CHOOSE);true}
            HiddenStage.RECOVER->{mutable.value=HiddenUiState(stage=HiddenStage.RECOVERY_OPTIONS);true}
            HiddenStage.RECOVERY_OPTIONS,HiddenStage.MANAGE->{mutable.value=HiddenUiState();true}
            HiddenStage.CHOOSE->{if(manager.state.value.configured){manager.lock();mutable.value=HiddenUiState();true}else false}
            else->false
        }
    }
    fun input(value:String){
        val state=mutable.value
        if(state.working)return
        if(state.stage==HiddenStage.RECOVER){mutable.update{it.copy(entry=HiddenLockManager.normalizeRecovery(value).filter{ch->ch in '0'..'9'||ch in 'A'..'F'}.take(32),message=null)};return}
        val digits=value.filter{it in '0'..'9'}.take(6)
        mutable.update{it.copy(entry=digits,message=null)}
        if(digits.length!=6)return
        when(state.stage){
            HiddenStage.UNLOCK->perform{
                when(manager.verifyPin(digits.toCharArray())){
                    HiddenVerification.SUCCESS->if(!resumeSetup())mutable.value=HiddenUiState()
                    HiddenVerification.WRONG->mutable.update{it.copy(entry="",message="Incorrect PIN. Try again.")}
                    HiddenVerification.WAIT->mutable.update{it.copy(entry="",message="Too many attempts. Please wait.")}
                    HiddenVerification.CANCELLED->mutable.value=HiddenUiState()
                }
            }
            HiddenStage.CHOOSE->{clearSecrets();firstPin=digits.toCharArray();mutable.value=HiddenUiState(stage=HiddenStage.CONFIRM)}
            HiddenStage.CONFIRM->{
                if(firstPin?.contentEquals(digits.toCharArray())==true)mutable.value=HiddenUiState(stage=HiddenStage.SAVE_KEY,recovery=manager.newRecoveryKey())
                else mutable.update{it.copy(entry="",message="The PINs did not match. Try again.")}
            }
            else->Unit
        }
    }
    fun save(){
        val state=mutable.value
        if(!state.saved||state.stage!=HiddenStage.SAVE_KEY)return
        perform {
            val pin=firstPin?.copyOf()?:error("Choose your PIN again")
            manager.configure(pin,state.recovery)
            clearSecrets();mutable.value=HiddenUiState()
        }
    }
    fun verifyRecovery(){
        val value=mutable.value
        if(value.entry.length!=32||value.stage!=HiddenStage.RECOVER)return
        perform {
            when(manager.verifyRecovery(value.entry)){
                HiddenVerification.SUCCESS->if(!resumeSetup(true))mutable.value=HiddenUiState(stage=HiddenStage.CHOOSE)
                HiddenVerification.WRONG->mutable.update{it.copy(entry="",message="This recovery key did not match.")}
                HiddenVerification.WAIT->mutable.update{it.copy(message="Too many attempts. Please wait.")}
                HiddenVerification.CANCELLED->mutable.value=HiddenUiState()
            }
        }
    }
    fun biometric(platform:HiddenBiometrics,forReset:Boolean=false){perform {
        val ticket=manager.state.value.epoch
        val payload=manager.biometricPayload()?:error("Use your PIN or recovery key")
        val secret=platform.authenticate(payload.first,payload.second)?:return@perform
        if(manager.verifyBiometric(secret,ticket,forReset)){if(!resumeSetup(forReset))mutable.value=HiddenUiState(stage=if(forReset)HiddenStage.CHOOSE else HiddenStage.UNLOCK)}
        else mutable.update{it.copy(message="Hidden was locked. Verify again.")}
    }}
    fun toggleBiometric(platform:HiddenBiometrics){perform {
        check(manager.state.value.unlocked)
        if(manager.state.value.biometrics)manager.disableBiometric()
        else {
            val enrollment=platform.enroll()?:return@perform
            manager.enrollBiometric(enrollment.iv,enrollment.ciphertext,enrollment.secret)
        }
    }}
    private fun perform(block:suspend ()->Unit){
        if(mutable.value.working)return
        mutable.update{it.copy(working=true,message=null)}
        viewModelScope.launch {
            try{block()}
            catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){mutable.update{it.copy(entry="",message="Couldn't complete verification. Try your PIN or recovery key.")}}
            finally{mutable.update{it.copy(working=false)};refreshDelay()}
        }
    }
    override fun onCleared(){clearSecrets();mutable.value=HiddenUiState()}
}
