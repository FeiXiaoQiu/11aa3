# Roleplay Hub Android

Roleplay Hub（RP-Hub）的 Android 客户端。以原生 WebView 加载打包进 APK 的前端页面，并通过 JavaScript 桥（JSBridge）把网页里的调用转发到 Android 系统能力，让纯前端的 Roleplay Hub 可以在手机上离线运行。

> 前端核心基于 [STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub/)，本项目在其基础上新增 Android 客户端。

[![License: CC BY-NC 4.0](https://img.shields.io/badge/License-CC%20BY--NC%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc/4.0/)

---

## 特性

- **离线运行**：前端资源打包进 APK，无需浏览器、无需联网即可打开。
- **文件下载**：通过前台通知展示下载进度，文件保存到系统下载目录。
- **图片保存**：生成的角色图一键保存到相册目录。
- **数据备份**：角色卡、剧情、配置等数据可导出备份，也可恢复。
- **在线更新**：前端资源包支持在线更新，无需重新安装 APK。
- **沉浸式全屏**：对话与阅读场景进入沉浸式全屏。

---

## 构建

需要 JDK 17 与 Android SDK。构建前请先同步前端资源：将根目录的 `index.html`、`assets/`、`character/`、`novel/` 复制到 `android-app/app/src/main/assets/www/`。

```bash
cd android-app
./gradlew assembleRelease
```

构建产物位于 `android-app/app/build/outputs/apk/release/`。

---

## 关于上游

本项目前端核心基于 [STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub/)，这是一个纯前端运行的本地 AI 角色扮演工具，支持角色卡、世界书、记忆、剧情分支、自动生图、万相广场等能力，并内置在线人数同步服务（`presence-server/`）。

前端功能与使用说明请见 [上游仓库](https://github.com/STA1N156/RP-Hub/)。本仓库未改动前端核心逻辑，仅新增 Android 客户端与离线打包所需对接。

---

## 许可

[Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)](https://creativecommons.org/licenses/by-nc/4.0/deed.zh-hans)

本项目仅供非商业使用。共享或演绎时须保留署名并附上许可链接，禁止任何形式的售卖、付费订阅集成或广告牟利。详细条款见 [`LICENSE`](./LICENSE)。
