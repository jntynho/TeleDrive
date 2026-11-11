// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
package com.infinity.drive.presentation.onboarding

import app.tgdrive.ui.elasticVerticalScroll as verticalScroll
import app.tgdrive.ui.ElasticLazyColumn as LazyColumn
import androidx.compose.foundation.relocation.bringIntoViewRequester
import app.tgdrive.ui.DriveIcons
import com.infinity.drive.presentation.input.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.setText
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.infinity.drive.resources.Res
import com.infinity.drive.resources.app_name
import com.infinity.drive.resources.backup
import com.infinity.drive.resources.common_try_again
import com.infinity.drive.resources.onboarding_action_continue
import com.infinity.drive.resources.onboarding_allow_all_files
import com.infinity.drive.resources.onboarding_allow_media
import com.infinity.drive.resources.onboarding_api_access
import com.infinity.drive.resources.onboarding_api_hash
import com.infinity.drive.resources.onboarding_api_id
import com.infinity.drive.resources.onboarding_channel_files_in_channel
import com.infinity.drive.resources.onboarding_choose_api_dev_tools
import com.infinity.drive.resources.onboarding_choose_country
import com.infinity.drive.resources.onboarding_choose_drive
import com.infinity.drive.resources.onboarding_choose_what_back_change
import com.infinity.drive.resources.onboarding_code
import com.infinity.drive.resources.onboarding_code_sent
import com.infinity.drive.resources.onboarding_code_via_call
import com.infinity.drive.resources.onboarding_code_via_email
import com.infinity.drive.resources.onboarding_code_via_flash_call
import com.infinity.drive.resources.onboarding_code_via_fragment
import com.infinity.drive.resources.onboarding_code_via_missed_call
import com.infinity.drive.resources.onboarding_code_via_sms
import com.infinity.drive.resources.onboarding_code_via_sms_phrase
import com.infinity.drive.resources.onboarding_code_via_sms_word
import com.infinity.drive.resources.onboarding_code_via_telegram
import com.infinity.drive.resources.onboarding_continue
import com.infinity.drive.resources.onboarding_continue_without
import com.infinity.drive.resources.onboarding_copy_api_keys
import com.infinity.drive.resources.onboarding_country
import com.infinity.drive.resources.onboarding_drive_created_desc
import com.infinity.drive.resources.onboarding_drive_ready
import com.infinity.drive.resources.onboarding_email_code_title
import com.infinity.drive.resources.onboarding_email_continue
import com.infinity.drive.resources.onboarding_email_description
import com.infinity.drive.resources.onboarding_email_label
import com.infinity.drive.resources.onboarding_email_title
import com.infinity.drive.resources.onboarding_enter_code
import com.infinity.drive.resources.onboarding_enter_password
import com.infinity.drive.resources.onboarding_files_access_covers_documents
import com.infinity.drive.resources.onboarding_files_live_own_private
import com.infinity.drive.resources.onboarding_get_api_keys
import com.infinity.drive.resources.onboarding_get_started
import com.infinity.drive.resources.onboarding_got
import com.infinity.drive.resources.onboarding_include_country_code_like
import com.infinity.drive.resources.onboarding_looking_for_drives
import com.infinity.drive.resources.onboarding_my_telegram_org
import com.infinity.drive.resources.onboarding_no_country_match
import com.infinity.drive.resources.onboarding_no_drive_yet
import com.infinity.drive.resources.onboarding_number
import com.infinity.drive.resources.onboarding_one_time_setup_keys
import com.infinity.drive.resources.onboarding_open_dev_page
import com.infinity.drive.resources.onboarding_opening
import com.infinity.drive.resources.onboarding_password
import com.infinity.drive.resources.onboarding_phone_number
import com.infinity.drive.resources.onboarding_phone_number_telegram_account
import com.infinity.drive.resources.onboarding_pick_drive_desc
import com.infinity.drive.resources.onboarding_qr_code_description
import com.infinity.drive.resources.onboarding_qr_confirm_instead
import com.infinity.drive.resources.onboarding_qr_description
import com.infinity.drive.resources.onboarding_qr_open_failed
import com.infinity.drive.resources.onboarding_qr_open_telegram
import com.infinity.drive.resources.onboarding_qr_same_device_description
import com.infinity.drive.resources.onboarding_qr_scan_instead
import com.infinity.drive.resources.onboarding_qr_sign_in
import com.infinity.drive.resources.onboarding_qr_step_1
import com.infinity.drive.resources.onboarding_qr_step_2
import com.infinity.drive.resources.onboarding_qr_step_3
import com.infinity.drive.resources.onboarding_qr_title
import com.infinity.drive.resources.onboarding_qr_use_phone
import com.infinity.drive.resources.onboarding_qr_waiting
import com.infinity.drive.resources.onboarding_resend_code
import com.infinity.drive.resources.onboarding_search_country
import com.infinity.drive.resources.onboarding_section_behavior
import com.infinity.drive.resources.onboarding_section_folders
import com.infinity.drive.resources.onboarding_send_code
import com.infinity.drive.resources.onboarding_set_up_proxy
import com.infinity.drive.resources.onboarding_sign
import com.infinity.drive.resources.onboarding_single_drive_desc
import com.infinity.drive.resources.onboarding_step
import com.infinity.drive.resources.onboarding_step_counter
import com.infinity.drive.resources.onboarding_storage_access
import com.infinity.drive.resources.onboarding_driveinfinity_needs_media_files
import com.infinity.drive.resources.onboarding_telegram_blocked
import com.infinity.drive.resources.onboarding_toggle_auto_backup
import com.infinity.drive.resources.onboarding_toggle_camera
import com.infinity.drive.resources.onboarding_toggle_movies
import com.infinity.drive.resources.onboarding_toggle_pictures
import com.infinity.drive.resources.onboarding_toggle_wifi_only
import com.infinity.drive.resources.onboarding_two_step_verification
import com.infinity.drive.resources.onboarding_verify
import com.infinity.drive.resources.onboarding_where_i_get_these
import com.infinity.drive.core.permissions.AppPermission
import com.infinity.drive.core.permissions.PermissionChecker
import com.infinity.drive.presentation.platform.LocalPermissionRequester
import com.infinity.drive.presentation.platform.LocalPlatformCapabilities
import com.infinity.drive.presentation.platform.LocalSystemScreens
import com.infinity.drive.presentation.platform.LocalTelegramLinkOpener
import com.infinity.drive.resources.ic_launcher_monochrome
import org.koin.compose.koinInject
import com.infinity.drive.core.telegram.CodeDeliveryChannel
import com.infinity.drive.domain.model.Country
import com.infinity.drive.domain.model.DriveChannel
import com.infinity.drive.presentation.common.UiText
import com.infinity.drive.presentation.common.resolve
import com.infinity.drive.presentation.components.ChannelAvatar
import com.infinity.drive.presentation.components.QrCode

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    onOpenProxy: () -> Unit,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.step == OnboardingStep.DONE) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }

    val initialDark=MaterialTheme.colorScheme.background.luminance()<.5f
    var dark by rememberSaveable{mutableStateOf(initialDark)}
    val canvas=if(MaterialTheme.colorScheme.background==Color.Black)Color.Black else Color(0xFF101216)
    val colors=if(dark)app.tgdrive.ui.DriveColors.copy(background=canvas,surface=canvas,surfaceContainerHigh=Color(0xFF20242A),primary=Color(0xFF87B8FF),onPrimary=Color(0xFF091625))
        else lightColorScheme(primary=Color(0xFF1765B9),background=Color(0xFFF7F8FA),surface=Color(0xFFF7F8FA),surfaceContainerHigh=Color(0xFFEAEDF2))
    val inputs=remember{mutableMapOf<OnboardingStep,InlineInputController>()}
    val input=inputs.getOrPut(state.step){InlineInputController()}
    val formStates=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val canBack=state.step in setOf(OnboardingStep.API_CREDENTIALS,OnboardingStep.PHONE,OnboardingStep.CODE,OnboardingStep.EMAIL_ADDRESS,OnboardingStep.EMAIL_CODE,OnboardingStep.PASSWORD)
    com.infinity.drive.presentation.common.AppBackHandler(canBack){if(!state.working)viewModel.backFromSignIn()}
    com.infinity.drive.presentation.theme.PlatformThemeSideEffects(dark)
    MaterialTheme(colorScheme=colors){
        Scaffold(containerColor=colors.background){padding->
            Column(Modifier.fillMaxSize().padding(top=padding.calculateTopPadding()).navigationBarsPadding()){
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
                    if(canBack)IconButton(viewModel::backFromSignIn,enabled=!state.working){Icon(DriveIcons.ArrowBack,"Back")}
                    else Icon(DriveIcons.CloudQueue,null,Modifier.padding(start=12.dp,end=10.dp).size(24.dp),tint=colors.primary)
                    Text(when(state.step){OnboardingStep.WELCOME->"TeleDrive";OnboardingStep.PHONE->"Sign in";OnboardingStep.CODE,OnboardingStep.EMAIL_CODE->"Verification";OnboardingStep.PASSWORD->"Two-step verification";else->"Set up TeleDrive"},
                        Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    IconButton({dark=!dark;viewModel.setSignInTheme(dark)}){Icon(DriveIcons.AutoAwesome,if(dark)"Use light theme"else"Use dark theme")}
                }
                BoxWithConstraints(Modifier.weight(1f)){
                    val compact=maxHeight<600.dp
                    val body:@Composable (Modifier)->Unit={modifier->
                        val scroll=rememberScrollState()
                        LaunchedEffect(state.step){scroll.scrollTo(0)}
                        Column(modifier.verticalScroll(scroll)){
                            AnimatedContent(state.step,transitionSpec={
                                (com.infinity.drive.presentation.navigation.NavigationTransitions.enter(targetState.ordinal>=initialState.ordinal)
                                    togetherWith com.infinity.drive.presentation.navigation.NavigationTransitions.exit(targetState.ordinal>=initialState.ordinal))
                                    .using(SizeTransform(clip=true){_,_->snap()})
                            },label="sign-in-step"){step->
                                val pageInput=inputs.getOrPut(step){InlineInputController()}
                                CompositionLocalProvider(LocalInlineInput provides pageInput){
                                    formStates.SaveableStateProvider(step.name){
                        when (step) {
                            OnboardingStep.WELCOME -> WelcomeStep(onContinue = viewModel::start)
                            OnboardingStep.API_CREDENTIALS -> CredentialsStep(
                                working = state.working,
                                error = state.error,
                                onSubmit = viewModel::submitCredentials
                            )

                            OnboardingStep.EMAIL_ADDRESS -> EmailAddressStep(
                                working = state.working,
                                error = state.error,
                                onSubmit = viewModel::submitEmailAddress
                            )

                            OnboardingStep.EMAIL_CODE -> EmailCodeStep(
                                working = state.working,
                                error = state.error,
                                emailPattern = state.codePhoneNumber,
                                codeLength = state.codeLength,
                                onSubmit = viewModel::submitEmailCode
                            )

                            OnboardingStep.PHONE -> if (state.qrMode) {
                                QrStep(
                                    working = state.working,
                                    error = state.error,
                                    link = state.qrLink,
                                    onCancel = viewModel::cancelQrLogin
                                )
                            } else PhoneStep(
                                countries = state.countries,
                                selectedCountry = state.selectedCountry,
                                countryLoadState = state.countryLoadState,
                                onLoadCountries = viewModel::loadCountries,
                                onSelectCountry = viewModel::selectCountry,
                                onUseQrCode = viewModel::startQrLogin,
                                onOpenProxy = onOpenProxy,
                                working = state.working,
                                error = state.error,
                                registrationRequired = state.registrationRequired,
                                onSubmit = viewModel::submitPhone
                            )

                            OnboardingStep.CODE -> CodeStep(
                                working = state.working,
                                error = state.error,
                                phoneNumber = state.codePhoneNumber,
                                channel = state.codeChannel,
                                codeLength = state.codeLength,
                                onSubmit = viewModel::submitCode,
                                onResend = viewModel::resendCode
                            )

                            OnboardingStep.PASSWORD -> PasswordStep(
                                working = state.working,
                                error = state.error,
                                hint = state.passwordHint,
                                onSubmit = viewModel::submitPassword
                            )

                            OnboardingStep.PERMISSIONS -> PermissionsStep(
                                working = state.working,
                                onResolved = viewModel::onPermissionsResolved
                            )

                            OnboardingStep.CHANNEL_SELECT -> ChannelSelectStep(
                                channels = state.channels,
                                selectedChatId = state.selectedChatId,
                                justCreated = state.channelCreated,
                                working = state.working,
                                error = state.error,
                                onSelect = viewModel::selectChannel,
                                onRetry = viewModel::retryDriveSetup,
                                onContinue = viewModel::confirmChannel
                            )

                            OnboardingStep.BACKUP_SETUP -> BackupSetupStep(
                                state = state,
                                onDcim = viewModel::setBackupDcim,
                                onPictures = viewModel::setBackupPictures,
                                onMovies = viewModel::setBackupMovies,
                                onAutoBackup = viewModel::setAutoBackup,
                                onWifiOnly = viewModel::setWifiOnly,
                                onFinish = viewModel::finishSetup,
                                finishing = state.finishing
                            )

                            OnboardingStep.DONE -> Unit
                        }
                                    }
                                }
                            }
                        }
                    }
                    val customEntry=state.step in setOf(OnboardingStep.API_CREDENTIALS,OnboardingStep.PHONE,OnboardingStep.CODE,OnboardingStep.EMAIL_ADDRESS,OnboardingStep.EMAIL_CODE,OnboardingStep.PASSWORD)&&!(state.step==OnboardingStep.PHONE&&state.qrMode)
                    val controls:@Composable (Modifier)->Unit={modifier->
                        if(customEntry){
                            Column(modifier.padding(horizontal=24.dp).padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                                InlineActionButton(input,when(state.step){OnboardingStep.CODE,OnboardingStep.EMAIL_CODE->"Verify";OnboardingStep.PASSWORD->"Sign in";else->"Continue"})
                                Box(Modifier.fillMaxWidth().height(if(compact)220.dp else 256.dp),contentAlignment=Alignment.BottomCenter){InlineKeyboard(input,compact=compact)}
                            }
                        }
                    }
                    if(maxWidth>600.dp&&maxWidth>maxHeight){
                        Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){body(Modifier.weight(1f).fillMaxHeight());controls(Modifier.weight(1f))}
                    }else Column(Modifier.fillMaxSize()) {body(Modifier.weight(1f).fillMaxWidth());controls(Modifier.fillMaxWidth())}
                }
            }
        }
    }
}

