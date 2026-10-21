import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "uz.reaktivombor"
version = "1.0.0"

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        // Rang qoidasi, validatsiya, miqdor formati va zaxira fayli — Android ilova bilan bitta kod.
        kotlin.srcDir("../android/app/src/main/java/uz/reaktivombor/domain")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    // Android’da org.json tizimning o‘zida bor; kompyuterda alohida kutubxona.
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

compose.desktop {
    application {
        mainClass = "uz.reaktivombor.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Reaktivlar"
            packageVersion = "1.0.0"
            description = "Laboratoriya reaktivlari hisobi"
            vendor = "Reaktivlar hisobi"
            modules("java.instrument", "jdk.unsupported")

            windows {
                menuGroup = "Reaktivlar"
                shortcut = true
                dirChooser = true
                // Administrator huquqisiz o‘rnatiladi (laboratoriya kompyuterlari uchun qulay).
                perUserInstall = true
                // O‘zgarmasin: yangi versiya eskisining ustidan o‘rnatilishi uchun kerak.
                upgradeUuid = "6f0b7a52-3c1e-4d7b-9a51-2e8f4c6d9b13"
                iconFile.set(project.file("icons/reaktivlar.ico"))
            }
            linux {
                iconFile.set(project.file("icons/reaktivlar.png"))
            }
        }
    }
}
