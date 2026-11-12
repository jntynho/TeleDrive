package app.tgdrive.rebuild

import coil3.asImage
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.infinity.drive.core.crypto.CredentialCipher
import com.infinity.drive.data.local.dao.FileDao
import com.infinity.drive.data.local.entity.FileEntity
import com.infinity.drive.domain.model.FileCategory
import com.infinity.drive.domain.model.BackupState
import org.koin.core.context.GlobalContext
import com.infinity.drive.core.files.PendingShare
import com.infinity.drive.di.appModules
import com.infinity.drive.domain.model.UserPreferences
import com.infinity.drive.domain.repository.SettingsRepository
import com.infinity.drive.presentation.DriveInfinityApp
import com.infinity.drive.presentation.platform.ProvidePlatformActions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Website-only fixture. No real account, uploaded file, artwork or personal data is used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WebsiteScreenshotsTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    private val images=mutableMapOf<String,ByteArray>()
    private fun artwork(index:Int):ByteArray {
        val bitmap=Bitmap.createBitmap(400,500,Bitmap.Config.ARGB_8888)
        val c=android.graphics.Canvas(bitmap);val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val sky=listOf(0xff6f9fae.toInt(),0xffccaa85.toInt(),0xff698f89.toInt(),0xff9ea99b.toInt(),0xffa2b4c4.toInt(),0xffa88e7b.toInt())[index%6]
        p.shader=android.graphics.LinearGradient(0f,0f,400f,500f,sky,0xffeee3ca.toInt(),android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,400f,500f,p);p.shader=null;p.color=0xfff5e6c6.toInt();c.drawCircle(280f,130f,45f,p)
        p.color=if(index%2==0)0xff647c6a.toInt()else 0xffb38e6e.toInt()
        val first=android.graphics.Path().apply{moveTo(-50f,350f);cubicTo(100f,170f,190f,450f,450f,260f);lineTo(450f,520f);lineTo(-50f,520f);close()}
        c.drawPath(first,p);p.color=if(index%2==0)0xff334e4b.toInt()else 0xff7e6654.toInt()
        val second=android.graphics.Path().apply{moveTo(-50f,460f);cubicTo(130f,300f,260f,430f,450f,370f);lineTo(450f,520f);lineTo(-50f,520f);close()}
        c.drawPath(second,p)
        return java.io.ByteArrayOutputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray()}
    }
    @Before fun start(){
        val context=RuntimeEnvironment.getApplication()
        val app=startKoin{
            androidContext(context);workManagerFactory()
            modules(appModules+module{
                single<CredentialCipher>{object:CredentialCipher{
                    override fun encrypt(plaintext:ByteArray)=plaintext.copyOf()
                    override fun decrypt(ciphertext:ByteArray)=ciphertext.copyOf()
                }}
                single<com.infinity.drive.core.media.ThumbnailStore>{object:com.infinity.drive.core.media.ThumbnailStore{
                    override suspend fun thumbnailBytes(fileId:String)=images[fileId]
                    override suspend fun cachedThumbnailBytes(fileId:String)=images[fileId]
                    override suspend fun uploadThumbnailFile(fileId:String):File?=null
                }}
            })
        }
        runBlocking{
            app.koin.get<SettingsRepository>().update{UserPreferences(onboardingComplete=true,updateCheckEnabled=false,storageChatId=-1001234567890)}
            val dao=app.koin.get<FileDao>()
            val now=java.time.Instant.parse("2026-11-12T09:41:00Z").toEpochMilli()
            val entries=listOf("A quiet morning.jpg" to FileCategory.IMAGE,"Weekend plans.pdf" to FileCategory.DOCUMENT,"Blue hour.mp3" to FileCategory.AUDIO,"A little escape.mp4" to FileCategory.VIDEO)+
                (1..11).map{"Summer light $it.jpg" to FileCategory.IMAGE}+listOf("Slow Sunday.flac" to FileCategory.AUDIO,"Field notes.m4a" to FileCategory.AUDIO,"Coastline.mp3" to FileCategory.AUDIO,"Good days.mp3" to FileCategory.AUDIO,"Studio sketches.m4a" to FileCategory.AUDIO)
            entries.forEachIndexed{index,(name,category)->
                val id="website-demo-$index"
                if(category!=FileCategory.DOCUMENT)images[id]=artwork(index)
                dao.upsert(FileEntity(id=id,folderId=null,name=name,category=category,sizeBytes=if(category==FileCategory.VIDEO)4_810_000_000 else if(category==FileCategory.AUDIO)28_000_000 else if(category==FileCategory.DOCUMENT)2_400_000 else 4_800_000,
                    mimeType=when(category){FileCategory.IMAGE->"image/jpeg";FileCategory.VIDEO->"video/mp4";FileCategory.AUDIO->"audio/mpeg";else->"application/pdf"},
                    localPath=null,contentHash=null,chatId=-1001234567890,messageId=index+1L,remoteFileId="demo-$index",remoteUniqueId="demo-$index",backupState=BackupState.BACKED_UP,
                    isFavorite=index%3==0,durationMs=if(category==FileCategory.AUDIO)243000 else if(category==FileCategory.VIDEO)165000 else null,
                    createdAt=now-index*3_600_000L,modifiedAt=now-index*3_600_000L,addedAt=now-index*3_600_000L))
            }
        }
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val share=app.koin.get<PendingShare>()
        activity.get().setContent{
            val handler=coil3.compose.AsyncImagePreviewHandler { request ->
                val id=(request.data as? com.infinity.drive.core.media.ThumbnailModel)?.fileId
                val bytes=images[id]
                if(bytes==null)coil3.ColorImage(android.graphics.Color.TRANSPARENT)
                else android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size).asImage()
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalInspectionMode provides true,
                coil3.compose.LocalAsyncImagePreviewHandler provides handler
            ){ProvidePlatformActions{androidx.compose.runtime.CompositionLocalProvider(com.infinity.drive.presentation.platform.LocalHiddenContentAccess provides null){DriveInfinityApp(share)}}}
        }
    }
    @After fun close(){if(::activity.isInitialized)activity.pause().stop().destroy();stopKoin()}
    @Test fun exportFourRealScreensWithFictionalSampleData(){
        compose.waitUntil(15000){compose.onAllNodesWithText("Your library").fetchSemanticsNodes().isNotEmpty()}
        capture("home")
        compose.onNodeWithTag("navigation-gallery").performClick();compose.waitUntil(15000){compose.onAllNodesWithContentDescription("A quiet morning.jpg",substring=true).fetchSemanticsNodes().isNotEmpty()};capture("gallery")
        compose.onNodeWithTag("navigation-audio").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("Blue hour",substring=false).fetchSemanticsNodes().isNotEmpty()}
        capture("audio")
        compose.onNodeWithTag("navigation-settings").performClick();compose.waitForIdle();capture("settings")
    }
    private fun capture(name:String){
        compose.waitForIdle()
        val settleUntil=System.nanoTime()+800_000_000L
        compose.waitUntil(3000){System.nanoTime()>=settleUntil}
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(1000))
        compose.mainClock.advanceTimeBy(1500);compose.waitForIdle()
        val file=File("../site/tools/generated/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