/**
 * Shown only when the account already holds more than one drive, so the user
 * says which one this device opens before anything is indexed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelSelectStep(
    channels: List<DriveChannel>,
    selectedChatId: Long?,
    justCreated: Boolean,
    working: Boolean,
    error: UiText?,
    onSelect: (Long) -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit
) {
    OnboardingPage(
        icon = DriveIcons.CloudQueue,
        title = when {
            channels.isEmpty() && error != null ->
                stringResource(Res.string.onboarding_no_drive_yet)

            justCreated -> stringResource(Res.string.onboarding_drive_ready)
            else -> stringResource(Res.string.onboarding_choose_drive)
        },
        description = when {
            channels.isEmpty() && error != null -> error.resolve()

            justCreated -> stringResource(Res.string.onboarding_drive_created_desc)

            channels.size > 1 -> stringResource(Res.string.onboarding_pick_drive_desc)

            else -> stringResource(Res.string.onboarding_single_drive_desc)
        }
    ) {
        channels.forEach { channel ->
            val selected = channel.chatId == selectedChatId
            val single = channels.size == 1
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
                    .selectable(
                        selected = selected,
                        enabled = !single,
                        role = Role.RadioButton,
                        onClick = { onSelect(channel.chatId) }
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (single) {
                    ChannelAvatar(channel = channel, size = 40.dp)
                } else {
                    RadioButton(selected = selected, onClick = null)
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = channel.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            Res.string.onboarding_channel_files_in_channel,
                            channel.remoteFileCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (channels.isEmpty()) {
            BigButton(
                label = stringResource(
                    if (working) Res.string.onboarding_looking_for_drives
                    else Res.string.common_try_again
                ),
                onClick = onRetry,
                enabled = !working
            )
            return@OnboardingPage
        }
        BigButton(
            label = stringResource(
                if (working) Res.string.onboarding_opening else Res.string.onboarding_continue
            ),
            onClick = onContinue,
            enabled = selectedChatId != null && !working,
            loading = working
        )
    }
}

/**
 * Every onboarding step is laid out the same way: hero shape, title, supporting
 * line, then whatever that step needs. Only the content block differs.
 */
