import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

fun localProperty(key: String, default: String = ""): String {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return default
    return Properties().apply { file.inputStream().use { load(it) } }.getProperty(key) ?: default
}

val auraVersionBase = (project.findProperty("aura.versionBase") as String? ?: "534").toInt()
val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 0 }.getOrElse(0)
val shortSha = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }.getOrElse("dev")
val computedVersionCode = auraVersionBase + gitCommitCount

/**
 * sherpa-onnx is intentionally not in git. Fetch the pinned release AAR at configuration time
 * if it is missing. The voice layer imports sherpa directly, so a missing AAR is a hard build
 * error with a clear message rather than a wall of unresolved references.
 */
val sherpaVersion = "1.12.28"
val sherpaAar = file("libs/sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar")
val fetchSherpaAar by tasks.registering {
    outputs.file(sherpaAar)
    doLast {
        if (sherpaAar.exists()) return@doLast
        sherpaAar.parentFile.mkdirs()
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/" +
            "sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar"
        val downloaded = runCatching {
            URI(url).toURL().openStream().use { input ->
                sherpaAar.outputStream().use { output -> input.copyTo(output) }
            }
        }.onFailure {
            // Avoid `logger` here: it captures the build-script object, which the configuration
            // cache cannot serialize.
            System.err.println("sherpa-onnx download failed: ${it.message}")
        }.isSuccess && sherpaAar.length() > 0
        if (!downloaded) {
            sherpaAar.delete()
            throw GradleException(
                "sherpa-onnx AAR is required to compile the voice layer.\n" +
                    "Download it once and place it at ${sherpaAar.absolutePath}:\n  $url\n" +
                    "or run the build with network access.",
            )
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchSherpaAar) }

/**
 * Fetches voice model assets listed in `app/src/main/assets/models.txt`.
 * Format: one entry per line `<target-path-relative-to-assets> <url> [# comment]`.
 * Missing entries, empty file, or download failure all log a warning and continue — the build
 * never fails on model download; [VoiceEngine] and [WakeWordEngine] degrade cleanly without them.
 *
 * The official model index lives at:
 *   https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html
 * Pick a KWS model zip/tar.bz2, list its files here, and rebuild.
 * Example models.txt entry:
 *   kws/encoder.onnx https://example.com/kws/encoder.onnx
 */
val modelsTxt = file("src/main/assets/models.txt")
val fetchSherpaModels by tasks.registering {
    outputs.upToDateWhen {
        // considered up-to-date when every [[target]] already exists in the apk assets
        !modelsTxt.exists() || modelsTxt.readLines().filter { it.isNotBlank() && !it.trim().startsWith("#") }.all { line ->
            val target = line.trim().substringBefore(' ')
            file("src/main/assets/$target").exists()
        }
    }
    doLast {
        if (!modelsTxt.exists() || modelsTxt.readText().isBlank()) {
            System.err.println("voice models not listed — see app/src/main/assets/models.txt and docs/DECISIONS.md")
            return@doLast
        }
        val assetsDir = file("src/main/assets")
        modelsTxt.readLines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val parts = line.split(Regex("\\s+"), 2)
            if (parts.size != 2) { System.err.println("models.txt: bad line: $raw"); return@forEach }
            val (target, url) = parts
            val dest = File(assetsDir, target.removePrefix("/"))
            if (dest.exists()) { System.err.println("voice model already present: $target"); return@forEach }
            dest.parentFile?.mkdirs()
            runCatching {
                URI(url).toURL().openStream().use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
                System.err.println("fetched voice model: $target (${dest.length()} bytes)")
            }.onFailure {
                dest.delete()
                System.err.println("voice model download failed for $target: ${it.message} (voice disabled)")
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchSherpaModels) }

android {
    namespace = "com.aura.aura_ui"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aura.aura_ui.feature"
        minSdk = 26
        targetSdk = 36
        versionCode = computedVersionCode
        versionName = "1.0.$computedVersionCode"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "AURA_BUILD_SHA", "\"$shortSha\"")
        buildConfigField("int", "AURA_VERSION_BASE", auraVersionBase.toString())

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-$shortSha"
            isMinifyEnabled = false
            ndk {
                // x86_64 for emulators, arm64-v8a so physical debug builds install and run.
                abiFilters += listOf("x86_64", "arm64-v8a")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                abiFilters.clear()
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            signingConfig = if (rootProject.file("keystore.properties").exists() ||
                System.getenv("AURA_KEYSTORE_PATH") != null
            ) {
                signingConfigs.create("auraRelease") {
                    val props = rootProject.file("keystore.properties")
                    if (props.exists()) {
                        val p = Properties().apply { props.inputStream().use { load(it) } }
                        storeFile = rootProject.file(p.getProperty("storeFile"))
                        storeType = p.getProperty("storeType") ?: "JKS"
                        storePassword = p.getProperty("storePassword")
                        keyAlias = p.getProperty("keyAlias")
                        keyPassword = p.getProperty("keyPassword")
                    } else {
                        storeFile = System.getenv("AURA_KEYSTORE_PATH")?.let { file(it) }
                        storePassword = System.getenv("AURA_KEYSTORE_PASSWORD")
                        keyAlias = System.getenv("AURA_KEY_ALIAS")
                        keyPassword = System.getenv("AURA_KEY_PASSWORD")
                    }
                }
            } else {
                // CI (and any local release build without a production keystore) still needs an
                // installable APK. The debug key is used only as a fallback and the build report
                // states which key signed the artifact.
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/versions/**/module-info.class",
                "META-INF/*.SF",
                "META-INF/*.RSA",
                "META-INF/*.DSA",
            )
        }
        jniLibs {
            // sherpa-onnx's static-link AAR and onnxruntime-android both ship libonnxruntime.so.
            // The first one wins; sherpa's JNI is statically linked, so either copy is loadable.
            pickFirsts += "**/libonnxruntime.so"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":mcp-server"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.compose.google.fonts)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.media)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.google.material)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Optional Firebase backend (see firebase/README.md). The app is fully functional
    // offline; without app/google-services.json all Firebase calls are no-ops.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.remote.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.appcheck)
    implementation(libs.play.services.auth)

    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    implementation(libs.mcp.kotlin.client)

    implementation(libs.onnxruntime)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.bcpkix)
    implementation(libs.zxing.core)
    implementation(libs.commons.codec)
    implementation(libs.permissionx)
    implementation(libs.lottie.compose)
    implementation(libs.shimmer)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
}

// Firebase is opt-in: without app/google-services.json this build is offline-only.
// CI injects the file from GOOGLE_SERVICES_JSON_BASE64 when the secret is set.
if (rootProject.file("app/google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle("app/google-services.json is missing; building offline-only (Firebase sync disabled).")
}
