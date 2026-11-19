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

internal data class DeviceAccount(val id: String, val label: String)

internal class DeviceAccounts(context: Context) {
    private val store = context.getSharedPreferences("teledrive-device-accounts", Context.MODE_PRIVATE)

    fun profiles(): List<DeviceAccount> {
        val json = runCatching { JSONArray(store.getString("profiles", "[]")) }.getOrDefault(JSONArray())
        return listOf(DeviceAccount(PRIMARY, "")) + (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val label = item.optString("label")
            if (idPattern.matches(id) && label.isNotBlank()) DeviceAccount(id, label) else null
        }.distinctBy(DeviceAccount::id)
    }

    fun selected(): String = store.getString("active", PRIMARY)
        ?.takeIf { id -> profiles().any { it.id == id } } ?: PRIMARY

    fun create(label: String): DeviceAccount {
        require(label.isNotBlank() && label.length <= 40)
        val profiles = profiles()
        check(profiles.size < 5)
        val added = DeviceAccount(UUID.randomUUID().toString(), label)
        val saved = JSONArray()
        (profiles.drop(1) + added).forEach { profile ->
            saved.put(JSONObject().put("id", profile.id).put("label", profile.label))
        }
        check(store.edit().putString("profiles", saved.toString()).commit())
        return added
    }

    suspend fun activate(activity: Activity, next: String) {
        require(profiles().any { it.id == next } && next != activeId)
        withContext(Dispatchers.IO) {
            // Existing jobs are tied to the currently open database. Recovery resumes its queue
            // when this profile is selected again, and no job migrates to another identity.
            WorkManager.getInstance(activity).cancelAllWork().result.get(10, TimeUnit.SECONDS)
            activity.getSystemService(NotificationManager::class.java)?.cancelAll()
            check(store.edit().putString("active", next).commit())
        }
        activity.startActivity(Intent(activity, AccountSwitchActivity::class.java)
            .putExtra(AccountSwitchActivity.PROCESS, Process.myPid())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    companion object {
        const val PRIMARY = "primary"
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
