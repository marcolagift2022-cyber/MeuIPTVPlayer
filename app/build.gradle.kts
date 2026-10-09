plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.meuiptv.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.meuiptv.player"
        minSdk = 21
        targetSdk = 34
        // No GitHub, cada compilação ganha um número novo (1.0.1, 1.0.2...)
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
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
        // Chave da Play Store: NUNCA fica no GitHub. O GitHub Actions monta o arquivo
        // a partir dos "Secrets" do repositório (veja .github/workflows/build-play.yml).
        val uploadKeystore = System.getenv("UPLOAD_KEYSTORE_FILE")
        if (uploadKeystore != null && file(uploadKeystore).exists()) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // Duas versões do mesmo app:
    //  - direto: a de hoje (Downloader/APK). DNS na nuvem, cliente digita só usuário e senha,
    //            e o próprio app avisa e instala atualizações.
    //  - play:   para a Google Play. Reprodutor genérico (o cliente informa o servidor ou a lista),
    //            sem atualização própria (quem atualiza é a Play Store).
    flavorDimensions += "loja"
    productFlavors {
        create("direto") {
            dimension = "loja"
            applicationId = "com.meuiptv.player"
            targetSdk = 34
            buildConfigField("boolean", "PLAY_STORE", "false")
        }
        create("play") {
            dimension = "loja"
            applicationId = "com.xbrtopcine.player"
            targetSdk = 36
            buildConfigField("boolean", "PLAY_STORE", "true")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("teste")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("teste")
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
