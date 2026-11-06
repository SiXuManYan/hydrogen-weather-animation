plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.sugar.weather"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sugar.weather"
        minSdk = 29
        targetSdk = 37
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    // AGP 9 的内置 Kotlin 会自动跟随 compileOptions 的 targetCompatibility（当前 Java 17），
    // 无需重复声明 kotlin { compilerOptions { jvmTarget = ... } }。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // 只打包面向用户的文档，避免将设计资料与场景录屏放入安装包。
    sourceSets["main"].assets.directories.add(
        layout.buildDirectory.dir("generated/appDocuments").get().asFile.path
    )
}

// 仓库 Markdown 是唯一正文来源；每次构建自动同步，删除源文件时也清理旧资产。
val syncAppDocuments by tasks.registering(Sync::class) {
    from(rootProject.file("docs/legal")) { into("legal") }
    from(rootProject.file("CHANGELOG.md")) { into("history") }
    from(rootProject.file("docs/USER_GUIDE.md")) { into("help") }
    from(rootProject.file("docs/THIRD_PARTY_NOTICES.md")) { into("help") }
    into(layout.buildDirectory.dir("generated/appDocuments"))
}

tasks.named("preBuild").configure { dependsOn(syncAppDocuments) }

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    // 数据层(DESIGN.md §10.1):Retrofit + Gson 反射解析,零编译器插件,兼容 AGP 9 内置 Kotlin
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.datastore.preferences)
    // 城市多页/列表/刷新/详情卡布局(UI 层将使用,统一在此声明)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.markwon.core)
    implementation(libs.androidx.browser)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