@Composable
private fun OnboardingPage(
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_PADDING, vertical = PAGE_VERTICAL_PADDING),
        horizontalAlignment = Alignment.Start
    ) {
        if(icon!=DriveIcons.PhoneAndroid) {
            if(icon in setOf(DriveIcons.Sms,DriveIcons.Email,DriveIcons.Lock))Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){HeroShape(icon,iconPainter)}
            else HeroShape(icon=icon,iconPainter=iconPainter)
            Spacer(Modifier.height(24.dp))
        }
        StepTitle(title=title,description=description,centered=icon in setOf(DriveIcons.Sms,DriveIcons.Email,DriveIcons.Lock))
        Spacer(Modifier.height(28.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroShape(icon: ImageVector?, iconPainter: Painter?) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        when {
            iconPainter != null -> Icon(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepTitle(title: String, description: String,centered:Boolean=false) {
    Column(Modifier.fillMaxWidth(),horizontalAlignment=if(centered)Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            text = title,
            textAlign=if(centered)TextAlign.Center else TextAlign.Start,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = description,
            textAlign=if(centered)TextAlign.Center else TextAlign.Start,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WelcomeStep(onContinue:()->Unit){
    Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)){
        com.infinity.drive.presentation.platform.LocalAppIcon.current(Modifier.size(112.dp))
        Text("Your files.\nAlways with you.",style=MaterialTheme.typography.headlineLarge)
        Text("Your own space, powered by Telegram.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(DriveIcons.Description to "Keep original files",DriveIcons.PhotoLibrary to "Browse photos and music",DriveIcons.History to "Pick up where you left off").forEach{(icon,text)->
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,Modifier.size(25.dp));Spacer(Modifier.width(16.dp));Text(text,style=MaterialTheme.typography.bodyLarge)}
        }
        Spacer(Modifier.height(12.dp))
        BigButton("Get started",onContinue)
        Text("Sign in with your Telegram account.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CredentialsStep(
    working: Boolean,
    error: UiText?,
    onSubmit: (String, String) -> Unit
) {
    var apiId by rememberSaveable { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    var showGuide by rememberSaveable { mutableStateOf(false) }

    OnboardingPage(
        icon = DriveIcons.Key,
        title = stringResource(Res.string.onboarding_api_access),
        description = stringResource(Res.string.onboarding_one_time_setup_keys)
    ) {
        TextButton(
            onClick = { showGuide = true },
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                DriveIcons.HelpOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(Res.string.onboarding_where_i_get_these))
        }
        Spacer(Modifier.height(20.dp))
        OnboardingField(
            value = apiId,
            onChange = { apiId = it.filter(Char::isDigit) },
            label = stringResource(Res.string.onboarding_api_id),
            keyboardType = KeyboardType.Number,
            enabled = !working
        )
        Spacer(Modifier.height(12.dp))
        OnboardingField(
            value = apiHash,
            onChange = { apiHash = it.trim() },
            label = stringResource(Res.string.onboarding_api_hash),
            password=true,
            enabled = !working
        )
        ErrorAndAction(
            working = working,
            error = error,
            actionLabel = stringResource(Res.string.onboarding_action_continue),
            enabled = apiId.isNotBlank() && apiHash.isNotBlank(),
            onAction = { onSubmit(apiId, apiHash) }
        )
    }

    if (showGuide) {
        ApiGuideDialog(onDismiss = { showGuide = false })
    }
}

/**
 * The way out for anyone whose network blocks Telegram outright, offered where
 * they first stall rather than buried in a screen they cannot reach yet.
 */
@Composable
private fun BlockedNetworkCard(onOpenProxy: () -> Unit) {
    TextButton(onClick=onOpenProxy,modifier=Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.onboarding_set_up_proxy))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ApiGuideDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.onboarding_get_api_keys)) },
        text = {
            Column {
                InstructionCard(number = 1) {
                    Column {
                        Text(
                            stringResource(Res.string.onboarding_open_dev_page),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = { uriHandler.openUri("https://my.telegram.org") },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(
                                DriveIcons.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(Res.string.onboarding_my_telegram_org))
                        }
                    }
                }
                InstructionCard(number = 2) {
                    Text(
                        stringResource(Res.string.onboarding_choose_api_dev_tools),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                InstructionCard(number = 3) {
                    Text(
                        stringResource(Res.string.onboarding_copy_api_keys),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(18.dp)
            ) { Text(stringResource(Res.string.onboarding_got)) }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstructionCard(number: Int, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.size(14.dp))
            Box(modifier = Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun QrStep(
    working: Boolean,
    error: UiText?,
    link: String?,
    onCancel: () -> Unit
) {
    var openFailed by remember { mutableStateOf(false) }

    /* The QR payload is a tg: link, so a Telegram app on this same device can
       confirm the login directly. Scanning is only needed across devices. */
    val telegramLinkOpener = LocalTelegramLinkOpener.current
    val canConfirmHere = telegramLinkOpener.canOpenTelegram
    /* One route at a time: showing both at once reads like two required steps. */
    var scanning by rememberSaveable(canConfirmHere) { mutableStateOf(!canConfirmHere) }

    OnboardingPage(
        icon = DriveIcons.QrCode2,
        title = stringResource(Res.string.onboarding_qr_title),
        description = if (scanning) {
            stringResource(Res.string.onboarding_qr_description)
        } else {
            stringResource(Res.string.onboarding_qr_same_device_description)
        }
    ) {
        if (scanning) {
            Surface(
                color = QR_SURFACE,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth(QR_WIDTH_FRACTION)
                    .align(Alignment.CenterHorizontally)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(QR_PADDING)) {
                    if (link == null) {
                        CircularProgressIndicator(modifier = Modifier.padding(QR_PADDING))
                    } else {
                        QrCode(
                            content = link,
                            contentDescription = stringResource(
                                Res.string.onboarding_qr_code_description
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            QrInstruction(1, stringResource(Res.string.onboarding_qr_step_1))
            QrInstruction(2, stringResource(Res.string.onboarding_qr_step_2))
            QrInstruction(3, stringResource(Res.string.onboarding_qr_step_3))
        } else {
            BigButton(
                label = stringResource(Res.string.onboarding_qr_open_telegram),
                enabled = !working && link != null,
                onClick = {
                    openFailed = link == null || !telegramLinkOpener.open(link)
                }
            )
            if (openFailed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.onboarding_qr_open_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        if (error != null) {
            Text(
                text = error.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SPINNER_SIZE),
                    strokeWidth = SPINNER_STROKE
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(Res.string.onboarding_qr_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (canConfirmHere) {
            TextButton(
                onClick = { scanning = !scanning },
                enabled = !working,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    stringResource(
                        if (scanning) {
                            Res.string.onboarding_qr_confirm_instead
                        } else {
                            Res.string.onboarding_qr_scan_instead
                        }
                    )
                )
            }
        }
        TextButton(
            onClick = onCancel,
            enabled = !working,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(stringResource(Res.string.onboarding_qr_use_phone)) }
    }
}

@Composable
private fun QrInstruction(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(QR_STEP_BADGE)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhoneStep(
    working: Boolean,
    error: UiText?,
    registrationRequired: Boolean,
    countries: List<Country>,
    selectedCountry: Country?,
    countryLoadState: CountryLoadState,
    onLoadCountries: () -> Unit,
    onSelectCountry: (Country) -> Unit,
    onUseQrCode: () -> Unit,
    onOpenProxy: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var national by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var typedBeforeCountries by rememberSaveable { mutableStateOf(false) }
    val manual = countryLoadState == CountryLoadState.FAILED || typedBeforeCountries
    val callingCode = if (manual) "" else selectedCountry?.callingCode.orEmpty()

    LaunchedEffect(Unit) { onLoadCountries() }

    OnboardingPage(
        icon = DriveIcons.PhoneAndroid,
        title = "Your phone number",
        description = stringResource(Res.string.onboarding_phone_number_telegram_account)
    ) {
        if (!manual) {
            CountryField(
                country = selectedCountry,
                loading = countryLoadState == CountryLoadState.LOADING,
                enabled = !working && countries.isNotEmpty(),
                onClick = { picking = true }
            )
            Spacer(Modifier.height(12.dp))
        }
        OnboardingField(
            value = national,
            onChange = { entry ->
                if (countryLoadState == CountryLoadState.LOADING) typedBeforeCountries = true
                national = entry.filter { it.isDigit() }
            },
            label = stringResource(Res.string.onboarding_phone_number),
            supportingText = if (manual) {
                stringResource(Res.string.onboarding_include_country_code_like)
            } else {
                null
            },
            keyboardType = KeyboardType.Phone,
            enabled = !working,
            prefix = "+$callingCode",
            onPaste={raw->
                val digits=raw.mapNotNull{ch->Character.digit(ch,10).takeIf{it>=0}?.digitToChar()}.joinToString("")
                val international=raw.trimStart().startsWith("+")||raw.trimStart().startsWith("00")
                val number=if(raw.trimStart().startsWith("00"))digits.removePrefix("00")else digits
                val country=if(international)countries.sortedByDescending{it.callingCode.length}.firstOrNull{number.startsWith(it.callingCode)}else null
                if(country!=null){onSelectCountry(country);typedBeforeCountries=false;national=number.removePrefix(country.callingCode)}
                else{if(international)typedBeforeCountries=true;national=number}
            }
        )
        ErrorAndAction(working,error,"Continue",national.isNotBlank()&&!registrationRequired){onSubmit("+$callingCode$national")}
        TextButton(
            onClick = onUseQrCode,
            enabled = !working,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = DriveIcons.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(QR_BUTTON_ICON)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.onboarding_qr_sign_in))
        }
        Spacer(Modifier.height(12.dp))
        BlockedNetworkCard(onOpenProxy = onOpenProxy)
    }

    if (picking) {
        CountryPickerSheet(
            countries = countries,
            onDismiss = { picking = false },
            onSelect = { country ->
                onSelectCountry(country)
                picking = false
            }
        )
    }
}

@Composable
private fun CountryField(country:Country?,loading:Boolean,enabled:Boolean,onClick:()->Unit){
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(horizontal=16.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(DriveIcons.Language,null,Modifier.size(22.dp));Spacer(Modifier.width(12.dp))
            Text(country?.name?:if(loading)"Loading countries…"else"Choose country",Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
            if(loading)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Icon(DriveIcons.ArrowDropDown,null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerSheet(countries:List<Country>,onDismiss:()->Unit,onSelect:(Country)->Unit){
    var query by remember{mutableStateOf("")}
    val input=remember{InlineInputController()}
    val matches=remember(countries,query){countries.filter{query.isBlank()||it.name.contains(query,true)||it.callingCode.contains(query)||it.isoCode.equals(query,true)}}
    val density=androidx.compose.ui.platform.LocalDensity.current
    val limit=with(density){androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp()*.85f}
    com.infinity.drive.presentation.components.DriveBottomSheet(onDismissRequest=onDismiss){
        val closeThen=com.infinity.drive.presentation.components.LocalSheetCloseThen.current
        CompositionLocalProvider(LocalInlineInput provides input){
            Column(Modifier.fillMaxWidth().height(limit).padding(horizontal=20.dp)){
                Text("Choose country",style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                InlineInputField(query,{query=it},"Search countries",Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f).fillMaxWidth()){
                    items(matches,key={"${it.isoCode}_${it.callingCode}"}){country->
                        Row(Modifier.fillMaxWidth().clickable{closeThen{onSelect(country)}}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(country.name,Modifier.weight(1f));Text("+${country.callingCode}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                InlineKeyboard(input,compact=true)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun codeDeliveryText(channel: CodeDeliveryChannel, target: String): String =
    when (channel) {
        CodeDeliveryChannel.TELEGRAM_APP -> stringResource(Res.string.onboarding_code_via_telegram)
        CodeDeliveryChannel.SMS -> stringResource(Res.string.onboarding_code_via_sms, target)
        CodeDeliveryChannel.SMS_WORD -> stringResource(
            Res.string.onboarding_code_via_sms_word,
            target
        )

        CodeDeliveryChannel.SMS_PHRASE ->
            stringResource(Res.string.onboarding_code_via_sms_phrase, target)

        CodeDeliveryChannel.CALL -> stringResource(Res.string.onboarding_code_via_call, target)
        CodeDeliveryChannel.FLASH_CALL ->
            stringResource(Res.string.onboarding_code_via_flash_call, target)

        CodeDeliveryChannel.MISSED_CALL ->
            stringResource(Res.string.onboarding_code_via_missed_call, target)

        CodeDeliveryChannel.FRAGMENT -> stringResource(
            Res.string.onboarding_code_via_fragment,
            target
        )

        CodeDeliveryChannel.FIREBASE -> stringResource(Res.string.onboarding_code_via_sms, target)
        CodeDeliveryChannel.EMAIL -> stringResource(Res.string.onboarding_code_via_email, target)
        CodeDeliveryChannel.OTHER -> stringResource(Res.string.onboarding_code_sent)
    }

@Composable
private fun EmailAddressStep(
    working: Boolean,
    error: UiText?,
    onSubmit: (String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    OnboardingPage(
        icon = DriveIcons.Email,
        title = stringResource(Res.string.onboarding_email_title),
        description = stringResource(Res.string.onboarding_email_description)
    ) {
        OnboardingField(
            value = email,
            onChange = { email = it },
            label = stringResource(Res.string.onboarding_email_label),
            keyboardType = KeyboardType.Email,
            enabled = !working
        )
        ErrorAndAction(
            working = working,
            error = error,
            actionLabel = stringResource(Res.string.onboarding_email_continue),
            enabled = email.isNotBlank(),
            onAction = { onSubmit(email) }
        )
    }
}

@Composable
private fun EmailCodeStep(
    working: Boolean,
    error: UiText?,
    emailPattern: String,
    codeLength: Int?,
    onSubmit: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    OnboardingPage(
        icon = DriveIcons.Email,
        title = stringResource(Res.string.onboarding_email_code_title),
        description = codeDeliveryText(CodeDeliveryChannel.EMAIL, emailPattern)
    ) {
        VerificationInput(value=code,onChange={code=it},length=codeLength,enabled=!working)
        ErrorAndAction(
            working = working,
            error = error,
            actionLabel = stringResource(Res.string.onboarding_verify),
            enabled = code.isNotBlank() && (codeLength == null || code.length == codeLength),
            onAction = { onSubmit(code) }
        )
    }
}

@Composable
private fun CodeStep(
    working: Boolean,
    error: UiText?,
    phoneNumber: String,
    channel: CodeDeliveryChannel,
    codeLength: Int?,
    onSubmit: (String) -> Unit,
    onResend: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    OnboardingPage(
        icon = DriveIcons.Sms,
        title = if(channel==CodeDeliveryChannel.TELEGRAM_APP)"Check Telegram"else"Enter your code",
        description = codeDeliveryText(channel, phoneNumber)
    ) {
        VerificationInput(value=code,onChange={code=it},length=codeLength,enabled=!working,
            numeric=channel !in setOf(CodeDeliveryChannel.SMS_WORD,CodeDeliveryChannel.SMS_PHRASE,CodeDeliveryChannel.OTHER))
        ErrorAndAction(
            working = working,
            error = error,
            actionLabel = stringResource(Res.string.onboarding_verify),
            enabled = code.isNotBlank() && (channel==CodeDeliveryChannel.SMS_WORD || channel==CodeDeliveryChannel.SMS_PHRASE || codeLength == null || code.length == codeLength),
            onAction = { onSubmit(code) }
        )
        TextButton(
            onClick = onResend,
            enabled = !working,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(stringResource(Res.string.onboarding_resend_code)) }
    }
}

/** Accessible visual slots backed by the in-app keypad; no platform IME connection. */
@Composable
private fun VerificationInput(value:String,onChange:(String)->Unit,length:Int?,enabled:Boolean,numeric:Boolean=true){
    val slots=length?.takeIf{numeric&&it in 1..8}
    if(slots==null){
        InlineInputField(value,{onChange(if(length!=null)it.take(length.coerceAtLeast(0))else it)},"Verification code",mode=if(numeric)InlineInputMode.NUMBER else InlineInputMode.TEXT,enabled=enabled)
        return
    }
    val input=checkNotNull(LocalInlineInput.current)
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val available by rememberUpdatedState(enabled)
    val id=remember{java.util.UUID.randomUUID().toString()}
    val target=remember(id,slots){InlineTarget(id,InlineInputMode.NUMBER,{TextFieldValue(current,TextRange(current.length))},
        {change(it.text.filter{ch->ch in '0'..'9'}.take(slots))},{available})}
    val bring=remember{androidx.compose.foundation.relocation.BringIntoViewRequester()}
    LaunchedEffect(id){input.target=target;androidx.compose.runtime.withFrameNanos{};bring.bringIntoView()}
    DisposableEffect(id){onDispose{if(input.target?.id==id)input.target=null}}
    Row(Modifier.fillMaxWidth().bringIntoViewRequester(bring).semantics{contentDescription="Verification code";setText{if(enabled){change(it.text.filter{ch->ch in '0'..'9'}.take(slots));true}else false}},horizontalArrangement=Arrangement.spacedBy(8.dp)){
        repeat(slots){index->
            val active=index==value.length
            Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(if(active)1.5.dp else 1.dp,if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(14.dp))
                .clickable(enabled){input.target=target},contentAlignment=Alignment.Center){Text(value.getOrNull(index)?.toString().orEmpty(),style=MaterialTheme.typography.headlineSmall)}
        }
    }
}

@Composable
private fun PasswordStep(
    working: Boolean,
    error: UiText?,
    hint: String?,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    OnboardingPage(
        icon = DriveIcons.Lock,
        title = "Your password",
        description = "Enter your Telegram two-step password."
    ) {
        OnboardingField(
            value = password,
            onChange = { password = it },
            label = stringResource(Res.string.onboarding_password),
            keyboardType = KeyboardType.Password,
            password = true,
            enabled = !working
        )
        if(!hint.isNullOrBlank())Text("Password hint: $hint",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
        val opener=com.infinity.drive.presentation.platform.LocalUrlOpener.current
        TextButton({opener.open("https://telegram.org/faq#what-if-i-forget-my-password")}){Text("Forgot password?")}
        ErrorAndAction(
            working = working,
            error = error,
            actionLabel = stringResource(Res.string.onboarding_sign),
            enabled = password.isNotEmpty(),
            onAction = { onSubmit(password) }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionsStep(working: Boolean, onResolved: () -> Unit) {
    var mediaRequested by rememberSaveable { mutableStateOf(false) }
    val mediaPermissions = listOf(
        AppPermission.MEDIA_IMAGES,
        AppPermission.MEDIA_VIDEO,
        AppPermission.NOTIFICATIONS
    )
    val permissionChecker = koinInject<PermissionChecker>()
    val permissionRequester = LocalPermissionRequester.current
    val systemScreens = LocalSystemScreens.current

    fun mediaAccess(): Boolean = mediaPermissions.all { permissionChecker.isGranted(it) }

    var mediaGranted by remember { mutableStateOf(mediaAccess()) }

    var allFilesGranted by remember { mutableStateOf(permissionChecker.hasAllFilesAccess()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mediaGranted = mediaAccess()
                allFilesGranted = permissionChecker.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    OnboardingPage(
        icon = DriveIcons.Photo,
        title = stringResource(Res.string.onboarding_storage_access),
        description = stringResource(Res.string.onboarding_driveinfinity_needs_media_files)
    ) {
        val askForMedia = !mediaGranted && !mediaRequested
        BigButton(
            label = when {
                working -> stringResource(Res.string.onboarding_looking_for_drives)
                askForMedia -> stringResource(Res.string.onboarding_allow_media)
                !allFilesGranted -> stringResource(Res.string.onboarding_allow_all_files)
                else -> stringResource(Res.string.onboarding_continue)
            },
            enabled = !working,
            loading = working,
            onClick = {
                when {
                    askForMedia -> permissionRequester.request(mediaPermissions) { mediaRequested = true }
                    !allFilesGranted -> systemScreens.openAllFilesAccess()
                    else -> onResolved()
                }
            }
        )
        if ((mediaRequested || mediaGranted) && !allFilesGranted) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_files_access_covers_documents),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onResolved,
                shape = RoundedCornerShape(18.dp),
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(Res.string.onboarding_continue_without)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupSetupStep(
    state: OnboardingUiState,
    onDcim: (Boolean) -> Unit,
    onPictures: (Boolean) -> Unit,
    onMovies: (Boolean) -> Unit,
    onAutoBackup: (Boolean) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onFinish: () -> Unit,
    finishing: Boolean
) {
    OnboardingPage(
        icon = DriveIcons.CloudDone,
        title = stringResource(Res.string.backup),
        description = stringResource(Res.string.onboarding_choose_what_back_change)
    ) {
        SectionLabel(stringResource(Res.string.onboarding_section_folders))
        ToggleCard(stringResource(Res.string.onboarding_toggle_camera), state.backupDcim, onDcim)
        ToggleCard(
            stringResource(Res.string.onboarding_toggle_pictures),
            state.backupPictures,
            onPictures
        )
        ToggleCard(stringResource(Res.string.onboarding_toggle_movies), state.backupMovies, onMovies)
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(Res.string.onboarding_section_behavior))
        ToggleCard(
            stringResource(Res.string.onboarding_toggle_auto_backup),
            state.autoBackupEnabled,
            onAutoBackup
        )
        ToggleCard(stringResource(Res.string.onboarding_toggle_wifi_only), state.wifiOnly, onWifiOnly)
        Spacer(Modifier.height(32.dp))
        BigButton(
            label = if (finishing) "Syncing your drive…" else "Finish setup",
            onClick = onFinish,
            enabled = !finishing,
            loading = finishing
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun ToggleCard(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(checked,role=Role.Switch,onValueChange=onChange).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(16.dp))
            app.tgdrive.ui.DriveSwitch(checked,null)
        }
    }
}

@Composable
private fun OnboardingField(value:String,onChange:(String)->Unit,label:String,supportingText:String?=null,
    keyboardType:KeyboardType=KeyboardType.Text,password:Boolean=false,enabled:Boolean=true,prefix:String?=null,onPaste:((String)->Unit)?=null){
    InlineInputField(value,onChange,label,Modifier.fillMaxWidth(),
        mode=if(keyboardType in listOf(KeyboardType.Number,KeyboardType.NumberPassword))InlineInputMode.NUMBER
            else if(keyboardType==KeyboardType.Phone)InlineInputMode.PHONE else InlineInputMode.TEXT,
        password=password,enabled=enabled,prefix=prefix,supportingText=supportingText,onPaste=onPaste)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BigButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min=52.dp),
        colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary)
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(color=LocalContentColor.current,modifier=Modifier.size(20.dp),strokeWidth=2.dp)
            Spacer(Modifier.size(10.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ErrorAndAction(working:Boolean,error:UiText?,actionLabel:String,enabled:Boolean,onAction:()->Unit){
    val input=LocalInlineInput.current
    val latestAction by rememberUpdatedState(onAction)
    SideEffect{if(input!=null)input.action=InlineAction(actionLabel,enabled,working){latestAction()}}
    DisposableEffect(input){onDispose{input?.action=null}}
    error?.let{Text(it.resolve(),Modifier.padding(top=12.dp,bottom=8.dp).semantics{liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)}
    if(input==null)BigButton(actionLabel,onAction,enabled&&!working,working)
}

private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val PAGE_PADDING = 24.dp
private val PAGE_VERTICAL_PADDING = 24.dp

private val PREFIX_GAP = 4.dp
private val SPINNER_SIZE = 20.dp
private val SPINNER_STROKE = 2.dp

/* White frame behind the code: scanners expect dark modules on light. */
private val QR_SURFACE = Color.White
private const val QR_WIDTH_FRACTION = 0.8f
private val QR_PADDING = 16.dp
private val QR_STEP_BADGE = 28.dp
private val QR_BUTTON_ICON = 18.dp
private const val TELEGRAM_PROBE_URI = "tg://login"
