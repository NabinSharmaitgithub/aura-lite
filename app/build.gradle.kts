import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
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
                abiFilters += "x86_64"
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
