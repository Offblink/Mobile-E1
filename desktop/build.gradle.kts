import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.compose") version "1.9.0"
    // Kotlin 2.0+ 起 Compose 编译器单独成插件，版本必须与 kotlin 一致
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

group = "com.offblink.keyou"
version = "1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Compose Multiplatform 桌面端（material3 不在 currentOs 里，要单独加）
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "com.offblink.keyou.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "KeyouClassGroup"
            packageVersion = "1.0.0"
        }
    }
}

/** 无界面自检：gradlew -p desktop checkApi -PbaseUrl=http://127.0.0.1:8081 */
tasks.register<JavaExec>("checkApi") {
    group = "verification"
    description = "Ping the server without opening a window"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.offblink.keyou.desktop.MainKt")
    args("--check", (project.findProperty("baseUrl") as String?) ?: "http://127.0.0.1:8081")
}
