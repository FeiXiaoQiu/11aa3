# Roleplay Hub · Android 客户端（WebView 封装）

> **一款纯前端运行的本地角色扮演（Roleplay）对话和角色卡生成工具。**
> 本仓库在其基础上新增了 **Android 客户端（WebView 封装）**、**在线人数服务** 与 **自动化发布流程**。

**复刻自** [STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub/)。

[![License: CC BY-NC 4.0](https://img.shields.io/badge/License-CC%20BY--NC%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc/4.0/)
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D.svg?logo=vue.js)](https://vuejs.org/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-38B2AC?logo=tailwind-css&logoColor=white)](https://tailwindcss.com/)
[![DaisyUI](https://img.shields.io/badge/DaisyUI-5A0EF8?logo=daisyui&logoColor=white)](https://daisyui.com/)

---

## 本仓库新增内容

在原版纯前端应用之上，本仓库额外提供以下能力：

- **Android 客户端（WebView 封装）**（`android-app/`）：以原生 WebView 容器加载打包进 APK 的前端页面，通过 JavaScript 桥（JSBridge）提供文件下载、图片保存、数据备份与在线更新。
- **在线人数服务**（`presence-server/`）：无需数据库的匿名在线人数接口，可部署到 Zeabur。
- **发布流程**：版本号规范、本地构建验证与 GitHub Actions 发布。

---

## Android 客户端（WebView 封装）

`android-app/` 是一个 Kotlin 编写的 Android 原生工程。它以原生 WebView 作为渲染容器，加载打包进 APK 的前端资源（HTML/CSS/JS），并通过 JavaScript 桥（JSBridge）把网页里的调用转发到 Android 系统能力，实现文件下载、图片保存、数据备份与在线更新。

### 打包与交付

前端资源位于 `android-app/app/src/main/assets/www/`，需与根目录 `assets/`、`character/`、`novel/` 及 `index.html` 保持同步。构建前请先同步前端资源并 `diff` 校验一致，再执行构建。

```bash
# 同步前端资源到 android-app（由发布流程维护，此处示意）
# 同步后校验：diff -r assets android-app/app/src/main/assets/www

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
cd android-app
/opt/gradle-8.7/bin/gradle clean assembleRelease -x lintVitalRelease --no-daemon
```

构建产物输出到 `android-app/app/build/outputs/apk/release/`，交付物按版本号归档到根目录 `release/RoleplayHub-Beta-<versionName>.apk`。

### 版本号规则

- 采用三段式语义化版本 `Beta-x.y.z`（如 `Beta-1.3.7`）。
- `versionName=Beta-x.y.z`，`versionCode` 对应纯数字（如 `137`）。
- 版本递增时按语义化规则逐位递增，禁止跳位（如 `Beta-1.2.10`）。

### 签名

- 密钥库：`android-app/roleplayhub-release.keystore`
- 签名配置：`android-app/keystore.properties`（`keyAlias=roleplayhub`）
- `local.properties` 与 `keystore.properties` 含本机敏感路径，已被 `.gitignore` 排除，请勿提交。

### 原生桥（JSBridge）说明

| 类 | 职责 |
|---|---|
| `MainActivity` | 主界面、悬浮菜单、全屏控制、下载/保存调度 |
| `RoleplayHubNativeBridge` | JavaScript 桥（JSBridge），处理保存图片/文件/全屏等调用 |
| `WebAssetLoader` | 离线资源加载、assets → files/www 缓存同步、MIME 映射与路径穿越防护 |
| `DownloadService` | 前台下载服务，自定义进度通知（不使用 `DownloadManager`） |
| `FileDownloader` | base64 文件落盘，文件名净化 |
| `ImageSaver` | 图片保存到 `Download/RPHub/` |
| `BackupManager` | 数据备份导出与恢复 |
| `UpdateManager` | 前端资源包在线更新 |

---

## 在线人数服务 (presence-server)

`presence-server/` 是一个无需数据库的匿名在线人数接口，基于 Node.js，可一键部署到 Zeabur。

- 浏览器每 20 秒报到一次，60 秒未报到自动离线；同一浏览器多标签页共用一个编号，仅计为 1 人。
- 不接收或保存角色卡、聊天记录、API 密钥等数据，仅在内存保存随机编号与过期时间。

主要接口：

- `GET /health`：健康检查。
- `GET /v1/online`：读取当前在线人数。
- `POST /v1/presence`：匿名心跳，返回在线人数与最新公告 ID。

部署与配置细节见 [`presence-server/README.md`](./presence-server/README.md)。

---

## 发布流程 (Release)

1. 本地同步前端资源并 `diff` 校验一致，执行 `assembleRelease` 构建验证。
2. 本地验证通过后，推送源码到远程 `main` 分支，触发 GitHub Actions 构建并发布 release。
3. 交付 APK 归档到 `release/`，删除同目录旧版本。

---

## 目录结构 (Directory Structure)

```text
Roleplay-Hub/
├── index.html                     # 主界面与脚本加载入口
├── character/                     # 角色卡生成工具
├── novel/                         # 墨韵 · 造梦
├── assets/
│   ├── css/styles.css             # 全局样式
│   ├── vendor/                    # 离线第三方依赖（Vue/Tailwind/DaisyUI 等）
│   └── js/
│       ├── built-in-content.js    # 默认预设、模式提示词、画师串与更新公告
│       ├── core-utils.js          # 通用工具、角色卡处理与基础配置
│       ├── data-services.js       # 存储、记忆、上下文、分支与 UI 状态
│       ├── runtime-services.js    # API 请求、消息渲染与运行状态
│       ├── ui-components.js       # 选择器、侧边栏、弹窗与页面组件
│       └── app.js                 # 主业务入口与页面状态
├── presence-server/               # 在线人数服务（Node.js，可部署到 Zeabur）
├── android-app/                   # Android 客户端（Kotlin，WebView 封装）
│   └── app/src/main/
│       ├── java/com/roleplayhub/app/
│       │   ├── MainActivity.kt            # 主界面、菜单、全屏、下载/保存调度
│       │   ├── RoleplayHubNativeBridge.kt # JavaScript 桥（保存图片/文件/全屏）
│       │   ├── WebAssetLoader.kt          # 离线资源加载与缓存同步
│       │   ├── DownloadService.kt         # 前台下载服务
│       │   ├── FileDownloader.kt          # base64 文件落盘
│       │   ├── ImageSaver.kt              # 图片保存到 Download/RPHub
│       │   ├── BackupManager.kt           # 数据备份导出/恢复
│       │   └── UpdateManager.kt           # 资源包在线更新
│       ├── assets/www/            # 打包进 APK 的前端资源（与根目录同步）
│       └── res/                   # 图标与主题资源
├── release/                       # 已构建的 APK 交付物
└── README.md                      # 项目说明
```

---

## 关于上游 (Upstream)

本项目核心前端基于 [STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub/)。上游是一个纯前端运行的本地 AI 角色扮演工具，支持角色卡、世界书、总结记忆与向量记忆、剧情分支、自动生图、角色卡生成与万相广场等能力。

- 详细功能与使用说明请见 [上游仓库](https://github.com/STA1N156/RP-Hub/)。
- 本仓库未改动前端核心逻辑，仅新增 Android 客户端（WebView 封装）、在线人数服务与发布流程，前端改动集中在离线打包所需的依赖本地化与 JavaScript 桥对接。

### 快速开始

本项目无需复杂的 Node.js 环境或依赖安装，即开即用：下载 ZIP 解压后双击 `index.html` 即可在浏览器中启动，随后在设置中填入 API 节点与 Key，导入角色卡即可开始使用。完整步骤见上游 README。

---

## 协议与许可 (License)

本项目严格遵守以下开源协议：

**[Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)](https://creativecommons.org/licenses/by-nc/4.0/deed.zh-hans)**

* **您可以**：自由地共享（在任何媒介以任何形式复制、发行本作品）与演绎（修改、转换或以本作品为基础进行创作）。
* **您必须**：
  * **署名 (Attribution)**：给出适当的署名，提供指向本许可协议的链接，同时标明是否对原始作品作了修改。
  * **非商业性使用 (NonCommercial)**：**您不得将本作品或演绎作品用于任何商业目的。** 禁止任何形式的售卖、付费订阅集成或利用本项目进行广告牟利。
* 若要获取本项目的商业授权，请直接联系项目原作者。

详细许可条款请参见根目录下的 [`LICENSE`](./LICENSE) 文件。
