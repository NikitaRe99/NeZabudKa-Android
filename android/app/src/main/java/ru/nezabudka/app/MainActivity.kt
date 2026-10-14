package ru.nezabudka.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import ru.nezabudka.app.domain.Note
import ru.nezabudka.app.domain.CommandScope
import ru.nezabudka.app.ui.applyLegacySystemBarColors
import ru.nezabudka.app.domain.ALARM_PRESENTATION_SCREEN
import ru.nezabudka.app.application.ActionEffect
import ru.nezabudka.app.ui.AppTheme
import ru.nezabudka.app.ui.LocalAssistantTheme
import ru.nezabudka.app.ui.AssistantViewModel
import ru.nezabudka.app.ui.AssistantUiEvent
import ru.nezabudka.app.ui.screens.AssistantScreen
import ru.nezabudka.app.ui.screens.NoteEditorScreen
import ru.nezabudka.app.ui.screens.NotesScreen
import ru.nezabudka.app.ui.screens.ReminderEditorScreen
import ru.nezabudka.app.ui.screens.ReminderSnoozeDialog
import ru.nezabudka.app.domain.AssistantTipKind
import ru.nezabudka.app.domain.AssistantSettingsTarget
import ru.nezabudka.app.ui.screens.AppAlertDialog
import ru.nezabudka.app.ui.screens.RemindersScreen
import ru.nezabudka.app.ui.screens.SettingsScreen
import ru.nezabudka.app.ui.screens.WhisperModelsMenu
import androidx.activity.compose.rememberLauncherForActivityResult
import ru.nezabudka.app.notifications.EXTRA_OPEN_REMINDER_ID
import ru.nezabudka.app.notifications.EXTRA_SNOOZE_REMINDER_ID
import ru.nezabudka.app.notifications.DeviceAutoStartSettings
import ru.nezabudka.app.notifications.NotificationChannels
import ru.nezabudka.app.notifications.dismissReminderNotification
import ru.nezabudka.app.widget.WidgetLaunch
import ru.nezabudka.app.widget.WidgetLaunchIntent
import ru.nezabudka.app.widget.WidgetDestination

