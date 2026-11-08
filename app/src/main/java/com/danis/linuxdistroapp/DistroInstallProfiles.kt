package com.danis.linuxdistroapp

data class DistroInstallProfile(
    val id: String,
    val imageRepository: String? = null,
    val imageTag: String? = null,
    val lxcImagePath: String? = null,
    val preInstallCommand: String,
    val installCommand: String,
    val desktopPackages: List<String>,
    val minimumFreeBytes: Long = 4L * 1024 * 1024 * 1024
)

object DistroInstallProfiles {
    private val profiles = listOf(
        DistroInstallProfile(
            id = "debian",
            imageRepository = "library/debian",
            imageTag = "bookworm-slim",
            preInstallCommand = "apt-get -o Acquire::Retries=3 update",
            installCommand = "apt-get -o Acquire::Retries=3 install -y --no-install-recommends",
            desktopPackages = listOf(
                "dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings",
                "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server",
                "tigervnc-tools", "xterm", "xfonts-base"
            )
        ),
        DistroInstallProfile(
            id = "ubuntu",
            imageRepository = "library/ubuntu",
            imageTag = "noble",
            preInstallCommand = "apt-get -o Acquire::Retries=3 update",
            installCommand = "apt-get -o Acquire::Retries=3 install -y --no-install-recommends",
            desktopPackages = listOf(
                "dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings",
                "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server",
                "tigervnc-tools", "xterm", "xfonts-base"
            )
        ),
        DistroInstallProfile(
            id = "arch",
            lxcImagePath = "archlinux/current",
            preInstallCommand = "pacman-key --init && pacman-key --populate archlinux",
            installCommand = "pacman -Syu --noconfirm --needed",
            desktopPackages = listOf(
                "archlinux-keyring", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings",
                "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "dbus", "xterm"
            )
        ),
        DistroInstallProfile(
            id = "fedora",
            imageRepository = "library/fedora",
            imageTag = "latest",
            preInstallCommand = "",
            installCommand = "dnf -y --setopt=install_weak_deps=False install",
            desktopPackages = listOf(
                "dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings",
                "xfdesktop", "xfce4-terminal", "thunar", "tigervnc-server", "xterm"
            )
        ),
        DistroInstallProfile(
            id = "kali",
            imageRepository = "kalilinux/kali-rolling",
            imageTag = "latest",
            preInstallCommand = "apt-get -o Acquire::Retries=3 update",
            installCommand = "apt-get -o Acquire::Retries=3 install -y --no-install-recommends",
            desktopPackages = listOf(
                "dbus-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings",
                "xfdesktop4", "xfce4-terminal", "thunar", "tigervnc-standalone-server",
                "tigervnc-tools", "xterm", "xfonts-base"
            )
        ),
        DistroInstallProfile(
            id = "alpine",
            imageRepository = "library/alpine",
            imageTag = "3.23",
            preInstallCommand = "apk update",
            installCommand = "apk add",
            desktopPackages = listOf(
                "dbus", "xfce4-session", "xfce4-panel", "xfce4-wm", "xfce4-settings",
                "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm"
            )
        ),
        DistroInstallProfile(
            id = "manjaro",
            imageRepository = "manjarolinux/base",
            imageTag = "latest",
            preInstallCommand = "pacman-key --init && pacman-key --populate manjaro",
            installCommand = "pacman -Syu --noconfirm --needed",
            desktopPackages = listOf(
                "manjaro-keyring", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings",
                "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "dbus", "xterm"
            )
        ),
        DistroInstallProfile(
            id = "opensuse",
            imageRepository = "opensuse/tumbleweed",
            imageTag = "latest",
            preInstallCommand = "zypper --non-interactive refresh",
            installCommand = "zypper --non-interactive install --no-recommends",
            desktopPackages = listOf(
                "dbus-1-x11", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings",
                "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm"
            )
        ),
        DistroInstallProfile(
            id = "void",
            lxcImagePath = "voidlinux/current",
            preInstallCommand = "",
            installCommand = "xbps-install -Sy",
            desktopPackages = listOf(
                "dbus", "xfce4-session", "xfce4-panel", "xfwm4", "xfce4-settings",
                "xfdesktop", "xfce4-terminal", "thunar", "tigervnc", "xterm"
            )
        ),
        DistroInstallProfile(
            id = "gentoo",
            imageRepository = "gentoo/stage3",
            imageTag = "latest",
            preInstallCommand = "emerge-webrsync",
            installCommand = "MAKEOPTS=-j1 emerge --ask=n --jobs=1 --load-average=1",
            desktopPackages = listOf("xfce-base/xfce4-meta", "net-misc/tigervnc", "x11-terms/xterm"),
            minimumFreeBytes = 8L * 1024 * 1024 * 1024
        )
    )

    private val profilesById = profiles.associateBy(DistroInstallProfile::id)

    fun get(id: String): DistroInstallProfile? = profilesById[id]

    fun allIds(): Set<String> = profilesById.keys
}
