plugins {
    id("com.android.application")
}

android {
    namespace = "com.mevolthome.upsmonitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mevolthome.upsmonitor"
        minSdk = 23
        targetSdk = 36
        versionCode = 11
        versionName = "1.10"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
