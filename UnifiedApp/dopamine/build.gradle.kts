plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.reeltracker"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    sourceSets["main"].java.srcDirs("../../Dopamine-Diet/DopamineDiet/app/src/main/java")
    sourceSets["main"].res.srcDirs("../../Dopamine-Diet/DopamineDiet/app/src/main/res")
    sourceSets["main"].manifest.srcFile("src/main/AndroidManifest.xml")
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // The unified app hosts public UI and service types from the original module.
    api(project(":service"))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}
