package com.infinity.drive.core.security

fun interface CollectionLockStores { fun create(name:String):HiddenLockStore }

/** Independent, opt-in, device-local locks. Hidden retains its existing protected-content policy. */
class CollectionLocks(elapsedClock:()->Long={System.nanoTime()/1_000_000},stores:CollectionLockStores) {
    private val managers=listOf("FAVORITES","ARCHIVED","TRASH").associateWith{HiddenLockManager(stores.create(it.lowercase()),elapsedClock=elapsedClock)}
    fun manager(type:String):HiddenLockManager = requireNotNull(managers[type])
    suspend fun clearAll(){managers.values.forEach{it.clear()}}
    fun lockAll(){managers.values.forEach{it.lock()}}
    fun onAppStopped(){managers.values.forEach{it.onAppStopped()}}
    fun onAppStarted(){managers.values.forEach{it.onAppStarted()}}
}
