import com.android.build.api.artifact.SingleArtifact
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/** 本机签名凭据单独保存，不将口令提交到版本库。 */
val keystorePropertiesFile = file("keystore.properties")
/** 仅从未跟踪的本机文件读取签名值，缺项由打包任务报告。 */
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

/** 两个变体共用 app/jks 中的项目密钥；Gradle 属性优先于本机凭据文件。 */
val projectKeystoreFile = file("jks/weather.jks")
/** 两个变体共享的口令与别名；不在配置输出中显示实际值。 */
val projectSigningValues = listOf("storePassword", "keyAlias", "keyPassword").associateWith { key ->
    providers.gradleProperty(key).orNull?.takeIf(String::isNotBlank)
        ?: keystoreProperties.getProperty(key)?.takeIf(String::isNotBlank)
}

/** 打包前检查配置，缺少凭据时明确失败，避免输出未签名 APK 或使用默认 debug.jks。 */
val validateProjectSigning = tasks.register("validateProjectSigning") {
    group = "verification"
    description = "检查 app/jks 项目签名及本机凭据是否完整。"
    // 仅捕获可序列化的局部值，任务执行时不读取 Project 或脚本实例。
    val signingFile = projectKeystoreFile
    val missingKeys = projectSigningValues.filterValues { it.isNullOrBlank() }.keys.joinToString()
    doLast {
        check(signingFile.isFile) { "缺少项目签名文件：${signingFile.path}" }
        check(missingKeys.isEmpty()) {
            "缺少签名配置：$missingKeys。请填写 app/keystore.properties（参照 .example），或设置同名 Gradle 属性。"
        }
    }
}

/** 产物文件名使用与拼图工坊一致的本机构建日期。 */
val buildDateStamp: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

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

    signingConfigs {
        create("project") {
            storeFile = projectKeystoreFile
            storePassword = projectSigningValues["storePassword"]
            keyAlias = projectSigningValues["keyAlias"]
            keyPassword = projectSigningValues["keyPassword"]
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("project")
        }
        release {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("project")
            // AGP 9 同时启用 R8 与资源收缩；反射边界在 src/main/keepRules 中显式保留。
            optimization {
                enable = true
                keepRules {
                    includeDefault = true
                }
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

androidComponents.onVariants { variant ->
    val variantName = variant.name.replaceFirstChar { character ->
        if (character.isLowerCase()) character.titlecase() else character.toString()
    }
    val buildTypeName = variant.buildType ?: variant.name
    // 使用公开 Variant API 改名，版本名保持原值，日期与拼图工坊采用同一格式。
    variant.outputs.forEach { output ->
        output.outputFileName.set(
            output.versionName.map { versionName ->
                "HydrogenWeather_${versionName}_${buildDateStamp}_${buildTypeName}.apk"
            },
        )
    }

    // 只在 APK/AAB 打包或签名时检查凭据；IDE Sync 和纯编译无需持有密钥口令。
    tasks.matching { task ->
        task.name in setOf(
            "validateSigning$variantName",
            "package$variantName",
            "package${variantName}Bundle",
            "sign${variantName}Bundle",
        )
    }.configureEach { dependsOn(validateProjectSigning) }

    // 从官方 APK 产物目录导出，支持变体目录变化，并保持 app/release 作为统一查找入口。
    tasks.register<Copy>("export${variantName}Apk") {
        group = "build"
        description = "构建 ${variant.name} APK 并导出到 app/release。"
        dependsOn("assemble$variantName")
        from(variant.artifacts.get(SingleArtifact.APK)) { include("*.apk") }
        into(layout.projectDirectory.dir("release"))
    }
}

dependencies {
    // 显式依赖模型使用的 @Keep，避免依赖其他库的传递实现细节。
    implementation(libs.androidx.annotation)
    implementation(project(":core_framework"))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    // 网络工厂与 Retrofit / OkHttp / Gson 由 core_framework 统一提供。
    implementation(libs.androidx.datastore.preferences)
    // 城市多页/列表/刷新/详情卡布局(UI 层将使用,统一在此声明)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.markwon.core)
    implementation(libs.androidx.browser)
    // 微件刷新由系统持久调度，应用退出或设备重启后仍可继续。
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
