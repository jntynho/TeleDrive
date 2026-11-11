package app.tgdrive.rebuild

import android.app.Application
import com.infinity.drive.core.crypto.CredentialCipher
import com.infinity.drive.core.security.*
import com.infinity.drive.data.local.dao.*
import com.infinity.drive.data.local.entity.*
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.*
import com.infinity.drive.di.appModules
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.*
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,manifest=Config.NONE)
class HiddenRepositoryTest {
    @After fun close(){stopKoin()}
    @Test fun lockFiltersQueriesAncestorsAndDirectPreviewsWhileTrustedUploadLookupsRemainAvailable()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val store=object:HiddenLockStore {
            var record:HiddenLockRecord?=null
            override suspend fun read()=record
            override suspend fun write(record:HiddenLockRecord){this.record=record}
            override suspend fun clear(){record=null}
        }
        val lock=HiddenLockManager(store,1000,{100_000},scope)
        val koin=startKoin {
            androidContext(RuntimeEnvironment.getApplication());workManagerFactory()
            modules(appModules+module{
                single<HiddenLockManager>{lock}
                single<CredentialCipher>{object:CredentialCipher{override fun encrypt(plaintext:ByteArray)=plaintext.copyOf();override fun decrypt(ciphertext:ByteArray)=ciphertext.copyOf()}}
            })
        }.koin
        try {
            koin.get<SettingsRepository>().update{UserPreferences(onboardingComplete=true,updateCheckEnabled=false,instantBackupEnabled=false)}
            val folders=koin.get<FolderDao>();val dao=koin.get<FileDao>()
            folders.upsert(FolderEntity("private-folder",null,null,"Private",isHidden=true,createdAt=1,modifiedAt=1))
            folders.upsert(FolderEntity("nested",null,"private-folder","Nested",createdAt=1,modifiedAt=1))
            val visibleFolder=FolderEntity("visible-folder",null,null,"Visible",createdAt=1,modifiedAt=1)
            folders.upsert(visibleFolder)
            fun file(id:String,hidden:Boolean=false,folder:String?=null)=FileEntity(id,folder,"$id.jpg",1,"image/jpeg",FileCategory.IMAGE,null,null,null,null,null,null,
                backupState=BackupState.NONE,isHidden=hidden,createdAt=1,modifiedAt=1,addedAt=1)
            dao.upsert(file("public"));dao.upsert(file("hidden",true));dao.upsert(file("child",folder="nested"));dao.upsert(file("visible-child",folder="visible-folder"))
            lock.awaitReady();lock.configure("246810".toCharArray(),"0123456789ABCDEF0123456789ABCDEF");lock.lock()
            val repo=koin.get<FileRepository>();val access=koin.get<HiddenContentAccess>()
            val spec=FileQuerySpec(showHidden=true,showArchived=true)
            val changes=Channel<Set<String>>(Channel.UNLIMITED)
            val job=launch{repo.observeFiles(spec).map{rows->rows.map{it.id}.toSet()}.distinctUntilChanged().collect{changes.send(it)}}
            assertEquals(setOf("public","visible-child"),withTimeout(5000){changes.receive()})
            val publicSnapshot=access.observeAccessFile("public").first()!!
            val hiddenSnapshot=access.observeAccessFile("hidden").first()!!
            val childSnapshot=access.observeAccessFile("child").first()!!
            withTimeout(5000){access.protectedFolders.filterNotNull().first()}
            assertEquals(true,access.snapshotCanRead(publicSnapshot))
            assertEquals(false,access.snapshotCanRead(hiddenSnapshot))
            assertEquals(false,access.snapshotCanRead(childSnapshot))
            // No-op public navigation epochs must not flash a public row or expose a private one.
            repeat(3){lock.lock();assertEquals(true,access.snapshotCanRead(publicSnapshot));assertEquals(false,access.snapshotCanRead(hiddenSnapshot))}
            assertTrue(repo.fileIdsInTree("private-folder").isEmpty())
            assertFalse(access.canRead("hidden"));assertFalse(access.canRead("child"));assertTrue(access.canRead("public"))
            assertTrue(repo.observeFiles(spec.copy(respectHiddenLock=false)).first().any{it.id=="hidden"})
            assertEquals("Hidden file",access.notificationName("hidden","hidden.jpg"))
            assertEquals("Hidden file",access.notificationName("child","child.jpg"))
            assertEquals(HiddenVerification.SUCCESS,lock.verifyPin("246810".toCharArray()))
            assertEquals(setOf("public","visible-child","hidden","child"),withTimeout(5000){changes.receive()})
            lock.lock();assertEquals(setOf("public","visible-child"),withTimeout(5000){changes.receive()})
            folders.update(visibleFolder.copy(isHidden=true,modifiedAt=2))
            assertEquals(setOf("public"),withTimeout(5000){changes.receive()})
            assertFalse(withTimeout(5000){access.observeCanRead("visible-child").first{!it}})
            assertNull(repo.observeFile("hidden").first())
            assertTrue(repo.previewWindow(spec,"public").none{it.id=="hidden"||it.id=="child"})
            job.cancelAndJoin()
            dao.upsert(file("new-public"))
            assertEquals(HiddenVerification.SUCCESS,lock.verifyPin("246810".toCharArray()))
            repo.setFilesHidden(listOf("new-public"),true)
            assertFalse(lock.state.value.unlocked)
            assertEquals("Hidden file",access.notificationName("new-public","new-public.jpg"))
        } finally {scope.cancel()}
    }
}
