package com.danis.linuxdistroapp

data class DistroInstallProfile(
    val id: String,
    val imageRepository: String? = null,
    val imageTag: String? = null,
    val lxcImagePath: String? = null,
    val preInstallCommand: String,
    val installCommand: String,
    val desktopPackages: Map<String, List<String>>,
    val minimumFreeBytes: Long = 4L * 1024 * 1024 * 1024
) {
    fun packagesFor(desktopEnvironment: String): List<String> =
        desktopPackages[desktopEnvironment] ?: desktopPackages["xfce"] ?: emptyList()
}

object DistroInstallProfiles {
    private val profiles = listOf(
        DistroInstallProfile(
            id = "debian",
            imageRepository = "library/debian",
            imageTag = "bookworm-slim",
            preInstallCommand = "apt-get -o Acquire::Retries=3 update",
            installCommand = "apt-get -o Acquire::Retries=3 install -y --no-install-recommends",
            desktopPackages = mapOf(
                "xfce" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                "gnome" to listOf("dbus-x11", "gnome-session", "gnome-shell", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                "kde" to listOf("dbus-x11", "plasma-workspace", "plasma-desktop", "konsole", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                "lxqt" to listOf("dbus-x11", "lxqt", "lxqt-session", "qterminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                "mate" to listOf("dbus-x11", "mate-desktop-environment", "mate-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                "cinnamon" to listOf("dbus-x11", "cinnamon", "cinnamon-session", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                "i3" to listOf("dbus-x11", "i3", "i3status", "i3lock", "dmenu", "xterm", "tigervnc-standalone-server", "tigervnc-tools", "xfonts-base"),
                "dwm" to listOf("dbus-x11", "dwm", "st", "dmenu", "xterm", "tigervnc-standalone-server", "tigervnc-tools", "xfonts-base")
            )
        ),
        DistroInstallProfile(
            id = "ubuntu",
            imageRepository = "library/ubuntu",
            imageTag = "noble",
            preInstallCommand = "apt-get -o Acquire::Retries=3 update",
            installCommand = "apt-get -o Acquire::Retries=3 install -y --no-install-recommends",
            desktopPackages = mapOf(
                "xfce" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                "gnome" to listOf("dbus-x11", "gnome-session", "gnome-shell", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                "mate" to listOf("dbus-x11", "mate-desktop-environment", "mate-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                "cinnamon" to listOf("dbus-x11", "cinnamon", "cinnamon-session", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base")
            )
        ),
        DistroInstallProfile(
            id = "arch",
            lxcImagePath = "archlinux/current",
            preInstallCommand = "pacman-key --init && pacman-key --populate archlinux",
            installCommand = "pacman -Syu --noconfirm --needed",
            desktopPackages = mapOf(
                "xfce" to listOf("archlinux-keyring", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "dbus", "xterm"),
                "gnome" to listOf("gnome", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                "gnome" to listOf("gnome", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                "kde" to listOf("plasma", "konsole", "tigervnc", "dbus", "xterm"),
                "lxqt" to listOf("lxqt", "qterminal", "tigervnc", "dbus", "xterm"),
                "mate" to listOf("mate", "mate-terminal", "tigervnc", "dbus", "xterm"),
                "cinnamon" to listOf("cinnamon", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                "i3" to listOf("i3-wm", "i3status", "i3lock", "dmenu", "xterm", "tigervnc", "dbus"),
                "sway" to listOf("sway", "wayvnc", "xorg-x11-server-Xwayland", "foot", "mesa", "dbus"),
                "hyprland" to listOf("hyprland", "wayvnc", "xorg-x11-server-Xwayland", "kitty", "mesa", "dbus"),
                "dwm" to listOf("dwm", "st", "dmenu", "xterm", "tigervnc", "dbus")
            )
        ),
        DistroInstallProfile(
            id = "fedora",
            imageRepository = "library/fedora",
            imageTag = "latest",
            preInstallCommand = "",
            installCommand = "dnf -y --setopt=install_weak_deps=False install",
            desktopPackages = mapOf(
                "xfce" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc-server", "xterm"),
                "gnome" to listOf("gnome-session", "gnome-shell", "gnome-terminal", "tigervnc-server", "xterm", "dbus-x11"),
                "kde" to listOf("plasma-workspace", "plasma-desktop", "konsole", "tigervnc-server", "xterm", "dbus-x11"),
                "mate" to listOf("mate-desktop", "mate-terminal", "tigervnc-server", "xterm", "dbus-x11"),
                "cinnamon" to listOf("cinnamon", "gnome-terminal", "tigervnc-server", "xterm", "dbus-x11"),
                "sway" to listOf("sway", "wayvnc", "xorg-x11-server-Xwayland", "foot", "mesa-dri-drivers", "dbus-x11")
            )
        ),
        DistroInstallProfile(
            id = "kali",
            imageRepository = "kalilinux/kali-rolling",
            imageTag = "latest",
            preInstallCommand = "apt-get -o Acquire::Retries=3 update",
            installCommand = "apt-get -o Acquire::Retries=3 install -y --no-install-recommends",
            desktopPackages = mapOf(
                "xfce" to listOf("dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                "gnome" to listOf("dbus-x11", "gnome-session", "gnome-shell", "gnome-terminal", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base"),
                "kde" to listOf("dbus-x11", "plasma-workspace", "plasma-desktop", "konsole", "tigervnc-standalone-server", "tigervnc-tools", "xterm", "xfonts-base")
            )
        ),
        DistroInstallProfile(
            id = "alpine",
            imageRepository = "library/alpine",
            imageTag = "3.23",
            preInstallCommand = "apk update",
            installCommand = "apk add",
            desktopPackages = mapOf(
                "xfce" to listOf("dbus", "xfce4-session", "xfce4-panel", "xfce4-wm", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm"),
                "lxqt" to listOf("dbus", "lxqt", "qterminal", "tigervnc", "xterm"),
                "i3" to listOf("dbus", "i3wm", "i3status", "xterm", "tigervnc"),
                "dwm" to listOf("dbus", "dwm", "st", "dmenu", "xterm", "tigervnc")
            )
        ),
        DistroInstallProfile(
            id = "manjaro",
            imageRepository = "manjarolinux/base",
            imageTag = "latest",
            preInstallCommand = "pacman-key --init && pacman-key --populate manjaro",
            installCommand = "pacman -Syu --noconfirm --needed",
            desktopPackages = mapOf(
                "xfce" to listOf("manjaro-keyring", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "dbus", "xterm"),
                "gnome" to listOf("gnome", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                "kde" to listOf("plasma", "konsole", "tigervnc", "dbus", "xterm"),
                "mate" to listOf("mate", "mate-terminal", "tigervnc", "dbus", "xterm"),
                "cinnamon" to listOf("cinnamon", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                "i3" to listOf("i3-wm", "i3status", "i3lock", "dmenu", "xterm", "tigervnc", "dbus")
            )
        ),
        DistroInstallProfile(
            id = "opensuse",
            imageRepository = "opensuse/tumbleweed",
            imageTag = "latest",
            preInstallCommand = "zypper --non-interactive refresh",
            installCommand = "zypper --non-interactive install --no-recommends",
            desktopPackages = mapOf(
                "xfce" to listOf("dbus-1-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm"),
                "gnome" to listOf("gnome-session", "gnome-shell", "gnome-terminal", "tigervnc", "xterm"),
                "kde" to listOf("plasma5-workspace", "konsole", "tigervnc", "xterm"),
                "mate" to listOf("mate-session", "mate-terminal", "tigervnc", "xterm"),
                "cinnamon" to listOf("cinnamon", "gnome-terminal", "tigervnc", "xterm"),
                "i3" to listOf("i3", "i3status", "xterm", "tigervnc")
            )
        ),
        DistroInstallProfile(
            id = "void",
            lxcImagePath = "voidlinux/current",
            preInstallCommand = "",
            installCommand = "xbps-install -Sy",
            desktopPackages = mapOf(
                "xfce" to listOf("dbus", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings", "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm"),
                "gnome" to listOf("gnome", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                "kde" to listOf("plasma", "konsole", "tigervnc", "dbus", "xterm"),
                "lxqt" to listOf("lxqt", "qterminal", "tigervnc", "dbus", "xterm"),
                "mate" to listOf("mate", "mate-terminal", "tigervnc", "dbus", "xterm"),
                "cinnamon" to listOf("cinnamon", "gnome-terminal", "tigervnc", "dbus", "xterm"),
                "i3" to listOf("i3", "i3status", "xterm", "tigervnc", "dbus"),
                "sway" to listOf("sway", "wayvnc", "xorg-server-xwayland", "foot", "mesa", "dbus"),
                "hyprland" to listOf("hyprland", "wayvnc", "xorg-server-xwayland", "kitty", "mesa", "dbus"),
                "dwm" to listOf("dwm", "st", "dmenu", "xterm", "tigervnc", "dbus")
            )
        ),
        DistroInstallProfile(
            id = "gentoo",
            imageRepository = "gentoo/stage3",
            imageTag = "latest",
            preInstallCommand = "emerge-webrsync",
            installCommand = "MAKEOPTS=-j1 emerge --ask=n --jobs=1 --load-average=1",
            desktopPackages = mapOf(
                "xfce" to listOf("xfce-base/xfce4-meta", "net-misc/tigervnc", "x11-terms/xterm"),
                "gnome" to listOf("gnome-base/gnome", "x11-terms/gnome-terminal", "net-misc/tigervnc", "x11-terms/xterm"),
                "kde" to listOf("kde-plasma/plasma-meta", "kde-apps/konsole", "net-misc/tigervnc", "x11-terms/xterm"),
                "i3" to listOf("x11-wm/i3", "x11-misc/i3status", "x11-terms/xterm", "net-misc/tigervnc"),
                "sway" to listOf("gui-wm/sway", "gui-apps/wayvnc", "x11-base/xwayland", "gui-apps/foot", "media-libs/mesa", "sys-apps/dbus"),
                "hyprland" to listOf("gui-wm/hyprland", "gui-apps/wayvnc", "x11-base/xwayland", "x11-terms/kitty", "media-libs/mesa", "sys-apps/dbus"),
                "dwm" to listOf("x11-wm/dwm", "x11-terms/st", "x11-misc/dmenu", "x11-terms/xterm", "net-misc/tigervnc")
            ),
            minimumFreeBytes = 8L * 1024 * 1024 * 1024
        )
    )

    private val profilesById = profiles.associateBy(DistroInstallProfile::id)

    fun get(id: String): DistroInstallProfile? = profilesById[id]

    fun allIds(): Set<String> = profilesById.keys
}
