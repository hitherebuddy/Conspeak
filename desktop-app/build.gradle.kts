import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.compose") version "1.5.12"
}

group = "com.conspeak"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(project(":protocol"))

    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // mDNS discovery
    implementation("org.jmdns:jmdns:3.5.9")

    // Opus codec (pure Java) - bundled JAR for reliable offline builds
    implementation(files("libs/concentus-1.0.2.jar"))

    // JNA for WASAPI/audio device interaction
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.11")

    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "com.conspeak.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Conspeak"
            packageVersion = "1.0.0"
            description = "Use your phone as a microphone"
            vendor = "Conspeak"

            windows {
                menuGroup = "Conspeak"
                upgradeUuid = "c0n5p3ak-d3sk-t0p1-w1nd-0ws1n5tall3r"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
