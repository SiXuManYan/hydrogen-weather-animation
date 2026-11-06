package com.template.framework

/** 基础运行配置，不包含服务器地址或网络协议。 */
data class FrameworkConfig(
    val debug: Boolean,
    val versionCode: Int,
    val versionName: String
)
