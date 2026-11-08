import java.net.URI
import com.skydoves.compose.hotswan.interpreter.compiler.gradle.InterpreterExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hotswan.compiler) apply false
  alias(libs.plugins.ksp)
  alias(libs.plugins.skydoves.navgraph)
}

// Native verification and hot reload both use the Android 11 baseline.
if (providers.gradleProperty("wastenot.hotReload").orElse("true").get().toBooleanStrict()) {
    pluginManager.apply("com.github.skydoves.compose.hotswan.compiler")
    extensions.configure<InterpreterExtension> {
        // HotSwan 2.0.1 cannot bridge this DTO's cross-file credential value classes.
        exclude("com.example.wastenot.data.network.dto.SessionTokens")
    }
}

fun apiBaseUrl(property: String, defaultValue: String = "", allowLocalHttp: Boolean = false): String {
    val value = providers.gradleProperty(property).orElse(defaultValue).get().trim()
    require(value.isNotEmpty()) { "$property must not be empty." }
    val uri = runCatching { URI(value) }.getOrElse {
        error("$property must be an absolute API URL.")
    }
    require(!uri.host.isNullOrBlank() && (uri.port == -1 || uri.port in 1..65535)) {
        "$property must have a valid host and port."
    }
    require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
        "$property must not contain credentials, query parameters or a fragment."
    }
    val localHttp = allowLocalHttp && uri.scheme.equals("http", ignoreCase = true) &&
        uri.host.lowercase() in setOf("10.0.2.2", "localhost", "127.0.0.1")
    require(uri.scheme.equals("https", ignoreCase = true) || localHttp) {
        "$property must use HTTPS; debug HTTP is limited to emulator/localhost."
    }
    return uri.toASCIIString().trimEnd('/') + "/"
}

android {
    namespace = "com.example.wastenot"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.wastenot"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "com.example.wastenot.WasteNotTestRunner"
    }

    val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull?.takeIf { it.isNotBlank() }
    val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull?.takeIf { it.isNotBlank() }
    val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull?.takeIf { it.isNotBlank() }
    val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull?.takeIf { it.isNotBlank() }
    val signingValues = listOf(releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword)
    require(signingValues.all { it == null } || signingValues.none { it == null }) {
        "Release signing requires all four ANDROID_KEYSTORE_PATH/PASSWORD and ANDROID_KEY_ALIAS/PASSWORD variables."
    }

    if (
        releaseKeystorePath != null &&
        releaseKeystorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
    ) {
        val rawFile = File(releaseKeystorePath)
        val keystoreFile = if (rawFile.isAbsolute) rawFile else rootProject.file(releaseKeystorePath)
        require(keystoreFile.isFile) { "Release keystore file does not exist." }
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl("wastenot.debugApiBaseUrl", "https://pred-autoscaler.bccdev.id/wastenot/api/v1/", allowLocalHttp = true)}\"")
        }
        create("staging") {
            initWith(getByName("debug"))
            // HotSwan 2.0.1 only provides suspend-lambda rewrite tasks for the debug variant.
            isDebuggable = false
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl("wastenot.stagingApiBaseUrl", "https://pred-autoscaler.bccdev.id/wastenot/api/v1/")}\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl("wastenot.releaseApiBaseUrl", "https://pred-autoscaler.bccdev.id/wastenot/api/v1/")}\"")
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    sourceSets {
        getByName("test").kotlin.srcDir("src/testFixtures/java")
        getByName("androidTest").kotlin.srcDir("src/testFixtures/java")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    testLogging.exceptionFormat = TestExceptionFormat.FULL
}

dependencies {
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.work.runtime)
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(libs.retrofit)
  implementation(libs.retrofit.serialization)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.mockwebserver)
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
