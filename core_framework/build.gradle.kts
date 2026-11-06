plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.template.framework"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    // 源码 Library 跟随宿主 AGP 9 的内置 Kotlin 与 Java 17 字节码目标。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // 公开 API 暴露的类型使用 api，使用者无需重复声明同一依赖。
    api(libs.androidx.appcompat)
    api(libs.androidx.recyclerview)
    // LoadingDialog 的 XML 引用 Material 控件，资源编译与宿主使用均需要此依赖。
    api(libs.material)

    api(libs.androidx.lifecycle.runtime.ktx)

    api(libs.androidx.datastore.preferences)

    api(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    api(libs.okhttp)
    api(libs.gson)

    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.timber)

    testImplementation(libs.junit)
    // 列表测试需要真实 Observable 初始化；Robolectric 仅在本机 JVM 运行。
    testImplementation(libs.robolectric)
}
