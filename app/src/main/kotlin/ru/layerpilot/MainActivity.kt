package ru.layerpilot

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import ru.layerpilot.ui.locale.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.layerpilot.data.identity.AppIdentity
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.screens.AmsScreen
import ru.layerpilot.ui.screens.CameraScreen
import ru.layerpilot.ui.screens.ConnectScreen
import ru.layerpilot.ui.screens.ControlScreen
import ru.layerpilot.ui.screens.DashboardScreen
import ru.layerpilot.ui.screens.FilesScreen
import ru.layerpilot.ui.screens.PrinterInfoScreen
import ru.layerpilot.ui.screens.PrintersScreen
import ru.layerpilot.ui.screens.FarmScreen
import ru.layerpilot.ui.screens.MaintenanceScreen
import ru.layerpilot.ui.screens.HistoryScreen
import ru.layerpilot.ui.screens.CalibrationScreen
import ru.layerpilot.ui.screens.VideoPlayerScreen
import ru.layerpilot.data.files.RemoteFile
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.ui.screens.SettingsScreen
import ru.layerpilot.ui.components.LocalWaveStyle
import ru.layerpilot.ui.components.Printer3dIcon
import ru.layerpilot.ui.components.WaveStyle
import ru.layerpilot.ui.locale.Lang
import ru.layerpilot.ui.locale.LocalLang
import ru.layerpilot.ui.theme.BambukHomeTheme
import ru.layerpilot.clawd.ClawdOverlay

class MainActivity : FragmentActivity() {

    private var pendingPrinterAction by mutableStateOf<String?>(null)
    private var pendingShortcut by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingPrinterAction = intent?.getStringExtra("printer_action")
        pendingShortcut = intent?.getStringExtra("shortcut")
        setContent {
            val vm: AppViewModel = viewModel()
            LaunchedEffect(pendingPrinterAction) {
                when (pendingPrinterAction) {
                    "pause" -> vm.pause()
                    "resume" -> vm.resume()
                    "stop" -> vm.stop()
                }
                pendingPrinterAction = null
            }
            val settings by vm.settings.collectAsStateWithLifecycle()
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    val prefs = getSharedPreferences("ui", MODE_PRIVATE)
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted && !prefs.getBoolean("notif_asked", false)) {
                        prefs.edit().putBoolean("notif_asked", true).apply()
                        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            val loaded by vm.settingsLoaded.collectAsStateWithLifecycle()
            val systemLanguage = java.util.Locale.getDefault().language
            val appLanguage = settings.language.ifBlank {
                if (systemLanguage in Lang.supported) systemLanguage else "ru"
            }
            LaunchedEffect(appLanguage) { Lang.setCode(appLanguage) }
            LaunchedEffect(settings.keepScreenOn) {
                window.decorView.keepScreenOn = settings.keepScreenOn
            }

            val view = LocalView.current
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ru.layerpilot.data.model.ThemeMode.DARK -> true
                ru.layerpilot.data.model.ThemeMode.LIGHT -> false
                else -> systemDark
            }
            SideEffect {
                val activityWindow = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                WindowCompat.getInsetsController(activityWindow, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            BambukHomeTheme(settings = settings) {
                CompositionLocalProvider(
                    LocalLang provides appLanguage,
                    LocalWaveStyle provides WaveStyle(
                        thickness = settings.waveThickness,
                        amplitude = settings.waveAmplitude,
                        animated = settings.waveAnimation,
                    ),
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BiometricGate(enabled = settings.biometricLock) {
                    when {
                        !loaded -> Splash()
                        !settings.isConfigured -> ConnectScreen(vm = vm, onConnected = { })
                        else -> MainScaffold(
                            vm = vm,
                            pendingShortcut = pendingShortcut,
                            onShortcutConsumed = { pendingShortcut = null },
                        )
                    }
                    }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPrinterAction = intent.getStringExtra("printer_action")
        pendingShortcut = intent.getStringExtra("shortcut")
    }


}

@Composable
private fun BiometricGate(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var unlocked by remember(enabled) { mutableStateOf(false) }
    fun prompt() {
        if (activity == null) { unlocked = true; return }
        val manager = BiometricManager.from(context)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlocked = true
            return
        }
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                }
            },
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("BambukHome")
                .setSubtitle("Подтвердите личность")
                .setNegativeButtonText("Отмена")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }
    LaunchedEffect(enabled) { if (enabled) prompt() }
    if (unlocked) content() else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BambukHome", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary)
                androidx.compose.material3.TextButton(onClick = { prompt() }) {
                    Text("Открыть по биометрии")
                }
            }
        }
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("BambukHome", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector = icon,
)

private fun androidx.navigation.NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun androidx.navigation.NavHostController.backToDashboard() {
    if (!popBackStack("dashboard", inclusive = false)) {
        navigate("dashboard") { launchSingleTop = true }
    }
}

private fun tabsFor(): List<Tab> = listOf(
    Tab("dashboard", "Печать", Printer3dIcon, Printer3dIcon),
    Tab("ams", "Пластик", Icons.Rounded.Science, Icons.Outlined.Science),
    Tab("files", "Файлы", Icons.Rounded.Folder, Icons.Outlined.Folder),
)

@Composable
private fun TabIcon(tab: Tab, selected: Boolean) {
    Icon(
        if (selected) tab.icon else tab.inactiveIcon,
        tab.label,
        Modifier.size(24.dp),
    )
}

