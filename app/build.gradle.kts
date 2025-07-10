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

        resValue("string", "BANNER_AD_ID", "ca-app-pub-3940256099942544/6300978111") // Reemplazar con ID real en producción
    }

    buildFeatures {
        buildConfig = true // Habilita la generación de BuildConfig
        viewBinding = true
    }

    signingConfigs {
        create("release") {
            // Configura manualmente para pruebas; reemplaza con valores reales en producción
            storeFile = file("path/to/your.keystore") // Reemplazar con la ruta real si existe
            storePassword = "your_store_password" // Reemplazar con tu contraseña
            keyAlias = "your_key_alias" // Reemplazar con tu alias
            keyPassword = "your_key_password" // Reemplazar con tu contraseña
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
            buildConfigField("String", "REWARDED_AD_TASK_ID", "\"ca-app-pub-3940256099942544/5224354917\"") // Reemplazar con ID real
            buildConfigField("String", "REWARDED_AD_SPINS_ID", "\"ca-app-pub-3940256099942544/5224354917\"") // Reemplazar con ID real
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

    configurations.all {
        resolutionStrategy {
            force("com.google.android.gms:play-services-ads:23.1.0")
            force("com.google.android.gms:play-services-ads-lite:23.1.0")
            force("com.google.android.gms:play-services-measurement-api:22.0.2")
            force("com.google.android.gms:play-services-measurement:22.0.2")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.android.gms:play-services-ads:23.1.0") {
        exclude(group = "com.google.android.gms", module = "play-services-measurement-api")
    }
    implementation("com.google.android.gms:play-services-measurement-api:22.0.2")
    implementation("com.google.android.ump:user-messaging-platform:2.2.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}