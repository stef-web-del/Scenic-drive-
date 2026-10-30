import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "de.scenicroutes.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "de.scenicroutes.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 15
        versionName = "0.6.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Optionaler Navigations-Server (Einstellungen). Leer = Routen von Vercel/aus der App.
        buildConfigField("String", "ROUTING_PACK_URL", "\"https://wqofepnfttvlzsvq.public.blob.vercel-storage.com/routing/miltenberg-car-v1.tar.gz\"")
        buildConfigField("String", "ROUTING_PACK_SHA256", "\"dd9ee630828de62af7471d2bcb76f969b843d49122beaae080961c47b72a6b71\"")
        buildConfigField("long", "ROUTING_PACK_BYTES", "35960349L")
        buildConfigField("String", "DEFAULT_BACKEND", "\"${project.findProperty("scenic.backend") ?: "https://scenic-routes-nav-riederstef-8388.vercel.app"}\"")
    }
    // Fester Release-Schlüssel: app/keys/ (gitignored, nie veröffentlichen)
    val ksFile = rootProject.file("keys/keystore.properties")
    signingConfigs {
        if (ksFile.exists()) create("release") {
            val p = Properties().apply { ksFile.inputStream().use { load(it) } }
            storeFile = rootProject.file("keys/" + p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword"); keyAlias = p.getProperty("keyAlias"); keyPassword = p.getProperty("keyPassword")
        }
    }
    buildTypes {
        // „Runde ab Punkt“-Server: nur im Debug-Build vorbelegt (10.0.2.2 = Rechner hinter dem Emulator). Release: leer = aus.
        debug { buildConfigField("String", "FREE_LOOP_URL", "\"http://10.0.2.2:8765\"") }
        release {
            buildConfigField("String", "FREE_LOOP_URL", "\"\"")
            isMinifyEnabled = false // kein R8: nichts darf wegoptimiert werden
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "x86_64") } } // Handy + Emulator
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    testOptions { unitTests { isIncludeAndroidResources = true; all { it.maxHeapSize = "4g"; it.setForkEvery(12) } } }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":shared"))
    coreLibraryDesugaring(libs.desugar)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.lucide)  // Lucide-Icons (MIT), gleiche Icons wie die Web-App
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    implementation(libs.ferrostar.core)
    implementation(libs.ferrostar.ui.compose)
    implementation(libs.ferrostar.ui.maplibre)
    implementation(libs.ferrostar.gms)
    implementation(libs.maplibre.compose)
    implementation(libs.play.services.location)
    implementation(libs.okhttp)
    implementation(libs.valhalla.mobile)
    implementation("io.github.rallista:valhalla-models-config:0.6.0")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}


// Icon-Regel: Die App nutzt nur Lucide (wie die Web-App). Material-Icons brechen den Build ab.
val checkNoMaterialIcons by tasks.registering {
    group = "verification"
    description = "Verbietet androidx.compose.material.icons im App-Code."
    val sources = fileTree("src") { include("**/*.kt") }
    inputs.files(sources)
    doLast {
        val hits = sources.files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if (line.contains("androidx.compose.material.icons") || Regex("""\bIcons\.(Outlined|Filled|Default|Rounded|AutoMirrored|Sharp|TwoTone)\b""").containsMatchIn(line))
                    "${f.relativeTo(projectDir)}:${i + 1}: $line" else null
            }
        }
        if (hits.isNotEmpty()) throw GradleException("Material-Icons sind gesperrt, bitte Lucide nutzen:\n" + hits.joinToString("\n"))
    }
}
tasks.named("preBuild") { dependsOn(checkNoMaterialIcons) }
