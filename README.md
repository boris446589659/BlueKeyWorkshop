<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="128" height="128" alt="蓝键工坊图标">
</p>

<h1 align="center">蓝键工坊</h1>

<p align="center">面向 Gboard 的 LSPosed/Xposed 功能增强与隐私控制模块</p>

蓝键工坊基于 [KeyFlux](https://github.com/NawafCode/KeyFlux) 二次开发，在保留原项目能力的基础上，提供独立的中文设置应用、中文学习控制、剪贴板增强、主题调整和运行状态诊断。

> 本仓库保留 GitHub fork 关系和完整提交历史。项目内部的 `com.keyflux` 包名、`KeyFluxPrefs` 存储名及 `keyflux_*` 配置键暂时保留，用于兼容已安装版本和已有设置。

## 主要功能

- **独立设置应用**：所有模块选项均在蓝键工坊中管理，不向 Gboard 设置页面注入界面。
- **中文学习控制**：控制 Gboard 的本地中文学习、个性化建议和表情建议，并保留纠错学习数据。
- **剪贴板增强**：调整历史条目数量和保留时间，避免敏感文本进入历史记录。
- **输入功能解锁**：控制多语言输入、语法检查、智能输入、悬浮键盘及 Emoji Kitchen 等功能。
- **实验功能**：提供内联建议、主动表情建议、剪贴板快捷操作、TFLite 引擎和新版快捷栏开关。
- **外观定制**：提供 AMOLED 深色模式及自定义主题调色板。
- **隐私控制**：可强制无痕模式并减少遥测和分析数据。
- **状态诊断**：显示 LSPosed、Xposed API、Gboard 版本和模块注入状态。

部分选项对应 Gboard 内部实验开关，是否生效取决于 Gboard 版本、Google 服务端配置、Android 版本及设备 ROM。

## 环境要求

- Android 7.0（API 24）或更高版本
- 已安装 Gboard
- 已安装并启用 LSPosed 兼容框架
- 在模块作用域中勾选 Gboard
- 当前构建包含 `arm64-v8a` 和 `x86_64` 原生库

## 安装与使用

1. 从本仓库 Releases 下载蓝键工坊 APK 并安装。
2. 在 LSPosed 管理器中启用蓝键工坊。
3. 将 Gboard 加入模块作用域。
4. 强制停止并重新启动 Gboard。
5. 打开蓝键工坊配置所需功能；修改后按界面提示重启 Gboard。

## 本地构建

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

发布构建：

```bash
./gradlew :app:assembleRelease
```

生成的 APK 位于 `app/build/outputs/apk/`。

## 隐私说明

蓝键工坊不会主动记录剪贴板内容或复制的敏感文本。提交问题和日志前，请先删除设备信息、账号、输入内容等隐私数据。

## 问题排查

如模块未生效，请依次确认：

1. 蓝键工坊已在 LSPosed 中启用，且作用域包含 Gboard。
2. 修改设置后已经强制停止并重新启动 Gboard。
3. 蓝键工坊首页显示的模块版本与当前安装版本一致。
4. 当前 Gboard 版本仍包含对应的内部功能。

反馈问题时请附上 Android/ROM、Gboard 版本、蓝键工坊版本及已脱敏的 LSPosed 日志。

## 上游与致谢

- 上游项目：[NawafCode/KeyFlux](https://github.com/NawafCode/KeyFlux)
- 原项目作者：[NawafCode](https://github.com/NawafCode)
- 本 fork 维护：[Boris448](https://github.com/boris446589659)
- 开发辅助：OpenAI Codex

## 许可证

本项目沿用上游项目的 [MIT License](LICENSE)。
