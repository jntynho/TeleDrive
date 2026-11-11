package app.tgdrive.rebuild

import android.app.Application
import androidx.paging.AsyncPagingDataDiffer
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.DiffUtil
import com.infinity.drive.core.crypto.CredentialCipher
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.*
import com.infinity.drive.di.appModules
import kotlinx.coroutines.*
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
class StartupRepositoryTest {
    @After fun close(){stopKoin()}
    @Test fun thePreparedPageIsShownThenRevalidatedAgainstActualRoomRows()=runBlocking {
        val koin=startKoin{
            androidContext(RuntimeEnvironment.getApplication());workManagerFactory()
            modules(appModules+module {single<CredentialCipher>{object:CredentialCipher{
                override fun encrypt(plaintext:ByteArray)=plaintext.copyOf();override fun decrypt(ciphertext:ByteArray)=ciphertext.copyOf()
            }}})
        }.koin
        koin.get<SettingsRepository>().update{UserPreferences(onboardingComplete=true,updateCheckEnabled=false,instantBackupEnabled=false)}
        val dao=koin.get<FileDao>()
        dao.upsert(FileEntity("warm-row",null,"Before.jpg",1,"image/jpeg",FileCategory.IMAGE,null,null,null,null,null,null,
            backupState=BackupState.NONE,createdAt=1,modifiedAt=1,addedAt=1))
        val repo=koin.get<FileRepository>();repo.warmLibrary()
        dao.rename("warm-row","After.jpg",2)
        val spec=FileQuerySpec(categories=listOf(FileCategory.IMAGE,FileCategory.VIDEO),sortField=FileSortField.DATE_MODIFIED,sortDirection=SortDirection.DESCENDING)
        val emissions=repo.pagedFiles(spec).take(2).toList()
        assertEquals(2,emissions.size)
        val changed=CompletableDeferred<Unit>()
        val differ=AsyncPagingDataDiffer(object:DiffUtil.ItemCallback<DriveFile>(){
            override fun areItemsTheSame(a:DriveFile,b:DriveFile)=a.id==b.id
            override fun areContentsTheSame(a:DriveFile,b:DriveFile)=a==b
        },object:ListUpdateCallback{
            override fun onInserted(position:Int,count:Int){};override fun onRemoved(position:Int,count:Int){}
            override fun onMoved(fromPosition:Int,toPosition:Int){};override fun onChanged(position:Int,count:Int,payload:Any?){}
        },mainDispatcher=Dispatchers.Unconfined,workerDispatcher=Dispatchers.Unconfined)
        differ.submitData(emissions[0]);assertEquals("Before.jpg",differ.snapshot().items.single().name)
        differ.addOnPagesUpdatedListener{if(differ.snapshot().items.firstOrNull()?.name=="After.jpg")changed.complete(Unit)}
        val job=launch{differ.submitData(emissions[1])}
        withTimeout(5000){changed.await()}
        assertEquals("After.jpg",differ.snapshot().items.single().name)
        job.cancelAndJoin()
    }
}
