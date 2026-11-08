import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.danis.linuxdistroapp"
    compileSdk = 35
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.danis.linuxdistroapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        externalNativeBuild {
            cmake {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(layout.buildDirectory.dir("generated/prootJniLibs"))
            assets.srcDir(layout.buildDirectory.dir("generated/prootSources/licenses"))
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

val prootSourcesDirectory = layout.buildDirectory.dir("generated/prootSources")
val prepareProotSources = tasks.register("prepareProotSources") {
    val outputDirectory = prootSourcesDirectory.get().asFile
    outputs.dir(outputDirectory)

    doLast {
        fun downloadVerified(url: String, expectedSha256: String, destination: File) {
            if (destination.isFile) {
                val existingHash = MessageDigest.getInstance("SHA-256")
                    .digest(destination.readBytes())
                    .joinToString("") { "%02x".format(it) }
                if (existingHash == expectedSha256) return
                destination.delete()
            }

            destination.parentFile.mkdirs()
            val partial = File(destination.parentFile, "${destination.name}.part")
            URI(url).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 120_000
            }.getInputStream().use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }

            val actualHash = MessageDigest.getInstance("SHA-256")
                .digest(partial.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(actualHash == expectedSha256) {
                "SHA-256 check failed for ${destination.name}"
            }
            check(partial.renameTo(destination)) {
                "Unable to finalize ${destination.name}"
            }
        }

        val archiveDirectory = File(outputDirectory, "archives")
        val prootArchive = File(archiveDirectory, "proot-5.1.107.102.zip")
        val tallocArchive = File(archiveDirectory, "talloc-2.5.0.tar.gz")
        downloadVerified(
            "https://github.com/termux/proot/archive/refs/tags/v5.1.107.102.zip",
            "30d12a6e2cc4147123756ff58edea262d3083bbc238c12d0ab9b39e3d1c40852",
            prootArchive
        )
        downloadVerified(
            "https://download.samba.org/pub/talloc/talloc-2.5.0.tar.gz",
            "912afa237510ae542a7733998eb18a12bcda35ab6729c8e2ddb43e8d0ebab007",
            tallocArchive
        )

        val prootDirectory = File(outputDirectory, "proot")
        val tallocDirectory = File(outputDirectory, "talloc")
        prootDirectory.deleteRecursively()
        tallocDirectory.deleteRecursively()
        copy {
            from(zipTree(prootArchive)) {
                include("proot-5.1.107.102/src/**")
                eachFile {
                    relativePath = RelativePath(true, *relativePath.segments.drop(2).toTypedArray())
                }
                includeEmptyDirs = false
            }
            from(zipTree(prootArchive)) {
                include("proot-5.1.107.102/COPYING")
                eachFile {
                    relativePath = RelativePath(true, "COPYING")
                }
                includeEmptyDirs = false
            }
            into(prootDirectory)
        }
        copy {
            from(tarTree(resources.gzip(tallocArchive))) {
                include(
                    "talloc-2.5.0/talloc.c",
                    "talloc-2.5.0/talloc.h",
                    "talloc-2.5.0/LICENSE"
                )
                includeEmptyDirs = false
            }
            into(tallocDirectory)
        }
        val licenseDirectory = File(outputDirectory, "licenses")
        licenseDirectory.mkdirs()
        File(prootDirectory, "COPYING").copyTo(
            File(licenseDirectory, "PROOT-COPYING"),
            overwrite = true
        )
        File(tallocDirectory, "talloc-2.5.0/LICENSE").copyTo(
            File(licenseDirectory, "TALLOC-COPYING"),
            overwrite = true
        )
        check(File(prootDirectory, "arch.h").isFile &&
            File(tallocDirectory, "talloc-2.5.0/talloc.c").isFile &&
            File(licenseDirectory, "PROOT-COPYING").isFile &&
            File(licenseDirectory, "TALLOC-COPYING").isFile) {
            "Failed to prepare pinned PRoot and talloc sources"
        }
    }
}

tasks.configureEach {
    if (name.startsWith("configureCMake") ||
        (name.startsWith("merge") && name.endsWith("Assets"))) {
        dependsOn(prepareProotSources)
    }
}

android.defaultConfig.externalNativeBuild.cmake.arguments += listOf(
    "-DPROOT_SOURCE_DIR=${File(prootSourcesDirectory.get().asFile, "proot").absolutePath}",
    "-DTALLOC_SOURCE_DIR=${File(prootSourcesDirectory.get().asFile, "talloc/talloc-2.5.0").absolutePath}",
    "-DPROOT_JNI_OUTPUT_DIR=${layout.buildDirectory.dir("generated/prootJniLibs").get().asFile.absolutePath}"
)

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-graphics:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.material:material:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.tukaani:xz:1.10")
}