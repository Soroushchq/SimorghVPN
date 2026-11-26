import java.io.File
import java.net.HttpURLConnection
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.simorgh.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simorgh.vpn"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // VPN cores
    implementation(files("libs/libv2ray.aar")) // Xray core (auto-downloaded if missing)
    implementation(files("libs/hev-socks5-tunnel.aar")) // tun2socks

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.ui.tooling)
}

// ---------------------------------------------------------------------------
// Xray core auto-download (local dev convenience; CI downloads it explicitly)
// ---------------------------------------------------------------------------
val xrayCoreAar = File(projectDir, "libs/libv2ray.aar")

tasks.register("downloadXrayCore") {
    group = "build setup"
    description = "Downloads libv2ray.aar (Xray core) from 2dust/AndroidLibXrayLite if missing."
    onlyIf { !xrayCoreAar.exists() }
    doLast {
        val repo = "2dust/AndroidLibXrayLite"
        logger.lifecycle("[simorgh] resolving latest release of $repo ...")
        val conn = URI("https://api.github.com/repos/$repo/releases/latest").toURL()
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let {
            conn.setRequestProperty("Authorization", "Bearer $it")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val url = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]*libv2ray\\.aar)\"")
            .find(body)?.groupValues?.get(1)
            ?: error("libv2ray.aar not found in latest $repo release")
        logger.lifecycle("[simorgh] downloading $url")
        xrayCoreAar.parentFile.mkdirs()
        val tmp = File(xrayCoreAar.parentFile, "libv2ray.aar.part")
        URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        check(tmp.length() > 1_000_000) { "downloaded file too small: ${tmp.length()}" }
        check(tmp.renameTo(xrayCoreAar)) { "cannot move downloaded aar" }
        logger.lifecycle("[simorgh] Xray core ready: ${xrayCoreAar.absolutePath} (${xrayCoreAar.length()} bytes)")
    }
}

tasks.named("preBuild") { dependsOn("downloadXrayCore") }
