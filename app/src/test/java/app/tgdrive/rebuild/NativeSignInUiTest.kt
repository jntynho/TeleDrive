package app.tgdrive.rebuild

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.crypto.CredentialCipher
import com.infinity.drive.core.telegram.*
import com.infinity.drive.domain.model.*
import com.infinity.drive.domain.repository.*
import com.infinity.drive.di.appModules
import com.infinity.drive.presentation.onboarding.*
import com.infinity.drive.presentation.platform.ProvidePlatformActions
import com.infinity.drive.presentation.theme.DriveInfinityTheme
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.*
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.*
import java.lang.reflect.Proxy
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class NativeSignInUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityController<ComponentActivity>
    private lateinit var model:OnboardingViewModel
    private val auth=MutableStateFlow<TelegramAuthState>(TelegramAuthState.WaitingForPhoneNumber)
    private val grants=mutableSetOf<com.infinity.drive.core.permissions.AppPermission>()
    private val requested=mutableListOf<List<com.infinity.drive.core.permissions.AppPermission>>()
    private var fileAccessOpened=false
    private var storedCredentials=true
    private var email:String?=null
    private var phone:String?=null
    private var code:String?=null
    private var password:String?=null
    @Before fun start(){
        val country=Country("GB","United Kingdom","44")
        val repo=Proxy.newProxyInstance(TelegramAuthRepository::class.java.classLoader,arrayOf(TelegramAuthRepository::class.java)){_,method,args->
            when(method.name){
                "getAuthState"->auth
                "getConnectionState"->MutableStateFlow(TelegramConnectionState.READY)
                "hasCredentials"->flowOf(true)
                "startFromStoredCredentials"->AppResult.Success(storedCredentials)
                "countries"->AppResult.Success(CountryList(listOf(country,Country("FR","France","33")),country))
                "submitPhoneNumber"->{phone=args!![0] as String;auth.value=TelegramAuthState.WaitingForCode(phone!!,CodeDeliveryChannel.TELEGRAM_APP,5,60);AppResult.Success(Unit)}
                "requestQrCodeAuthentication"->{auth.value=TelegramAuthState.WaitingForQrScan("tg://login?token=design-preview");AppResult.Success(Unit)}
                "submitEmailAddress"->{email=args!![0] as String;auth.value=TelegramAuthState.WaitingForEmailCode(email!!,6);AppResult.Success(Unit)}
                "submitEmailCode"->{auth.value=TelegramAuthState.Ready;AppResult.Success(Unit)}
                "submitCode"->{code=args!![0] as String;AppResult.Success(Unit)}
                "submitPassword"->{password=args!![0] as String;AppResult.Success(Unit)}
                else->error("Unexpected auth method ${method.name}")
            }
        } as TelegramAuthRepository
        val koin=startKoin{
            androidContext(RuntimeEnvironment.getApplication());workManagerFactory()
            modules(appModules+module{
                single<TelegramAuthRepository>{repo}
                single<com.infinity.drive.core.permissions.PermissionChecker>{object:com.infinity.drive.core.permissions.PermissionChecker{
                    override fun isGranted(permission:com.infinity.drive.core.permissions.AppPermission)=permission in grants
                    override fun hasAllFilesAccess()=false
                    override fun statuses()=com.infinity.drive.core.permissions.AppPermission.entries.associateWith{it in grants}
                    override fun missingCritical()=com.infinity.drive.core.permissions.AppPermission.entries.filter{it.critical}
                    override fun isRequestable(permission:com.infinity.drive.core.permissions.AppPermission)=true
                }}
                single<CredentialCipher>{object:CredentialCipher{override fun encrypt(plaintext:ByteArray)=plaintext.copyOf();override fun decrypt(ciphertext:ByteArray)=ciphertext.copyOf()}}
            })
        }.koin
        model=koin.get()
        activity=Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity.get().setContent{ProvidePlatformActions{
            androidx.compose.runtime.CompositionLocalProvider(
                com.infinity.drive.presentation.platform.LocalPermissionRequester provides com.infinity.drive.presentation.platform.PermissionRequester{permissions,done->requested.add(permissions);grants.addAll(permissions);done()},
                com.infinity.drive.presentation.platform.LocalSystemScreens provides object:com.infinity.drive.presentation.platform.SystemScreens{
                    override fun openAppSettings()=Unit
                    override fun openAllFilesAccess(){fileAccessOpened=true}
                }
            ){DriveInfinityTheme{OnboardingScreen({},{},model)}}
        }}
    }
    @After fun close(){
        if(::activity.isInitialized)activity.pause().stop().destroy()
        if(::model.isInitialized)ViewModelStore().apply{put("auth",model);clear()}
        stopKoin()
    }
    private fun capture(name:String){
        compose.waitForIdle()
        val file=File("build/design-previews/native-auth-$name.png").apply{parentFile.mkdirs()}
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun nativePhoneAndCodeEditorsSupportPasteAndImeSubmissionWithoutAnEmbeddedKeyboard(){
        compose.onNodeWithText("Get started").assertIsDisplayed()
        compose.onNodeWithContentDescription("Use light theme").assertDoesNotExist()
        compose.onNodeWithContentDescription("Use dark theme").assertDoesNotExist()
        val welcomeBounds=compose.onRoot().fetchSemanticsNode().boundsInRoot
        val actionBounds=compose.onNodeWithText("Get started").fetchSemanticsNode().boundsInRoot
        assertTrue("Welcome action should sit in the lower half",actionBounds.top>welcomeBounds.center.y)
        compose.onNodeWithContentDescription("TeleDrive app icon").assertIsDisplayed()
        capture("welcome")
        compose.onNodeWithText("Get started").performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText("United Kingdom",substring=false).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("paste",ignoreCase=true).assertDoesNotExist()
        compose.onNodeWithContentDescription("delete",ignoreCase=true).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertIsFocused();capture("phone")
        compose.onNode(hasSetTextAction()).performTextInput("٧٧٠٠٩٠٠١٢٣")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5000){compose.onAllNodesWithText("Check Telegram").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("+447700900123",phone)
        compose.onNodeWithContentDescription("Verification code").performTextInput("١٢٣")
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        val button=compose.onNodeWithContentDescription("Verify",substring=false).fetchSemanticsNode().boundsInRoot
        assertTrue("Verify button outside viewport: $button / $root",button.bottom<=root.bottom)
        capture("code")
        compose.onNodeWithContentDescription("Verification code").performTextInput("٤٥")
        compose.onNodeWithContentDescription("Verification code").performImeAction()
        compose.waitUntil(5000){code!=null}
        assertEquals("12345",code)
    }
    @Test fun passwordUsesASystemPasswordEditorAndPreservesUnicode(){
        compose.runOnIdle{auth.value=TelegramAuthState.WaitingForPassword(null)}
        compose.waitUntil(5000){compose.onNodeWithText("Your password").isDisplayed()}
        compose.mainClock.advanceTimeBy(1000)
        capture("password")
        compose.onNode(hasSetTextAction()).performTextInput(" A字🔐 ")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5000){password!=null}
        assertEquals(" A字🔐 ",password)
    }

    @Test fun emailSignInUsesNativeEditorsAndReachesOptionalPermissions(){
        compose.runOnIdle{auth.value=TelegramAuthState.WaitingForEmailAddress}
        compose.waitUntil(5000){compose.onNodeWithText("Login email").isDisplayed()}
        capture("email")
        compose.onNode(hasSetTextAction()).performTextInput("alex@example.com")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(5000){compose.onNodeWithText("Enter email code").isDisplayed()}
        assertEquals("alex@example.com",email)
        capture("email-code")
        compose.onNodeWithContentDescription("Verification code").performTextInput("١٢٣٤٥٦")
        compose.onNodeWithContentDescription("Verification code").performImeAction()
        compose.waitUntil(5000){compose.onNodeWithText("Permissions",substring=false).isDisplayed()}
        compose.onNodeWithText("Continue",substring=false).assertIsEnabled()
        compose.onNodeWithText("You can continue without granting access.").assertIsDisplayed()
        capture("permissions")
    }

    @Test fun apiFallbackAndQrKeepNativeInputsAndReadableControls(){
        storedCredentials=false
        compose.onNodeWithText("Get started").performClick()
        compose.waitUntil(5000){compose.onNodeWithText("API access",substring=false).isDisplayed()}
        capture("api")
        compose.onNodeWithContentDescription("Back").performClick()
        storedCredentials=true
        compose.onNodeWithText("Get started").performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText("United Kingdom").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Sign in by QR code",substring=false).performClick()
        compose.waitUntil(5000){model.uiState.value.qrLink!=null}
        compose.mainClock.advanceTimeBy(1000)
        capture("qr")
    }

    @Test fun permissionsAskOnlyForTheSelectedAccessAndDoNotBlockContinue(){
        compose.runOnIdle{auth.value=TelegramAuthState.Ready}
        compose.waitUntil(5000){compose.onNodeWithText("Permissions",substring=false).isDisplayed()}
        compose.onNodeWithText("Continue",substring=false).assertIsEnabled()
        compose.onNode(hasText("Photos and videos") and hasClickAction()).performClick()
        assertEquals(listOf(com.infinity.drive.core.permissions.AppPermission.MEDIA_IMAGES,com.infinity.drive.core.permissions.AppPermission.MEDIA_VIDEO),requested.single())
        compose.onNodeWithText("Allowed",substring=false).assertIsDisplayed()
        compose.onNode(hasText("Notifications",substring=false) and hasClickAction()).performClick()
        assertEquals(listOf(com.infinity.drive.core.permissions.AppPermission.NOTIFICATIONS),requested.last())
        compose.onNode(hasText("File access",substring=false) and hasClickAction()).performClick()
        assertTrue(fileAccessOpened)
        compose.onNodeWithText("Continue",substring=false).assertIsEnabled()
    }

    @Test @Config(qualifiers="w320dp-h568dp-xhdpi")
    fun compactScreenKeepsWelcomeActionAndNativeFormNavigationReachable(){
        compose.onNodeWithText("Get started").assertIsDisplayed().performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText("United Kingdom").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Continue",substring=false).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("7700900123")
        compose.onNodeWithContentDescription("Continue",substring=false).performClick()
        compose.waitUntil(5000){compose.onNodeWithContentDescription("Verification code").isDisplayed()}
        compose.onNodeWithContentDescription("Verify",substring=false).assertIsDisplayed()
    }

}
