plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.follgramer.diamantesproplayers"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.follgramer.diamantesproplayers"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resValue("string", "BANNER_AD_ID", "ca-app-pub-3940256099942544/6300978111")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("path/to/your.keystore")
            storePassword = "your_store_password"
            keyAlias = "your_key_alias"
            keyPassword = "your_key_password"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "REWARDED_AD_TASK_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "REWARDED_AD_SPINS_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
        debug {
            buildConfigField("String", "REWARDED_AD_TASK_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "REWARDED_AD_SPINS_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src/main/assets")
            }
        }
    }

    // ✅ RESOLUCIÓN DE CONFLICTOS DE DEPENDENCIAS CORREGIDA
    configurations.all {
        resolutionStrategy {
            // Usar versiones que realmente existen
            force("com.google.android.gms:play-services-ads:22.6.0")
            force("com.google.android.gms:play-services-ads-lite:22.6.0")
            force("com.google.android.gms:play-services-measurement-api:22.0.2")
            force("com.google.android.gms:play-services-measurement:22.0.2")
            force("com.google.android.gms:play-services-basement:18.3.0")

            // Excluir versiones conflictivas
            exclude(group = "com.google.android.gms", module = "play-services-safetynet")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    // ✅ DEPENDENCIAS CORREGIDAS CON VERSIONES COMPATIBLES
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Google Ads con versiones que existen
    implementation("com.google.android.gms:play-services-ads:22.6.0")

    // NO INCLUIR manualmente measurement - se resuelve automáticamente
    // implementation("com.google.android.gms:play-services-ads-lite:22.6.0")
    // implementation("com.google.android.gms:play-services-measurement-api:22.6.0")

    // --- Otras dependencias existentes ---
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.1")
    implementation("androidx.webkit:webkit:1.11.0")

    // Firebase BOM para gestión de versiones
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    implementation("com.google.code.gson:gson:2.10.1")

    // User Messaging Platform para consentimiento
    implementation("com.google.android.ump:user-messaging-platform:2.2.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}