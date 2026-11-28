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
        versionCode = 8
        versionName = "0.3.1"
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
    implementation(libs.zxing.embedded) // QR scanner (Derafsh/V2rayNG pattern)
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
        xrayCoreAar.parentFile.mkdirs()
        val tmp = File(xrayCoreAar.parentFile, "libv2ray.aar.part")

        // 1) direct redirect (no API — survives API rate limits)
        val direct = "https://github.com/$repo/releases/latest/download/libv2ray.aar"
        logger.lifecycle("[simorgh] downloading $direct")
        val directOk = runCatching {
            URI(direct).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.length() > 1_000_000
        }.getOrDefault(false)

        // 2) fallback: resolve asset url via the GitHub API
        if (!directOk) {
            logger.lifecycle("[simorgh] direct download failed; resolving via API ...")
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
            URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        }

        check(tmp.length() > 1_000_000) { "downloaded file too small: ${tmp.length()}" }
        check(tmp.renameTo(xrayCoreAar)) { "cannot move downloaded aar" }
        logger.lifecycle("[simorgh] Xray core ready: ${xrayCoreAar.absolutePath} (${xrayCoreAar.length()} bytes)")
    }
}

// ---------------------------------------------------------------------------
// Built-in free-config seed auto-download — snapshot of the default public
// pool (0xRadikal top100, health-checked hourly upstream). Fetched at build
// time so every release ships fresh configs. Mirrors BuiltInSources index 0.
// ---------------------------------------------------------------------------
val builtinSeed = File(projectDir, "src/main/assets/builtin_seed.txt")
val builtinSeedUrl = "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/top100.txt"

tasks.register("downloadBuiltinSeed") {
    group = "build setup"
    description = "Downloads the built-in free-config seed snapshot if missing."
    onlyIf { !builtinSeed.exists() }
    doLast {
        logger.lifecycle("[simorgh] downloading built-in seed from $builtinSeedUrl")
        builtinSeed.parentFile.mkdirs()
        val tmp = File(builtinSeed.parentFile, "builtin_seed.txt.part")
        URI(builtinSeedUrl).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        val links = tmp.readText().lineSequence().count { it.matches(Regex("^(vless|vmess|trojan|ss)://.*")) }
        check(links >= 50) { "seed snapshot too small: only $links links" }
        check(tmp.renameTo(builtinSeed)) { "cannot move downloaded seed" }
        logger.lifecycle("[simorgh] built-in seed ready: $links configs")
    }
}

tasks.named("preBuild") {
    dependsOn("downloadXrayCore", "downloadBuiltinSeed")
}
