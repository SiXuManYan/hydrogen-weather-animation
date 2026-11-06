package com.sugar.weather

import android.app.Application
import com.sugar.weather.di.AppContainer
import com.template.framework.Framework
import com.template.framework.FrameworkConfig

/**
 * 应用入口(DESIGN.md §2.3):初始化基础框架并持有手写 DI 容器 [AppContainer]。
 *
 * 需在 AndroidManifest.xml 的 <application> 上注册 android:name=".WeatherApplication"。
 * 取用方式:`(application as WeatherApplication).container`。
 */
class WeatherApplication : Application() {

    /** 进程级依赖容器，框架日志初始化完成后再创建网络工厂。 */
    lateinit var container: AppContainer
        private set

    /** 注入框架运行信息，不启用额外存储、认证或 WebSocket 能力。 */
    override fun onCreate() {
        super.onCreate()
        Framework.init(
            app = this,
            config = FrameworkConfig(
                debug = BuildConfig.DEBUG,
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME
            )
        )
        container = AppContainer(context = this)
        container.weatherWidgetController.start()
    }
}
