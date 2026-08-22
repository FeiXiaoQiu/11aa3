# Roleplay Hub Android

Roleplay Hub（RP-Hub）的第三方 Android 客户端。

上游 [STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub/) 是一个纯前端运行的本地 AI 角色扮演工具。本项目将其打包成 Android 应用：以原生 WebView 加载打包进 APK 的前端页面，并用 JavaScript 桥（JSBridge）补上浏览器里做不到的能力，让它在手机上可以离线运行、并把文件真正写到系统目录。

[![License: CC BY-NC 4.0](https://img.shields.io/badge/License-CC%20BY--NC%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc/4.0/)

---

## 功能特性

除上游的前端功能外，本客户端通过原生代码补充了以下浏览器里做不到的能力：

- **离线运行**：前端资源打包进 APK，无需联网、无需浏览器即可打开。
- **图片保存**：把生成的角色图一键保存到系统相册目录。
- **文件下载**：把前端产生的文件写入系统下载目录，带前台进度通知。
- **沉浸式全屏**：对话与阅读时隐藏系统状态栏。
- **明文备份**：把全部数据以可读文件导出——角色卡（PNG）、聊天记录（JSONL）、记忆与全局数据（JSON），打包为 zip；导入兼容明文备份与旧版（137 及以前）内部数据备份。
  - 导出在后台执行，通知栏实时显示进度。
  - 支持两种方式：**导出完整数据**（含聊天图片）与**导出时剥离图片附件**（文件更小）。
  - 默认导出到系统下载目录 `Download/`。

---

## 下载与安装

最新版本见 [Releases](../../releases)，下载对应版本的 APK 安装即可。

首次使用时，按提示开启「所有文件访问」权限，以启用图片保存、文件下载与数据备份等功能。

---

## 构建

需要 JDK 17 与 Android SDK（API 34）。

1. 同步前端资源：将根目录的 `index.html`、`assets/`、`character/`、`novel/` 复制到 `android-app/app/src/main/assets/www/`。
2. 构建：

```bash
cd android-app
export ANDROID_HOME=<你的 Android SDK 路径>
gradle assembleRelease
```

构建产物位于 `android-app/app/build/outputs/apk/release/`。

---

## 关于上游

本项目前端核心基于 [STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub/)，未改动其核心逻辑，仅新增 Android 客户端与离线打包所需的原生桥对接。

前端功能与使用说明（角色卡、世界书、记忆、剧情分支、自动生图、万相广场、在线人数同步等）请见 [上游仓库](https://github.com/STA1N156/RP-Hub/)。

---

## 许可

[Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)](https://creativecommons.org/licenses/by-nc/4.0/deed.zh-hans)

本项目仅供非商业使用。共享或演绎时须保留署名并附上许可链接，禁止任何形式的售卖、付费订阅集成或广告牟利。详细条款见 [`LICENSE`](./LICENSE)。
