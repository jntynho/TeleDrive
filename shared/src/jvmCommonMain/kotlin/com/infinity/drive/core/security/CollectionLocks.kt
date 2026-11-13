package com.infinity.drive.core.security

fun interface CollectionLockStores { fun create(name:String):HiddenLockStore }

/** Independent, opt-in, device-local locks. Hidden retains its existing protected-content policy. */
class CollectionLocks(stores:CollectionLockStores) {
    private val managers=listOf("FAVORITES","ARCHIVED","TRASH").associateWith{HiddenLockManager(stores.create(it.lowercase()))}
    fun manager(type:String):HiddenLockManager = requireNotNull(managers[type])
    suspend fun clearAll(){managers.values.forEach{it.clear()}}
    fun lockAll(){managers.values.forEach{it.lock()}}
    fun leaveExcept(type:String?){managers.forEach{(key,value)->if(key!=type)value.lock()}}
}
