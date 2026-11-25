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

// Native builds are the default; hot reload is an explicit development option.
if (providers.gradleProperty("wastenot.hotReload").orElse("false").get().toBooleanStrict()) {
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
    val normalized = uri.toASCIIString().trimEnd('/')
    return if (normalized.endsWith("/v1")) "$normalized/" else "$normalized/v1/"
}

android {
    namespace = "com.example.wastenot"
    compileSdk = 37
    val googleWebClientId = providers.gradleProperty("wastenot.googleWebClientId")
        .orElse(providers.environmentVariable("WASTENOT_GOOGLE_WEB_CLIENT_ID"))
        .orElse("").get().trim()
    require(googleWebClientId.isEmpty() || Regex("[A-Za-z0-9._-]+\\.apps\\.googleusercontent\\.com").matches(googleWebClientId)) {
        "wastenot.googleWebClientId must be a Google OAuth Web client ID."
    }
    defaultConfig {
        applicationId = "com.example.wastenot"
        minSdk = 30
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.8"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
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
            val installSuffix = providers.gradleProperty("wastenot.debugApplicationIdSuffix").orElse("").get()
            require(installSuffix.isEmpty() || Regex("(\\.[a-z][a-z0-9]*)+").matches(installSuffix)) {
                "wastenot.debugApplicationIdSuffix must be a lowercase application ID suffix."
            }
            if (installSuffix.isNotEmpty()) {
                applicationIdSuffix = installSuffix
                resValue("string", "app_name", "WasteNot QA")
            }
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl("wastenot.debugApiBaseUrl", "https://waste-not-api.bccdev.id/v1/", allowLocalHttp = true)}\"")
        }
        create("staging") {
            initWith(getByName("debug"))
            // HotSwan 2.0.1 only provides suspend-lambda rewrite tasks for the debug variant.
            isDebuggable = false
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl("wastenot.stagingApiBaseUrl", "https://waste-not-api.bccdev.id/v1/")}\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl("wastenot.releaseApiBaseUrl", "https://waste-not-api.bccdev.id/v1/")}\"")
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    sourceSets {
        getByName("test").kotlin.directories.add("src/testFixtures/java")
        getByName("androidTest").kotlin.directories.add("src/testFixtures/java")
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
      resValues = true
      shaders = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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

// Resolve SDK jars through Gradle's repository/cache handling before the test JVM starts.
val robolectricSdks = configurations.create("robolectricSdks") { isTransitive = false }
val prepareRobolectricSdks = tasks.register<Sync>("prepareRobolectricSdks") {
    from(robolectricSdks)
    into(layout.buildDirectory.dir("robolectric-sdks"))
}
tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricSdks)
    systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-sdks").get().asFile.absolutePath)
    testLogging.exceptionFormat = TestExceptionFormat.FULL
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
  // API 35 is the explicit host-test SDK for Room and the generated navigation render check.
  robolectricSdks(libs.robolectric.sdk)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  testImplementation(libs.robolectric)
  implementation(libs.androidx.datastore.preferences)
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
  testImplementation(composeBom)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.credentials.core)
  implementation(libs.androidx.credentials.play.services.auth)
  implementation(libs.googleid)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.haze)
  implementation(libs.coil.compose)
  implementation(libs.vico.compose.m3)
  implementation(libs.compose.shimmer)
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