class MainActivity : ComponentActivity() {
    private var pendingMicrophoneAction: (() -> Unit)? = null
    private var requestExactAfterNotifications = false
    private val microphoneEducationVisible = mutableStateOf(false)
    private val notificationEducationVisible = mutableStateOf(false)
    private val exactAlarmEducationVisible = mutableStateOf(false)
    private val fullScreenEducationVisible = mutableStateOf(false)
    private val overlayEducationVisible = mutableStateOf(false)
    private val permissionPreferences by lazy {
        getSharedPreferences("permission_prompts", MODE_PRIVATE)
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingMicrophoneAction
        pendingMicrophoneAction = null
        if (granted) action?.invoke()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsEnabled.value = granted && NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (granted && requestExactAfterNotifications) requestExactAlarmAccessIfNeeded()
        requestExactAfterNotifications = false
    }
    private val viewModel: AssistantViewModel by viewModels { AssistantViewModel.Factory((application as AssistantApplication).container) }
    private val requestedReminderId = mutableStateOf<Long?>(null)
    private val requestedSnoozeId = mutableStateOf<Long?>(null)
    private val requestedWidgetLaunch = mutableStateOf<WidgetLaunch?>(null)
    private val notificationsEnabled = mutableStateOf(true)
    private val exactAlarmsAllowed = mutableStateOf(true)
    private val fullScreenAlarmsAllowed = mutableStateOf(true)
    private val overlayWindowsAllowed = mutableStateOf(false)
    private val backgroundRunAllowed = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptReminderIntent(intent)
        requestedWidgetLaunch.value = WidgetLaunchIntent.consume(intent)
        setContent {
            val appSettings by viewModel.settings.collectAsStateWithLifecycle()
            LaunchedEffect(notificationsEnabled.value, exactAlarmsAllowed.value, backgroundRunAllowed.value,
                fullScreenAlarmsAllowed.value, overlayWindowsAllowed.value, appSettings.reminderAlarmMode,
                appSettings.reminderAlarmPresentation) {
                notificationsEnabled.value = NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled() &&
                    ru.nezabudka.app.notifications.NotificationChannels.enabledForMode(this@MainActivity, appSettings.reminderAlarmMode)
                viewModel.refreshPermissionTips(ru.nezabudka.app.domain.ReminderPermissionStatus(
                    notificationsEnabled.value, exactAlarmsAllowed.value, backgroundRunAllowed.value,
                    fullScreenAlarmsAllowed.value, overlayWindowsAllowed.value),
                    appSettings.reminderAlarmMode && appSettings.reminderAlarmPresentation == ALARM_PRESENTATION_SCREEN,
                    DeviceAutoStartSettings.destination(this@MainActivity) != null ||
                        DeviceAutoStartSettings.alarmPermissionDestination(this@MainActivity) != null)
            }

            val alarmVendorSettingsTitle = remember {
                DeviceAutoStartSettings.alarmPermissionDestination(this)?.title
            }
            val autoStartSettingsTitle = remember {
                DeviceAutoStartSettings.destination(this)?.title
            }
            val backgroundSettingsTitle = remember {
                DeviceAutoStartSettings.backgroundDestination(this).title
            }
            val reducedUiEffects = when (appSettings.uiPerformanceMode) {
                ru.nezabudka.app.domain.UI_PERFORMANCE_REDUCED -> true
                ru.nezabudka.app.domain.UI_PERFORMANCE_FULL -> false
                else -> viewModel.deviceCapabilities.reducedUiEffects
            }
            LocalAssistantTheme(appSettings, reduceEffects = reducedUiEffects) {
            val colorScheme = MaterialTheme.colorScheme
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = colorScheme.background.luminance() > 0.5f
                    isAppearanceLightNavigationBars = colorScheme.surfaceContainer.luminance() > 0.5f
                }
                applyLegacySystemBarColors(
                    window,
                    colorScheme.background.toArgb(),
                    colorScheme.surfaceContainer.toArgb(),
                )
            }
            AssistantApp(
                viewModel,
                requestedReminderId.value,
                requestedSnoozeId.value,
                requestedWidgetLaunch.value,
                notificationsEnabled.value,
                exactAlarmsAllowed.value,
                fullScreenAlarmsAllowed.value,
                overlayWindowsAllowed.value,
                backgroundRunAllowed.value,
                reducedUiEffects,
                backgroundSettingsTitle,
                alarmVendorSettingsTitle,
                autoStartSettingsTitle,
                consumeReminderIntent = { requestedReminderId.value = null },
                consumeSnoozeIntent = { requestedSnoozeId.value = null },
                consumeWidgetIntent = { requestedWidgetLaunch.value = null },
                returnToWidget = { finish() },
                openNotificationSettings = ::openNotificationSettings,
                openAppPermissionSettings = ::openAppDetailsSettings,
                permissionDialogVisible = microphoneEducationVisible.value || notificationEducationVisible.value ||
                    exactAlarmEducationVisible.value || fullScreenEducationVisible.value || overlayEducationVisible.value,
                openExactAlarmSettings = ::openExactAlarmSettings,
                openFullScreenAlarmSettings = ::openFullScreenAlarmSettings,
                openOverlaySettings = ::openOverlaySettings,
                openAlarmNotificationSettings = ::openAlarmNotificationSettings,
                openBackgroundRunSettings = ::openBackgroundRunSettings,
                openAlarmVendorSettings = ::openAlarmVendorSettings,
                openAutoStartSettings = ::openAutoStartSettings,
                withMicrophonePermission = ::withMicrophonePermission,
                requestReminderAccess = ::requestReminderAccess,
                requestFullScreenAccess = ::requestAlarmWindowAccessIfNeeded,
                resetFirstUseSetup = ::resetFirstUseSetup,
                requestNotificationFromSetup = ::requestNotificationFromSetup,
                reminderSetupExplained = ::reminderSetupExplained,
            )
            PermissionEducationDialogs(
                microphoneVisible = microphoneEducationVisible.value,
                notificationVisible = notificationEducationVisible.value,
                exactAlarmVisible = exactAlarmEducationVisible.value,
                fullScreenVisible = fullScreenEducationVisible.value,
                overlayVisible = overlayEducationVisible.value,
                dismissMicrophone = ::dismissMicrophoneEducation,
                continueMicrophone = ::continueMicrophonePermission,
                dismissNotification = ::dismissNotificationEducation,
                continueNotification = ::continueNotificationPermission,
                dismissExactAlarm = { exactAlarmEducationVisible.value = false },
                continueExactAlarm = ::continueExactAlarmPermission,
                dismissFullScreen = { fullScreenEducationVisible.value = false },
                continueFullScreen = ::continueFullScreenPermission,
                dismissOverlay = { overlayEducationVisible.value = false },
                continueOverlay = ::continueOverlayPermission,
            )
            }
        }
        (application as AssistantApplication).startUiServices()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptReminderIntent(intent)
        WidgetLaunchIntent.consume(intent)?.let { requestedWidgetLaunch.value = it }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAppIcon()
        if (viewModel.assistantUi.value.errorSettingsTarget == AssistantSettingsTarget.MICROPHONE &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            viewModel.dismissError()
        notificationsEnabled.value = NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            ru.nezabudka.app.notifications.NotificationChannels.enabledForMode(this, viewModel.settings.value.reminderAlarmMode)
        exactAlarmsAllowed.value = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        fullScreenAlarmsAllowed.value = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        overlayWindowsAllowed.value = Settings.canDrawOverlays(this)
        backgroundRunAllowed.value = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)
    }

    override fun onStop() {
        viewModel.onAppBackgrounded()
        super.onStop()
    }

    private fun openNotificationSettings() {
        startFirstAvailable(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startFirstAvailable(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
            )
        }
    }

    private fun openFullScreenAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startFirstAvailable(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun openOverlaySettings() {
        startFirstAvailable(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
        )
    }

    private fun openAlarmNotificationSettings() {
        val appNotificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        val channelSettings = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.alarmChannelId(this))
        startFirstAvailable(appNotificationSettings, channelSettings)
    }

    private fun openBackgroundRunSettings() {
        startFirstAvailable(DeviceAutoStartSettings.backgroundDestination(this).intent)
    }

    private fun openAutoStartSettings() {
        startFirstAvailable(*listOfNotNull(DeviceAutoStartSettings.destination(this)?.intent).toTypedArray())
    }

    private fun openAlarmVendorSettings() {
        startFirstAvailable(*listOfNotNull(DeviceAutoStartSettings.alarmPermissionDestination(this)?.intent).toTypedArray())
    }

    private fun withMicrophonePermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
            return
        }
        pendingMicrophoneAction = action
        microphoneEducationVisible.value = true
    }

    private fun dismissMicrophoneEducation() {
        microphoneEducationVisible.value = false
        pendingMicrophoneAction = null
    }

    private fun continueMicrophonePermission() {
        microphoneEducationVisible.value = false
        val requestedBefore = permissionPreferences.getBoolean(KEY_MICROPHONE_REQUESTED, false)
        if (requestedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            pendingMicrophoneAction = null
            openAppDetailsSettings()
        } else {
            permissionPreferences.edit().putBoolean(KEY_MICROPHONE_REQUESTED, true).apply()
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestReminderAccess(requiresExactTime: Boolean) {
        val notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val exactAllowed = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val backgroundAllowed = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        if (!notificationsAllowed || !ru.nezabudka.app.notifications.NotificationChannels.enabledForMode(this, viewModel.settings.value.reminderAlarmMode) ||
            (requiresExactTime && !exactAllowed) || !backgroundAllowed) viewModel.offerTip(AssistantTipKind.REMINDER_DELIVERY)
        if (DeviceAutoStartSettings.destination(this) != null || DeviceAutoStartSettings.alarmPermissionDestination(this) != null)
            viewModel.offerTip(AssistantTipKind.REMINDER_DELIVERY)
    }

    private fun dismissNotificationEducation() {
        notificationEducationVisible.value = false
        requestExactAfterNotifications = false
    }

    private fun continueNotificationPermission() {
        notificationEducationVisible.value = false
        permissionPreferences.edit().putBoolean(KEY_NOTIFICATION_REQUESTED, true).apply()
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
            requestExactAfterNotifications = false
        }
    }

    private fun requestExactAlarmAccessIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) return
        if (permissionPreferences.getBoolean(KEY_EXACT_ALARM_EXPLAINED, false)) return
        exactAlarmEducationVisible.value = true
    }

    private fun continueExactAlarmPermission() {
        exactAlarmEducationVisible.value = false
        permissionPreferences.edit().putBoolean(KEY_EXACT_ALARM_EXPLAINED, true).apply()
        openExactAlarmSettings()
    }

    private fun requestFullScreenAccessIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) return
        if (permissionPreferences.getBoolean(KEY_FULL_SCREEN_EXPLAINED, false)) return
        fullScreenEducationVisible.value = true
    }

    private fun requestAlarmWindowAccessIfNeeded() {
        if (!Settings.canDrawOverlays(this)) {
            if (!permissionPreferences.getBoolean(KEY_OVERLAY_EXPLAINED, false)) {
                overlayEducationVisible.value = true
            }
            return
        }
        requestFullScreenAccessIfNeeded()
    }

    private fun continueOverlayPermission() {
        overlayEducationVisible.value = false
        permissionPreferences.edit().putBoolean(KEY_OVERLAY_EXPLAINED, true).apply()
        openOverlaySettings()
    }

    private fun continueFullScreenPermission() {
        fullScreenEducationVisible.value = false
        permissionPreferences.edit().putBoolean(KEY_FULL_SCREEN_EXPLAINED, true).apply()
        openFullScreenAlarmSettings()
    }

    private fun startFirstAvailable(vararg intents: Intent) {
        (intents.asList() + appDetailsIntent()).forEach { candidate ->
            if (candidate.resolveActivity(packageManager) != null && runCatching { startActivity(candidate) }.isSuccess) {
                return
            }
        }
    }

    private fun openAppDetailsSettings() = startFirstAvailable(appDetailsIntent())

    private fun appDetailsIntent() = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$packageName"),
    )

    private fun resetFirstUseSetup() {
        viewModel.resetFirstUseSetup()
        permissionPreferences.edit()
            .remove(KEY_NOTIFICATION_EXPLAINED)
            .remove(KEY_EXACT_ALARM_EXPLAINED)
            .remove(KEY_FULL_SCREEN_EXPLAINED)
            .remove(KEY_OVERLAY_EXPLAINED).apply()
    }

    private fun reminderSetupExplained(requiresExactTime: Boolean) {
        permissionPreferences.edit().putBoolean(KEY_NOTIFICATION_EXPLAINED, true)
            .apply { if (requiresExactTime) putBoolean(KEY_EXACT_ALARM_EXPLAINED, true) }.apply()
    }

    private fun requestNotificationFromSetup() {
        val requested = permissionPreferences.getBoolean(KEY_NOTIFICATION_REQUESTED, false)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!requested || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            permissionPreferences.edit().putBoolean(KEY_NOTIFICATION_REQUESTED, true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else openNotificationSettings()
    }

    private fun acceptReminderIntent(intent: Intent?) {
        intent?.getLongExtra(EXTRA_OPEN_REMINDER_ID, -1L)?.takeIf { it > 0L }?.let {
            dismissReminderNotification(this, it)
            requestedReminderId.value = it
            intent.removeExtra(EXTRA_OPEN_REMINDER_ID)
        }
        intent?.getLongExtra(EXTRA_SNOOZE_REMINDER_ID, -1L)?.takeIf { it > 0L }?.let {
            dismissReminderNotification(this, it)
            requestedSnoozeId.value = it
            intent.removeExtra(EXTRA_SNOOZE_REMINDER_ID)
        }
    }

    private companion object {
        const val KEY_MICROPHONE_REQUESTED = "microphone_requested"
        const val KEY_NOTIFICATION_REQUESTED = "notification_requested"
        const val KEY_NOTIFICATION_EXPLAINED = "notification_explained"
        const val KEY_EXACT_ALARM_EXPLAINED = "exact_alarm_explained"
        const val KEY_FULL_SCREEN_EXPLAINED = "full_screen_explained"
        const val KEY_OVERLAY_EXPLAINED = "overlay_explained"
    }
}

