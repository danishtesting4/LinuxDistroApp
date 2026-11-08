package com.danis.linuxdistroapp

data class DistroInstallProfile(
    val id: String,
    val imageRepository: String? = null,
    val imageTag: String? = null,
    val lxcImagePath: String? = null,
    val preInstallCommand: String,
    val installCommand: String,
    val desktopPackages: Map<String, Map<String, List<String>>>,
    val vncPackage: String,
    val minimumFreeBytes: Long = 4L * 1024 * 1024 * 1024
) {
    fun packagesFor(desktopEnvironment: String, tier: String = "standard"): List<String> {
        val byTier = desktopPackages[desktopEnvironment] ?: desktopPackages["xfce"] ?: emptyMap()
        return byTier[tier] ?: byTier["standard"] ?: byTier.values.firstOrNull() ?: emptyList()
    }
}

object DistroInstallProfiles {
    // Common VNC server package names per distro family.
    private const val VNC_DEBIAN = "tigervnc-standalone-server tigervnc-tools"
    private const val VNC_ARCH = "tigervnc"
    private const val VNC_FEDORA = "tigervnc-server"
    private const val VNC_ALPINE = "tigervnc"
    private const val VNC_OPENSUSE = "tigervnc"
    private const val VNC_VOID = "tigervnc"
    private const val VNC_GENTOO = "net-misc/tigervnc"

