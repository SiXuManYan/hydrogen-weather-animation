package com.sugar.weather.scene

/**
 * 演示用天气场景清单。
 *
 * 背景色取自原天气 App 的 weather_* 颜色资源；场景全部由 Canvas 程序化绘制，不读取图片资源。
 * 晴天、多云、阴天、夜间等配色均按原 App 的日/夜两套色板配置。
 */
enum class SceneType(
    val label: String,
    val dayBackground: Int,
    val nightBackground: Int
) {
    SUNNY("晴天", 0xFF4A97D2.toInt(), 0xFF051325.toInt()),
    CLOUDY("多云", 0xFF4A97D2.toInt(), 0xFF051325.toInt()),
    OVERCAST("阴天", 0xFF93A4AE.toInt(), 0xFF171E26.toInt()),
    FOG("雾", 0xFF617D8F.toInt(), 0xFF2A383F.toInt()),
    HAZE("霾", 0xFF475D6A.toInt(), 0xFF272B2D.toInt()),
    SANDSTORM("沙尘暴", 0xFFA59056.toInt(), 0xFF312820.toInt()),
    LIGHT_RAIN("小雨", 0xFF1899B9.toInt(), 0xFF002244.toInt()),
    RAIN("中雨", 0xFF116C9B.toInt(), 0xFF002244.toInt()),
    HEAVY_RAIN("大雨", 0xFF1865B4.toInt(), 0xFF002244.toInt()),
    STORM("暴雨", 0xFF101238.toInt(), 0xFF101238.toInt()),
    THUNDER("雷阵雨", 0xFF190C38.toInt(), 0xFF190C38.toInt()),
    SLEET("雨夹雪", 0xFF3081CD.toInt(), 0xFF132F45.toInt()),
    LIGHT_SNOW("小雪", 0xFF3081CD.toInt(), 0xFF132F45.toInt()),
    SNOW("大雪", 0xFF3081CD.toInt(), 0xFF132F45.toInt()),
    HAIL("冰雹", 0xFF7C7699.toInt(), 0xFF7C7699.toInt());

    /** 根据昼夜返回全屏背景色；具体天气图形由 [WeatherScene] 绘制。 */
    fun background(night: Boolean): Int = if (night) nightBackground else dayBackground

    /**
     * 场景工厂。雨夹雪用 [CompositeScene] 把一层小雨和一层小雪叠在一起；
     * 冰雹复用雨场景，并打开 hail 开关。
     */
    fun createScene(): WeatherScene = when (this) {
        SUNNY -> SunnyScene()
        CLOUDY -> CloudScene(overcast = false)
        OVERCAST -> CloudScene(overcast = true)
        FOG -> FogScene()
        HAZE -> HazeScene()
        SANDSTORM -> SandstormScene()
        LIGHT_RAIN -> RainScene(RainScene.Intensity.LIGHT)
        RAIN -> RainScene(RainScene.Intensity.MEDIUM)
        HEAVY_RAIN -> RainScene(RainScene.Intensity.HEAVY)
        STORM -> RainScene(RainScene.Intensity.STORM)
        THUNDER -> RainScene(RainScene.Intensity.THUNDER)
        SLEET -> CompositeScene(
            RainScene(RainScene.Intensity.LIGHT),
            SnowScene(heavy = false)
        )
        LIGHT_SNOW -> SnowScene(heavy = false)
        SNOW -> SnowScene(heavy = true)
        HAIL -> RainScene(RainScene.Intensity.MEDIUM, hail = true)
    }
}
