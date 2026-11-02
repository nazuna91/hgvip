plugins {
    id("com.android.application")
}

android {
    namespace = "com.dsh.hgvip"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dsh.hgvip"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