@Composable
private fun AssistantApp(
    vm: AssistantViewModel,
    requestedReminderId: Long?,
    requestedSnoozeId: Long?,
    requestedWidgetLaunch: WidgetLaunch?,
    notificationsEnabled: Boolean,
    exactAlarmsAllowed: Boolean,
    fullScreenAlarmsAllowed: Boolean,
    overlayWindowsAllowed: Boolean,
    backgroundRunAllowed: Boolean,
    reducedUiEffects: Boolean,
    backgroundSettingsTitle: String,
    alarmVendorSettingsTitle: String?,
    autoStartSettingsTitle: String?,
    consumeReminderIntent: () -> Unit,
    consumeSnoozeIntent: () -> Unit,
    consumeWidgetIntent: () -> Unit,
    returnToWidget: () -> Unit,
    openNotificationSettings: () -> Unit,
    openAppPermissionSettings: () -> Unit,
    permissionDialogVisible: Boolean,
    openExactAlarmSettings: () -> Unit,
    openFullScreenAlarmSettings: () -> Unit,
    openOverlaySettings: () -> Unit,
    openAlarmNotificationSettings: () -> Unit,
    openBackgroundRunSettings: () -> Unit,
    openAlarmVendorSettings: () -> Unit,
    openAutoStartSettings: () -> Unit,
    withMicrophonePermission: (() -> Unit) -> Unit,
    requestReminderAccess: (Boolean) -> Unit,
    requestFullScreenAccess: () -> Unit,
    resetFirstUseSetup: () -> Unit,
    requestNotificationFromSetup: () -> Unit,
    reminderSetupExplained: (Boolean) -> Unit,
) {
    val nav = rememberNavController()
    val appSettings by vm.settings.collectAsStateWithLifecycle()
    val event by vm.event.collectAsStateWithLifecycle()
    val actionNotice = (event as? AssistantUiEvent.Action)?.notice
    val themeTokens = AppTheme.tokens
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var editorFolderId by remember { mutableStateOf<Long?>(null) }
    var editorSessionId by remember { mutableLongStateOf(0L) }
    var settingsSearchRequest by remember { mutableStateOf<String?>(null) }
    var assistantSettingsRequest by remember { mutableStateOf<AssistantSettingsTarget?>(null) }
    var returnToWidgetOnExit by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var widgetReminderCreator by remember { mutableStateOf(false) }
    val widgetContext = androidx.compose.ui.platform.LocalContext.current
    val whisperModelsMenu by vm.whisperModelsMenu.collectAsStateWithLifecycle()
    val voiceState by vm.voiceActionUi.collectAsStateWithLifecycle()
    ru.nezabudka.app.ui.RecordingHapticEffect(voiceState.isRecording)
    val updateState by remember(widgetContext) { ru.nezabudka.app.updates.GithubAppUpdater.observe(widgetContext) }
        .collectAsStateWithLifecycle(initialValue = ru.nezabudka.app.updates.GithubAppUpdater.read(widgetContext))
    var startupUpdatePending by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    val startupCheckBaseline = androidx.compose.runtime.saveable.rememberSaveable { ru.nezabudka.app.updates.GithubAppUpdater.read(widgetContext).checkedAt }
    var startupCheckRequested by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startupCheckRequested) return@LaunchedEffect
        startupCheckRequested = true
        if (requestedWidgetLaunch == null && requestedReminderId == null && requestedSnoozeId == null)
            ru.nezabudka.app.updates.GithubAppUpdater.checkNow(widgetContext, startup = true)
        else startupUpdatePending = false
    }
    LaunchedEffect(updateState.checkedAt) {
        if (updateState.checkedAt != 0L && updateState.checkedAt != startupCheckBaseline && updateState.status == "none") startupUpdatePending = false
    }
    if (startupUpdatePending && currentRoute == "assistant" && !permissionDialogVisible && !whisperModelsMenu &&
        !voiceState.isRecording && !voiceState.isProcessing && voiceState.recognizedText.isBlank() &&
        updateState.searchEnabled && updateState.checkedAt != 0L && updateState.checkedAt != startupCheckBaseline &&
        updateState.status in setOf("available", "ready")) {
        AppAlertDialog(
            onDismissRequest = { startupUpdatePending = false },
            title = { Text("Доступно обновление") },
            text = { Text("Найдена версия ${updateState.version}. Перейдите в настройки обновлений, чтобы скачать и установить её.") },
            confirmButton = { TextButton(onClick = {
                startupUpdatePending = false
                settingsSearchRequest = "обновления"
                nav.navigate("settings") { launchSingleTop = true }
            }) { Text("К обновлению") } },
            dismissButton = { TextButton(onClick = { startupUpdatePending = false }) { Text("Отмена") } },
        )
    }
    val destinations = remember { listOf(
        Triple("assistant", "Помощник", Icons.Outlined.Mic),
        Triple("reminders", "Напоминания", Icons.Outlined.Notifications),
        Triple("notes", "Заметки", Icons.Outlined.Description),
        Triple("settings", "Настройки", Icons.Outlined.Settings),
    ) }
    BackHandler(enabled = destinations.any { it.first == currentRoute }) {
        if (returnToWidgetOnExit) returnToWidget()
    }
    val openTopLevelDestination: (String) -> Unit = { route ->
        returnToWidgetOnExit = false
        if (currentRoute != route) {
            nav.navigate(route) {
                popUpTo(nav.graph.id)
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(requestedReminderId) {
        requestedReminderId?.let { id ->
            returnToWidgetOnExit = false
            nav.navigate("reminderEditor/$id/false") { launchSingleTop = true }
            consumeReminderIntent()
        }
    }
    LaunchedEffect(requestedWidgetLaunch?.deliveryId) {
        val launch = requestedWidgetLaunch ?: return@LaunchedEffect
        returnToWidgetOnExit = true
        try {
            val notesDestination = launch.destination in setOf(WidgetDestination.NOTES, WidgetDestination.NOTE, WidgetDestination.NEW_NOTE)
            val route = if (notesDestination) "notes" else "reminders"
            val folder = if (notesDestination) vm.folderForWidget(launch.folderId) else null
            if (notesDestination) vm.selectFolder(folder)
            nav.navigate(route) { popUpTo(nav.graph.id); launchSingleTop = true }
            when (launch.destination) {
                WidgetDestination.NOTE -> {
                    val note = launch.itemId?.let { vm.noteForWidget(it) }?.takeUnless { it.trashed }
                    if (note != null) {
                        editingNote = note; editorFolderId = folder ?: note.folderId
                        editorSessionId = SystemClock.elapsedRealtime()
                        nav.navigate("noteEditor")
                    } else android.widget.Toast.makeText(widgetContext, "Заметка уже удалена", android.widget.Toast.LENGTH_SHORT).show()
                }
                WidgetDestination.REMINDER -> {
                    val reminder = launch.itemId?.let { vm.reminderForWidget(it) }
                    if (reminder != null) nav.navigate("reminderEditor/${reminder.id}/false")
                    else android.widget.Toast.makeText(widgetContext, "Напоминание уже выполнено или удалено", android.widget.Toast.LENGTH_SHORT).show()
                }
                WidgetDestination.NEW_NOTE -> {
                    editingNote = null; editorFolderId = folder; editorSessionId = SystemClock.elapsedRealtime()
                    nav.navigate("noteEditor")
                }
                WidgetDestination.NEW_REMINDER -> widgetReminderCreator = true
                else -> Unit
            }
        } finally { consumeWidgetIntent() }
    }
    LaunchedEffect(actionNotice?.id) {
        val created = actionNotice?.effect as? ActionEffect.ReminderCreated
        if (created != null) requestReminderAccess(created.reminder.remindAt != null)
    }
    Scaffold(bottomBar = {
        if (!imeVisible && currentRoute != "noteEditor" && currentRoute != "reminderEditor/{id}/{changeTime}") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(themeTokens.colors.navigation)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            ) {
                ru.nezabudka.app.ui.screens.FullWidthNavigation(
                    destinations = destinations, currentRoute = currentRoute,
                    showLabels = appSettings.showNavigationLabels,
                    open = openTopLevelDestination,
                )
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = "assistant",
            modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("assistant") {
                val ui by vm.assistantUi.collectAsStateWithLifecycle()
                val settings by vm.settings.collectAsStateWithLifecycle()
                val tips by vm.assistantTips.collectAsStateWithLifecycle()
                AssistantScreen(
                    ui, actionNotice, settings, vm::editText,
                    { vm.primaryAction(CommandScope.ASSISTANT) },
                    { withMicrophonePermission { vm.beginPushToTalk(CommandScope.ASSISTANT) } },
                    vm::endPushToTalk, vm::playLast,
                    editCreatedNote = { note -> editingNote = note; editorFolderId = note.folderId; vm.dismissNoteNotice(); nav.navigate("noteEditor") },
                    changeCreatedFolder = { note -> editingNote = note; editorFolderId = note.folderId; vm.dismissNoteNotice(); nav.navigate("noteEditor") },
                    openReminder = { reminder -> nav.navigate("reminderEditor/${reminder.id}/false") },
                    changeReminderTime = { reminder, remindAt, recurrence ->
                        vm.changeReminderTime(reminder, remindAt, recurrence)
                        if (remindAt != null) requestReminderAccess(true)
                    },
                    undoLastAction = vm::undoLastAction,
                    dismissNotice = vm::dismissActionNotice,
                    answerReminderDateClarification = vm::answerReminderDateClarification,
                    openWhisperModels = vm::openWhisperModelsMenu,
                    hideWelcomeCard = vm::hideAssistantWelcomeCard,
                    tips = tips,
                    acknowledgeTip = vm::acknowledgeTip,
                    openSettings = { target ->
                        if (target == AssistantSettingsTarget.MICROPHONE) openAppPermissionSettings()
                        else {
                            assistantSettingsRequest = target
                            settingsSearchRequest = null
                            nav.navigate("settings") { launchSingleTop = true }
                        }
                    },
                )
            }
            composable("settings") {
                LaunchedEffect(Unit) { vm.refreshModelCacheStats() }
                val appIcon by vm.appIcon.collectAsStateWithLifecycle()
                val settings by vm.settings.collectAsStateWithLifecycle()
                val whisperModels by (if (settings.sttEngine == "gigaam") vm.gigaamModels else vm.whisperModels).collectAsStateWithLifecycle()
                val modelStorage by vm.modelStorage.collectAsStateWithLifecycle()
                val classicNluModel by vm.classicNluModel.collectAsStateWithLifecycle()
                val cloudSttCredentials by vm.cloudSttCredentials.collectAsStateWithLifecycle()
                val threadBenchmark by vm.whisperThreadBenchmark.collectAsStateWithLifecycle()
                val punctuationModel by vm.punctuationModel.collectAsStateWithLifecycle()
                val dataTransfer by vm.dataTransfer.collectAsStateWithLifecycle()
                val speechState by vm.ui.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings, vm.engines, modelStorage, whisperModels, if (settings.sttEngine == "gigaam") ru.nezabudka.app.speech.GigaamCatalog.downloads else vm.whisperDownloads,
                    classicNluModel, vm.classicNluDownloadAvailable, cloudSttCredentials, vm.deviceCapabilities,
                    vm::selectSttEngine, vm::setKeepWhisperModelLoaded, vm::setWhisperThreadCount,
                    vm::setAudioPreprocessing, vm::setShowPlaybackButton, vm::setShowTechnicalLog, vm::setShowAssistantWelcomeCard,
                    vm::setTrustStt, vm::setTheme, vm::setCustomTheme,
                    vm::setPopupDurationSeconds,
                    vm::setClassicNluEnabled,
                    vm::setTimeOfDaySettings,
                    vm::setNoteTrashRetentionDays, vm::setNoteFolderInterface,
                    vm::setShowNavigationLabels, vm::setUiPerformanceMode,
                    vm::setNotificationSound, vm::setNotificationVibrationEnabled,
                    { enabled ->
                        vm.setReminderAlarmMode(enabled)
                        if (enabled) {
                            requestReminderAccess(true)
                            if (settings.reminderAlarmPresentation == ALARM_PRESENTATION_SCREEN) {
                                requestFullScreenAccess()
                            }
                        }
                    },
                    { value ->
                        vm.setReminderAlarmPresentation(value)
                        if (value == ALARM_PRESENTATION_SCREEN) requestFullScreenAccess()
                    },
                    vm::setReminderAlarmSound,
                    vm::setNotificationWakeScreen,
                    vm::setReminderAlarmRingSeconds,
                    vm::setReminderAlarmPauseMinutes,
                    vm::setReminderAlarmRepeatCount,
                    vm::selectWhisperModel, vm::downloadWhisperModel, vm::importWhisperModel,
                    vm::downloadClassicNluModel, vm::importClassicNluModel, vm::selectClassicNluModel,
                    vm::selectModelsDirectory, vm::clearModelCache,
                    vm::saveYandexCredentials, vm::saveGoogleCredentials, vm::saveVkCredentials,
                    notificationsEnabled, exactAlarmsAllowed, fullScreenAlarmsAllowed, overlayWindowsAllowed, backgroundRunAllowed, backgroundSettingsTitle, alarmVendorSettingsTitle, autoStartSettingsTitle,
                    openNotificationSettings, openExactAlarmSettings, openFullScreenAlarmSettings, openOverlaySettings, openAlarmNotificationSettings, openBackgroundRunSettings,
                    openAlarmVendorSettings, openAutoStartSettings,
                    resetFirstUseSetup,
                    threadBenchmark, vm::startWhisperThreadBenchmark, vm::cancelWhisperThreadBenchmark, vm::applyBestWhisperThreadCount,
                    vm::setWhisperAccurateMode,
                    vm::setWhisperRawMode,
                    vm::setNotePunctuationModel,
                    punctuationModel, vm::downloadPunctuationModel, vm::cancelPunctuationDownload,
                    requestedSearch = settingsSearchRequest,
                    setGigaamLivePreview = vm::setGigaamLivePreview,
                    setEditorFullPunctuation = vm::setEditorFullPunctuation,
                    consumeRequestedSearch = { settingsSearchRequest = null },
                    appIcon = appIcon,
                    selectAppIcon = vm::setAppIcon,
                    appUpdateState = updateState,
                    checkForUpdate = { ru.nezabudka.app.updates.GithubAppUpdater.checkNow(widgetContext) },
                    installUpdate = { ru.nezabudka.app.updates.GithubAppUpdater.install(widgetContext) },
                    loadCloudCredential = vm::loadCloudCredential,
                    importPunctuation = vm::importPunctuationModel,
                    selectPunctuationFile = vm::selectPunctuationModel,
                    runReminderTest = vm::runReminderDisplayTest,
                    cancelReminderTest = vm::cancelReminderDisplayTest,
                    dataTransfer = dataTransfer,
                    exportData = vm::exportDataFile,
                    prepareDataRestore = vm::prepareDataRestore,
                    confirmDataRestore = vm::confirmDataRestore,
                    cancelDataRestore = vm::cancelDataRestore,
                    setUpdateSearchEnabled = { ru.nezabudka.app.updates.GithubAppUpdater.setSearchEnabled(widgetContext, it) },
                    downloadUpdate = { ru.nezabudka.app.updates.GithubAppUpdater.checkNow(widgetContext, download = true) },
                    deleteModel = vm::deleteDownloadedModel,
                    canDeleteModels = !speechState.isRecording && !speechState.isProcessing && !threadBenchmark.running,
                    requestedTarget = assistantSettingsRequest,
                    consumeRequestedTarget = { assistantSettingsRequest = null },
                    onWhisperModelsOpened = vm::whisperModelsOpened,
                    setUiHapticsEnabled = vm::setUiHapticsEnabled,
                    setHidePopupNotices = vm::setHidePopupNotices,
                    returnToAssistant = { nav.popBackStack() },
                )
            }
            composable("reminders") {
                val reminders by vm.reminders.collectAsStateWithLifecycle()
                val ui by vm.voiceActionUi.collectAsStateWithLifecycle()
                val settings by vm.settings.collectAsStateWithLifecycle()
                RemindersScreen(
                    requestCreate = widgetReminderCreator,
                    consumeCreateRequest = { widgetReminderCreator = false },
                    reminders = reminders,
                    ui = ui,
                    actionNotice = actionNotice,
                    popupDurationSeconds = settings.popupDurationSeconds,
                    reducedUiEffects = reducedUiEffects,
                    sort = ru.nezabudka.app.ui.ReminderSort.fromKey(settings.reminderSort),
                    setSort = vm::setReminderSort,
                    delete = vm::deleteReminder,
                    move = vm::moveReminder,
                    restore = vm::restoreReminder,
                    favorite = vm::toggleReminderFavorite,
                    complete = vm::toggleReminderCompleted,
                    undoCompleted = { id -> vm.setReminderCompleted(id, false) },
                    deleteOverdue = vm::deleteOverdueReminders,
                    deleteCompleted = vm::deleteCompletedReminders,
                    create = { text, remindAt, recurrence, favorite, onCreated ->
                        vm.createReminder(text, remindAt, recurrence, favorite) {
                            onCreated()
                            requestReminderAccess(remindAt != null)
                        }
                    },
                    beginPushToTalk = { withMicrophonePermission { vm.beginPushToTalk(CommandScope.REMINDERS) } },
                    endPushToTalk = vm::endPushToTalk,
                    primaryAction = { vm.primaryAction(CommandScope.REMINDERS) },
                    editRecognizedText = vm::editText,
                    openReminder = { reminder -> nav.navigate("reminderEditor/${reminder.id}/false") },
                    changeReminderTime = { reminder, remindAt, recurrence ->
                        vm.changeReminderTime(reminder, remindAt, recurrence)
                        if (remindAt != null) requestReminderAccess(true)
                    },
                    undoLastAction = vm::undoLastAction,
                    dismissNotice = vm::dismissActionNotice,
                    answerReminderDateClarification = vm::answerReminderDateClarification,
                )
            }
            composable(
                route = "notes",
                exitTransition = {
                    if (targetState.destination.route == "noteEditor") ExitTransition.None else null
                },
                popEnterTransition = {
                    if (initialState.destination.route == "noteEditor") EnterTransition.None else null
                },
            ) {
                val notesUi by vm.notesUi.collectAsStateWithLifecycle()
                val ui by vm.voiceActionUi.collectAsStateWithLifecycle()
                val settings by vm.settings.collectAsStateWithLifecycle()
                NotesScreen(
                notesUi.folders, notesUi.allNotes, notesUi.selectedFolderId, ui, actionNotice, vm::selectFolder,
                { withMicrophonePermission { vm.beginPushToTalk(CommandScope.NOTES) } }, vm::endPushToTalk,
                { vm.primaryAction(CommandScope.NOTES) },
                editRecognizedText = vm::editText, undoLastAction = vm::undoLastAction, dismissNotice = vm::dismissActionNotice,
                noteSort = settings.noteSort, noteColumns = settings.noteColumns,
                noteFolderInterface = settings.noteFolderInterface,
                setNoteSort = vm::setNoteSort, setNoteColumns = vm::setNoteColumns,
                setNoteFolderInterface = vm::setNoteFolderInterface,
                pinNotes = vm::pinNotes, colorNotes = vm::colorNotes, archiveNotes = vm::archiveNotes,
                trashNotes = vm::trashNotes, emptyTrash = vm::emptyNoteTrash, setNotesFolder = vm::setNotesFolder,
                createFolder = { name, done -> vm.createNoteFolder(name, done) },
                createFolderSilently = { name, done -> vm.createNoteFolderSilently(name, done) },
                renameFolder = vm::renameNoteFolder, moveFolder = vm::moveNoteFolder, deleteFolder = vm::deleteNoteFolder,
                moveNote = vm::moveNote,
                openNote = { note -> editingNote = note; editorFolderId = note.folderId; nav.navigate("noteEditor") },
                newNote = { folderId ->
                    editingNote = null
                    editorFolderId = folderId
                    editorSessionId = SystemClock.elapsedRealtime()
                    nav.navigate("noteEditor")
                },
                reducedUiEffects = reducedUiEffects,
                isLoading = notesUi.isLoading,
                openTrashSettings = { assistantSettingsRequest = AssistantSettingsTarget.TRASH; nav.navigate("settings") { launchSingleTop = true } },
                )
            }
            composable("reminderEditor/{id}/{changeTime}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                val reminderFlow = remember(id) { id?.let(vm::observeReminder) }
                val reminder = reminderFlow?.collectAsStateWithLifecycle(initialValue = null)?.value
                BackHandler(enabled = reminder == null && returnToWidgetOnExit) { returnToWidget() }
                if (reminder == null) Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                } else ReminderEditorScreen(
                    reminder = reminder,
                    back = {
                        if (returnToWidgetOnExit) returnToWidget()
                        else if (!nav.popBackStack("reminders", inclusive = false)) {
                            nav.navigate("reminders") {
                                popUpTo(nav.graph.id)
                                launchSingleTop = true
                            }
                        }
                    },
                    save = vm::saveReminder,
                    delete = { reminderId, done -> vm.deleteReminder(reminderId); done() },
                    openDatePickerInitially = entry.arguments?.getString("changeTime").toBoolean(),
                    onTimedReminderSaved = { requestReminderAccess(true) },
                )
            }
            composable(
                route = "noteEditor",
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                val folders by vm.folders.collectAsStateWithLifecycle()
                val dictationUi by vm.noteDictationUi.collectAsStateWithLifecycle()
                val dictationSessionId = remember(editingNote?.id, editorSessionId) { System.nanoTime() }
                NoteEditorScreen(
                note = editingNote, folders = folders, initialFolderId = editorFolderId,
                editorSessionId = editorSessionId,
                back = {
                    if (returnToWidgetOnExit) returnToWidget() else nav.popBackStack()
                },
                save = { id, selectedFolders, title, text, pinned, archived, color ->
                    vm.saveNote(id, selectedFolders, title, text, pinned, archived, color) {
                        if (returnToWidgetOnExit) returnToWidget() else nav.popBackStack()
                    }
                },
                createFolder = { name, done -> vm.createNoteFolder(name, done) },
                createReminder = { text, remindAt, recurrence, onCreated ->
                    vm.createReminder(text, remindAt, recurrence, favorite = false) {
                        onCreated()
                        requestReminderAccess(true)
                    }
                },
                trash = { note ->
                    vm.trashNote(note)
                    if (returnToWidgetOnExit) returnToWidget() else nav.popBackStack()
                },
                dictatedText = dictationUi.result
                    ?.takeIf { it.sessionId == dictationSessionId }
                    ?.text,
                dictationActive = dictationUi.active,
                dictationRecording = dictationUi.recording,
                dictationPreview = dictationUi.preview,
                beginDictation = { withMicrophonePermission { vm.beginNoteDictation(dictationSessionId) } },
                endDictation = { vm.endNoteDictation(dictationSessionId) },
                consumeDictation = { vm.consumeNoteDictation(dictationSessionId) },
                cancelDictation = { vm.cancelNoteDictation(dictationSessionId) },
                )
            }
        }
        }
    }
    if (whisperModelsMenu) {
        LaunchedEffect(Unit) { vm.refreshModelCacheStats() }
        val activeSettings by vm.settings.collectAsStateWithLifecycle()
        val isGigaam = activeSettings.sttEngine == "gigaam"
        val models by (if (isGigaam) vm.gigaamModels else vm.whisperModels).collectAsStateWithLifecycle()
        val storage by vm.modelStorage.collectAsStateWithLifecycle()
        val chooseStorage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(vm::selectModelsDirectory)
        }
        WhisperModelsMenu(
            models = models,
            downloads = if (isGigaam) ru.nezabudka.app.speech.GigaamCatalog.downloads else vm.whisperDownloads,
            storageConfigured = storage.configured,
            chooseStorage = { chooseStorage.launch(null) },
            select = vm::selectWhisperModel,
            download = vm::downloadWhisperModel,
            delete = { vm.deleteDownloadedModel(if (isGigaam) "gigaam" else "whisper", it) },
            gigaam = isGigaam,
            dismiss = vm::dismissWhisperModelsMenu,
            import = vm::importWhisperModel,
        )
    }
    if (requestedSnoozeId != null) {
        val snoozeReminders by vm.reminders.collectAsStateWithLifecycle()
        snoozeReminders.firstOrNull { it.id == requestedSnoozeId }?.let { reminder ->
            ReminderSnoozeDialog(
                reminder = reminder,
                dismiss = consumeSnoozeIntent,
                snooze = { date -> vm.snoozeReminder(reminder, date, consumeSnoozeIntent) },
            )
        }
    }
}

