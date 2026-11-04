package app.tgdrive.rebuild

import com.infinity.drive.core.network.*
import com.infinity.drive.core.telegram.TelegramPacer
import com.infinity.drive.core.transfer.*
import com.infinity.drive.data.local.dao.*
import com.infinity.drive.data.local.entity.TransferEntity
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class TransferQueueTest {
    private inline fun <reified T> fake(crossinline answer:(String,Array<out Any?>)->Any?):T =
        Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)){_,method,args->answer(method.name,args?:emptyArray())} as T
    private fun row(id:String,type:TransferType=TransferType.UPLOAD)=TransferEntity(id=id,type=type,fileId=id,displayName=id,localPath=null,chatId=null,messageId=null,
        remoteFileId=if(id.startsWith("copy"))"remote"else null,sizeBytes=10,state=TransferState.QUEUED,createdAt=0,updatedAt=0)
    private class Store(val rows:MutableMap<String,TransferEntity>) {
        val active get()=rows.values.count{it.state in listOf(TransferState.QUEUED,TransferState.RUNNING)}
    }
    private fun TestScope.drainer(store:Store,run:TransferRunner,pacer:TelegramPacer=TelegramPacer{testScheduler.currentTime}):TransferQueueDrainer {
        val dao=fake<TransferDao>{name,a->when(name){
            "queuedLane"->store.rows.values.filter{it.state==TransferState.QUEUED&&when(a[0] as Int){
                0->!it.type.isIncoming&&it.remoteFileId==null;1->!it.type.isIncoming&&it.remoteFileId!=null;else->it.type.isIncoming}}
            "observeActiveCount"->flowOf(store.active)
            "observeById"->flow{while(true){emit(store.rows[a[0]]);delay(10)}}
            "setRunningState"->{val id=a[0] as String;val row=store.rows[id]!!;if(row.state==TransferState.RUNNING)store.rows[id]=row.copy(state=a[1] as TransferState);Unit}
            "failRunning"->{val id=a[0] as String;val row=store.rows[id]!!;if(row.state==TransferState.RUNNING){store.rows[id]=row.copy(state=TransferState.FAILED);1}else 0}
            "requeueRunningExcept"->{val keep=(a[1] as List<*>).toSet();store.rows.replaceAll{_,v->if(v.state==TransferState.RUNNING&&v.id !in keep)v.copy(state=TransferState.QUEUED)else v};Unit}
            "claim"->{val id=a[0] as String;val row=store.rows[id]!!;if(row.state!=TransferState.QUEUED)0 else {store.rows[id]=row.copy(state=TransferState.RUNNING);1}}
            "byId"->store.rows[a[0]]
            "setState"->{val id=a[0] as String;store.rows[id]=store.rows[id]!!.copy(state=a[1] as TransferState);Unit}
            "defer"->{val id=a[0] as String;val row=store.rows[id]!!;if(row.state==TransferState.RUNNING)store.rows[id]=row.copy(state=TransferState.QUEUED,nextAttemptAt=a[1] as Long,retryCount=a[2] as Int,errorMessage=a[3] as String);Unit}
            "setFailed"->{val id=a[0] as String;store.rows[id]=store.rows[id]!!.copy(state=TransferState.FAILED);Unit}
            "requeueRunning"->{store.rows.replaceAll{_,v->if(v.state==TransferState.RUNNING)v.copy(state=TransferState.QUEUED)else v};Unit}
            else->error(name)
        }}
        val files=fake<FileDao>{name,_->when(name){"setBackupStateIfLocalOnly"->Unit;else->error(name)}}
        val settings=fake<SettingsRepository>{name,_->when(name){"getPreferences"->flowOf(UserPreferences(onboardingComplete=true));else->error(name)}}
        val tracker=fake<BackupSessionTracker>{_,_->Unit}
        val network=object:NetworkMonitor {
            override val status=flowOf(NetworkStatus.UNMETERED)
            override fun currentStatus()=NetworkStatus.UNMETERED
        }
        return TransferQueueDrainer(dao,files,run,tracker,settings,network,pacer){testScheduler.currentTime}
    }
    @Test fun `five hundred originals copies and downloads drain with bounded independent lanes and no upload gaps`()=runTest {
        val store=Store((List(400){row("upload$it")}+List(50){row("copy$it")}+List(50){row("download$it",TransferType.DOWNLOAD)}).associateBy{it.id}.toMutableMap())
        val running=IntArray(3);val peak=IntArray(3);val starts=mutableListOf<Long>()
        val run=TransferRunner{task->
            val lane=if(task.type.isIncoming)2 else if(task.remoteFileId!=null)1 else 0
            if(lane==0)starts+=testScheduler.currentTime
            running[lane]++;peak[lane]=maxOf(peak[lane],running[lane]);delay(50);running[lane]--
            store.rows[task.id]=task.copy(state=TransferState.COMPLETED);TransferExecutor.Outcome.Completed
        }
        val result=drainer(store,run).drain({false}){error("Unexpected failure")}
        assertEquals(TransferDrainResult.COMPLETED,result)
        assertEquals(500,store.rows.values.count{it.state==TransferState.COMPLETED})
        assertArrayEquals(intArrayOf(2,1,3),peak)
        assertEquals(listOf(0L,0L,50L,50L),starts.take(4))
        assertTrue(testScheduler.currentTime<30_000)
    }
    @Test fun `server wait holds all uploads without using retry budget while downloads finish`()=runTest {
        val store=Store((List(12){row("upload$it")}+row("download",TransferType.DOWNLOAD)).associateBy{it.id}.toMutableMap())
        var first=true;val starts=mutableListOf<Long>()
        val run=TransferRunner{task->
            if(!task.type.isIncoming)starts+=testScheduler.currentTime
            delay(25)
            if(task.id=="upload0"&&first){first=false;TransferExecutor.Outcome.Failed("retry after 160",160)}
            else {store.rows[task.id]=task.copy(state=TransferState.COMPLETED);TransferExecutor.Outcome.Completed}
        }
        val job=launch{drainer(store,run).drain({false}){error("Flood waits must not fail files")}}
        advanceTimeBy(1000);runCurrent()
        assertEquals(TransferState.COMPLETED,store.rows["download"]!!.state)
        assertEquals(0,store.rows["upload0"]!!.retryCount)
        assertEquals(TransferState.QUEUED,store.rows["upload0"]!!.state)
        assertEquals(listOf(0L,0L),starts)
        advanceUntilIdle();job.join()
        assertTrue(starts.drop(2).all{it>=161_025})
        assertEquals(13,store.rows.values.count{it.state==TransferState.COMPLETED})
    }
    @Test fun `a download added after drain begins does not wait for all uploads to finish`()=runTest {
        val store=Store(List(8){row("upload$it")}.associateBy{it.id}.toMutableMap())
        var downloadAt:Long?=null
        val run=TransferRunner{task->
            if(task.type.isIncoming)downloadAt=testScheduler.currentTime
            delay(if(task.type.isIncoming)10 else 2000)
            store.rows[task.id]=task.copy(state=TransferState.COMPLETED);TransferExecutor.Outcome.Completed
        }
        val job=launch{drainer(store,run).drain({false}){error("Unexpected failure")}}
        advanceTimeBy(250);store.rows["download"]=row("download",TransferType.DOWNLOAD)
        advanceUntilIdle();job.join()
        assertNotNull(downloadAt);assertTrue(downloadAt!!<2000)
    }
    @Test fun `pausing a stalled transfer cancels its underlying operation without progress events`()=runTest {
        val store=Store(mutableMapOf("upload" to row("upload")))
        var canceled=false
        val run=TransferRunner{task->try{delay(60_000);TransferExecutor.Outcome.Completed}finally{canceled=true}}
        val job=launch{drainer(store,run).drain({false}){error("Unexpected failure")}}
        advanceTimeBy(100);store.rows["upload"]=store.rows.getValue("upload").copy(state=TransferState.PAUSED)
        advanceTimeBy(500);runCurrent()
        assertTrue(canceled);assertTrue(job.isCompleted);assertEquals(TransferState.PAUSED,store.rows.getValue("upload").state)
    }
    @Test fun `opening the app does not requeue a transfer owned by a live background worker`()=runTest {
        val store=Store(mutableMapOf("upload" to row("upload")))
        lateinit var dao:TransferDao
        dao=fake{method,args->when(method){
            "queuedLane"->store.rows.values.filter{it.state==TransferState.QUEUED}
            "claim"->{val id=args[0] as String;store.rows[id]=store.rows.getValue(id).copy(state=TransferState.RUNNING);1}
            "requeueRunningExcept"->{val keep=(args[1] as List<*>).toSet();store.rows.replaceAll{_,v->if(v.state==TransferState.RUNNING&&v.id !in keep)v.copy(state=TransferState.QUEUED)else v};Unit}
            else->error(method)
        }}
        val owned=TransferActivityRegistry.claim(dao,0,0)!!
        try{TransferActivityRegistry.recover(dao,100);assertEquals(TransferState.RUNNING,store.rows.getValue("upload").state)}
        finally{TransferActivityRegistry.release(owned.id)}
        TransferActivityRegistry.recover(dao,200);assertEquals(TransferState.QUEUED,store.rows.getValue("upload").state)
    }

}
