import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing reads from a keystore.properties file that is never committed (see
// .gitignore and docs/RELEASE.md) — its absence must not break debug builds or this
// sandbox's build, so the release signing config is only wired up when it actually exists.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigningConfig = keystorePropertiesFile.exists()

// SECURITY.md and THREAT_MODEL.md are written for end users (see AboutScreen),
// not just developers browsing the repo, so they ship inside the APK as assets
// instead of only existing on GitHub. Copied at build time from the single
// source of truth in docs/ rather than duplicated by hand, so they can't drift.
val legalAssetsDir = layout.buildDirectory.dir("generated/legalAssets")
val copyLegalDocs by tasks.registering(Copy::class) {
    from(rootProject.file("docs")) {
        include("SECURITY.md", "THREAT_MODEL.md")
    }
    into(legalAssetsDir)
}

android {
    namespace = "com.sharif.sink.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sharif.sink"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(legalAssetsDir)
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// AGP's asset-merging tasks don't know about copyLegalDocs unless told explicitly —
// declaring assets.srcDir() alone doesn't create a task dependency.
tasks.matching { it.name.matches(Regex("merge.*Assets")) }.configureEach {
    dependsOn(copyLegalDocs)
}

dependencies {
    api("com.sharif.sink:protocol")
    api("com.sharif.sink:mesh-engine")

    implementation(project(":core:common"))
    implementation(project(":core:crypto"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:networking"))
    implementation(project(":core:logging"))
    implementation(project(":core:permissions"))

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:conversations"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:contacts"))
    implementation(project(":feature:discovery"))
    implementation(project(":feature:mesh"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:education"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
