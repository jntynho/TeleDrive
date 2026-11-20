package app.tgdrive.rebuild

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.infinity.drive.core.common.AppError
import com.infinity.drive.core.common.AppResult
import com.infinity.drive.core.telegram.TelegramAuthState
import com.infinity.drive.domain.model.DriveChannel
import com.infinity.drive.domain.repository.ChannelRepository
import com.infinity.drive.presentation.onboarding.OnboardingStep
import com.infinity.drive.presentation.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class OnboardingDriveTest {
    private inline fun <reified T> fake(crossinline answer: (String) -> Any? = { error(it) }): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            answer(method.name)
        } as T

    private val existing = DriveChannel(-1001L, "DriveInfinity Photos", 1, 1, 42, emptySet(), null, false, false, 0)

    private fun model(channels: ChannelRepository) = OnboardingViewModel(
        fake { MutableStateFlow<TelegramAuthState>(TelegramAuthState.WaitingForPhoneNumber) },
        fake(), fake(), fake(), channels, fake(), fake { true }
    )

    @Test fun discoveryFailureOffersRetryWithoutCreatingADrive() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var discovered: AppResult<List<DriveChannel>> = AppResult.Failure(AppError.Timeout())
        var creations = 0
        val model = model(fake { method ->
            when (method) {
                "refresh" -> discovered
                "create" -> { creations++; AppResult.Success(existing) }
                else -> error(method)
            }
        })
        try {
            model.onPermissionsResolved()
            runCurrent()
            assertEquals("Discovery failed before Telegram returned a drive list", 0, creations)
            assertEquals(OnboardingStep.CHANNEL_SELECT, model.uiState.value.step)
            assertNotNull(model.uiState.value.error)
            assertFalse(model.uiState.value.working)

            discovered = AppResult.Success(listOf(existing))
            model.retryDriveSetup()
            runCurrent()
            assertEquals(existing.chatId, model.uiState.value.selectedChatId)
            assertNull(model.uiState.value.error)
            assertEquals(0, creations)
        } finally {
            ViewModelStore().apply { put("onboarding", model); clear() }
            Dispatchers.resetMain()
        }
    }

    @Test fun newlyCreatedDriveIsSelectableWhenDiscoveryIsDelayed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = model(fake { method ->
            when (method) {
                "refresh" -> AppResult.Success(emptyList<DriveChannel>())
                "create" -> AppResult.Success(existing)
                else -> error(method)
            }
        })
        try {
            model.onPermissionsResolved()
            runCurrent()
            assertEquals(listOf(existing), model.uiState.value.channels)
            assertEquals(existing.chatId, model.uiState.value.selectedChatId)
            assertTrue(model.uiState.value.channelCreated)
        } finally {
            ViewModelStore().apply { put("onboarding", model); clear() }
            Dispatchers.resetMain()
        }
    }

    @Test fun failedDriveSelectionKeepsSetupOnTheSelectedDriveForRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var selection: AppResult<Unit> = AppResult.Failure(AppError.NotFound)
        val model = model(fake { method ->
            when (method) {
                "refresh" -> AppResult.Success(listOf(existing))
                "switchTo" -> selection
                else -> error(method)
            }
        })
        try {
            model.onPermissionsResolved()
            runCurrent()
            model.confirmChannel()
            runCurrent()
            assertEquals(OnboardingStep.CHANNEL_SELECT, model.uiState.value.step)
            assertNotNull(model.uiState.value.error)
            assertFalse(model.uiState.value.working)

            selection = AppResult.Success(Unit)
            model.confirmChannel()
            runCurrent()
            assertEquals(OnboardingStep.BACKUP_SETUP, model.uiState.value.step)
            assertNull(model.uiState.value.error)
        } finally {
            ViewModelStore().apply { put("onboarding", model); clear() }
            Dispatchers.resetMain()
        }
    }
}
