package com.sugar.weather

import android.app.Application
import com.sugar.weather.di.AppContainer

/**
 * 应用入口(DESIGN.md §2.3):持有手写 DI 容器 [AppContainer]。
 *
 * 需在 AndroidManifest.xml 的 <application> 上注册 android:name=".WeatherApplication"。
 * 取用方式:`(application as WeatherApplication).container`。
 */
class WeatherApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
