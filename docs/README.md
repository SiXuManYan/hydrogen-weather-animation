# 项目文档索引

更新日期：2026 年 11 月 6 日。按任务查阅，当前能力以用户帮助与设计说明为准，历史问题和验证范围以审查记录为准。

| 想了解什么 | 文档 |
| --- | --- |
| 项目介绍、构建与运行 | [中文 README](../README.zh-CN.md)、[English README](../README.md) |
| 添加、删除、排序城市，刷新与常见问题 | [使用帮助](USER_GUIDE.md) |
| 数据规则、架构、缓存、页面与动画 | [设计与实现](DESIGN.md) |
| 已完成的修复和仍待解决的问题 | [项目审查记录](REVIEW.md) |
| 每个版本具体改变了什么 | [版本更新日志](../CHANGELOG.md) |
| 使用条件与天气信息边界 | [用户协议](legal/USER_AGREEMENT.md)、[English](legal/en/USER_AGREEMENT.md) |
| 权限、网络请求、存储与删除 | [隐私政策](legal/PRIVACY_POLICY.md)、[English](legal/en/PRIVACY_POLICY.md) |
| 数据署名与组件许可入口 | [数据来源与开源组件](THIRD_PARTY_NOTICES.md) |
| Logo 源文件、颜色与图标生成 | [品牌资产说明](brand/README.md)、[SVG](brand/hydrogen-weather-logo.svg) |
| 天气 API 的历史调研 | [API 选型记录](free-weather-apis.md) |

## 文档与安装包

Gradle 的 `syncAppDocuments` 只同步以下文件到生成的 assets 目录：`docs/legal/`、根目录 `CHANGELOG.md`、`docs/USER_GUIDE.md` 和 `docs/THIRD_PARTY_NOTICES.md`。应用通过统一的本地 Markdown 阅读页打开中文正文；协议、隐私、帮助、日志与组件说明均不需要联网或定位权限。

研发文档、Logo 导出图和场景录屏不打包。文档外部链接由设备浏览器打开。源码注释和产品文案分别说明实现与用户操作，避免把研发细节放入操作流程。

## 维护规则

- 功能变更同步更新相关帮助、设计说明和版本日志；涉及权限、第三方请求或删除行为时核对隐私政策。
- 法律正文只在 `legal/` 中维护；旧地址 `PRIVACY.md` 保留为索引，不复制正文。
- 产品文档写已经实现的行为，尚未支持的能力明确写为限制；不要将历史设备检查当作当前版本已验证。
- 版本来自 `app/build.gradle.kts`，关于页读取生成的 `BuildConfig.VERSION_NAME`；发布时仍需手动新增对应历史条目。
