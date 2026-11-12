package com.infinity.drive.core.security

import com.infinity.drive.core.crypto.PassphraseKdf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

/** Device-local access control, independent of Telegram credentials and content encryption. */
@Serializable
data class HiddenLockRecord(
    val version:Int=1,
    val pinSalt:String="",val pinHash:String="",val recoverySalt:String="",val recoveryHash:String="",
    val failures:Int=0,val retryAfter:Long=0,val lastAttemptAt:Long=0,
    val biometricIv:String?=null,val biometricCiphertext:String?=null,val biometricHash:String?=null
)
interface HiddenLockStore {
    suspend fun read():HiddenLockRecord?
    suspend fun write(record:HiddenLockRecord)
    suspend fun clear()
}
data class HiddenLockState(val ready:Boolean=false,val configured:Boolean=false,val unlocked:Boolean=false,
    val biometrics:Boolean=false,val error:Boolean=false,val epoch:Long=0,val deviceLock:Boolean=false) {
    val restricted:Boolean get()=!ready||error||(configured&&!unlocked)
}
enum class HiddenVerification { SUCCESS, WRONG, WAIT, CANCELLED }

class HiddenLockManager(
    private val store:HiddenLockStore,
    private val iterations:Int=PassphraseKdf.ITERATIONS,
    private val clock:()->Long=System::currentTimeMillis,
    ownerScope:CoroutineScope?=null
) {
    private val scope=ownerScope?:CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutable=MutableStateFlow(HiddenLockState())
    val state:StateFlow<HiddenLockState> = mutable.asStateFlow()
    private val mutex=Mutex()
    private val epoch=AtomicLong()
    private var record:HiddenLockRecord?=null
    @Volatile private var resetPermit:Long?=null
    private val loaded=CompletableDeferred<Unit>()
    init { scope.launch {
        try { record=store.read();record?.let(::validate);publish() }
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){mutable.value=HiddenLockState(ready=true,configured=true,error=true,epoch=epoch.get())}
        finally { loaded.complete(Unit) }
    }}
    suspend fun awaitReady(){loaded.await()}
    private fun publish(unlocked:Boolean=false){
        mutable.value=HiddenLockState(true,record!=null,unlocked,record?.biometricHash!=null,false,epoch.get(),record?.version==2)
    }
    fun lock(){
        val next=epoch.incrementAndGet();resetPermit=null
        mutable.update{it.copy(unlocked=false,epoch=next)}
    }
    fun newRecoveryKey():String = ByteArray(16).also(SecureRandom()::nextBytes).joinToString(""){"%02X".format(it)}
    fun beginPinChange():Boolean {
        val ticket=epoch.get()
        if(!state.value.unlocked||ticket!=epoch.get())return false
        resetPermit=ticket;return ticket==epoch.get()
    }
    suspend fun configure(pin:CharArray,recoveryKey:String){
        try { awaitReady();mutex.withLock {
            check(!state.value.error){"Hidden lock could not be read"}
            check(record==null||resetPermit==epoch.get()){"Verify your identity again"}
            require(pin.size==6&&pin.all{it in '0'..'9'})
            val recovery=normalizeRecovery(recoveryKey)
            require(recovery.matches(Regex("[0-9A-F]{32}")))
            val ticket=epoch.get()
            val next=withContext(Dispatchers.Default){
                val kdf=PassphraseKdf();val salt=kdf.newSalt();val rs=kdf.newSalt()
                val recoveryChars=recovery.toCharArray()
                try{HiddenLockRecord(pinSalt=encode(salt),pinHash=encode(kdf.deriveKey(pin,salt,iterations)),
                    recoverySalt=encode(rs),recoveryHash=encode(kdf.deriveKey(recoveryChars,rs,iterations)))}finally{recoveryChars.fill('\u0000')}
            }
            check(ticket==epoch.get()){"Hidden was locked; start again"}
            withContext(NonCancellable+Dispatchers.IO){
                store.write(next);record=next;resetPermit=null;publish(unlocked=ticket==epoch.get())
            }
        }}finally{pin.fill('\u0000')}
    }
    suspend fun remainingDelaySeconds():Long {awaitReady();return mutex.withLock{delayFor(record)}}
    private fun delayFor(value:HiddenLockRecord?):Long {
        value?:return 0
        val now=clock()
        // A wall-clock rollback never grants an early retry. The persisted delay is bounded.
        val remaining=if(now<value.lastAttemptAt)value.retryAfter-value.lastAttemptAt else value.retryAfter-now
        return ((remaining.coerceIn(0,MAX_DELAY)+999)/1000)
    }
    suspend fun verifyPin(pin:CharArray):HiddenVerification = verify(pin,false)
    suspend fun verifyRecovery(key:String):HiddenVerification = verify(normalizeRecovery(key).toCharArray(),true)
    private suspend fun verify(candidate:CharArray,recovery:Boolean):HiddenVerification {
        try {awaitReady();return mutex.withLock {
            check(!state.value.error){"Hidden lock could not be read"}
            val current=record?:return@withLock HiddenVerification.WRONG
            if(current.version==2)return@withLock HiddenVerification.WRONG
            if(delayFor(current)>0)return@withLock HiddenVerification.WAIT
            val ticket=epoch.get()
            val valid=withContext(Dispatchers.Default){
                val actual=PassphraseKdf().deriveKey(candidate,decode(if(recovery)current.recoverySalt else current.pinSalt),iterations)
                try{MessageDigest.isEqual(actual,decode(if(recovery)current.recoveryHash else current.pinHash))}finally{actual.fill(0)}
            }
            val now=clock();val failures=if(valid)0 else (current.failures+1).coerceAtMost(30)
            val delay=if(failures<5)0 else (30_000L shl (failures-5).coerceAtMost(6)).coerceAtMost(MAX_DELAY)
            val next=current.copy(failures=failures,retryAfter=if(valid)0 else now+delay,lastAttemptAt=now)
            withContext(NonCancellable+Dispatchers.IO){store.write(next);record=next}
            if(ticket!=epoch.get())return@withLock HiddenVerification.CANCELLED
            if(valid){if(recovery)resetPermit=ticket else publish(unlocked=true);HiddenVerification.SUCCESS}
            else HiddenVerification.WRONG
        }}finally{candidate.fill('\u0000')}
    }
    suspend fun enrollBiometric(iv:ByteArray,ciphertext:ByteArray,secret:ByteArray){
        try{awaitReady();mutex.withLock{
            check(state.value.unlocked)
            val next=checkNotNull(record).copy(biometricIv=encode(iv),biometricCiphertext=encode(ciphertext),
                biometricHash=encode(MessageDigest.getInstance("SHA-256").digest(secret)))
            withContext(NonCancellable+Dispatchers.IO){store.write(next);record=next};publish(unlocked=state.value.unlocked)
        }}finally{secret.fill(0)}
    }
    suspend fun biometricPayload():Pair<ByteArray,ByteArray>?{awaitReady();return mutex.withLock{
        record?.let{r->if(r.biometricIv!=null&&r.biometricCiphertext!=null)decode(r.biometricIv) to decode(r.biometricCiphertext)else null}
    }}
    suspend fun verifyBiometric(secret:ByteArray,ticket:Long,forReset:Boolean=false):Boolean {
        try{awaitReady();return mutex.withLock{
            val hash=record?.biometricHash?:return@withLock false
            if(ticket!=epoch.get()||!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(secret),decode(hash)))return@withLock false
            if(forReset)resetPermit=ticket else publish(unlocked=true)
            true
        }}finally{secret.fill(0)}
    }
    suspend fun disableBiometric(){awaitReady();mutex.withLock{
        check(state.value.unlocked)
        val next=checkNotNull(record).copy(biometricHash=null,biometricIv=null,biometricCiphertext=null)
        withContext(NonCancellable+Dispatchers.IO){store.write(next);record=next};publish(unlocked=state.value.unlocked)
    }}
    /** Call only after the platform has authenticated the current device owner. */
    suspend fun unlockWithDevice(ticket:Long):Boolean {
        awaitReady()
        return mutex.withLock {
            if(ticket!=epoch.get()||state.value.error)return@withLock false
            if(record==null)return@withLock true
            try {
                if(record?.version!=2)withContext(NonCancellable+Dispatchers.IO){
                    val next=HiddenLockRecord(version=2)
                    store.write(next);record=next;resetPermit=null
                }
                val current=ticket==epoch.get()
                publish(unlocked=current)
                current
            }catch(cancel:CancellationException){lock();throw cancel}
            catch(error:Exception){failClosed();throw error}
        }
    }
    /** Both enabling and disabling are authenticated; an expired completion cannot disable a lock. */
    suspend fun setDeviceLock(enabled:Boolean,ticket:Long):Boolean {
        awaitReady()
        return mutex.withLock {
            if(ticket!=epoch.get()||state.value.error)return@withLock false
            val previous=record
            try {withContext(NonCancellable+Dispatchers.IO){
                if(enabled){
                    val next=HiddenLockRecord(version=2)
                    store.write(next);record=next;resetPermit=null
                    val current=ticket==epoch.get()
                    lock();publish()
                    current
                }else{
                    store.clear()
                    if(ticket!=epoch.get()){
                        if(previous!=null)store.write(previous)
                        record=previous;publish();false
                    }else{
                        record=null;lock();publish();true
                    }
                }
            }}catch(cancel:CancellationException){lock();throw cancel}
            catch(error:Exception){failClosed();throw error}
        }
    }
    private fun failClosed(){lock();mutable.update{it.copy(configured=true,error=true,unlocked=false)}}
    suspend fun clear(){awaitReady();lock();mutex.withLock{
        withContext(NonCancellable+Dispatchers.IO){store.clear();record=null;publish()}
    }}
    private fun validate(value:HiddenLockRecord){
        if(value.version==2){
            require(value.pinSalt.isEmpty()&&value.pinHash.isEmpty()&&value.recoverySalt.isEmpty()&&value.recoveryHash.isEmpty())
            require(value.biometricIv==null&&value.biometricCiphertext==null&&value.biometricHash==null)
            return
        }
        require(value.version==1&&decode(value.pinSalt).size==16&&decode(value.recoverySalt).size==16)
        require(decode(value.pinHash).size==32&&decode(value.recoveryHash).size==32)
        require(value.failures>=0)
    }
    companion object {
        private const val MAX_DELAY=1_800_000L
        fun normalizeRecovery(value:String)=value.filterNot{it=='-'||it.isWhitespace()}.uppercase(java.util.Locale.ROOT)
        private fun encode(value:ByteArray)=Base64.getEncoder().encodeToString(value)
        private fun decode(value:String)=Base64.getDecoder().decode(value)
    }
}
