package com.sugar.weather.ui

import com.sugar.weather.scene.SceneType

/**
 * 场景面板的状态：天气类型、昼夜、是否触摸模拟、是否显示传感器调试信息。
 * 高频的 pitch/roll 单独放在 MainViewModel.tilt 中，避免 50Hz 传感器事件触发整页 UI 状态重组。
 */
data class SceneUiState(
    val sceneType: SceneType = SceneType.SUNNY,
    val isNight: Boolean = false,
    val simulateTilt: Boolean = false,
    val showDebug: Boolean = false
)