    private val profiles = listOf(
        DistroInstallProfile(
            id = "debian",
            imageRepository = "library/debian",
            imageTag = "bookworm-slim",
            preInstallCommand = "apt-get -o Acquire::Retries=3 update",
            installCommand = "apt-get -o Acquire::Retries=3 install -y --no-install-recommends",
            vncPackage = "tigervnc-standalone-server",
            desktopPackages = mapOf(
                "xfce" to mapOf(
                    "minimal" to listOf("dbus-x11", "xfce4-session", "xfwm4", "xfce4-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox-esr", "libreoffice", "vlc", "gimp")
                ),
                "gnome" to mapOf(
                    "minimal" to listOf("dbus-x11", "gnome-session", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "gnome-session", "gnome-shell", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "gnome-session", "gnome-shell", "gnome-terminal", "nautilus", "gnome-control-center", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox-esr", "libreoffice", "vlc")
                ),
                "kde" to mapOf(
                    "minimal" to listOf("dbus-x11", "plasma-workspace", "konsole", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "plasma-workspace", "plasma-desktop", "konsole", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "plasma-workspace", "plasma-desktop", "konsole", "dolphin", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox-esr", "libreoffice", "vlc")
                ),
                "lxqt" to mapOf(
                    "minimal" to listOf("dbus-x11", "lxqt-session", "qterminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "lxqt", "lxqt-session", "qterminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "lxqt", "lxqt-session", "qterminal", "pcmanfm-qt", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox-esr", "libreoffice")
                ),
                "mate" to mapOf(
                    "minimal" to listOf("dbus-x11", "mate-session", "mate-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "mate-desktop-environment", "mate-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "mate-desktop-environment", "mate-terminal", "caja", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox-esr", "libreoffice", "vlc")
                ),
                "cinnamon" to mapOf(
                    "minimal" to listOf("dbus-x11", "cinnamon-session", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "cinnamon", "cinnamon-session", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "cinnamon", "cinnamon-session", "gnome-terminal", "nemo", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox-esr", "libreoffice")
                ),
                "i3" to mapOf(
                    "minimal" to listOf("dbus-x11", "i3", "i3status", "xterm", "tigervnc-standalone-server", "tigervnc-tools", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "i3", "i3status", "i3lock", "dmenu", "xterm", "tigervnc-standalone-server", "tigervnc-tools", "xfonts-base"),
                    "full" to listOf("dbus-x11", "i3", "i3status", "i3lock", "dmenu", "xterm", "tigervnc-standalone-server", "tigervnc-tools", "xfonts-base", "firefox-esr", "libreoffice")
                ),
                "dwm" to mapOf(
                    "minimal" to listOf("dbus-x11", "dwm", "st", "xterm", "tigervnc-standalone-server", "tigervnc-tools", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "dwm", "st", "dmenu", "xterm", "tigervnc-standalone-server", "tigervnc-tools", "xfonts-base"),
                    "full" to listOf("dbus-x11", "dwm", "st", "dmenu", "xterm", "tigervnc-standalone-server", "tigervnc-tools", "xfonts-base", "firefox-esr")
                )
            )
        ),
        DistroInstallProfile(
            id = "ubuntu",
            imageRepository = "library/ubuntu",
            imageTag = "noble",
            preInstallCommand = "apt-get -o Acquire::Retries=3 update",
            installCommand = "apt-get -o Acquire::Retries=3 install -y --no-install-recommends",
            vncPackage = "tigervnc-standalone-server",
            desktopPackages = mapOf(
                "gnome" to mapOf(
                    "minimal" to listOf("dbus-x11", "gnome-session", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "gnome-session", "gnome-shell", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "gnome-session", "gnome-shell", "gnome-terminal", "nautilus", "gnome-control-center", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox", "libreoffice", "vlc")
                ),
                "xfce" to mapOf(
                    "minimal" to listOf("dbus-x11", "xfce4-session", "xfwm4", "xfce4-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox", "libreoffice", "vlc")
                ),
                "mate" to mapOf(
                    "minimal" to listOf("dbus-x11", "mate-session", "mate-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "mate-desktop-environment", "mate-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "mate-desktop-environment", "mate-terminal", "caja", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox", "libreoffice")
                ),
                "cinnamon" to mapOf(
                    "minimal" to listOf("dbus-x11", "cinnamon-session", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "cinnamon", "cinnamon-session", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "cinnamon", "cinnamon-session", "gnome-terminal", "nemo", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox", "libreoffice")
                )
            )
        ),
        DistroInstallProfile(
            id = "arch",
            lxcImagePath = "archlinux/current",
            preInstallCommand = "pacman-key --init && pacman-key --populate archlinux",
            installCommand = "pacman -Syu --noconfirm --needed",
            vncPackage = "tigervnc",
            desktopPackages = mapOf(
                "xfce" to mapOf(
                    "minimal" to listOf("xfce4-session", "xfwm4", "xfce4-terminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("archlinux-keyring", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("archlinux-keyring", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "dbus", "xterm", "firefox", "libreoffice-fresh", "vlc")
                ),
                "gnome" to mapOf(
                    "minimal" to listOf("gnome-session", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("gnome", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("gnome", "gnome-terminal", "nautilus", "gnome-control-center", "tigervnc", "dbus", "xterm", "firefox", "libreoffice-fresh")
                ),
                "kde" to mapOf(
                    "minimal" to listOf("plasma-workspace", "konsole", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("plasma", "konsole", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("plasma", "konsole", "dolphin", "tigervnc", "dbus", "xterm", "firefox", "libreoffice-fresh")
                ),
                "lxqt" to mapOf(
                    "minimal" to listOf("lxqt-session", "qterminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("lxqt", "qterminal", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("lxqt", "qterminal", "pcmanfm-qt", "tigervnc", "dbus", "xterm", "firefox", "libreoffice-fresh")
                ),
                "mate" to mapOf(
                    "minimal" to listOf("mate-session", "mate-terminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("mate", "mate-terminal", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("mate", "mate-terminal", "caja", "tigervnc", "dbus", "xterm", "firefox", "libreoffice-fresh")
                ),
                "cinnamon" to mapOf(
                    "minimal" to listOf("cinnamon-session", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("cinnamon", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("cinnamon", "gnome-terminal", "nemo", "tigervnc", "dbus", "xterm", "firefox", "libreoffice-fresh")
                ),
                "i3" to mapOf(
                    "minimal" to listOf("i3-wm", "xterm", "tigervnc", "dbus"),
                    "standard" to listOf("i3-wm", "i3status", "i3lock", "dmenu", "xterm", "tigervnc", "dbus"),
                    "full" to listOf("i3-wm", "i3status", "i3lock", "dmenu", "xterm", "tigervnc", "dbus", "firefox", "libreoffice-fresh")
                ),
                "sway" to mapOf(
                    "minimal" to listOf("sway", "foot", "wayvnc", "mesa", "dbus"),
                    "standard" to listOf("sway", "wayvnc", "xorg-x11-server-Xwayland", "foot", "mesa", "dbus"),
                    "full" to listOf("sway", "wayvnc", "xorg-x11-server-Xwayland", "foot", "kitty", "mesa", "dbus", "firefox", "libreoffice-fresh")
                ),
                "hyprland" to mapOf(
                    "minimal" to listOf("hyprland", "wayvnc", "mesa", "dbus"),
                    "standard" to listOf("hyprland", "wayvnc", "xorg-x11-server-Xwayland", "kitty", "mesa", "dbus"),
                    "full" to listOf("hyprland", "wayvnc", "xorg-x11-server-Xwayland", "kitty", "mesa", "dbus", "firefox", "libreoffice-fresh")
                ),
                "dwm" to mapOf(
                    "minimal" to listOf("dwm", "st", "xterm", "tigervnc", "dbus"),
                    "standard" to listOf("dwm", "st", "dmenu", "xterm", "tigervnc", "dbus"),
                    "full" to listOf("dwm", "st", "dmenu", "xterm", "tigervnc", "dbus", "firefox", "libreoffice-fresh")
                )
            )
        ),
        DistroInstallProfile(
            id = "fedora",
            imageRepository = "library/fedora",
            imageTag = "latest",
            preInstallCommand = "",
            installCommand = "dnf -y --setopt=install_weak_deps=False install",
            vncPackage = "tigervnc-server",
            desktopPackages = mapOf(
                "xfce" to mapOf(
                    "minimal" to listOf("dbus-x11", "xfce4-session", "xfwm4", "xfce4-terminal", "tigervnc-server", "xterm"),
                    "standard" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc-server", "xterm"),
                    "full" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc-server", "xterm", "firefox", "libreoffice", "vlc")
                ),
                "gnome" to mapOf(
                    "minimal" to listOf("gnome-session", "gnome-terminal", "tigervnc-server", "xterm", "dbus-x11"),
                    "standard" to listOf("gnome-session", "gnome-shell", "gnome-terminal", "tigervnc-server", "xterm", "dbus-x11"),
                    "full" to listOf("gnome-session", "gnome-shell", "gnome-terminal", "nautilus", "gnome-control-center", "tigervnc-server", "xterm", "dbus-x11", "firefox", "libreoffice")
                ),
                "kde" to mapOf(
                    "minimal" to listOf("plasma-workspace", "konsole", "tigervnc-server", "xterm", "dbus-x11"),
                    "standard" to listOf("plasma-workspace", "plasma-desktop", "konsole", "tigervnc-server", "xterm", "dbus-x11"),
                    "full" to listOf("plasma-workspace", "plasma-desktop", "konsole", "dolphin", "tigervnc-server", "xterm", "dbus-x11", "firefox", "libreoffice")
                ),
                "mate" to mapOf(
                    "minimal" to listOf("mate-desktop", "mate-terminal", "tigervnc-server", "xterm", "dbus-x11"),
                    "standard" to listOf("mate-desktop", "mate-terminal", "tigervnc-server", "xterm", "dbus-x11"),
                    "full" to listOf("mate-desktop", "mate-terminal", "caja", "tigervnc-server", "xterm", "dbus-x11", "firefox", "libreoffice")
                ),
                "cinnamon" to mapOf(
                    "minimal" to listOf("cinnamon", "gnome-terminal", "tigervnc-server", "xterm", "dbus-x11"),
                    "standard" to listOf("cinnamon", "gnome-terminal", "tigervnc-server", "xterm", "dbus-x11"),
                    "full" to listOf("cinnamon", "gnome-terminal", "nemo", "tigervnc-server", "xterm", "dbus-x11", "firefox", "libreoffice")
                ),
                "sway" to mapOf(
                    "minimal" to listOf("sway", "foot", "wayvnc", "mesa-dri-drivers", "dbus-x11"),
                    "standard" to listOf("sway", "wayvnc", "xorg-x11-server-Xwayland", "foot", "mesa-dri-drivers", "dbus-x11"),
                    "full" to listOf("sway", "wayvnc", "xorg-x11-server-Xwayland", "foot", "mesa-dri-drivers", "dbus-x11", "firefox", "libreoffice")
                )
            )
        ),
        DistroInstallProfile(
            id = "kali",
            imageRepository = "kalilinux/kali-rolling",
            imageTag = "latest",
            preInstallCommand = "apt-get -o Acquire::Retries=3 update",
            installCommand = "apt-get -o Acquire::Retries=3 install -y --no-install-recommends",
            vncPackage = "tigervnc-standalone-server",
            desktopPackages = mapOf(
                "xfce" to mapOf(
                    "minimal" to listOf("dbus-x11", "xfce4-session", "xfwm4", "xfce4-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox-esr", "libreoffice", "vlc")
                ),
                "gnome" to mapOf(
                    "minimal" to listOf("dbus-x11", "gnome-session", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "gnome-session", "gnome-shell", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "gnome-session", "gnome-shell", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox-esr", "libreoffice")
                ),
                "kde" to mapOf(
                    "minimal" to listOf("dbus-x11", "plasma-workspace", "konsole", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "standard" to listOf("dbus-x11", "plasma-workspace", "plasma-desktop", "konsole", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                    "full" to listOf("dbus-x11", "plasma-workspace", "plasma-desktop", "konsole", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base", "firefox-esr", "libreoffice")
                )
            )
        ),
        DistroInstallProfile(
            id = "alpine",
            imageRepository = "library/alpine",
            imageTag = "3.23",
            preInstallCommand = "apk update",
            installCommand = "apk add",
            vncPackage = "tigervnc",
            desktopPackages = mapOf(
                "xfce" to mapOf(
                    "minimal" to listOf("dbus", "xfce4-session", "xfwm4", "xfce4-terminal", "tigervnc", "xterm"),
                    "standard" to listOf("dbus", "xfce4-session", "xfce4-panel", "xfce4-wm", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm"),
                    "full" to listOf("dbus", "xfce4-session", "xfce4-panel", "xfce4-wm", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm", "firefox", "libreoffice")
                ),
                "lxqt" to mapOf(
                    "minimal" to listOf("dbus", "lxqt-session", "qterminal", "tigervnc", "xterm"),
                    "standard" to listOf("dbus", "lxqt", "qterminal", "tigervnc", "xterm"),
                    "full" to listOf("dbus", "lxqt", "qterminal", "pcmanfm-qt", "tigervnc", "xterm", "firefox", "libreoffice")
                ),
                "i3" to mapOf(
                    "minimal" to listOf("dbus", "i3wm", "xterm", "tigervnc"),
                    "standard" to listOf("dbus", "i3wm", "i3status", "xterm", "tigervnc"),
                    "full" to listOf("dbus", "i3wm", "i3status", "xterm", "tigervnc", "firefox", "libreoffice")
                ),
                "dwm" to mapOf(
                    "minimal" to listOf("dbus", "dwm", "st", "xterm", "tigervnc"),
                    "standard" to listOf("dbus", "dwm", "st", "dmenu", "xterm", "tigervnc"),
                    "full" to listOf("dbus", "dwm", "st", "dmenu", "xterm", "tigervnc", "firefox")
                )
            )
        ),
        DistroInstallProfile(
            id = "manjaro",
            imageRepository = "manjarolinux/base",
            imageTag = "latest",
            preInstallCommand = "pacman-key --init && pacman-key --populate manjaro",
            installCommand = "pacman -Syu --noconfirm --needed",
            vncPackage = "tigervnc",
            desktopPackages = mapOf(
                "xfce" to mapOf(
                    "minimal" to listOf("xfce4-session", "xfwm4", "xfce4-terminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("manjaro-keyring", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("manjaro-keyring", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "dbus", "xterm", "firefox", "libreoffice", "vlc")
                ),
                "gnome" to mapOf(
                    "minimal" to listOf("gnome-session", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("gnome", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("gnome", "gnome-terminal", "nautilus", "tigervnc", "dbus", "xterm", "firefox", "libreoffice")
                ),
                "kde" to mapOf(
                    "minimal" to listOf("plasma-workspace", "konsole", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("plasma", "konsole", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("plasma", "konsole", "dolphin", "tigervnc", "dbus", "xterm", "firefox", "libreoffice")
                ),
                "mate" to mapOf(
                    "minimal" to listOf("mate-session", "mate-terminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("mate", "mate-terminal", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("mate", "mate-terminal", "caja", "tigervnc", "dbus", "xterm", "firefox", "libreoffice")
                ),
                "cinnamon" to mapOf(
                    "minimal" to listOf("cinnamon-session", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("cinnamon", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("cinnamon", "gnome-terminal", "nemo", "tigervnc", "dbus", "xterm", "firefox", "libreoffice")
                ),
                "i3" to mapOf(
                    "minimal" to listOf("i3-wm", "xterm", "tigervnc", "dbus"),
                    "standard" to listOf("i3-wm", "i3status", "i3lock", "dmenu", "xterm", "tigervnc", "dbus"),
                    "full" to listOf("i3-wm", "i3status", "i3lock", "dmenu", "xterm", "tigervnc", "dbus", "firefox", "libreoffice")
                )
            )
        ),
        DistroInstallProfile(
            id = "opensuse",
            imageRepository = "opensuse/tumbleweed",
            imageTag = "latest",
            preInstallCommand = "zypper --non-interactive refresh",
            installCommand = "zypper --non-interactive install --no-recommends",
            vncPackage = "tigervnc",
            desktopPackages = mapOf(
                "xfce" to mapOf(
                    "minimal" to listOf("dbus-1-x11", "xfce4-session", "xfwm4", "xfce4-terminal", "tigervnc", "xterm"),
                    "standard" to listOf("dbus-1-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm"),
                    "full" to listOf("dbus-1-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm", "firefox", "libreoffice")
                ),
                "gnome" to mapOf(
                    "minimal" to listOf("gnome-session", "gnome-terminal", "tigervnc", "xterm"),
                    "standard" to listOf("gnome-session", "gnome-shell", "gnome-terminal", "tigervnc", "xterm"),
                    "full" to listOf("gnome-session", "gnome-shell", "gnome-terminal", "nautilus", "tigervnc", "xterm", "firefox", "libreoffice")
                ),
                "kde" to mapOf(
                    "minimal" to listOf("plasma5-workspace", "konsole", "tigervnc", "xterm"),
                    "standard" to listOf("plasma5-workspace", "konsole", "tigervnc", "xterm"),
                    "full" to listOf("plasma5-workspace", "konsole", "dolphin", "tigervnc", "xterm", "firefox", "libreoffice")
                ),
                "mate" to mapOf(
                    "minimal" to listOf("mate-session", "mate-terminal", "tigervnc", "xterm"),
                    "standard" to listOf("mate-session", "mate-terminal", "tigervnc", "xterm"),
                    "full" to listOf("mate-session", "mate-terminal", "caja", "tigervnc", "xterm", "firefox", "libreoffice")
                ),
                "cinnamon" to mapOf(
                    "minimal" to listOf("cinnamon", "gnome-terminal", "tigervnc", "xterm"),
                    "standard" to listOf("cinnamon", "gnome-terminal", "tigervnc", "xterm"),
                    "full" to listOf("cinnamon", "gnome-terminal", "nemo", "tigervnc", "xterm", "firefox", "libreoffice")
                ),
                "i3" to mapOf(
                    "minimal" to listOf("i3", "xterm", "tigervnc"),
                    "standard" to listOf("i3", "i3status", "xterm", "tigervnc"),
                    "full" to listOf("i3", "i3status", "xterm", "tigervnc", "firefox", "libreoffice")
                )
            )
        ),
        DistroInstallProfile(
            id = "void",
            lxcImagePath = "voidlinux/current",
            preInstallCommand = "",
            installCommand = "xbps-install -Sy",
            vncPackage = "tigervnc",
            desktopPackages = mapOf(
                "xfce" to mapOf(
                    "minimal" to listOf("dbus", "xfce4-session", "xfwm4", "xfce4-terminal", "tigervnc", "xterm"),
                    "standard" to listOf("dbus", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm"),
                    "full" to listOf("dbus", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm", "firefox", "libreoffice")
                ),
                "gnome" to mapOf(
                    "minimal" to listOf("gnome", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("gnome", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("gnome", "gnome-terminal", "nautilus", "tigervnc", "dbus", "xterm", "firefox", "libreoffice")
                ),
                "kde" to mapOf(
                    "minimal" to listOf("plasma", "konsole", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("plasma", "konsole", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("plasma", "konsole", "dolphin", "tigervnc", "dbus", "xterm", "firefox", "libreoffice")
                ),
                "lxqt" to mapOf(
                    "minimal" to listOf("lxqt", "qterminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("lxqt", "qterminal", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("lxqt", "qterminal", "pcmanfm-qt", "tigervnc", "dbus", "xterm", "firefox", "libreoffice")
                ),
                "mate" to mapOf(
                    "minimal" to listOf("mate", "mate-terminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("mate", "mate-terminal", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("mate", "mate-terminal", "caja", "tigervnc", "dbus", "xterm", "firefox", "libreoffice")
                ),
                "cinnamon" to mapOf(
                    "minimal" to listOf("cinnamon", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "standard" to listOf("cinnamon", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                    "full" to listOf("cinnamon", "gnome-terminal", "nemo", "tigervnc", "dbus", "xterm", "firefox", "libreoffice")
                ),
                "i3" to mapOf(
                    "minimal" to listOf("i3", "xterm", "tigervnc", "dbus"),
                    "standard" to listOf("i3", "i3status", "xterm", "tigervnc", "dbus"),
                    "full" to listOf("i3", "i3status", "xterm", "tigervnc", "dbus", "firefox", "libreoffice")
                ),
                "sway" to mapOf(
                    "minimal" to listOf("sway", "foot", "wayvnc", "mesa", "dbus"),
                    "standard" to listOf("sway", "wayvnc", "xorg-server-xwayland", "foot", "mesa", "dbus"),
                    "full" to listOf("sway", "wayvnc", "xorg-server-xwayland", "foot", "mesa", "dbus", "firefox", "libreoffice")
                ),
                "hyprland" to mapOf(
                    "minimal" to listOf("hyprland", "wayvnc", "mesa", "dbus"),
                    "standard" to listOf("hyprland", "wayvnc", "xorg-server-xwayland", "kitty", "mesa", "dbus"),
                    "full" to listOf("hyprland", "wayvnc", "xorg-server-xwayland", "kitty", "mesa", "dbus", "firefox", "libreoffice")
                ),
                "dwm" to mapOf(
                    "minimal" to listOf("dwm", "st", "xterm", "tigervnc", "dbus"),
                    "standard" to listOf("dwm", "st", "dmenu", "xterm", "tigervnc", "dbus"),
                    "full" to listOf("dwm", "st", "dmenu", "xterm", "tigervnc", "dbus", "firefox")
                )
            )
        ),
        DistroInstallProfile(
            id = "gentoo",
            imageRepository = "gentoo/stage3",
            imageTag = "latest",
            preInstallCommand = "emerge-webrsync",
            installCommand = "MAKEOPTS=-j1 emerge --ask=n --jobs=1 --load-average=1",
            vncPackage = "net-misc/tigervnc",
            desktopPackages = mapOf(
                "xfce" to mapOf(
                    "minimal" to listOf("xfce-base/xfce4-meta", "net-misc/tigervnc", "x11-terms/xterm"),
                    "standard" to listOf("xfce-base/xfce4-meta", "net-misc/tigervnc", "x11-terms/xterm"),
                    "full" to listOf("xfce-base/xfce4-meta", "net-misc/tigervnc", "x11-terms/xterm", "www-client/firefox", "app-office/libreoffice")
                ),
                "gnome" to mapOf(
                    "minimal" to listOf("gnome-base/gnome-light", "net-misc/tigervnc", "x11-terms/xterm"),
                    "standard" to listOf("gnome-base/gnome", "net-misc/tigervnc", "x11-terms/xterm"),
                    "full" to listOf("gnome-base/gnome", "net-misc/tigervnc", "x11-terms/xterm", "www-client/firefox", "app-office/libreoffice")
                ),
                "kde" to mapOf(
                    "minimal" to listOf("kde-plasma/plasma-meta", "kde-apps/konsole", "net-misc/tigervnc", "x11-terms/xterm"),
                    "standard" to listOf("kde-plasma/plasma-meta", "kde-apps/konsole", "net-misc/tigervnc", "x11-terms/xterm"),
                    "full" to listOf("kde-plasma/plasma-meta", "kde-apps/konsole", "net-misc/tigervnc", "x11-terms/xterm", "www-client/firefox", "app-office/libreoffice")
                ),
                "i3" to mapOf(
                    "minimal" to listOf("x11-wm/i3", "x11-terms/xterm", "net-misc/tigervnc"),
                    "standard" to listOf("x11-wm/i3", "x11-misc/i3status", "x11-terms/xterm", "net-misc/tigervnc"),
                    "full" to listOf("x11-wm/i3", "x11-misc/i3status", "x11-terms/xterm", "net-misc/tigervnc", "www-client/firefox", "app-office/libreoffice")
                ),
                "sway" to mapOf(
                    "minimal" to listOf("gui-wm/sway", "gui-apps/wayvnc", "media-libs/mesa", "sys-apps/dbus"),
                    "standard" to listOf("gui-wm/sway", "gui-apps/wayvnc", "x11-base/xwayland", "gui-apps/foot", "media-libs/mesa", "sys-apps/dbus"),
                    "full" to listOf("gui-wm/sway", "gui-apps/wayvnc", "x11-base/xwayland", "gui-apps/foot", "media-libs/mesa", "sys-apps/dbus", "www-client/firefox", "app-office/libreoffice")
                ),
                "hyprland" to mapOf(
                    "minimal" to listOf("gui-wm/hyprland", "gui-apps/wayvnc", "media-libs/mesa", "sys-apps/dbus"),
                    "standard" to listOf("gui-wm/hyprland", "gui-apps/wayvnc", "x11-base/xwayland", "x11-terms/kitty", "media-libs/mesa", "sys-apps/dbus"),
                    "full" to listOf("gui-wm/hyprland", "gui-apps/wayvnc", "x11-base/xwayland", "x11-terms/kitty", "media-libs/mesa", "sys-apps/dbus", "www-client/firefox", "app-office/libreoffice")
                ),
                "dwm" to mapOf(
                    "minimal" to listOf("x11-wm/dwm", "x11-terms/st", "x11-terms/xterm", "net-misc/tigervnc"),
                    "standard" to listOf("x11-wm/dwm", "x11-terms/st", "x11-misc/dmenu", "x11-terms/xterm", "net-misc/tigervnc"),
                    "full" to listOf("x11-wm/dwm", "x11-terms/st", "x11-misc/dmenu", "x11-terms/xterm", "net-misc/tigervnc", "www-client/firefox")
                )
            ),
            minimumFreeBytes = 8L * 1024 * 1024 * 1024
        )
    )

    private val profilesById = profiles.associateBy(DistroInstallProfile::id)

    fun get(id: String): DistroInstallProfile? = profilesById[id]

    fun allIds(): Set<String> = profilesById.keys
}
