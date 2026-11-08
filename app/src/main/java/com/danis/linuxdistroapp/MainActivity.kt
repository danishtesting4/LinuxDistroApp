package com.danis.linuxdistroapp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.IOException

// ---------- Data Models ----------

private fun copyRuntimeLog(context: Context) {
    val log = try {
        RuntimeLog.read(context)
    } catch (error: IOException) {
        Toast.makeText(context, "Unable to read diagnostic log: ${error.message}", Toast.LENGTH_LONG).show()
        return
    }
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("Andistro diagnostic log", log))
    Toast.makeText(context, "Diagnostic log copied to clipboard.", Toast.LENGTH_SHORT).show()
}

data class Distro(
    val id: String,
    val name: String,
    val description: String,
    val iconRes: Int,
    val color: Color,
    val category: String
)

data class DesktopEnvironment(
    val id: String,
    val name: String,
    val description: String,
    val iconRes: Int,
    val color: Color,
    val size: String,
    val recommendedFor: List<String>
)

// ---------- Screens ----------

enum class Screen { MAIN, DISTRO_SELECTION, DE_SELECTION, DISTRO_DETAIL, DESKTOP }

// ---------- App Shell (Material You theme + Scaffold + navigation) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndistroApp() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    var previousScreen by remember { mutableStateOf(Screen.MAIN) }
    var selectedDistro by remember { mutableStateOf<Distro?>(null) }
    var selectedDE by remember { mutableStateOf<DesktopEnvironment?>(null) }
    var batteryDialogVisible by remember { mutableStateOf(false) }
    var desktopModeDialogVisible by remember { mutableStateOf(false) }
    var helpSheetVisible by remember { mutableStateOf(false) }
    val runtimeState by DistroRuntimeState.state.collectAsState()
    val installedDistros = getDistroList()
        .map(Distro::id)
        .filterTo(mutableSetOf()) { DistroRuntimeService.isDistroInstalled(context, it) }
        .also {
            if (runtimeState.installed) it += runtimeState.distroId
        }
    var pendingInstallDistroId by remember { mutableStateOf("debian") }
    val pausedDistros = if (runtimeState.installing) {
        emptySet()
    } else {
        getDistroList()
            .map(Distro::id)
            .filterTo(mutableSetOf()) { DistroRuntimeService.isDistroInstallPaused(context, it) }
    }

    val batterySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (DistroRuntimeService.install(context, pendingInstallDistroId)) {
            batteryDialogVisible = false
        } else {
            batteryDialogVisible = true
        }
    }

    fun requestInstall(distroId: String = "debian") {
        pendingInstallDistroId = distroId
        if (!DistroRuntimeService.install(context, distroId)) {
            batteryDialogVisible = true
        }
    }

    fun openTermuxX11() {
        try {
            context.startActivity(
                Intent().setComponent(
                    ComponentName(
                        DistroRuntimeService.TERMUX_X11_PACKAGE,
                        "${DistroRuntimeService.TERMUX_X11_PACKAGE}.MainActivity"
                    )
                )
            )
        } catch (error: ActivityNotFoundException) {
            DistroRuntimeService.stopDesktop(context)
            DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                message = "Unable to open Termux:X11",
                desktopRunning = false,
                error = error.message ?: "Termux:X11 could not be opened."
            )
        }
    }

    fun startDesktopWithMode(
        displayMode: DesktopDisplayMode,
        distroId: String = selectedDistro?.id ?: "debian"
    ) {
        if (displayMode == DesktopDisplayMode.TERMUX_X11 &&
            !DistroRuntimeService.isTermuxX11Installed(context)
        ) {
            return
        }
        if (currentScreen != Screen.DESKTOP) {
            previousScreen = currentScreen
        }
        desktopModeDialogVisible = false
        DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
            message = "Starting the $distroId desktop",
            distroId = distroId,
            installed = distroId in installedDistros,
            desktopRunning = false,
            displayMode = displayMode,
            vncPassword = null,
            error = null
        )
        DistroRuntimeService.startDesktop(context, distroId, displayMode)
        currentScreen = Screen.DESKTOP
        if (displayMode == DesktopDisplayMode.TERMUX_X11) {
            openTermuxX11()
        }
    }

    fun startDesktopOrExplain() {
        previousScreen = currentScreen
        val distroId = selectedDistro?.id ?: "debian"
        if (distroId !in installedDistros) {
            currentScreen = Screen.DESKTOP
        } else if (runtimeState.desktopRunning) {
            currentScreen = Screen.DESKTOP
            if (runtimeState.displayMode == DesktopDisplayMode.TERMUX_X11) {
                openTermuxX11()
            }
        } else {
            desktopModeDialogVisible = true
        }
    }

    fun openDebianDetails() {
        selectedDistro = getDistroList().first { it.id == "debian" }
        selectedDE = getDesktopEnvironments().first { it.id == "xfce" }
        currentScreen = Screen.DISTRO_DETAIL
    }

    fun selectDestination(destination: Screen) {
        when (destination) {
            Screen.MAIN -> currentScreen = Screen.MAIN
            Screen.DISTRO_SELECTION -> currentScreen = Screen.DISTRO_SELECTION
            Screen.DESKTOP -> startDesktopOrExplain()
            else -> Unit
        }
    }

    val destinations = listOf(
        Triple(Screen.MAIN, "Home", R.drawable.ic_home),
        Triple(Screen.DISTRO_SELECTION, "Distros", R.drawable.ic_apps),
        Triple(Screen.DESKTOP, "Desktop", R.drawable.ic_desktop)
    )
    val immersiveVncViewer = currentScreen == Screen.DESKTOP &&
        runtimeState.displayMode == DesktopDisplayMode.IN_APP_VNC &&
        runtimeState.vncPassword != null
    val showTopLevelNavigation =
        currentScreen == Screen.MAIN || currentScreen == Screen.DISTRO_SELECTION ||
            (currentScreen == Screen.DESKTOP && !immersiveVncViewer)
    val activity = context as? Activity
    DisposableEffect(activity, immersiveVncViewer) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (immersiveVncViewer) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
        onDispose {
            if (window != null && immersiveVncViewer) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp
        Scaffold(
            topBar = {
                if (currentScreen != Screen.DESKTOP) when (currentScreen) {
                    Screen.MAIN -> TopAppBar(
                        title = { Text("Andistro") },
                        actions = { HelpAction { helpSheetVisible = true } }
                    )
                    Screen.DISTRO_SELECTION -> TopAppBar(
                        title = { Text("Distributions") },
                        actions = { HelpAction { helpSheetVisible = true } }
                    )
                    Screen.DE_SELECTION -> TopAppBar(
                        title = { Text("Desktop environment") },
                        navigationIcon = {
                            IconButton(onClick = { currentScreen = Screen.DISTRO_SELECTION }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_back),
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = { HelpAction { helpSheetVisible = true } }
                    )
                    Screen.DISTRO_DETAIL -> TopAppBar(
                        title = { Text(selectedDistro?.name ?: "Details") },
                        navigationIcon = {
                            IconButton(onClick = { currentScreen = Screen.DE_SELECTION }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_back),
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = { HelpAction { helpSheetVisible = true } }
                    )
                    Screen.DESKTOP -> Unit
                }
            },
            contentWindowInsets = if (immersiveVncViewer) {
                WindowInsets(0, 0, 0, 0)
            } else {
                ScaffoldDefaults.contentWindowInsets
            },
            bottomBar = {
                if (!useNavigationRail && showTopLevelNavigation) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        destinations.forEach { (destination, label, icon) ->
                            NavigationBarItem(
                                selected = currentScreen == destination,
                                onClick = { selectDestination(destination) },
                                icon = {
                                    Icon(painter = painterResource(icon), contentDescription = null)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && showTopLevelNavigation) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        destinations.forEach { (destination, label, icon) ->
                            NavigationRailItem(
                                selected = currentScreen == destination,
                                onClick = { selectDestination(destination) },
                                icon = {
                                    Icon(painter = painterResource(icon), contentDescription = null)
                                },
                                label = { Text(label) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (currentScreen == Screen.DESKTOP) {
                                    Modifier
                                } else {
                                    Modifier.widthIn(max = 1000.dp)
                                }
                            )
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                if (!animationsEnabled(context)) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (fadeIn(tween(220)) togetherWith fadeOut(tween(160))) using SizeTransform { _, _ -> tween(0) }
                                }
                            },
                            label = "screen"
                        ) { screen ->
                        when (screen) {
                            Screen.MAIN -> MainScreen(
                                onInstallClick = {
                                    when {
                                        runtimeState.error != null -> requestInstall(runtimeState.distroId)
                                        "debian" in installedDistros -> openDebianDetails()
                                        else -> currentScreen = Screen.DISTRO_SELECTION
                                    }
                                },
                                onRunClick = ::startDesktopOrExplain,
                                onCopyLogs = { copyRuntimeLog(context) },
                                isInstalled = "debian" in installedDistros,
                                isPaused = "debian" in pausedDistros,
                                onContinueClick = { requestInstall("debian") },
                                runtimeState = runtimeState
                            )
                            Screen.DISTRO_SELECTION -> DistroSelectionScreen(
                                onDistroSelected = { distro ->
                                    selectedDistro = distro
                                    currentScreen = Screen.DE_SELECTION
                                },
                                installedDistros = installedDistros,
                                pausedDistros = pausedDistros
                            )
                            Screen.DE_SELECTION -> selectedDistro?.let { distro ->
                                DesktopEnvironmentSelectionScreen(
                                    distro = distro,
                                    onDESelected = { de ->
                                        selectedDE = de
                                        currentScreen = Screen.DISTRO_DETAIL
                                    }
                                )
                            }
                            Screen.DISTRO_DETAIL -> selectedDistro?.let { distro ->
                                val distroRuntimeState = runtimeState.takeIf {
                                    it.distroId == distro.id
                                } ?: RuntimeUiState(
                                    distroId = distro.id,
                                    installed = distro.id in installedDistros
                                )
                                DistroDetailScreen(
                                    distro = distro,
                                    selectedDE = selectedDE,
                                    runtimeState = distroRuntimeState,
                                    onInstall = {
                                        if (distro.id in installedDistros) {
                                            if (distroRuntimeState.desktopRunning) {
                                                DistroRuntimeService.stopDesktop(context)
                                            } else {
                                                previousScreen = Screen.DISTRO_DETAIL
                                                desktopModeDialogVisible = true
                                            }
                                        } else {
                                            requestInstall(distro.id)
                                        }
                                    },
                                    onCopyLogs = { copyRuntimeLog(context) },
                                    isInstalled = distro.id in installedDistros,
                                    onUninstall = {
                                        DistroRuntimeService.uninstall(context, distro.id)
                                    }
                                )
                            }
                            Screen.DESKTOP -> {
                                val distroId = selectedDistro?.id ?: "debian"
                                val distroName = getDistroList()
                                    .firstOrNull { it.id == distroId }
                                    ?.name
                                    ?: distroId
                                val desktopRuntimeState = runtimeState.takeIf {
                                    it.distroId == distroId
                                } ?: RuntimeUiState(
                                    distroId = distroId,
                                    installed = distroId in installedDistros
                                )
                                DesktopViewerScreen(
                                    runtimeState = desktopRuntimeState,
                                    distroName = distroName,
                                    isInstalled = distroId in installedDistros,
                                    onInstall = {
                                        currentScreen = Screen.DISTRO_SELECTION
                                    },
                                    onStartDesktop = { mode -> startDesktopWithMode(mode, distroId) },
                                    onCopyLogs = { copyRuntimeLog(context) },
                                    onBack = { currentScreen = previousScreen },
                                    onOpenTermuxX11 = ::openTermuxX11,
                                    onStopDesktop = { DistroRuntimeService.stopDesktop(context) }
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
    }

    if (batteryDialogVisible) {
        AlertDialog(
            onDismissRequest = { batteryDialogVisible = false },
            title = { Text("Allow background downloads") },
            text = {
                Text(
                    "Android can stop long downloads to save battery. To install an environment, " +
                        "allow Andistro to run without battery optimization."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        batteryDialogVisible = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            batterySettingsLauncher.launch(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else {
                            requestInstall()
                        }
                    }
                ) {
                    Text("Open battery settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { batteryDialogVisible = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (desktopModeDialogVisible) {
        val termuxX11Installed = DistroRuntimeService.isTermuxX11Installed(context)
        AlertDialog(
            onDismissRequest = { desktopModeDialogVisible = false },
            title = { Text("Choose a desktop display") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Open the desktop in Andistro, or use Termux:X11 for its native " +
                            "touch and keyboard controls."
                    )
                    if (!termuxX11Installed) {
                        Text(
                            "Termux:X11 must be installed from its official nightly release.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            startDesktopWithMode(DesktopDisplayMode.IN_APP_VNC)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("In-app VNC")
                    }
                    OutlinedButton(
                        onClick = {
                            startDesktopWithMode(DesktopDisplayMode.TERMUX_X11)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = termuxX11Installed
                    ) {
                        Text("Termux:X11")
                    }
                    TextButton(
                        onClick = { desktopModeDialogVisible = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            },
            dismissButton = {}
        )
    }

    if (helpSheetVisible) {
        HelpSheet(onDismiss = { helpSheetVisible = false })
    }
}

@Composable
private fun DesktopViewerScreen(
    runtimeState: RuntimeUiState,
    distroName: String = "Debian",
    isInstalled: Boolean,
    onInstall: () -> Unit,
    onStartDesktop: (DesktopDisplayMode) -> Unit,
    onCopyLogs: () -> Unit,
    onBack: () -> Unit,
    onOpenTermuxX11: () -> Unit,
    onStopDesktop: () -> Unit
) {
    var keyboardVisible by remember { mutableStateOf(false) }
    BackHandler {
        if (keyboardVisible) {
            keyboardVisible = false
        } else {
            onBack()
        }
    }

    if (runtimeState.displayMode == DesktopDisplayMode.TERMUX_X11) {
        TermuxX11HandoffScreen(
            runtimeState = runtimeState,
            distroName = distroName,
            onStartDesktop = { onStartDesktop(DesktopDisplayMode.TERMUX_X11) },
            onOpenTermuxX11 = onOpenTermuxX11,
            onStopDesktop = onStopDesktop,
            onCopyLogs = onCopyLogs,
            onBack = onBack
        )
        return
    }

    val password = runtimeState.vncPassword
    if (password.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = runtimeState.error ?: if (!isInstalled) {
                        "No desktop session is available yet."
                    } else if (runtimeState.desktopRunning) {
                        "Waiting for the desktop connection…"
                    } else {
                        runtimeState.message.ifBlank { "Starting the desktop…" }
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (runtimeState.error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                if (!isInstalled && runtimeState.error == null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "A session is a running desktop you start from an installed " +
                            "environment. Browse distributions to see what this build can install.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isInstalled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onInstall) {
                        Text("Browse distributions")
                    }
                } else if (runtimeState.error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { onStartDesktop(DesktopDisplayMode.IN_APP_VNC) }) {
                        Text("Retry starting desktop")
                    }
                    TextButton(onClick = onCopyLogs) {
                        Text("Copy diagnostic log")
                    }
                }
            }
        }
        return
    }

    val client = remember(password, distroName) { VncClient(password, distroName) }
    androidx.compose.runtime.DisposableEffect(client) {
        onDispose { client.close() }
    }
    val clientState by client.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(client, runtimeState.desktopRunning) {
        if (runtimeState.desktopRunning) client.connect()
    }
    androidx.compose.runtime.LaunchedEffect(clientState.error) {
        if (clientState.error != null) keyboardVisible = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val viewerWidth = maxWidth
        val overlaySurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                VncFramebufferView(context).apply {
                    setPointerListener(client::sendPointer)
                }
            },
            update = { view ->
                view.setFramebuffer(clientState.bitmap)
                view.setPointerListener(client::sendPointer)
            }
        )
        if (clientState.bitmap == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = clientState.error ?: clientState.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                if (clientState.error != null) {
                    Button(onClick = { client.connect() }) {
                        Text("Reconnect")
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(overlaySurface)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Back to Andistro"
                )
            }
            Surface(
                modifier = Modifier.widthIn(max = viewerWidth - 80.dp),
                shape = RoundedCornerShape(20.dp),
                color = overlaySurface
            ) {
                Text(
                    text = clientState.error ?: clientState.status,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (clientState.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        if (keyboardVisible) {
            PcKeyboard(
                client = client,
                onHide = { keyboardVisible = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(8.dp)
            )
        } else if (clientState.bitmap != null) {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { keyboardVisible = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(16.dp)
            ) {
                Text("Keyboard")
            }
        }
    }
}

@Composable
private fun TermuxX11HandoffScreen(
    runtimeState: RuntimeUiState,
    distroName: String,
    onStartDesktop: () -> Unit,
    onOpenTermuxX11: () -> Unit,
    onStopDesktop: () -> Unit,
    onCopyLogs: () -> Unit,
    onBack: () -> Unit
) {
    val message = runtimeState.error ?: if (runtimeState.desktopRunning) {
        "$distroName XFCE is running in Termux:X11. Use its touch gestures and keyboard, then return here to stop the session."
    } else {
        runtimeState.message.ifBlank { "Starting the Termux:X11 display…" }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(12.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = "Back to Andistro"
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Termux:X11", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = if (runtimeState.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (!runtimeState.desktopRunning && runtimeState.error == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (runtimeState.error != null) {
                Button(onClick = onStartDesktop, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry Termux:X11")
                }
            } else if (runtimeState.desktopRunning) {
                Button(onClick = onOpenTermuxX11, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Termux:X11")
                }
                OutlinedButton(onClick = onStopDesktop, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop desktop")
                }
            }
            TextButton(onClick = onCopyLogs) {
                Text("Copy diagnostic log")
            }
        }
    }
}

// ---------- Main Screen ----------

@Composable
fun MainScreen(
    onInstallClick: () -> Unit,
    onRunClick: () -> Unit,
    onCopyLogs: () -> Unit,
    isInstalled: Boolean,
    isPaused: Boolean = false,
    onContinueClick: () -> Unit = onInstallClick,
    runtimeState: RuntimeUiState
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Your environments",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Install and manage Linux environments on this device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (runtimeState.installing) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Setting up Debian + XFCE",
                        style = MaterialTheme.typography.titleLarge
                    )
                    LinearProgressIndicator(
                        progress = { runtimeState.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = runtimeState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (runtimeState.error != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Setup needs attention",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Debian + XFCE was not installed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = runtimeState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Button(onClick = onInstallClick, modifier = Modifier.fillMaxWidth()) {
                            Text("Retry install")
                        }
                        TextButton(onClick = onCopyLogs, modifier = Modifier.fillMaxWidth()) {
                            Text("Copy diagnostic log")
                        }
                    }
                }
            }
        }

        if (isInstalled) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFA80030)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_debian),
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    tint = Color.White
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Debian", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = if (runtimeState.desktopRunning) "XFCE · Running"
                                    else "XFCE · Installed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onInstallClick,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("Manage")
                            }
                            OutlinedButton(
                                onClick = onRunClick,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(if (runtimeState.desktopRunning) "View desktop" else "Open desktop")
                            }
                        }
                    }
                }
            }
        }

        if (!isInstalled && runtimeState.error == null && !runtimeState.installing) {
            item {
                                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Image(
                        painter = painterResource(R.drawable.ic_andistro_mark),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (isPaused) "Setup paused" else "Nothing installed yet",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = if (isPaused) {
                                "Debian + XFCE did not finish installing. Continue where it left off."
                            } else {
                                "Browse the catalog to see which environments are available on this build."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { if (isPaused) onContinueClick() else onInstallClick() }) {
                        Text(if (isPaused) "Continue install" else "Browse distributions")
                    }
                }
            }
        }
    }
}

// ---------- Distro Selection ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistroSelectionScreen(
    onDistroSelected: (Distro) -> Unit,
    installedDistros: Set<String> = emptySet(),
    pausedDistros: Set<String> = emptySet()
) {
    val distros = remember { getDistroList() }
    val installableDistros = distros.filter { DistroInstallProfiles.get(it.id) != null }
    val catalogDistros = distros.filterNot { DistroInstallProfiles.get(it.id) != null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Choose a distribution. XFCE is currently the supported desktop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        item { SectionHeader(text = "Available to install", emphasized = true) }
        items(installableDistros) { distro ->
            DistroCard(
                distro = distro,
                isInstalled = distro.id in installedDistros,
                isPaused = distro.id in pausedDistros,
                isSupported = true,
                onClick = { onDistroSelected(distro) }
            )
        }
        if (catalogDistros.isNotEmpty()) {
            item { SectionHeader(text = "Catalog only", emphasized = false) }
            items(catalogDistros) { distro ->
                DistroCard(
                    distro = distro,
                    isInstalled = distro.id in installedDistros,
                    isPaused = distro.id in pausedDistros,
                    isSupported = false,
                    onClick = { onDistroSelected(distro) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistroCard(
    distro: Distro,
    isInstalled: Boolean = false,
    isPaused: Boolean = false,
    isSupported: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Brand icon tile
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(distro.color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = distro.iconRes),
                    contentDescription = distro.name,
                    modifier = Modifier.size(30.dp),
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = distro.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isInstalled) {
                        Text(
                            text = "Installed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isPaused) {
                        Text(
                            text = "Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    } else {
                        Text(
                            text = if (isSupported) "Installable" else "Catalog only",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSupported) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = distro.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = distro.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = 180f }
            )
        }
    }
}

// ---------- Desktop Environment Selection ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopEnvironmentSelectionScreen(
    distro: Distro,
    onDESelected: (DesktopEnvironment) -> Unit
) {
    val desktopEnvironments = remember { getDesktopEnvironments() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Choose a desktop environment for ${distro.name}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        Text(
            text =             "XFCE can currently be installed. Other desktop environments remain catalog-only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionHeader(text = "Desktop environments", emphasized = true)
            }
            items(desktopEnvironments) { de ->
                DECard(
                    de = de,
                    isInstallable = DistroInstallProfiles.get(distro.id) != null && de.id == "xfce",
                    onClick = { onDESelected(de) }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, emphasized: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (emphasized) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DECard(
    de: DesktopEnvironment,
    isInstallable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(de.color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = de.iconRes),
                    contentDescription = de.name,
                    modifier = Modifier.size(30.dp),
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = de.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isInstallable) {
                        Text(
                            text = "Installable",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Catalog only",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = de.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                        text = if (isInstallable) de.size else "Not installable in this build",
                    style = MaterialTheme.typography.labelSmall,
                        color = if (isInstallable) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = 180f }
            )
        }
    }
}

// ---------- Distro Detail ----------

@Composable
fun DistroDetailScreen(
    distro: Distro,
    selectedDE: DesktopEnvironment? = null,
    onInstall: () -> Unit,
    onCopyLogs: () -> Unit,
    isInstalled: Boolean = false,
    onUninstall: () -> Unit = {},
    runtimeState: RuntimeUiState = RuntimeUiState()
) {
    val context = LocalContext.current
    val isPaused = !isInstalled &&
        !runtimeState.installing &&
        DistroRuntimeService.isDistroInstallPaused(context, distro.id)
    val canInstall = DistroInstallProfiles.get(distro.id) != null &&
        (selectedDE?.id == "xfce" || isInstalled || isPaused)
    var uninstallDialogVisible by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        // Hero banner in brand color
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(distro.color.forWhiteLabel())
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = distro.iconRes),
                        contentDescription = distro.name,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.95f)
                    )
                    if (selectedDE != null) {
                        Spacer(modifier = Modifier.width(20.dp))
                        Icon(
                            painter = painterResource(id = selectedDE.iconRes),
                            contentDescription = selectedDE.name,
                            modifier = Modifier.size(48.dp),
                            tint = Color.White.copy(alpha = 0.95f)
                        )
                    }
                }
                if (selectedDE != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${distro.name} + ${selectedDE.name}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = distro.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!canInstall) {
                Text(
                    text = if (distro.id == "debian") {
                        "This desktop choice is catalog-only. The installer currently supports XFCE."
                    } else if (DistroInstallProfiles.get(distro.id) == null) {
                        "This distribution is catalog-only. No installer is available yet."
                    } else {
                        "This desktop choice is catalog-only. The installer currently supports XFCE."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isPaused) {
                Text(
                    text = "Installation paused — continue to resume where it left off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // Selected desktop card
            if (selectedDE != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(selectedDE.color),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = selectedDE.iconRes),
                                contentDescription = selectedDE.name,
                                modifier = Modifier.size(26.dp),
                                tint = Color.White
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Desktop: ${selectedDE.name}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = selectedDE.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Size: ${selectedDE.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Spec rows
            InfoRow("Category", distro.category)
            InfoRow("Base", getDistroBase(distro.id))
            InfoRow("Package Manager", getPackageManager(distro.id))
            InfoRow("Init System", getInitSystem(distro.id))
            InfoRow("Architecture", "ARM64 / x86_64")

            if (runtimeState.installing || runtimeState.message.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (runtimeState.installing) {
                        LinearProgressIndicator(
                            progress = { runtimeState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = runtimeState.error ?: runtimeState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (runtimeState.error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (runtimeState.error != null) {
                        TextButton(onClick = onCopyLogs) {
                            Text("Copy diagnostic log")
                        }
                    }
                    if (runtimeState.desktopRunning && runtimeState.vncPassword != null) {
                        Text(
                            text = "VNC: 127.0.0.1:5901  ·  Password: ${runtimeState.vncPassword}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
                enabled = canInstall && !runtimeState.installing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInstalled && runtimeState.desktopRunning) MaterialTheme.colorScheme.tertiary
                    else if (!canInstall) MaterialTheme.colorScheme.surfaceVariant
                    else distro.color.forWhiteLabel(),
                    contentColor = if (isInstalled && runtimeState.desktopRunning) MaterialTheme.colorScheme.onTertiary
                    else if (!canInstall) MaterialTheme.colorScheme.onSurfaceVariant
                    else Color.White
                )
            ) {
                Text(
                    text = when {
                        !canInstall -> "Not available"
                        runtimeState.installing -> "Installing…"
                        runtimeState.desktopRunning -> "Stop XFCE desktop"
                        isInstalled -> "Start XFCE desktop"
                        isPaused -> "Continue install"
                        else -> "Install"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)
                )
            }

            if (isInstalled || isPaused) {
                OutlinedButton(
                    onClick = { uninstallDialogVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !runtimeState.installing
                ) {
                    Text("Uninstall")
                }
            }

            if (uninstallDialogVisible) {
                AlertDialog(
                    onDismissRequest = { uninstallDialogVisible = false },
                    title = { Text("Uninstall ${distro.name}?") },
                    text = {
                        Text(
                            "This removes the ${distro.name} filesystem and its downloaded " +
                                "files from this device. You can install it again later."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                uninstallDialogVisible = false
                                onUninstall()
                            }
                        ) {
                            Text(
                                text = "Uninstall",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { uninstallDialogVisible = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Brand surface that keeps white label text readable: returns the color unchanged
 * when white already measures >= 4.5:1 against it, otherwise darkens it in 10%
 * steps until it does (Ubuntu orange and Arch blue need one or two steps).
 */
fun Color.forWhiteLabel(): Color {
    var out = this
    var guard = 0
    while (guard < 12 && 1.05f / (out.luminance() + 0.05f) < 4.6f) {
        out = Color(out.red * 0.9f, out.green * 0.9f, out.blue * 0.9f, 1f)
        guard++
    }
    return out
}

// ---------- Help sheet ----------

@Composable
private fun HelpAction(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("Help") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                 .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Andistro glossary",
                style = MaterialTheme.typography.titleLarge
            )
            HelpEntry(
                term = "Installable vs catalog only",
                definition = "Installable entries can be set up on this device today. " +
                    "Catalog-only entries are reference information in this build."
            )
            HelpEntry(
                term = "Desktop session",
                definition = "A desktop that is running on this device. Start one from an " +
                    "installed environment and stop it when you are done."
            )
            HelpEntry(
                term = "XFCE",
                definition = "The desktop Andistro installs: a lightweight, traditional " +
                    "windows-and-taskbar layout."
            )
            HelpEntry(
                term = "In-app VNC",
                definition = "The built-in viewer. The desktop renders inside Andistro with " +
                    "an on-screen keyboard."
            )
            HelpEntry(
                term = "Termux:X11",
                definition = "An external display option with native touch and keyboard " +
                    "handling, installed separately from its official release."
            )
            HelpEntry(
                term = "PRoot",
                definition = "The layer that lets Linux run without root — software inside " +
                    "sees a normal Linux filesystem."
            )
        }
    }
}

@Composable
private fun HelpEntry(term: String, definition: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = term,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = definition,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun animationsEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    } catch (e: Exception) {
        true
    }
}
// ---------- Data Lists ----------

fun getDistroList(): List<Distro> = listOf(
    Distro("ubuntu", "Ubuntu", "Popular, user-friendly distro with excellent community support and LTS releases", R.drawable.ic_ubuntu, Color(0xFFE95420), "Beginner Friendly"),
    Distro("arch", "Arch Linux", "Lightweight, rolling release distro for advanced users who want full control", R.drawable.ic_arch, Color(0xFF1793D1), "Advanced"),
    Distro("fedora", "Fedora", "Cutting-edge distro with latest technologies, backed by Red Hat", R.drawable.ic_fedora, Color(0xFF294172), "Developer"),
    Distro("debian", "Debian", "Rock-solid stable foundation, parent of Ubuntu and many other distros", R.drawable.ic_debian, Color(0xFFA80030), "Stable"),
    Distro("kali", "Kali Linux", "Penetration testing and security auditing distro with 600+ tools", R.drawable.ic_kali, Color(0xFF557C94), "Security"),
    Distro("alpine", "Alpine Linux", "Tiny, security-focused distro using musl libc and busybox (~5MB)", R.drawable.ic_alpine, Color(0xFF0D597F), "Minimal"),
    Distro("manjaro", "Manjaro", "User-friendly Arch-based distro with graphical installer and held-back updates", R.drawable.ic_manjaro, Color(0xFF1E81B0), "Arch-based"),
    Distro("opensuse", "openSUSE", "Professional-grade distro with YaST config tool, Leap and Tumbleweed", R.drawable.ic_opensuse, Color(0xFF73BA25), "Enterprise"),
    Distro("void", "Void Linux", "Independent rolling distro with runit init, XBPS package manager", R.drawable.ic_void, Color(0xFF2E3440), "Independent"),
    Distro("gentoo", "Gentoo", "Source-based distro with extreme customization via USE flags and Portage", R.drawable.ic_gentoo, Color(0xFF5B4E9A), "Expert")
)

fun getDesktopEnvironments(): List<DesktopEnvironment> = listOf(
    DesktopEnvironment("gnome", "GNOME", "Modern, user-friendly desktop with activities overview and gestures", R.drawable.ic_gnome, Color(0xFF1E88E5), "Full-featured (~2GB)", listOf("ubuntu", "fedora", "debian", "opensuse", "manjaro")),
    DesktopEnvironment("kde", "KDE Plasma", "Highly customizable, feature-rich desktop with modern look", R.drawable.ic_kde, Color(0xFF3DAEE9), "Full-featured (~2GB)", listOf("arch", "manjaro", "opensuse", "fedora", "debian", "kali")),
    DesktopEnvironment("xfce", "XFCE", "Lightweight, fast, and traditional desktop environment", R.drawable.ic_xfce, Color(0xFF4CAF50), "Lightweight (~500MB)", listOf("arch", "debian", "manjaro", "void", "alpine", "gentoo")),
    DesktopEnvironment("lxqt", "LXQt", "Ultra-lightweight Qt-based desktop for older hardware", R.drawable.ic_lxqt, Color(0xFF009688), "Minimal (~300MB)", listOf("alpine", "void", "arch", "debian")),
    DesktopEnvironment("mate", "MATE", "Traditional GNOME 2 fork, stable and familiar interface", R.drawable.ic_mate, Color(0xFF795548), "Medium (~800MB)", listOf("ubuntu", "debian", "arch", "manjaro", "fedora")),
    DesktopEnvironment("cinnamon", "Cinnamon", "Traditional desktop with modern features, Linux Mint's flagship", R.drawable.ic_cinnamon, Color(0xFFE91E63), "Medium (~1GB)", listOf("ubuntu", "debian", "arch", "manjaro", "fedora")),
    DesktopEnvironment("i3", "i3wm", "Tiling window manager for keyboard-driven workflows", R.drawable.ic_i3wm, Color(0xFF607D8B), "Minimal (~100MB)", listOf("arch", "manjaro", "void", "gentoo", "alpine")),
    DesktopEnvironment("sway", "Sway", "Wayland-compatible tiling window manager (i3 clone)", R.drawable.ic_sway, Color(0xFF455A64), "Minimal (~150MB)", listOf("arch", "void", "gentoo", "fedora")),
    DesktopEnvironment("hyprland", "Hyprland", "Dynamic tiling Wayland compositor with animations", R.drawable.ic_hyprland, Color(0xFF880E4F), "Lightweight (~200MB)", listOf("arch", "void", "gentoo")),
    DesktopEnvironment("dwm", "dwm", "Minimal dynamic window manager for experts (suckless)", R.drawable.ic_dwm, Color(0xFF212121), "Tiny (~50MB)", listOf("arch", "void", "gentoo", "alpine"))
)

fun getDistroBase(id: String): String = when (id) {
    "ubuntu", "kali" -> "Debian"
    "arch", "manjaro" -> "Arch"
    "fedora" -> "Red Hat"
    "opensuse" -> "SUSE"
    else -> "Independent"
}

fun getPackageManager(id: String): String = when (id) {
    "ubuntu", "debian", "kali" -> "apt / dpkg"
    "arch", "manjaro" -> "pacman"
    "fedora" -> "dnf / rpm"
    "alpine" -> "apk"
    "opensuse" -> "zypper"
    "void" -> "xbps"
    "gentoo" -> "Portage (emerge)"
    else -> "Unknown"
}

fun getInitSystem(id: String): String = when (id) {
    "alpine" -> "OpenRC"
    "void" -> "runit"
    "gentoo" -> "OpenRC"
    else -> "systemd"
}

// ---------- Activity ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (darkTheme) {
                darkColorScheme(
                    primary = Color(0xFF8ED6B7),
                    onPrimary = Color(0xFF00382B),
                    primaryContainer = Color(0xFF15513F),
                    onPrimaryContainer = Color(0xFFE0E9E2),
                    secondary = Color(0xFFB8CCC1),
                    onSecondary = Color(0xFF24352D),
                    secondaryContainer = Color(0xFF15513F),
                    onSecondaryContainer = Color(0xFFE0E9E2),
                    tertiary = Color(0xFFFFB59F),
                    onTertiary = Color(0xFF5F1608),
                    tertiaryContainer = Color(0xFF7D2D1C),
                    onTertiaryContainer = Color(0xFFFFDAD0),
                    background = Color(0xFF101713),
                    onBackground = Color(0xFFE0E9E2),
                    surface = Color(0xFF101713),
                    onSurface = Color(0xFFE0E9E2),
                    surfaceVariant = Color(0xFF404B44),
                    onSurfaceVariant = Color(0xFFC0CCC4),
                    outline = Color(0xFF8A978E),
                    outlineVariant = Color(0xFF404B44)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF216B59),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFC8EFDF),
                    onPrimaryContainer = Color(0xFF171D19),
                    secondary = Color(0xFF52645A),
                    onSecondary = Color(0xFFFFFFFF),
                    secondaryContainer = Color(0xFFC8EFDF),
                    onSecondaryContainer = Color(0xFF171D19),
                    tertiary = Color(0xFF9C4F3A),
                    onTertiary = Color(0xFFFFFFFF),
                    tertiaryContainer = Color(0xFFFFDAD0),
                    onTertiaryContainer = Color(0xFF3D1008),
                    background = Color(0xFFF7F9F5),
                    onBackground = Color(0xFF171D19),
                    surface = Color(0xFFF7F9F5),
                    onSurface = Color(0xFF171D19),
                    surfaceVariant = Color(0xFFE1E9E3),
                    onSurfaceVariant = Color(0xFF414B45),
                    outline = Color(0xFF717B74),
                    outlineVariant = Color(0xFFC1CAC3)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AndistroApp()
                }
            }
        }
    }
}
