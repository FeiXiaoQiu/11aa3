# Roleplay Hub Android

Roleplay Hub（RP-Hub）的第三方 Android 客户端。

上游 [STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub/) 是一个纯前端运行的本地 AI 角色扮演工具。本项目将其打包成 Android 应用：以原生 WebView 加载打包进 APK 的前端页面，并用 JavaScript 桥（JSBridge）补上浏览器里做不到的能力，让它在手机上可以离线运行、并把文件真正写到系统目录。

[![License: CC BY-NC 4.0](https://img.shields.io/badge/License-CC%20BY--NC%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc/4.0/)

---

## 补充的能力

纯前端在 WebView 里只能跑页面，以下能力是浏览器默认做不到、由本客户端原生代码补上的：

- **离线运行**：前端资源打包进 APK，无需联网、无需浏览器即可打开。
- **图片保存**：把生成的角色图一键保存到系统相册目录（WebView 网页无法直接写相册）。
- **文件下载**：把前端产生的文件写入系统下载目录，带前台进度通知（WebView 默认不提供本地下载）。
- **沉浸式全屏**：对话与阅读时隐藏系统状态栏（网页无法控制系统 UI）。
- **明文备份**：把全部数据以可读文件导出——角色卡（PNG）、聊天记录（JSONL）、记忆与全局数据（JSON），打包为 zip 保存；导入兼容明文备份与旧版（137 及以前）内部数据备份（网页的 localStorage 无法导出为系统文件）。
  - 导出在后台执行，通知栏实时显示进度（收集数据 / 打包 / 写入）。
  - 两种导出方式：**导出完整数据**（含聊天图片）与**导出时剥离图片附件**（去除聊天图片 base64，文件更小）。
  - 默认导出到系统下载目录 `Download/`，无需手动选择保存位置。

---

## 构建

需要 JDK 17 与 Android SDK。构建前请先同步前端资源：将根目录的 `index.html`、`assets/`、`character/`、`novel/` 复制到 `android-app/app/src/main/assets/www/`。

```bash
# 同步前端资源
cp -r index.html assets character novel android-app/app/src/main/assets/www/

cd android-app
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
gradle assembleRelease -x lintVitalRelease --no-daemon
```

构建产物以版本化文件名输出：`android-app/app/build/outputs/apk/release/RoleplayHub-Beta-<versionName>.apk`（本地、发布目录与 GitHub Release 资产三处同名一致）。

## 发布

发布走 GitHub Actions，无需本地打正式包：

```bash
# 先推送源码
git push origin master:main
# 打 tag 触发构建与发布
git tag vX.Y.Z
git push origin vX.Y.Z
```

推送 `v*` 的 tag 后，`.github/workflows/release.yml` 会自动构建 release 包并创建 GitHub Release、上传 APK。发布完成后可用 `gh release edit vX.Y.Z --title "Roleplay Hub Android Beta-X.Y.Z"` 统一标题，用 `gh release edit vX.Y.Z --latest` 标记最新版本。

---

## 关于上游

本项目前端核心基于 [STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub/)，未改动其核心逻辑，仅新增 Android 客户端与离线打包所需的原生桥对接。

前端功能与使用说明（角色卡、世界书、记忆、剧情分支、自动生图、万相广场、在线人数同步等）请见 [上游仓库](https://github.com/STA1N156/RP-Hub/)。

---

## 许可

[Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)](https://creativecommons.org/licenses/by-nc/4.0/deed.zh-hans)

本项目仅供非商业使用。共享或演绎时须保留署名并附上许可链接，禁止任何形式的售卖、付费订阅集成或广告牟利。详细条款见 [`LICENSE`](./LICENSE)。
