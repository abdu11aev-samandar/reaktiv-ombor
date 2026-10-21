import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "uz.reaktivombor"
// MSI’ning ProductCode’i versiyadan olinadi: bir xil versiyali ikkinchi o‘rnatuvchi birinchisining ustiga o‘rnatilmaydi.
// Shuning uchun CI har yig‘ishga yangi raqam beradi (-PappVersion=1.0.<run_number>); bu qiymat — mahalliy yig‘ish uchun.
val appVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")
version = appVersion

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

// Windows 10/11 da Universal CRT (ucrtbase.dll, api-ms-win-*.dll) tizimning o‘zida bor.
// Ularning dastur papkasidagi nusxasini antiviruslar “tizim DLL’ini almashtirish” deb bloklaydi va
// MSI “Error writing to file … runtime\\bin\\ucrtbase.dll” xatosi bilan to‘xtaydi — shuning uchun olib tashlaymiz.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask>().configureEach {
    doLast {
        val bin = destinationDir.get().asFile.resolve("bin")
        bin.listFiles()
            ?.filter { it.name.equals("ucrtbase.dll", ignoreCase = true) || it.name.startsWith("api-ms-win-", ignoreCase = true) }
            ?.forEach { file ->
                logger.lifecycle("Runtime’dan olib tashlandi: ${file.name}")
                file.delete()
            }
    }
}

// jpackage’ning .deb skriptlari menyu yorlig‘ini xdg-desktop-menu bilan qo‘yadi. Menyu papkalari bo‘lmagan tizimda
// (/usr/share/desktop-directories yo‘q) u xato beradi va paket “yarim o‘rnatilgan” bo‘lib qoladi — hatto o‘chirib ham
// bo‘lmaydi. Yorliq — qo‘shimcha narsa: xato bo‘lsa .desktop faylni o‘zimiz qo‘yamiz (va o‘chiramiz).
tasks.matching { it.name == "packageDeb" }.configureEach {
    doLast {
        val desktopFile = "/usr/share/applications/reaktivlar-Reaktivlar.desktop"
        val work = temporaryDir.resolve("deb")
        fun run(vararg command: String) {
            val process = ProcessBuilder(*command).inheritIO().start()
            check(process.waitFor() == 0) { "Bajarilmadi: ${command.joinToString(" ")}" }
        }
        fun patch(script: String, line: Regex, replacement: String) {
            val file = work.resolve("DEBIAN/$script")
            val text = file.readText()
            if (desktopFile in text) return
            check(line.containsMatchIn(text)) { "$script: xdg-desktop-menu qatori topilmadi" }
            file.writeText(text.replace(line, replacement))
        }
        layout.buildDirectory.dir("compose/binaries/main/deb").get().asFile
            .listFiles { file -> file.extension == "deb" }
            ?.forEach { deb ->
                work.deleteRecursively()
                run("dpkg-deb", "-R", deb.path, work.path)
                patch(
                    "postinst",
                    Regex("""^(xdg-desktop-menu install (\S+))[ \t]*$""", RegexOption.MULTILINE),
                    "$1 2>/dev/null || install -D -m 644 $2 $desktopFile",
                )
                patch(
                    "prerm",
                    Regex("""^(xdg-desktop-menu uninstall \S+)[ \t]*$""", RegexOption.MULTILINE),
                    "$1 2>/dev/null || true\nrm -f $desktopFile",
                )
                run("dpkg-deb", "--build", "--root-owner-group", "-Zxz", work.path, deb.path)
                logger.lifecycle("Menyu yorlig‘i skriptlari tuzatildi: ${deb.name}")
            }
    }
}

compose.desktop {
    application {
        mainClass = "uz.reaktivombor.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Reaktivlar"
            packageVersion = appVersion
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
                // Menyuda “Ofis” bo‘limida chiqadi (aks holda “Boshqa”).
                menuGroup = "Office"
            }
        }
    }
}
