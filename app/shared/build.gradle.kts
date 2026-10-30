plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "de.scenicroutes.shared"
        compileSdk = 37
        minSdk = 29
        withHostTestBuilder {}
    }
    jvm() // für schnelle Unit-Tests der gemeinsamen Logik auf dem Host
    // iOS-Ziele: Struktur steht, Build erst auf macOS (Meilenstein M2)
    iosArm64(); iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies { implementation(libs.sqldelight.android) }
        jvmMain.dependencies { implementation(libs.sqldelight.jvm) }
        jvmTest.dependencies { implementation(libs.sqldelight.jvm) }
        iosMain.dependencies { implementation(libs.sqldelight.native) }
    }
}

sqldelight {
    databases { create("ScenicDb") {
        packageName.set("de.scenicroutes.shared.db")
        // Schema-Abzüge je Version (1–3 aus den veröffentlichten APKs rekonstruiert); prüft jede Migration bis zum aktuellen Schema.
        schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
        verifyMigrations.set(true)
    } }
}

tasks.matching { it.name == "verifyCommonMainScenicDbMigration" }.configureEach { mustRunAfter("generateCommonMainScenicDbSchema") }
