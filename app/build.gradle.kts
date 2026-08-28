plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.udpfs.app"

    compileSdk = 36
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.udpfs.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    splits {
        abi {
            isEnable = providers.gradleProperty("abiSplits").getOrElse("false").toBoolean()
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        localeFilters += listOf("en")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "/META-INF/**/LICENSE*"
        resources.excludes += "/META-INF/*.version"
        resources.excludes += "DebugProbesKt.bin"
    }
}

ktlint {
    version.set("1.8.0")
}

dependencies {
    implementation(files("libs/udpfsbridge.aar"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
}

tasks.register<Exec>("bridgeAar") {
    workingDir = rootDir
    commandLine("make", "aar")
    inputs.dir(rootDir.resolve("udpfsdbridge"))
    inputs.files(rootDir.resolve("go.mod"), rootDir.resolve("go.sum"), rootDir.resolve("Makefile"))
    outputs.file("libs/udpfsbridge.aar")
}
tasks.named("preBuild") { dependsOn("bridgeAar") }