@Composable
private fun MainScaffold(
    vm: AppViewModel,
    pendingShortcut: String?,
    onShortcutConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    val official = remember { AppIdentity.isOfficial(context) }
    var showUnofficial by remember { mutableStateOf(!official) }
    val tabs = remember { tabsFor() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "dashboard"
    val showBar = route in tabs.map { it.route }
    var announcedPhase by remember { mutableStateOf(state.phase) }
    LaunchedEffect(state.phase) {
        if (state.phase != announcedPhase) {
            val text = when (state.phase) {
                PrintPhase.PRINTING -> "Печать началась"
                PrintPhase.PAUSED -> "Печать приостановлена"
                PrintPhase.FINISHED -> "Печать завершена"
                PrintPhase.FAILED -> "Ошибка печати"
                else -> null
            }
            if (text != null) view.announceForAccessibility(text)
            announcedPhase = state.phase
        }
    }
    DisposableEffect(route, settings.hideCameraInRecents) {
        val activity = context as? android.app.Activity
        val secure = route == "camera" && settings.hideCameraInRecents
        if (secure) activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (secure) activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    var video by remember { mutableStateOf<RemoteFile?>(null) }
    LaunchedEffect(pendingShortcut) {
        when (pendingShortcut) {
            "farm" -> nav.navigate("farm") { launchSingleTop = true }
            "camera" -> nav.navigate("camera") { launchSingleTop = true }
        }
        if (pendingShortcut != null) onShortcutConsumed()
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBar && !wide) {
                Box(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(30.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        tonalElevation = 3.dp,
                        shadowElevation = 12.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        ),
                    ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(72.dp).padding(horizontal = 6.dp),
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.openTab(tab.route) },
                            icon = {
                                TabIcon(
                                    tab = tab,
                                    selected = route == tab.route,
                                )
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (showBar && wide) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    tabs.forEach { tab ->
                        NavigationRailItem(
                            selected = route == tab.route,
                            onClick = { nav.openTab(tab.route) },
                            icon = {
                                TabIcon(
                                    tab = tab,
                                    selected = route == tab.route,
                                )
                            },
                            label = { Text(tab.label, fontSize = 11.sp, maxLines = 1) },
                        )
                    }
                }
            }
        NavHost(
            navController = nav,
            startDestination = "dashboard",
            modifier = Modifier.weight(1f).fillMaxSize(),
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None },
        ) {
            composable("dashboard") {
                DashboardScreen(
                    vm = vm,
                    onSettings = { nav.navigate("settings") },
                    onControl = { nav.navigate("control") },
                    onAms = { nav.openTab("ams") },
                    onCamera = { nav.navigate("camera") },
                    onFiles = { nav.openTab("files") },
                    onPrinters = { nav.navigate("printers") },
                    onFarm = { nav.navigate("farm") },
                )
            }
            composable("ams") { AmsScreen(vm, onBack = { nav.backToDashboard() }) }
            composable("files") {
                FilesScreen(
                    vm,
                    onBack = { nav.backToDashboard() },
                    onPlayVideo = { video = it },
                )
            }
            composable("control") { ControlScreen(vm, onBack = { nav.popBackStack() }) }
            composable("camera") { CameraScreen(vm, onBack = { nav.popBackStack() }) }
            composable("printer_info") { PrinterInfoScreen(vm, onBack = { nav.popBackStack() }) }
            composable("printers") {
                PrintersScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onFarm = { nav.navigate("farm") },
                )
            }
            composable("history") {
                HistoryScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable("maintenance") {
                MaintenanceScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onCalibration = { nav.navigate("calibration") },
                )
            }
            composable("calibration") { CalibrationScreen(onBack = { nav.popBackStack() }) }
            composable("farm") {
                FarmScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpenPrinter = { id ->
                        vm.switchPrinter(id)
                        nav.navigate("dashboard") { launchSingleTop = true }
                    },
                    onPrinters = { nav.navigate("printers") { launchSingleTop = true } },
                )
            }
            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onEditConnection = { nav.navigate("connect") },
                    onPrinterInfo = { nav.navigate("printer_info") },
                    onPrinters = { nav.navigate("printers") },
                    onMaintenance = { nav.navigate("maintenance") },
                    onHistory = { nav.navigate("history") },
                    onFarm = { nav.navigate("farm") },
                )
            }
            composable("connect") {
                Box(Modifier.fillMaxSize()) {
                    ConnectScreen(vm = vm, onConnected = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                }
            }
        }
        }
    }
        if (settings.clawdEnabled && route in tabs.map { it.route }) {
            ClawdOverlay(vm = vm, settings = settings)
        }
        video?.let { target ->
            VideoPlayerScreen(vm = vm, remote = target, onClose = { video = null })
        }
    }

    if (showUnofficial) {
        AlertDialog(
            onDismissRequest = { showUnofficial = false },
            title = { Text("Неофициальная сборка") },
            text = {
                Text(
                    "Подпись приложения не совпадает с оригинальной. " +
                        "Оригинал и поддержка: ${AppIdentity.TELEGRAM_CHANNEL}. " +
                        "Если вы купили это приложение — вас обманули.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showUnofficial = false }) { Text("Понятно") }
            },
        )
    }
}
