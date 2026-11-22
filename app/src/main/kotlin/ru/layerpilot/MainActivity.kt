package ru.layerpilot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import ru.layerpilot.ui.locale.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import ru.layerpilot.ui.MakerWorldViewModel
import ru.layerpilot.ui.screens.AmsScreen
import ru.layerpilot.ui.screens.CameraScreen
import ru.layerpilot.ui.screens.ConnectScreen
import ru.layerpilot.ui.screens.ControlScreen
import ru.layerpilot.ui.screens.DashboardScreen
import ru.layerpilot.ui.screens.FilesScreen
import ru.layerpilot.ui.screens.MakerWorldScreen
import ru.layerpilot.ui.screens.Model3DViewerScreen
import ru.layerpilot.data.models.PlateInspector
import ru.layerpilot.ui.screens.ModelsScreen
import ru.layerpilot.ui.screens.PrinterInfoScreen
import ru.layerpilot.ui.screens.PrintersScreen
import ru.layerpilot.ui.screens.VideoPlayerScreen
import ru.layerpilot.data.files.RemoteFile
import ru.layerpilot.ui.screens.SettingsScreen
import ru.layerpilot.ui.components.LocalWaveStyle
import ru.layerpilot.ui.components.WaveStyle
import ru.layerpilot.ui.locale.Lang
import ru.layerpilot.ui.locale.LocalLang
import ru.layerpilot.ui.theme.BambukHomeTheme

class MainActivity : ComponentActivity() {

    private var pendingShare by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingShare = extractShare(intent)
        setContent {
            val vm: AppViewModel = viewModel()
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
                    when {
                        !loaded -> Splash()
                        !settings.isConfigured -> ConnectScreen(vm = vm, onConnected = { })
                        else -> MainScaffold(
                            vm = vm,
                            pendingShare = pendingShare,
                            onShareConsumed = { pendingShare = null },
                        )
                    }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShare = extractShare(intent)
    }

    private fun extractShare(intent: Intent?): Uri? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
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

private fun tabsFor(showModels: Boolean, showMakerWorld: Boolean): List<Tab> = buildList {
    add(Tab("dashboard", "Печать", Icons.Rounded.Print, Icons.Outlined.Print))
    add(Tab("ams", "Пластик", Icons.Rounded.Science, Icons.Outlined.Science))
    add(Tab("files", "Файлы", Icons.Rounded.Folder, Icons.Outlined.Folder))
    if (showModels) add(Tab("models", "Модели", Icons.Rounded.ViewInAr, Icons.Outlined.ViewInAr))
    if (showMakerWorld) add(Tab("makerworld", "MakerWorld", Icons.Rounded.Storefront, Icons.Outlined.Storefront))
}

@Composable
private fun MainScaffold(
    vm: AppViewModel,
    pendingShare: Uri?,
    onShareConsumed: () -> Unit,
) {
    val nav = rememberNavController()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val official = remember { AppIdentity.isOfficial(context) }
    var showUnofficial by remember { mutableStateOf(!official) }
    val tabs = remember(settings.showModelsTab, settings.showMakerWorldTab) {
        tabsFor(settings.showModelsTab, settings.showMakerWorldTab)
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "dashboard"
    val showBar = route in tabs.map { it.route }
    var video by remember { mutableStateOf<RemoteFile?>(null) }
    var model3d by remember { mutableStateOf<java.io.File?>(null) }
    val bed = remember(settings.model) { PlateInspector.bedSizeFor(settings.model.name) }

    LaunchedEffect(settings.showModelsTab, settings.showMakerWorldTab) {
        if (route == "models" && !settings.showModelsTab) {
            nav.navigate("dashboard") { launchSingleTop = true }
        }
        if (route == "makerworld" && !settings.showMakerWorldTab) {
            nav.navigate("dashboard") { launchSingleTop = true }
        }
    }
    LaunchedEffect(Unit) {
        vm.openModels.collect {
            nav.navigate("models") { launchSingleTop = true }
        }
    }
    LaunchedEffect(pendingShare) {
        if (pendingShare != null) {
            vm.importShared(pendingShare)
            onShareConsumed()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.openTab(tab.route) },
                            icon = {
                                Icon(
                                    if (route == tab.route) tab.icon else tab.inactiveIcon,
                                    tab.label,
                                    Modifier.size(24.dp),
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
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding),
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
            composable("models") {
                ModelsScreen(
                    vm,
                    onBack = { nav.backToDashboard() },
                    onMakerWorld = { nav.openTab("makerworld") },
                    onOpen3D = { model3d = it },
                )
            }
            composable("makerworld") {
                val makerWorldVm: MakerWorldViewModel = viewModel()
                MakerWorldScreen(
                    vm = makerWorldVm,
                    onBack = { nav.backToDashboard() },
                    onOpen3D = { model3d = it },
                )
            }
            composable("control") { ControlScreen(vm, onBack = { nav.popBackStack() }) }
            composable("camera") { CameraScreen(vm, onBack = { nav.popBackStack() }) }
            composable("printer_info") { PrinterInfoScreen(vm, onBack = { nav.popBackStack() }) }
            composable("printers") { PrintersScreen(vm, onBack = { nav.popBackStack() }) }
            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onEditConnection = { nav.navigate("connect") },
                    onPrinterInfo = { nav.navigate("printer_info") },
                    onPrinters = { nav.navigate("printers") },
                    onMakerWorld = { nav.openTab("makerworld") },
                )
            }
            composable("connect") {
                Box(Modifier.fillMaxSize()) {
                    ConnectScreen(vm = vm, onConnected = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                }
            }
        }
    }
        video?.let { target ->
            VideoPlayerScreen(vm = vm, remote = target, onClose = { video = null })
        }
        model3d?.let { target ->
            Model3DViewerScreen(
                file = target,
                bedWidth = bed.first,
                bedHeight = bed.second,
                onClose = { model3d = null },
            )
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
