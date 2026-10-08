plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.meuiptv.player"
    compileSdk = 34

    defaultConfig {
        // Troque por um identificador seu antes de publicar (ex: com.seunome.iptv)
        applicationId = "com.meuiptv.player"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Chave fixa SÓ PARA TESTES: assim cada APK novo instala por cima do anterior.
    // Para publicar na Play Store, crie outra chave e guarde fora do GitHub (veja o LEIA-ME).
    signingConfigs {
        create("teste") {
            storeFile = file("teste.keystore")
            storePassword = "xbrteste"
            keyAlias = "teste"
            keyPassword = "xbrteste"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("teste")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("teste")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Player de vídeo (o mesmo motor usado por muitos apps de IPTV)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Carregar logos dos canais e capas dos filmes
    implementation("io.coil-kt:coil:2.7.0")
}
