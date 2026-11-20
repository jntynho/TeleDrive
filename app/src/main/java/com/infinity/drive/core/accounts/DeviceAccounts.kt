package com.infinity.drive.core.accounts

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class DeviceAccount(val id: String, val label: String = "", val username: String? = null)

internal class DeviceAccounts(context: Context) {
    private val store = context.getSharedPreferences("teledrive-device-accounts", Context.MODE_PRIVATE)

    fun profiles(): List<DeviceAccount> {
        val json = runCatching { JSONArray(store.getString("profiles", "[]")) }.getOrDefault(JSONArray())
        val saved = (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id != PRIMARY && !idPattern.matches(id)) return@mapNotNull null
            DeviceAccount(id, item.optString("label"), item.optString("username").takeIf(String::isNotBlank))
        }.distinctBy(DeviceAccount::id)
        return listOf(saved.firstOrNull { it.id == PRIMARY } ?: DeviceAccount(PRIMARY)) + saved.filter { it.id != PRIMARY }
    }

    fun selected(): String = store.getString("active", PRIMARY)
        ?.takeIf { id -> profiles().any { it.id == id } } ?: PRIMARY

    fun rememberIdentity(id: String, name: String, username: String?) = synchronized(profileLock) {
        val items = profiles()
        val previous = items.firstOrNull { it.id == id } ?: return@synchronized
        val updated = previous.copy(label = name.trim(), username = username?.trim()?.takeIf(String::isNotBlank))
        if (updated != previous) {
            store.edit().putString("profiles", encode(items.map { if (it.id == id) updated else it })).apply()
        }
    }

    suspend fun addAndActivate(activity: Activity) {
        val items = profiles()
        val unfinished = items.firstOrNull {
            it.id != PRIMARY && it.id != activeId && it.label.isBlank() && it.username == null
        }
        if (unfinished != null) {
            activate(activity, unfinished.id)
        } else {
            check(items.size < MAX_PROFILES)
            val newAccount = DeviceAccount(UUID.randomUUID().toString())
            switch(activity, newAccount.id, newAccount)
        }
    }

    suspend fun activate(activity: Activity, next: String) {
        require(profiles().any { it.id == next })
        switch(activity, next)
    }

    private suspend fun switch(activity: Activity, next: String, newAccount: DeviceAccount? = null) {
        require(next != activeId)
        withContext(Dispatchers.IO) {
            // Existing jobs remain associated with this profile's database.
            WorkManager.getInstance(activity).cancelAllWork().result.get(10, TimeUnit.SECONDS)
            activity.getSystemService(NotificationManager::class.java)?.cancelAll()
            synchronized(profileLock) {
                val editor = store.edit().putString("active", next)
                if (newAccount != null) {
                    val items = profiles()
                    check(items.size < MAX_PROFILES)
                    editor.putString("profiles", encode(items + newAccount))
                }
                check(editor.commit())
            }
        }
        activity.startActivity(Intent(activity, AccountSwitchActivity::class.java)
            .putExtra(AccountSwitchActivity.PROCESS, Process.myPid())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun encode(accounts: List<DeviceAccount>): String = JSONArray().apply {
        accounts.forEach { profile ->
            put(JSONObject().put("id", profile.id).put("label", profile.label).put("username", profile.username))
        }
    }.toString()

    companion object {
        const val PRIMARY = "primary"
        const val MAX_PROFILES = 5
        private val profileLock = Any()
        private val idPattern = Regex("[0-9a-f-]{36}")

        @Volatile var activeId: String = PRIMARY
            private set

        val primary: Boolean get() = activeId == PRIMARY

        fun initialise(context: Context) {
            activeId = DeviceAccounts(context).selected()
        }

        fun scoped(base: String): String = if (primary) base else "$base-$activeId"

        fun isApplicationProcess(context: Context): Boolean {
            val name = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
            else context.getSystemService(ActivityManager::class.java)?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }?.processName
            return name == null || name == context.applicationInfo.processName
        }
    }
}

/** Runs separately so the previous app process releases its TDLib and database instances first. */
class AccountSwitchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val old = intent.getIntExtra(PROCESS, -1)
        if (old > 0 && old != Process.myPid()) Process.killProcess(old)
        startActivity(Intent.makeRestartActivityTask(
            android.content.ComponentName(this, app.tgdrive.MainActivity::class.java)
        ))
        finish()
    }

    companion object {
        const val PROCESS = "previous_process"
    }
}
