import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.quine.feature.onboarding"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        // 不显式设这个，测试 apk 装到设备后用的是平台默认的
        // InstrumentationTestRunner，它不认 @RunWith(AndroidJUnit4::class)
        // ——「OK (0 tests)」沉默失败。feature-chat 里有这条；feature-onboarding 漏了，
        // 之前的 SandboxSetupScreenTest 一次也没真正跑过。
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core-common"))
    // 公开接口（OnboardingDeps）暴露 QuineSettings / ProviderConfig，故为 api。
    api(project(":core-storage"))
    api(project(":core-gateway"))
    // SandboxSetupDeps 暴露 SandboxState，故为 api。
    api(project(":core-sandbox"))
    implementation(project(":core-design"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // 注入 AndroidJUnitRunner 的 manifest 条目 —— 不加这条，
    // 测试 apk 会用平台默认的 InstrumentationTestRunner，它不认
    // @RunWith(AndroidJUnit4::class)，**测试用例全发现不了**。
    // 编译过、APK 装得上、run 也跑——但 OK (0 tests)。这种沉默失败最难抓。
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
