package app.tgdrive.migration

import android.content.Context
import android.util.Base64
import app.tgdrive.data.SecureSettings
import com.infinity.drive.core.common.AppUpgrade
import com.infinity.drive.core.crypto.CryptoKeys
import com.infinity.drive.core.crypto.WrappedKeyRepository
import com.infinity.drive.core.files.AppStoragePaths
import com.infinity.drive.core.telegram.TelegramCredentials
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LegacySessionUpgrade(
    private val context:Context,private val keys:WrappedKeyRepository,
    private val settings:SettingsRepository,private val paths:AppStoragePaths
):AppUpgrade {
    override suspend fun prepare():String?=withContext(Dispatchers.IO){
        val done=File(paths.filesDir,"legacy-session-upgrade.done")
        if(done.exists()||settings.hasStoredTelegramCredentials())return@withContext null
        val profiles=context.getSharedPreferences("account-profiles",Context.MODE_PRIVATE)
        val profile=profiles.getString("active","legacy")?:"legacy"
        if(profile!="legacy"&&!profile.matches(Regex("[a-zA-Z0-9-]{1,80}")))return@withContext null
        val source=File(context.noBackupFilesDir,if(profile=="legacy")"telegram-db"else"sessions/$profile/db")
        if(!source.isDirectory)return@withContext null
        var keyToWipe:ByteArray?=null
        try {
            if(profiles.getBoolean("lock:$profile",false))settings.update{it.copy(appLockEnabled=true,blockScreenCapture=true)}
            val secure=SecureSettings(context,profile)
            val id=secure.get("api-id")?.toIntOrNull()?:return@withContext null
            val hash=secure.get("api-hash")?:return@withContext null
            val encoded=secure.get("database-key")?:error("Previous session key is unavailable.")
            val key=Base64.decode(encoded,Base64.NO_WRAP)
            keyToWipe=key
            require(key.size==32&&id>0&&hash.matches(Regex("[a-fA-F0-9]{32}")))
            val target=File(paths.filesDir,"tdlib/db")
            val prepared=File(paths.filesDir,"legacy-session-upgrade.prepared")
            if(target.exists()&&!prepared.exists())return@withContext "Your previous library is preserved. Sign in to set up the new private-channel drive; an existing new session was not replaced."
            if(!target.exists()){
                prepared.parentFile!!.mkdirs()
                prepared.writeText(profile)
                copySessionDirectory(source,target)
            }
            require(prepared.readText()==profile){"Session upgrade profile changed."}
            keys.store(CryptoKeys.TDLIB_DATABASE,key.copyOf())
            settings.setTelegramCredentials(TelegramCredentials(id,hash))
            done.writeText(profile)
            "Your Telegram session was copied safely. New files use a private TG Drive channel. Your previous Saved Messages library is available under Settings → Account → Previous library. Unfinished 0.x uploads were stopped; select their original files again to add them to the new drive."
        }catch(e:CancellationException){throw e}catch(_:Exception){
            "Your previous session and files are preserved. Automatic session adoption could not finish; sign in to create the new private-channel drive. The previous library remains available in Settings."
        } finally { keyToWipe?.fill(0) }
    }
}
