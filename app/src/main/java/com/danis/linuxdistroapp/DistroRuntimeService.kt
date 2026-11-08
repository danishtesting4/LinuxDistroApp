package com.danis.linuxdistroapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import android.system.Os
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tukaani.xz.XZInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream

data class RuntimeUiState(
    val message: String = "",
    val progress: Float = 0f,
    val installing: Boolean = false,
    val installed: Boolean = false,
    val distroId: String = "debian",
    val desktopRunning: Boolean = false,
    val displayMode: DesktopDisplayMode = DesktopDisplayMode.IN_APP_VNC,
    val vncPassword: String? = null,
    val error: String? = null
)

enum class DesktopDisplayMode(val id: String) {
    IN_APP_VNC("in_app_vnc"),
    TERMUX_X11("termux_x11");

    companion object {
        fun fromId(id: String?): DesktopDisplayMode =
            values().firstOrNull { it.id == id } ?: IN_APP_VNC
    }
}

private data class PinnedImage(
    val architecture: String,
    val layerDigest: String,
    val layerSha256: String
)

object DistroRuntimeState {
    val state = kotlinx.coroutines.flow.MutableStateFlow(RuntimeUiState())
}

class DistroRuntimeService : Service() {
    companion object {
        private const val ACTION_INSTALL = "com.danis.linuxdistroapp.INSTALL_DEBIAN"
        private const val ACTION_START_DESKTOP = "com.danis.linuxdistroapp.START_DESKTOP"
        private const val ACTION_STOP_DESKTOP = "com.danis.linuxdistroapp.STOP_DESKTOP"
        private const val ACTION_UNINSTALL = "com.danis.linuxdistroapp.UNINSTALL"
        private const val EXTRA_DISPLAY_MODE = "display_mode"
    private const val EXTRA_DISTRO_ID = "distro_id"
    private const val EXTRA_DE_ID = "desktop_environment_id"
        const val TERMUX_X11_PACKAGE = "com.termux.x11"
        // SHA-256 of the certificate in Termux:X11's upstream debug signing keystore.
        private const val TERMUX_X11_CERT_SHA256 =
            "b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1"
        private const val CHANNEL_ID = "linux_runtime"
        private const val NOTIFICATION_ID = 41
        private const val TAR_BLOCK_SIZE = 512
        private const val MINIMUM_FREE_BYTES = 2L * 1024 * 1024 * 1024
        private const val NETWORK_TIMEOUT_MILLIS = 30_000
        private const val MAX_DOWNLOAD_ATTEMPTS = 5
        private const val DOWNLOAD_RETRY_DELAY_MILLIS = 1_000L
        private const val EXPECTED_LAYER_SIZE_BYTES = 100_000_000L
        private const val APT_LIST_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
        private val ARM64_IMAGE = PinnedImage(
            "arm64",
            "sha256:2d66c782e4847b8495b17a4b86be1159aa13d140fe4fd17cb1d536953d5114c6",
            "2d66c782e4847b8495b17a4b86be1159aa13d140fe4fd17cb1d536953d5114c6"
        )
        private val X86_64_IMAGE = PinnedImage(
            "amd64",
            "sha256:dd051e1b8c92ff101fa3071ab97ea4924885942f15644e01c3dc095764e863e3",
            "dd051e1b8c92ff101fa3071ab97ea4924885942f15644e01c3dc095764e863e3"
        )

        fun install(context: Context, distroId: String = "debian", desktopEnvironment: String = "xfce"): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(PowerManager::class.java)
                if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) return false
            }
            start(context, ACTION_INSTALL, distroId = distroId, desktopEnvironment = desktopEnvironment)
            return true
        }

        fun startDesktop(
            context: Context,
            distroId: String = "debian",
            displayMode: DesktopDisplayMode = DesktopDisplayMode.IN_APP_VNC,
            desktopEnvironment: String? = null
        ) = start(context, ACTION_START_DESKTOP, displayMode, distroId, desktopEnvironment)

        fun stopDesktop(context: Context) = start(context, ACTION_STOP_DESKTOP)

        fun uninstall(context: Context, distroId: String) =
            start(context, ACTION_UNINSTALL, distroId = distroId)

        /**
         * An installation is paused when the container directory holds partial
         * work (an extracted base, a partial extraction, or markers) but the
         * final install-complete marker is missing. Re-running the install
         * resumes from where it stopped.
         */
        fun isDistroInstallPaused(context: Context, distroId: String): Boolean {
            if (DistroInstallProfiles.get(distroId) == null) return false
            if (isDistroInstalled(context, distroId)) return false
            val container = File(context.filesDir, distroId)
            return container.isDirectory && container.list()?.isNotEmpty() == true
        }

        fun isTermuxX11Installed(context: Context): Boolean =
            try {
                context.packageManager.getPackageInfo(TERMUX_X11_PACKAGE, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }

        fun isDebianInstalled(context: Context): Boolean =
            isDistroInstalled(context, "debian")

        fun isDistroInstalled(context: Context, distroId: String): Boolean {
            if (DistroInstallProfiles.get(distroId) == null) return false
            val rootfs = File(context.filesDir, "$distroId/rootfs")
            return File(context.filesDir, "$distroId/install-complete").isFile &&
                rootfs.resolve(if (distroId == "debian") "usr/bin/apt-get" else "bin/sh").isFile
        }

        private fun start(
            context: Context,
            action: String,
            displayMode: DesktopDisplayMode? = null,
            distroId: String? = null,
            desktopEnvironment: String? = null
        ) {
            val intent = Intent(context, DistroRuntimeService::class.java).setAction(action)
            displayMode?.let { intent.putExtra(EXTRA_DISPLAY_MODE, it.id) }
            distroId?.let { intent.putExtra(EXTRA_DISTRO_ID, it) }
            desktopEnvironment?.let { intent.putExtra(EXTRA_DE_ID, it) }
            if (action == ACTION_STOP_DESKTOP) {
                context.startService(intent)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installJob: Job? = null
    private var uninstallJob: Job? = null
    private var desktopProcess: Process? = null
    private var termuxX11Process: Process? = null
    private lateinit var rootfs: File
    private lateinit var proot: File
    private lateinit var loader: File
    private var currentDistroId = "debian"
    private var currentDesktopEnvironment = "xfce"

    override fun onCreate() {
        super.onCreate()
        rootfs = File(filesDir, "debian/rootfs")
        proot = File(applicationInfo.nativeLibraryDir, "libproot.so")
        loader = File(applicationInfo.nativeLibraryDir, "libproot_loader.so")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INSTALL -> {
                val distroId = intent.getStringExtra(EXTRA_DISTRO_ID) ?: "debian"
                val desktopEnvironment = intent.getStringExtra(EXTRA_DE_ID) ?: "xfce"
                val profile = DistroInstallProfiles.get(distroId)
                if (profile == null) {
                    DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                        message = "Unsupported distribution",
                        installing = false,
                        error = "No installer is available for $distroId."
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (installJob?.isActive == true) {
                    DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                        message = "Another distribution is already installing",
                        error = "Wait for the current installation to finish before starting another."
                    )
                    return START_REDELIVER_INTENT
                }
                if (uninstallJob?.isActive == true) {
                    DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                        message = "An uninstall is running",
                        error = "Wait for the uninstall to finish before starting an installation."
                    )
                    return START_REDELIVER_INTENT
                }
                currentDistroId = distroId
                currentDesktopEnvironment = desktopEnvironment
                rootfs = File(filesDir, "$distroId/rootfs")
                DistroRuntimeState.state.value = RuntimeUiState(
                message = "Preparing $distroId + $desktopEnvironment",
                    installing = true,
                    installed = isDistroInstalled(this, distroId),
                    distroId = distroId
                )
                promoteToForeground("Preparing ${distroId} + XFCE", ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                if (installJob?.isActive != true) {
                    installJob = serviceScope.launch {
                        try {
                        RuntimeLog.info(this@DistroRuntimeService, "Starting $distroId + $currentDesktopEnvironment installation.")
                        if (distroId == "debian" && currentDesktopEnvironment == "xfce") installDebian() else installCatalogDistro(profile)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            RuntimeLog.error(this@DistroRuntimeService, "$distroId installation failed.", error)
                            DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                                message = "Installation failed",
                                installing = false,
                                error = error.message ?: error.javaClass.simpleName
                            )
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            }

            ACTION_START_DESKTOP -> {
                val distroId = intent.getStringExtra(EXTRA_DISTRO_ID) ?: "debian"
                val desktopEnvironment = intent.getStringExtra(EXTRA_DE_ID) ?: run { val m = File(filesDir, "$distroId/desktop-environment"); if (m.isFile) m.readText().trim() else "xfce" }
                check(DistroInstallProfiles.get(distroId) != null) {
                    "No desktop runtime is available for $distroId."
                }
                if (installJob?.isActive == true) {
                    DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                        message = "A distribution is still installing",
                        error = "Wait for the current installation to finish before starting a desktop."
                    )
                    return START_NOT_STICKY
                }
                if (uninstallJob?.isActive == true) {
                    DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                        message = "An uninstall is running",
                        error = "Wait for the uninstall to finish before starting a desktop."
                    )
                    return START_NOT_STICKY
                }
                if (currentDistroId != distroId &&
                    (desktopProcess?.isAlive == true || termuxX11Process?.isAlive == true)
                ) {
                    stopDesktopProcesses()
                }
                currentDistroId = distroId
                currentDesktopEnvironment = desktopEnvironment
                rootfs = File(filesDir, "$distroId/rootfs")
                val displayMode = DesktopDisplayMode.fromId(
                    intent.getStringExtra(EXTRA_DISPLAY_MODE)
                )
                DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                message = "Starting the $distroId ($desktopEnvironment) desktop",
                    distroId = distroId,
                    installed = isDistroInstalled(this, distroId),
                    desktopRunning = false,
                    displayMode = displayMode,
                    vncPassword = null,
                    error = null
                )
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                promoteToForeground("Starting the $distroId desktop", type)
                serviceScope.launch {
                    try {
                        RuntimeLog.info(
                            this@DistroRuntimeService,
                            "Starting the $distroId desktop in ${displayMode.id} mode."
                        )
                        when (displayMode) {
                            DesktopDisplayMode.IN_APP_VNC -> startVncDesktop()
                            DesktopDisplayMode.TERMUX_X11 -> startTermuxX11Desktop()
                        }
                    } catch (error: Exception) {
                        stopDesktopProcesses()
                        RuntimeLog.error(this@DistroRuntimeService, "Unable to start the $distroId desktop.", error)
                        DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                            message = "Unable to start the desktop",
                            desktopRunning = false,
                            error = error.message ?: error.javaClass.simpleName
                        )
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }

            ACTION_STOP_DESKTOP -> {
                stopDesktopProcesses()
                DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                    message = "Desktop stopped",
                    distroId = currentDistroId,
                    desktopRunning = false,
                    displayMode = DesktopDisplayMode.IN_APP_VNC,
                    vncPassword = null
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_UNINSTALL -> {
                val distroId = intent.getStringExtra(EXTRA_DISTRO_ID) ?: currentDistroId
                promoteToForeground(
                    "Uninstalling $distroId",
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
                uninstallJob = serviceScope.launch { uninstallDistro(distroId) }
            }
        }
        return if (intent?.action == ACTION_INSTALL) START_REDELIVER_INTENT else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        installJob?.cancel()
        uninstallJob?.cancel()
        stopDesktopProcesses()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Removes a distribution's filesystem and download caches. Refuses to run
     * while an installation is active, and stops this distribution's desktop
     * processes first so nothing keeps writing into the directory.
     */
    private suspend fun uninstallDistro(distroId: String) {
        try {
            if (installJob?.isActive == true) {
                DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                    message = "An installation is still running",
                    error = "Wait for the current installation to finish before uninstalling $distroId."
                )
                return
            }
            if (currentDistroId == distroId &&
                (desktopProcess?.isAlive == true || termuxX11Process?.isAlive == true)
            ) {
                stopDesktopProcesses()
            }

            RuntimeLog.info(this, "Uninstalling $distroId.")
            val before = DistroRuntimeState.state.value
            DistroRuntimeState.state.value = before.copy(
                message = "Removing $distroId files",
                installing = false,
                desktopRunning = if (before.distroId == distroId) false else before.desktopRunning,
                vncPassword = if (before.distroId == distroId) null else before.vncPassword,
                error = null
            )

            val container = File(filesDir, distroId)
            if (container.exists() && !container.deleteRecursively()) {
                error("Unable to remove the $distroId files from storage.")
            }
            File(cacheDir, "oci-layers/$distroId").deleteRecursively()
            cacheDir.listFiles()
                ?.filter {
                    it.name.startsWith("rootfs-$distroId-") ||
                        (distroId == "debian" && it.name.startsWith("debian-"))
                }
                ?.forEach { it.deleteRecursively() }

            RuntimeLog.info(this, "Uninstalled $distroId.")
            val after = DistroRuntimeState.state.value
            DistroRuntimeState.state.value = if (after.distroId == distroId) {
                RuntimeUiState(message = "$distroId uninstalled", distroId = distroId)
            } else {
                after.copy(message = "$distroId uninstalled")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            RuntimeLog.error(this, "Unable to uninstall $distroId.", error)
            DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
                message = "Uninstall failed",
                installing = false,
                error = error.message ?: error.javaClass.simpleName
            )
        } finally {
            if (installJob?.isActive != true &&
                desktopProcess?.isAlive != true &&
                termuxX11Process?.isAlive != true
            ) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun installDebian() {
        val state = DistroRuntimeState.state.value
        DistroRuntimeState.state.value = state.copy(
            message = "Checking device storage",
            progress = 0f,
            installing = true,
            installed = false,
            error = null
        )
        check(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }) {
            "This build supports 64-bit ARM and x86-64 devices only."
        }
        check(StatFs(filesDir.path).availableBytes >= MINIMUM_FREE_BYTES) {
            "Free at least 2 GB of storage before installing Debian and XFCE."
        }
        prepareRuntimeBinaries()

        val completeMarker = File(rootfs.parentFile, "install-complete")
        if (completeMarker.isFile &&
            rootfs.resolve("usr/bin/apt-get").isFile &&
            rootfs.resolve("usr/bin/tigervncpasswd").isFile
        ) {
            DistroRuntimeState.state.value = RuntimeUiState(
                message = "Debian + minimal XFCE is installed",
                progress = 1f,
                installed = true
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (completeMarker.isFile && rootfs.resolve("usr/bin/apt-get").isFile) {
            RuntimeLog.info(this, "Repairing the completed Debian install: TigerVNC password utility is missing.")
        }

        val stagedMarker = File(rootfs.parentFile, "base-ready")
        if (!rootfs.resolve("usr/bin/apt-get").isFile ||
            (!stagedMarker.isFile && !completeMarker.isFile)
        ) {
            downloadAndExtractBase(checkNotNull(rootfs.parentFile))
            check(rootfs.resolve("usr/bin/apt-get").isFile) {
                "The downloaded Debian base image is missing apt."
            }
            stagedMarker.writeText("debian-bookworm-slim\n")
        }

        updateProgress("Resolving the device DNS settings", 0.38f)
        configureDebian()

        ensureAptListsCurrent()
        runProot(
            listOf(
                "/bin/sh",
                "-c",
                "export DEBIAN_FRONTEND=noninteractive; dpkg --configure -a"
            )
        )

        val lastAptActivity = readLastAptActivity()
        updateProgress(
            if (lastAptActivity == null) {
                "Installing minimal XFCE and the VNC server"
            } else {
                "Resuming XFCE installation from APT cache: ${lastAptActivity.take(70)}"
            },
            0.43f
        )
        runProot(
            listOf(
                "/bin/sh",
                "-c",
                "export DEBIAN_FRONTEND=noninteractive; " +
                    "apt-get -o Acquire::Retries=3 install -y --no-install-recommends " +
                    "dbus-x11 xfce4-session xfce4-panel xfwm4 xfce4-settings " +
                    "xfdesktop4 xfce4-terminal thunar tigervnc-standalone-server " +
                    "tigervnc-tools xterm xfonts-base"
            )
        ) { line ->
            if (line.startsWith("Get:") || line.startsWith("Setting up ")) {
                saveLastAptActivity(line)
                updateProgress("Installing $currentDesktopEnvironment: ${line.take(90)}", 0.6f)
            }
        }

        check(rootfs.resolve("usr/bin/vncserver").exists() || rootfs.resolve("usr/bin/tigervncserver").exists()) {
            "Package setup finished without installing the VNC server."
        }
        check(rootfs.resolve("usr/bin/tigervncpasswd").isFile) {
            "Package setup finished without installing the TigerVNC password utility."
        }
        completeMarker.writeText("debian-bookworm-slim-xfce\n")
        lastAptActivityFile().delete()
        stagedMarker.delete()
        DistroRuntimeState.state.value = RuntimeUiState(
            message = "Debian + minimal XFCE is installed",
            progress = 1f,
            installed = true
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun installCatalogDistro(profile: DistroInstallProfile) {
        DistroRuntimeState.state.value = RuntimeUiState(
            message = "Checking device storage",
            progress = 0f,
            installing = true,
            distroId = profile.id
        )
        check(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }) {
            "This build supports 64-bit ARM and x86-64 devices only."
        }
        check(StatFs(filesDir.path).availableBytes >= profile.minimumFreeBytes) {
            "Free at least ${profile.minimumFreeBytes / (1024 * 1024 * 1024)} GB of storage " +
                "before installing ${profile.id} and XFCE."
        }
        prepareRuntimeBinaries()

        val containerDirectory = checkNotNull(rootfs.parentFile)
        val completeMarker = File(containerDirectory, "install-complete")
        if (completeMarker.isFile &&
            rootfs.resolve("usr/bin/startxfce4").isFile &&
            hasVncPasswordUtility()
        ) {
            DistroRuntimeState.state.value = RuntimeUiState(
            message = "${profile.id} + $currentDesktopEnvironment is installed",
                progress = 1f,
                installed = true,
                distroId = profile.id
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val stagedMarker = File(containerDirectory, "base-ready")
        if (!stagedMarker.isFile || !rootfs.resolve("bin/sh").isFile) {
            if (profile.lxcImagePath != null) {
                downloadAndExtractLxcBase(profile, containerDirectory)
            } else {
                downloadAndExtractOciBase(profile, containerDirectory)
            }
            stagedMarker.writeText("${profile.id}-rootfs\n")
        }

        updateProgress("Resolving device DNS settings", 0.38f)
        configureDistro()
        val commands = listOfNotNull(
            profile.preInstallCommand.takeIf(String::isNotBlank),
            "${profile.installCommand} ${profile.packagesFor(currentDesktopEnvironment).joinToString(" ")}"
        )
        runProot(
            listOf(
                "/bin/sh",
                "-c",
                "export DEBIAN_FRONTEND=noninteractive; " +
                    commands.joinToString(" && ")
            )
        ) { line ->
            if (line.contains("install", ignoreCase = true) ||
                line.contains("setting up", ignoreCase = true) ||
                line.contains("installed", ignoreCase = true)
            ) {
                updateProgress("Installing XFCE: ${line.take(90)}", 0.6f)
            }
        }

        check(rootfs.resolve(desktopLauncherBinary(currentDesktopEnvironment).removePrefix("/")).isFile) {
            "${profile.id} package setup finished without installing the $currentDesktopEnvironment session launcher."
        }
        check(
            rootfs.resolve("usr/bin/vncserver").isFile ||
                rootfs.resolve("usr/bin/tigervncserver").isFile
        ) {
            "${profile.id} package setup finished without installing a VNC server."
        }
        check(hasVncPasswordUtility()) {
            "${profile.id} package setup finished without installing a VNC password utility."
        }
        completeMarker.writeText("${profile.id}-$currentDesktopEnvironment\n")
        stagedMarker.delete()
        DistroRuntimeState.state.value = RuntimeUiState(
            message = "${profile.id} + minimal XFCE is installed",
            progress = 1f,
            installed = true,
            distroId = profile.id
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun downloadAndExtractOciBase(
        profile: DistroInstallProfile,
        containerDirectory: File
    ) {
        val repository = checkNotNull(profile.imageRepository) {
            "No OCI image is configured for ${profile.id}."
        }
        val imageTag = checkNotNull(profile.imageTag) {
            "No OCI image tag is configured for ${profile.id}."
        }
        val architecture = if (Build.SUPPORTED_ABIS.first() == "arm64-v8a") "arm64" else "amd64"
        val token = dockerRegistryToken(repository)
        val initialManifest = dockerManifest(
            repository,
            imageTag,
            token
        )
        val manifest = if (initialManifest.has("manifests")) {
            val descriptors = initialManifest.getJSONArray("manifests")
            val descriptor = (0 until descriptors.length())
                .map(descriptors::getJSONObject)
                .firstOrNull {
                    val platform = it.optJSONObject("platform") ?: return@firstOrNull false
                    platform.optString("os") == "linux" &&
                        platform.optString("architecture") == architecture
                } ?: error("No Linux $architecture image is available for ${profile.id}.")
            dockerManifest(repository, descriptor.getString("digest"), token)
        } else {
            initialManifest
        }

        val layers = manifest.getJSONArray("layers")
        check(layers.length() > 0) { "${profile.id} image contains no root filesystem layers." }
        val temporaryRootfs = File(containerDirectory, "rootfs.partial")
        temporaryRootfs.deleteRecursively()
        check(temporaryRootfs.mkdirs()) { "Unable to prepare the ${profile.id} filesystem." }
        val layerCache = File(cacheDir, "oci-layers/${profile.id}")
        check(layerCache.isDirectory || layerCache.mkdirs()) {
            "Unable to prepare the ${profile.id} download cache."
        }

        for (index in 0 until layers.length()) {
            val layer = layers.getJSONObject(index)
            val digest = layer.getString("digest")
            val mediaType = layer.optString("mediaType")
            check(!mediaType.contains("zstd", ignoreCase = true)) {
                "The ${profile.id} image uses an unsupported compressed layer."
            }
            val layerFile = File(layerCache, digest.substringAfter(':'))
            if (!layerFile.isFile || sha256(layerFile) != digest.substringAfter(':')) {
                updateProgress(
                    "Downloading ${profile.id} base layer ${index + 1}/${layers.length()}",
                    0.02f + 0.28f * index / layers.length()
                )
                downloadOciBlob(repository, digest, token, layerFile)
            }
            updateProgress(
                "Extracting ${profile.id} base layer ${index + 1}/${layers.length()}",
                0.32f + 0.06f * (index + 1) / layers.length()
            )
            extractLayer(layerFile, temporaryRootfs, mediaType)
            layerFile.delete()
        }

        check(temporaryRootfs.resolve("bin/sh").isFile) {
            "The ${profile.id} image did not contain a usable root filesystem."
        }
        rootfs.deleteRecursively()
        check(temporaryRootfs.renameTo(rootfs)) {
            "Unable to finalize the ${profile.id} filesystem."
        }
    }

    private suspend fun downloadAndExtractLxcBase(
        profile: DistroInstallProfile,
        containerDirectory: File
    ) {
        val architecture = if (Build.SUPPORTED_ABIS.first() == "arm64-v8a") "arm64" else "x86_64"
        val imagePath = checkNotNull(profile.lxcImagePath) {
            "No LXC image source is configured for ${profile.id}."
        }
        val baseUrl = "https://images.linuxcontainers.org/images/$imagePath/$architecture/default/"
        val index = readUrlText(baseUrl)
        val timestamp = Regex("href=\"(\\d{8}_\\d{2}(?::|%3A)\\d{2})/\"")
            .findAll(index)
            .map { it.groupValues[1] }
            .maxOrNull()
            ?: error("No $architecture root filesystem is available for ${profile.id}.")
        val snapshotUrl = "$baseUrl${timestamp.replace(":", "%3A")}/"
        val checksums = readUrlText("${snapshotUrl}SHA256SUMS")
        val checksumPattern = Regex("([a-fA-F0-9]{64})\\s+\\*?rootfs\\.tar\\.xz")
        val expectedHash = checksums.lineSequence()
            .mapNotNull { checksumPattern.matchEntire(it)?.groupValues?.get(1) }
            .firstOrNull()
            ?.lowercase()
            ?: error("The ${profile.id} image did not provide a rootfs checksum.")

        val archive = File(cacheDir, "rootfs-${profile.id}-$architecture-$expectedHash.tar.xz")
        if (!archive.isFile || sha256(archive) != expectedHash) {
            updateProgress("Downloading ${profile.id} base filesystem", 0.02f)
            downloadResumable("${snapshotUrl}rootfs.tar.xz", archive, expectedHash)
        }

        updateProgress("Extracting ${profile.id} base filesystem", 0.32f)
        val temporaryRootfs = File(containerDirectory, "rootfs.partial")
        temporaryRootfs.deleteRecursively()
        check(temporaryRootfs.mkdirs()) { "Unable to prepare the ${profile.id} filesystem." }
        extractLayer(archive, temporaryRootfs, "application/x-xz")
        check(temporaryRootfs.resolve("bin/sh").isFile) {
            "The ${profile.id} archive did not contain a usable root filesystem."
        }
        rootfs.deleteRecursively()
        check(temporaryRootfs.renameTo(rootfs)) {
            "Unable to finalize the ${profile.id} filesystem."
        }
        archive.delete()
    }

    private fun readUrlText(url: String): String {
        val connection = URL(url).openConnection()
        connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
        connection.readTimeout = NETWORK_TIMEOUT_MILLIS
        return connection.getInputStream().bufferedReader().use { it.readText() }
    }

    private suspend fun downloadResumable(url: String, destination: File, expectedHash: String) {
        repeat(MAX_DOWNLOAD_ATTEMPTS) { attempt ->
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                val offset = destination.length()
                if (offset > 0L) connection.setRequestProperty("Range", "bytes=$offset-")
                connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
                connection.readTimeout = NETWORK_TIMEOUT_MILLIS
                try {
                    val responseCode = connection.responseCode
                    if (responseCode == 416 && offset > 0L) {
                        destination.delete()
                        throw java.io.IOException("The partial rootfs download could not be resumed.")
                    }
                    val append = responseCode == HttpURLConnection.HTTP_PARTIAL
                    if (responseCode != HttpURLConnection.HTTP_OK && !append) {
                        throw DownloadHttpException(
                            responseCode,
                            "Root filesystem download failed with HTTP $responseCode."
                        )
                    }
                    if (append && contentRangeStart(connection.getHeaderField("Content-Range")) != offset) {
                        destination.delete()
                        throw java.io.IOException("The server returned an invalid rootfs resume range.")
                    }
                    FileOutputStream(destination, append).use { output ->
                        connection.inputStream.use { input -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }
                if (sha256(destination) == expectedHash) return
                destination.delete()
                throw java.io.IOException("Root filesystem checksum verification failed.")
            } catch (error: java.io.IOException) {
                if (!isRetryableDownloadError(error) || attempt == MAX_DOWNLOAD_ATTEMPTS - 1) {
                    throw java.io.IOException(
                        "Root filesystem download failed after ${attempt + 1} attempt(s): " +
                            (error.message ?: "connection interrupted."),
                        error
                    )
                }
                delay(DOWNLOAD_RETRY_DELAY_MILLIS * (attempt + 1))
            }
        }
        error("Unreachable rootfs download retry state")
    }

    private fun dockerRegistryToken(repository: String): String {
        val connection = URL(
            "https://auth.docker.io/token?service=registry.docker.io&scope=repository%3A" +
                "$repository%3Apull"
        ).openConnection() as HttpURLConnection
        connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
        connection.readTimeout = NETWORK_TIMEOUT_MILLIS
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Unable to authenticate a public ${repository.substringAfterLast('/')} image."
            }
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            return json.optString("token").ifBlank { json.optString("access_token") }
                .also { check(it.isNotBlank()) { "Docker Hub did not provide an image token." } }
        } finally {
            connection.disconnect()
        }
    }

    private fun dockerManifest(repository: String, reference: String, token: String): JSONObject {
        val connection = URL(
            "https://registry-1.docker.io/v2/$repository/manifests/$reference"
        ).openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty(
            "Accept",
            listOf(
                "application/vnd.oci.image.index.v1+json",
                "application/vnd.docker.distribution.manifest.list.v2+json",
                "application/vnd.oci.image.manifest.v1+json",
                "application/vnd.docker.distribution.manifest.v2+json"
            ).joinToString(", ")
        )
        connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
        connection.readTimeout = NETWORK_TIMEOUT_MILLIS
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Unable to fetch the ${repository.substringAfterLast('/')} image manifest."
            }
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadOciBlob(
        repository: String,
        digest: String,
        token: String,
        destination: File
    ) {
        val expectedHash = digest.substringAfter(':')
        repeat(MAX_DOWNLOAD_ATTEMPTS) { attempt ->
            try {
                val connection = URL(
                    "https://registry-1.docker.io/v2/$repository/blobs/$digest"
                ).openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $token")
                val offset = destination.length()
                if (offset > 0L) connection.setRequestProperty("Range", "bytes=$offset-")
                connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
                connection.readTimeout = NETWORK_TIMEOUT_MILLIS
                try {
                    val responseCode = connection.responseCode
                    if (responseCode == 416 && offset > 0L) {
                        destination.delete()
                        throw java.io.IOException("The partial image layer could not be resumed.")
                    }
                    val append = responseCode == HttpURLConnection.HTTP_PARTIAL
                    if (responseCode != HttpURLConnection.HTTP_OK && !append) {
                        throw DownloadHttpException(
                            responseCode,
                            "Image layer download failed with HTTP $responseCode."
                        )
                    }
                    if (append) {
                        if (contentRangeStart(connection.getHeaderField("Content-Range")) != offset) {
                            destination.delete()
                            throw java.io.IOException("The image server returned an invalid resumed range.")
                        }
                    }
                    FileOutputStream(destination, append).use { output ->
                        connection.inputStream.use { input -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }
                if (sha256(destination) == expectedHash) return
                destination.delete()
                throw java.io.IOException("Image layer checksum verification failed.")
            } catch (error: java.io.IOException) {
                if (!isRetryableDownloadError(error) || attempt == MAX_DOWNLOAD_ATTEMPTS - 1) {
                    throw java.io.IOException(
                        "Image layer download failed after ${attempt + 1} attempt(s): " +
                            (error.message ?: "connection interrupted."),
                        error
                    )
                }
                delay(DOWNLOAD_RETRY_DELAY_MILLIS * (attempt + 1))
            }
        }
        error("Unreachable OCI layer download retry state")
    }

    private suspend fun ensureAptListsCurrent() {
        val stamp = aptUpdateStamp()
        val updatedAt = stamp.takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.toLongOrNull()
        val age = updatedAt?.let { System.currentTimeMillis() - it }
        if (age != null && age in 0 until APT_LIST_MAX_AGE_MILLIS) {
            updateProgress("Reusing cached Debian package lists", 0.40f)
            RuntimeLog.info(this, "Reusing Debian package lists from the last successful update.")
            return
        }

        updateProgress("Refreshing Debian package lists", 0.40f)
        runProot(
            listOf(
                "/bin/sh",
                "-c",
                "export DEBIAN_FRONTEND=noninteractive; apt-get -o Acquire::Retries=3 update"
            )
        )
        stamp.parentFile?.mkdirs()
        stamp.writeText(System.currentTimeMillis().toString())
        RuntimeLog.info(this, "Saved the successful APT package-list update checkpoint.")
    }

    private fun aptUpdateStamp(): File =
        rootfs.resolve("var/lib/andistro/apt-update-success")

    private fun lastAptActivityFile(): File =
        rootfs.resolve("var/lib/andistro/last-apt-activity")

    private fun readLastAptActivity(): String? =
        lastAptActivityFile()
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun saveLastAptActivity(line: String) {
        val checkpoint = lastAptActivityFile()
        checkpoint.parentFile?.mkdirs()
        checkpoint.writeText(line)
    }

    private suspend fun downloadAndExtractBase(containerDirectory: File) {
        val abi = Build.SUPPORTED_ABIS.first { it == "arm64-v8a" || it == "x86_64" }
        val image = if (abi == "arm64-v8a") ARM64_IMAGE else X86_64_IMAGE
        val layer = File(cacheDir, "debian-${image.architecture}-${image.layerSha256}.tar.gz")
        if (!layer.isFile || sha256(layer) != image.layerSha256) {
            updateProgress("Downloading the verified Debian ${image.architecture} base", 0.02f)
            downloadLayer(image, layer)
        }

        updateProgress("Extracting Debian's base filesystem", 0.34f)
        val temporaryRootfs = File(containerDirectory, "rootfs.partial")
        RuntimeLog.info(this, "Extracting verified ${image.architecture} Debian layer into ${temporaryRootfs.absolutePath}.")
        temporaryRootfs.deleteRecursively()
        check(temporaryRootfs.mkdirs()) { "Unable to prepare the Debian filesystem." }
        extractLayer(layer, temporaryRootfs, "application/gzip")
        check(temporaryRootfs.resolve("usr/bin/apt-get").isFile) {
            "The Debian archive did not contain a valid root filesystem."
        }
        rootfs.deleteRecursively()
        check(temporaryRootfs.renameTo(rootfs)) { "Unable to finalize the Debian filesystem." }
        RuntimeLog.info(this, "Debian base filesystem extraction completed.")
        layer.delete()
    }

    private suspend fun downloadLayer(image: PinnedImage, destination: File) {
        repeat(MAX_DOWNLOAD_ATTEMPTS) { attempt ->
            try {
                downloadLayerAttempt(image, destination)
                if (sha256(destination) == image.layerSha256) return
                destination.delete()
                throw java.io.IOException("Debian archive checksum verification failed; restarting download.")
            } catch (error: java.io.IOException) {
                if (!isRetryableDownloadError(error) || attempt == MAX_DOWNLOAD_ATTEMPTS - 1) {
                    throw java.io.IOException(
                        "Debian download failed after ${attempt + 1} attempt(s): " +
                            (error.message ?: "connection interrupted."),
                        error
                    )
                }
                updateProgress(
                    "Download interrupted; retrying (${attempt + 1}/$MAX_DOWNLOAD_ATTEMPTS)",
                    0.02f + (destination.length().toFloat() / EXPECTED_LAYER_SIZE_BYTES)
                        .coerceIn(0f, 1f) * 0.30f
                )
                delay(DOWNLOAD_RETRY_DELAY_MILLIS * (attempt + 1))
            }
        }
        error("Unreachable download retry state")
    }

    private suspend fun downloadLayerAttempt(image: PinnedImage, destination: File) {
        val tokenConnection = URL(
            "https://auth.docker.io/token?service=registry.docker.io&scope=repository%3Alibrary%2Fdebian%3Apull"
        ).openConnection() as HttpURLConnection
        tokenConnection.connectTimeout = NETWORK_TIMEOUT_MILLIS
        tokenConnection.readTimeout = NETWORK_TIMEOUT_MILLIS
        val tokenJson = try {
            val responseCode = tokenConnection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw DownloadHttpException(responseCode, "Unable to authenticate a public Debian download.")
            }
            tokenConnection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            tokenConnection.disconnect()
        }
        val token = tokenJson.optString("token").ifBlank { tokenJson.optString("access_token") }
        check(token.isNotBlank()) { "Docker Hub did not provide a public image token." }

        val connection = URL("https://registry-1.docker.io/v2/library/debian/blobs/${image.layerDigest}")
            .openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
        connection.readTimeout = NETWORK_TIMEOUT_MILLIS
        val offset = destination.length()
        if (offset > 0L) connection.setRequestProperty("Range", "bytes=$offset-")
        try {
            val responseCode = connection.responseCode
            if (responseCode == 416 && offset > 0L) {
                destination.delete()
                throw java.io.IOException("The partial Debian download could not be resumed.")
            }
            if (responseCode != HttpURLConnection.HTTP_OK &&
                responseCode != HttpURLConnection.HTTP_PARTIAL
            ) {
                throw DownloadHttpException(responseCode, "Debian download failed with HTTP $responseCode.")
            }

            val append = responseCode == HttpURLConnection.HTTP_PARTIAL
            val contentRange = connection.getHeaderField("Content-Range")
            if (append && contentRangeStart(contentRange) != offset) {
                destination.delete()
                throw java.io.IOException("The server returned an invalid resumed download range.")
            }
            val startingBytes = if (append) offset else 0L
            val contentLength = connection.contentLengthLong
            val totalBytes = contentRangeTotal(contentRange) ?: when {
                contentLength >= 0L -> startingBytes + contentLength
                else -> EXPECTED_LAYER_SIZE_BYTES
            }
            var downloadedBytes = startingBytes
            FileOutputStream(destination, append).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        if (totalBytes > 0L) {
                            val percentage = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            updateProgress(
                                "Downloading Debian: ${(percentage * 100).toInt()}%",
                                0.02f + percentage * 0.30f
                            )
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun isRetryableDownloadError(error: java.io.IOException): Boolean = when (error) {
        is DownloadHttpException -> error.statusCode == 408 ||
            error.statusCode == 429 ||
            error.statusCode >= 500
        is SocketTimeoutException, is SocketException -> true
        else -> error !is DownloadHttpException
    }

    private fun contentRangeStart(contentRange: String?): Long? =
        contentRange?.substringAfter("bytes ", missingDelimiterValue = "")
            ?.substringBefore('-')
            ?.toLongOrNull()

    private fun contentRangeTotal(contentRange: String?): Long? =
        contentRange?.substringAfter('/', missingDelimiterValue = "")
            ?.toLongOrNull()

    private class DownloadHttpException(
        val statusCode: Int,
        message: String
    ) : java.io.IOException(message)

    private fun extractLayer(archive: File, destinationRoot: File, mediaType: String) {
        val root = destinationRoot.canonicalFile
        val pendingHardLinks = mutableListOf<Triple<File, File, Int>>()
        val fileModes = mutableListOf<Pair<File, Int>>()
        val directoryModes = mutableListOf<Pair<File, Int>>()
        val archiveInput = FileInputStream(archive).let { input ->
            when {
                mediaType.contains("gzip", ignoreCase = true) -> GZIPInputStream(input, 64 * 1024)
                mediaType.contains("xz", ignoreCase = true) -> XZInputStream(input)
                else -> input
            }
        }
        DataInputStream(archiveInput).use { input ->
            val header = ByteArray(TAR_BLOCK_SIZE)
            val globalPax = mutableMapOf<String, String>()
            var pendingPax = emptyMap<String, String>()
            var nextPath: String? = null
            var nextLink: String? = null
            while (true) {
                val count = runCatching { input.readFully(header); TAR_BLOCK_SIZE }.getOrElse {
                    if (it is java.io.EOFException) 0 else throw it
                }
                if (count == 0 || header.all { it == 0.toByte() }) break

                val entryName = tarString(header, 0, 100)
                val prefix = tarString(header, 345, 155)
                val rawPath = if (prefix.isEmpty()) entryName else "$prefix/$entryName"
                val size = tarOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                if (type == 'L' || type == 'K' || type == 'x' || type == 'g') {
                    check(size <= 1024 * 1024) { "A tar metadata record is too large." }
                    val metadata = ByteArray(size.toInt())
                    input.readFully(metadata)
                    val remainder = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE
                    skipExactly(input, remainder)
                    when (type) {
                        'L' -> nextPath = metadata.decodeToString().trimEnd('\u0000', '\n')
                        'K' -> nextLink = metadata.decodeToString().trimEnd('\u0000', '\n')
                        'x' -> pendingPax = parsePaxRecords(metadata)
                        'g' -> globalPax.putAll(parsePaxRecords(metadata))
                    }
                    continue
                }

                val pax = globalPax + pendingPax
                val path = pax["path"] ?: nextPath ?: rawPath
                val linkName = pax["linkpath"] ?: nextLink ?: tarString(header, 157, 100)
                pendingPax = emptyMap()
                nextPath = null
                nextLink = null
                val leaf = path.substringAfterLast('/')
                if (leaf.startsWith(".wh.")) {
                    applyLayerWhiteout(root, path)
                    skipExactly(input, size)
                    val whiteoutRemainder = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE
                    skipExactly(input, whiteoutRemainder)
                    continue
                }
                val entry = safeTarPath(root, path)
                val mode = tarOctal(header, 100, 8).toInt()

                when (type) {
                    '0', '\u0000' -> {
                        ensureSafeParent(root, entry)
                        entry.parentFile?.mkdirs()
                        entry.delete()
                        FileOutputStream(entry).use { output -> copyExactly(input, output, size) }
                        fileModes += entry to mode
                    }

                    '5' -> {
                        ensureSafeParent(root, entry)
                        check(entry.isDirectory || entry.mkdirs() || entry.isDirectory) {
                            "Unable to create a directory in the Linux filesystem."
                        }
                        directoryModes += entry to mode
                    }

                    '2' -> {
                        ensureSafeParent(root, entry)
                        entry.parentFile?.mkdirs()
                        entry.delete()
                        val linkTarget = safeLinkTarget(root, entry, linkName)
                        Os.symlink(linkTarget, entry.path)
                    }

                    '1' -> {
                        ensureSafeParent(root, entry)
                        val target = safeTarPath(root, linkName)
                        pendingHardLinks += Triple(target, entry, mode)
                    }

                    else -> Unit
                }

                val remainder = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE
                if (type != '0' && type != '\u0000') skipExactly(input, size)
                skipExactly(input, remainder)
            }
        }

        pendingHardLinks.forEach { (target, link, mode) ->
            ensureSafeParent(root, link)
            check(target.isFile) { "A root filesystem hard link refers to a missing file." }
            link.parentFile?.mkdirs()
            RuntimeLog.info(
                this,
                "Materializing hard link ${link.relativeTo(root)} from ${target.relativeTo(root)}."
            )
            FileInputStream(target).use { source ->
                FileOutputStream(link).use { destination -> source.copyTo(destination) }
            }
            fileModes += link to mode
        }

        fileModes.forEach { (file, mode) -> Os.chmod(file.path, mode and 0x1ff) }
        directoryModes
            .sortedByDescending { (directory, _) ->
                directory.relativeTo(root).path.count { it == File.separatorChar }
            }
            .forEach { (directory, mode) -> Os.chmod(directory.path, mode and 0x1ff) }
    }

    private fun applyLayerWhiteout(root: File, path: String) {
        val parentPath = path.substringBeforeLast('/', "")
        val parent = safeTarPath(root, parentPath)
        val name = path.substringAfterLast('/')
        if (name == ".wh..wh..opq") {
            parent.listFiles()?.forEach { child ->
                check(child.deleteRecursively()) {
                    "Unable to apply an opaque OCI layer whiteout."
                }
            }
            return
        }

        val targetName = name.removePrefix(".wh.")
        check(targetName.isNotBlank()) { "The OCI image contains an invalid whiteout." }
        val target = safeTarPath(root, listOf(parentPath, targetName).filter(String::isNotBlank).joinToString("/"))
        check(target.deleteRecursively()) { "Unable to apply an OCI layer whiteout." }
    }

    private fun parsePaxRecords(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var offset = 0
        while (offset < bytes.size) {
            var space = offset
            while (space < bytes.size && bytes[space] != ' '.code.toByte()) space++
            check(space < bytes.size) { "A tar PAX record has an invalid length." }
            val recordLength = String(bytes, offset, space - offset, Charsets.US_ASCII).toIntOrNull()
                ?: error("A tar PAX record has an invalid length.")
            check(recordLength > space - offset + 2 && offset + recordLength <= bytes.size) {
                "A tar PAX record has an invalid boundary."
            }
            val recordEnd = offset + recordLength - 1
            check(bytes[recordEnd] == '\n'.code.toByte()) { "A tar PAX record is missing its terminator." }
            val record = String(bytes, space + 1, recordEnd - space - 1, Charsets.UTF_8)
            val separator = record.indexOf('=')
            if (separator > 0) {
                result[record.substring(0, separator)] = record.substring(separator + 1)
            }
            offset += recordLength
        }
        return result
    }

    private fun safeTarPath(root: File, rawPath: String): File {
        val parts = mutableListOf<String>()
        rawPath.replace('\\', '/').split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> check(parts.isNotEmpty()) { "The root filesystem archive contains an unsafe path." }
                    .also { parts.removeAt(parts.lastIndex) }
                else -> parts += segment
            }
        }
        val destination = File(root, parts.joinToString("/"))
        check(destination.canonicalPath == root.path || destination.canonicalPath.startsWith(root.path + File.separator)) {
            "The root filesystem archive contains a path outside its filesystem."
        }
        return destination
    }

    private fun safeLinkTarget(root: File, link: File, target: String): String {
        val relativeTarget = if (target.startsWith("/")) {
            target.dropWhile { it == '/' }
        } else {
            link.relativeTo(root).parent?.let { "$it/$target" } ?: target
        }
        val normalized = safeTarPath(root, relativeTarget)
        val targetParts = normalized.relativeTo(root).path
            .split(File.separatorChar)
            .filter { it.isNotEmpty() }
        val parentParts = link.parentFile?.relativeTo(root)?.path
            ?.split(File.separatorChar)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val commonLength = parentParts.zip(targetParts).takeWhile { it.first == it.second }.size
        return (List(parentParts.size - commonLength) { ".." } +
            targetParts.drop(commonLength)).joinToString("/").ifEmpty { "." }
    }

    private fun ensureSafeParent(root: File, destination: File) {
        if (destination.canonicalPath == root.path) return
        val parent = destination.parentFile ?: root
        check(parent.canonicalPath == root.path || parent.canonicalPath.startsWith(root.path + File.separator)) {
            "A root filesystem link resolves outside its filesystem."
        }
    }

    private fun configureDebian() {
        configureDistro()
    }

    private fun configureDistro() {
        val etc = rootfs.resolve("etc")
        val resolvConf = etc.resolve("resolv.conf")
        resolvConf.delete()
        val dnsServers = activeDnsServers()
        check(dnsServers.isNotEmpty()) {
            "Android did not provide a DNS server; connect to a network and try again."
        }
        resolvConf.writeText(dnsServers.joinToString("") { "nameserver $it\n" })
        etc.resolve("hosts").writeText(
            "127.0.0.1 localhost\n" +
                "::1 localhost ip6-localhost ip6-loopback\n" +
                "255.255.255.255 broadcasthost\n"
        )
        val servicePolicy = rootfs.resolve("usr/sbin/policy-rc.d")
        servicePolicy.parentFile?.mkdirs()
        servicePolicy.writeText("#!/bin/sh\nexit 101\n")
        servicePolicy.setExecutable(true, true)
    }

    private fun activeDnsServers(): List<String> {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return emptyList()
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return emptyList()
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return emptyList()
        return connectivity.getLinkProperties(network)?.dnsServers
            ?.map { it.hostAddress?.substringBefore('%') }
            ?.filterNotNull()
            ?.distinct()
            .orEmpty()
    }

    private suspend fun runProot(
        command: List<String>,
        onOutput: suspend (String) -> Unit = {}
    ) {
        RuntimeLog.info(
            this,
            "Launching PRoot executable=${proot.absolutePath}, loader=${loader.absolutePath}, rootfs=${rootfs.absolutePath}."
        )
        val process = ProcessBuilder(prootArguments(command))
            .redirectErrorStream(true)
            .apply {
                environment()["PROOT_LOADER"] = loader.absolutePath
                environment()["PROOT_TMP_DIR"] = prepareProotTempDirectories().absolutePath
            }
            .start()

        BufferedReader(InputStreamReader(process.inputStream)).use { output ->
            while (true) {
                val line = output.readLine() ?: break
                if (line.startsWith("E:") || line.startsWith("W:") ||
                    line.contains("error", ignoreCase = true)
                ) {
                    RuntimeLog.info(this, "PRoot output: ${line.take(400)}")
                }
                onOutput(line)
            }
        }
        val exitCode = withContext(Dispatchers.IO) { process.waitFor() }
        RuntimeLog.info(this, "PRoot exited with code $exitCode.")
        check(exitCode == 0) {
            "Linux command exited with code $exitCode. Check the network connection and available storage, then retry."
        }
    }

    private fun prepareRuntimeBinaries() {
        RuntimeLog.info(this, "Using packaged PRoot binary ${proot.absolutePath}.")
        RuntimeLog.info(this, "Using packaged PRoot loader ${loader.absolutePath}.")
        check(proot.isFile && proot.canExecute()) {
            "The bundled PRoot executable is missing or not executable for ${Build.SUPPORTED_ABIS.firstOrNull()}."
        }
        check(loader.isFile && loader.canExecute()) {
            "The bundled PRoot loader is missing or not executable for ${Build.SUPPORTED_ABIS.firstOrNull()}."
        }
    }

    private fun prepareProotTempDirectories(): File {
        val hostTemp = File(filesDir, "proot-tmp")
        if (!hostTemp.isDirectory) {
            check(hostTemp.mkdirs()) { "Unable to prepare PRoot's temporary directory." }
        }
        Os.chmod(hostTemp.path, 0x1c0)

        val linuxTemp = rootfs.resolve("tmp")
        if (!linuxTemp.isDirectory) {
            check(linuxTemp.mkdirs()) { "Unable to prepare the Linux /tmp directory." }
        }
        Os.chmod(linuxTemp.path, 0x3ff)
        return hostTemp
    }

    private fun prootArguments(command: List<String>): List<String> = buildList {
        add(proot.absolutePath)
        addAll(listOf("-0", "--link2symlink", "-r", rootfs.absolutePath))
        addAll(listOf("-b", "/dev", "-b", "/proc", "-b", "/sys"))
        addAll(listOf("-w", "/root", "/usr/bin/env"))
        add("HOME=/root")
        add("LANG=C.UTF-8")
        add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        add("TMPDIR=/tmp")
        add("TMP=/tmp")
        add("TEMP=/tmp")
        addAll(command)
    }

    private fun desktopLauncherBinary(desktopEnvironment: String): String = when (desktopEnvironment) {
        "gnome" -> "/usr/bin/gnome-session"
        "kde" -> "/usr/bin/startplasma-x11"
        "lxqt" -> "/usr/bin/startlxqt"
        "mate" -> "/usr/bin/mate-session"
        "cinnamon" -> "/usr/bin/cinnamon-session"
        "i3" -> "/usr/bin/i3"
        "dwm" -> "/usr/bin/dwm"
        else -> "/usr/bin/startxfce4"
    }

    private fun desktopLaunchCommand(desktopEnvironment: String): String = when (desktopEnvironment) {
        "gnome" -> "exec dbus-run-session -- gnome-session"
        "kde" -> "exec dbus-run-session -- startplasma-x11"
        "lxqt" -> "exec dbus-run-session -- startlxqt"
        "mate" -> "exec dbus-run-session -- mate-session"
        "cinnamon" -> "exec dbus-run-session -- cinnamon-session"
        "i3" -> "exec dbus-run-session -- i3"
        "dwm" -> "exec dbus-run-session -- dwm"
        else -> "exec dbus-run-session -- startxfce4"
    }

    private fun desktopDisplayName(desktopEnvironment: String): String = when (desktopEnvironment) {
        "gnome" -> "GNOME"
        "kde" -> "KDE Plasma"
        "lxqt" -> "LXQt"
        "mate" -> "MATE"
        "cinnamon" -> "Cinnamon"
        "i3" -> "i3"
        "dwm" -> "dwm"
        else -> "XFCE"
    }

    private suspend fun startVncDesktop() {
        check(isDistroInstalled(this, currentDistroId)) {
            "Install $currentDistroId + $currentDesktopEnvironment before starting the desktop."
        }
        prepareRuntimeBinaries()
        if (desktopProcess?.isAlive == true) return

        val passwordUtility = ensureTigerVncPasswordUtility()
        val password = generateVncPassword()
        runProot(
            listOf(
                "/bin/sh", "-c",
                "mkdir -p /root/.vnc && " +
                    "printf '%s\\n%s\\n' '$password' '$password' | " +
                    "$passwordUtility -f > /root/.vnc/passwd && chmod 600 /root/.vnc/passwd"
            )
        )
        val xstartup = rootfs.resolve("root/.vnc/xstartup")
        xstartup.parentFile?.mkdirs()
        xstartup.writeText(
            "#!/bin/sh\n" +
                "unset SESSION_MANAGER\n" +
                "unset DBUS_SESSION_BUS_ADDRESS\n" +
                "${desktopLaunchCommand(currentDesktopEnvironment)}\n"
        )
        xstartup.setExecutable(true, true)

        val vncServer = listOf("/usr/bin/vncserver", "/usr/bin/tigervncserver")
            .firstOrNull { rootfs.resolve(it.removePrefix("/")).isFile }
            ?: error("$currentDistroId does not have a supported VNC server binary.")
        desktopProcess = ProcessBuilder(
            prootArguments(
                listOf(
                    "/bin/sh", "-c",
                    "$vncServer :1 -localhost yes -geometry 960x540 -depth 24 " +
                        "-SecurityTypes VncAuth -rfbauth /root/.vnc/passwd && " +
                        "while :; do sleep 3600; done"
                )
            )
        ).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(File(filesDir, "$currentDesktopEnvironment-vnc.log")))
            .apply {
                environment()["PROOT_LOADER"] = loader.absolutePath
                environment()["PROOT_TMP_DIR"] = prepareProotTempDirectories().absolutePath
            }.start()
        kotlinx.coroutines.delay(500)
        check(desktopProcess?.isAlive == true) {
            "The VNC desktop exited during startup. Check the installation status and try again."
        }

        DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
            message = "${desktopDisplayName(currentDesktopEnvironment)} is running on 127.0.0.1:5901",
            desktopRunning = true,
            displayMode = DesktopDisplayMode.IN_APP_VNC,
            vncPassword = password,
            error = null
        )
    }

    private suspend fun startTermuxX11Desktop() {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            "Termux:X11 requires Android 8.0 or later."
        }
        check(isDistroInstalled(this, currentDistroId)) {
            "Install $currentDistroId + $currentDesktopEnvironment before starting the desktop."
        }
        prepareRuntimeBinaries()
        check(desktopProcess?.isAlive != true && termuxX11Process?.isAlive != true) {
            "Stop the current desktop before switching display modes."
        }

        val xkbConfig = rootfs.resolve("usr/share/X11/xkb")
        check(xkbConfig.isDirectory) {
            "$currentDistroId's XKB keyboard configuration is missing."
        }
        val x11Apk = verifyTermuxX11Package()
        val linuxTemp = rootfs.resolve("tmp")
        if (!linuxTemp.isDirectory) {
            check(linuxTemp.mkdirs()) {
                "Unable to prepare $currentDistroId's /tmp directory."
            }
        }
        Os.chmod(linuxTemp.path, 0x3ff)

        val x11Log = File(filesDir, "termux-x11.log")
        termuxX11Process = ProcessBuilder(
            "/system/bin/app_process",
            "-Xnoimage-dex2oat",
            "/",
            "--nice-name=andistro-termux-x11",
            "com.termux.x11.CmdEntryPoint",
            ":1"
        ).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(x11Log))
            .apply {
                environment()["CLASSPATH"] = x11Apk.absolutePath
                environment()["TMPDIR"] = linuxTemp.absolutePath
                environment()["XKB_CONFIG_ROOT"] = xkbConfig.absolutePath
                environment().remove("LD_LIBRARY_PATH")
                environment().remove("LD_PRELOAD")
            }
            .start()

        val displaySocket = linuxTemp.resolve(".X11-unix/X1")
        var displayReady = false
        var attempts = 0
        while (attempts < 100 && !displayReady) {
            check(termuxX11Process?.isAlive == true) {
                "Termux:X11 exited during startup. Check its installation and retry."
            }
            displayReady = displaySocket.exists() && isX11DisplayReady(displaySocket)
            if (!displayReady) delay(100)
            attempts++
        }
        check(displayReady) {
            "Termux:X11 did not accept X11 connections. Check termux-x11.log and retry."
        }
        delay(250)

        desktopProcess = ProcessBuilder(
            prootArguments(
                listOf(
                    "/bin/sh",
                    "-c",
                    "export DISPLAY=:1; " +
                        "export XKB_CONFIG_ROOT=/usr/share/X11/xkb; " +
                        "unset SESSION_MANAGER; " +
                        "unset DBUS_SESSION_BUS_ADDRESS; " +
                        "${desktopLaunchCommand(currentDesktopEnvironment)}"
                )
            )
        ).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(File(filesDir, "$currentDesktopEnvironment-x11.log")))
            .apply {
                environment()["PROOT_LOADER"] = loader.absolutePath
                environment()["PROOT_TMP_DIR"] = prepareProotTempDirectories().absolutePath
            }
            .start()

        delay(500)
        check(desktopProcess?.isAlive == true) {
            "${desktopDisplayName(currentDesktopEnvironment)} exited during Termux:X11 startup. Check $currentDesktopEnvironment-x11.log and retry."
        }

        DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
            message = "${desktopDisplayName(currentDesktopEnvironment)} is running in Termux:X11",
            distroId = currentDistroId,
            desktopRunning = true,
            displayMode = DesktopDisplayMode.TERMUX_X11,
            vncPassword = null,
            error = null
        )
    }

    private fun isX11DisplayReady(displaySocket: File): Boolean {
        return try {
            LocalSocket().use { socket ->
                socket.connect(
                    LocalSocketAddress(
                        displaySocket.absolutePath,
                        LocalSocketAddress.Namespace.FILESYSTEM
                    )
                )
                true
            }
        } catch (_: java.io.IOException) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyTermuxX11Package(): File {
        check(isTermuxX11Installed(this)) {
            "Install the Termux:X11 Android app from its official nightly release first."
        }
        val packageInfo = packageManager.getPackageInfo(
            TERMUX_X11_PACKAGE,
            PackageManager.GET_SIGNATURES
        )
        val signer = packageInfo.signatures?.singleOrNull()
            ?: error("The installed Termux:X11 app has an unsupported signing configuration.")
        val signerSha256 = MessageDigest.getInstance("SHA-256")
            .digest(signer.toByteArray())
            .joinToString("") { "%02x".format(it) }
        check(signerSha256 == TERMUX_X11_CERT_SHA256) {
            "The Termux:X11 app signature is not recognized. Install the official nightly build."
        }

        val apk = File(packageInfo.applicationInfo?.sourceDir.orEmpty())
        check(apk.isFile && apk.canRead()) {
            "Unable to read the installed Termux:X11 app package."
        }
        return apk
    }

    private fun stopDesktopProcesses() {
        desktopProcess?.destroy()
        desktopProcess = null
        termuxX11Process?.destroy()
        termuxX11Process = null
    }

    private suspend fun ensureTigerVncPasswordUtility(): String {
        var passwordUtility = vncPasswordUtility()
        if (passwordUtility == null && currentDistroId == "debian") {
            updateProgress("Installing the missing TigerVNC utility", 0.9f)
            ensureAptListsCurrent()
            runProot(
                listOf(
                    "/bin/sh",
                    "-c",
                    "export DEBIAN_FRONTEND=noninteractive; " +
                        "apt-get -o Acquire::Retries=3 install -y --no-install-recommends tigervnc-tools"
                )
            )
            passwordUtility = vncPasswordUtility()
        }
        return checkNotNull(passwordUtility) {
            "$currentDistroId does not have a supported VNC password utility."
        }
    }

    private fun vncPasswordUtility(): String? =
        listOf("/usr/bin/tigervncpasswd", "/usr/bin/vncpasswd")
            .firstOrNull { rootfs.resolve(it.removePrefix("/")).isFile }

    private fun hasVncPasswordUtility(): Boolean = vncPasswordUtility() != null

    private fun generateVncPassword(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.map { alphabet[(it.toInt() and 0xff) % alphabet.length] }.joinToString("")
    }

    private suspend fun updateProgress(message: String, progress: Float) {
        if (!message.startsWith("Installing XFCE:")) {
            RuntimeLog.info(this, "Progress: $message")
        }
        DistroRuntimeState.state.value = DistroRuntimeState.state.value.copy(
            message = message,
            progress = progress.coerceIn(0f, 1f)
        )
        promoteToForeground(message, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun promoteToForeground(message: String, serviceType: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Andistro")
            .setContentText(message.take(120))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Linux installation and desktop",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun tarString(header: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { header[it] == 0.toByte() }
            ?: offset + length
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    private fun tarOctal(header: ByteArray, offset: Int, length: Int): Long {
        val value = tarString(header, offset, length).trim().trimStart('0')
        return if (value.isEmpty()) 0L else value.toLong(8)
    }

    private fun copyExactly(input: InputStream, output: FileOutputStream, byteCount: Long) {
        var remaining = byteCount
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(count >= 0) { "The Linux archive ended unexpectedly." }
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun skipExactly(input: InputStream, byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                check(input.read() >= 0) { "The Linux archive ended unexpectedly." }
                remaining--
            }
        }
    }

}