@Composable
private fun PermissionEducationDialogs(
    microphoneVisible: Boolean,
    notificationVisible: Boolean,
    exactAlarmVisible: Boolean,
    fullScreenVisible: Boolean,
    overlayVisible: Boolean,
    dismissMicrophone: () -> Unit,
    continueMicrophone: () -> Unit,
    dismissNotification: () -> Unit,
    continueNotification: () -> Unit,
    dismissExactAlarm: () -> Unit,
    continueExactAlarm: () -> Unit,
    dismissFullScreen: () -> Unit,
    continueFullScreen: () -> Unit,
    dismissOverlay: () -> Unit,
    continueOverlay: () -> Unit,
) {
    val dialog = when {
        microphoneVisible -> PermissionDialogContent(
            title = "Доступ к микрофону",
            text = "Микрофон нужен только во время голосового ввода. Без разрешения текстовый ввод продолжит работать.",
            dismiss = dismissMicrophone,
            confirm = continueMicrophone,
        )
        notificationVisible -> PermissionDialogContent(
            title = "Разрешите уведомления",
            text = "Уведомления нужны, чтобы созданные напоминания появлялись вовремя. При отказе записи сохранятся, но Android не покажет сигнал.",
            dismiss = dismissNotification,
            confirm = continueNotification,
        )
        exactAlarmVisible -> PermissionDialogContent(
            title = "Точное время напоминаний",
            text = "Специальный доступ нужен для срабатывания в выбранную минуту. При отказе Android поставит энергосберегающий неточный будильник, который может задержаться.",
            dismiss = dismissExactAlarm,
            confirm = continueExactAlarm,
        )
        fullScreenVisible -> PermissionDialogContent(
            title = "Будильник отдельным окном",
            text = "Полноэкранный доступ нужен только для выбранного режима отдельного окна на заблокированном экране. При отказе останется обычное уведомление.",
            dismiss = dismissFullScreen,
            confirm = continueFullScreen,
        )
        overlayVisible -> PermissionDialogContent(
            title = "Окно поверх приложений",
            text = "Доступ нужен только для автоматического компактного окна будильника на разблокированном экране. Без него останется уведомление, которое можно открыть нажатием.",
            dismiss = dismissOverlay,
            confirm = continueOverlay,
        )
        else -> null
    } ?: return
    AppAlertDialog(
        onDismissRequest = dialog.dismiss,
        title = { Text(dialog.title) },
        text = { Text(dialog.text) },
        dismissButton = { TextButton(onClick = dialog.dismiss) { Text("Не сейчас") } },
        confirmButton = { TextButton(onClick = dialog.confirm) { Text("Продолжить") } },
    )
}

private data class PermissionDialogContent(
    val title: String,
    val text: String,
    val dismiss: () -> Unit,
    val confirm: () -> Unit,
)
