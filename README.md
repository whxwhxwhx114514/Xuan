<h1 align="center">📱 Xuan</h1>
<p align="center"><strong>iOS 风格 Android 应用管理工具</strong></p>
<p align="center">
  <img src="https://img.shields.io/badge/版本-1.0-blue" alt="version">
  <img src="https://img.shields.io/badge/最低SDK-Android%205.0%20(API21)-green" alt="min-sdk">
  <img src="https://img.shields.io/badge/目标SDK-Android%2015%20(API36)-orange" alt="target-sdk">
  <img src="https://img.shields.io/badge/需要-Root-red" alt="root">
</p>

---

## 🔍 什么是 Xuan？

Xuan 是一款**仿 iOS 设计风格**的 Android 应用管理工具，帮助你轻松管理手机中的所有应用。

| 功能 | 说明 | 需要 Root |
|------|------|-----------|
| ❄️ **冻结/解冻** | 一键冻结无用系统应用，节省电量与内存 | ✅ |
| 🗑️ **卸载应用** | Root 权限卸载顽固系统应用 | ✅ |
| 📦 **提取 APK** | 备份应用安装包到 Download/Xuan/ | 系统应用需 ✅ |
| 🛑 **强制停止** | 立即终止运行中的应用 | ✅ |
| 🧹 **清除数据** | 一键清除应用缓存和数据 | ✅ |
| ⚡ **Root 授权** | 为任意应用授予永久 Root 权限 | ✅ |
| 🔍 **启动/详情** | 打开应用、查看包名版本大小等 | ❌ |

---

## 📱 界面一览

- **状态栏** — 右上角显示 `Root ✓` 或 `无权限` 状态
- **搜索框** — 实时搜索应用名或包名
- **用户/系统** — 一键切换用户应用和系统应用列表
- **批量操作** — 多选后批量提取、卸载、冻结、清除
- **长按菜单** — 长按应用弹出 iOS 风格操作面板
- **详情页** — 查看版本号、APK 大小、安装时间等

---

## 📥 安装步骤

| 步骤 | 操作 |
|------|------|
| 1️⃣ | 下载 `xuan.apk` 文件（见 [Release](https://github.com/whxwhxwhx114514/Xuan/releases)） |
| 2️⃣ | 设置 → 安全 → 允许安装未知应用 |
| 3️⃣ | 点击 APK 文件安装 |
| 4️⃣ | 安装 Magisk 获取 Root 权限以解锁全部功能（推荐） |

> ⚠ **提示**：冻结/解冻/卸载系统应用等功能需要 Root 权限。无 Root 也可使用启动、详情、提取APK等基础功能。

---

## 🔨 从源码构建

```bash
# 环境要求: Android SDK (aapt, dx, apksigner, zipalign)
cd xuan_app
bash build.sh
# APK 输出: /sdcard/Download/xuan.apk
```

构建脚本自动完成：编译 → dex → 打包 → 对齐 → 签名（V1+V2+V3）

---

## 🛠 技术栈

| 项目 | 说明 |
|------|------|
| 语言 | Java (Android) |
| 最低 SDK | Android 5.0 (API 21) |
| 目标 SDK | Android 15 (API 36) |
| 构建工具 | aapt + dx + apksigner + zipalign |
| Root 框架 | Magisk / KernelSU / APatch |
| 设计风格 | 仿 iOS (Cupertino) |
| 架构 | 单 Activity + 工具类 |

---

## 📂 源码结构

```
xuan_app/
├── AndroidManifest.xml     # 应用清单
├── build.sh                # 构建脚本
├── res/                    # 资源文件
│   ├── layout/             # 布局 XML
│   ├── drawable/           # 形状/背景
│   ├── mipmap-*/           # 图标
│   ├── values/             # 颜色/字符串/主题
│   ├── anim/               # 动画
│   └── xml/                # FileProvider
└── src/com/xuan/app/
    ├── MainActivity.java   # 主界面
    ├── RootHelper.java     # Root 工具类
    └── AppInfo.java        # 应用信息模型
```

---

## ⚠ 注意事项

- 🔒 **Root 权限** — 系统级操作需要 Root，请先安装 Magisk
- ⚠️ **风险提示** — 卸载系统应用可能导致系统不稳定，请谨慎操作
- 📱 **兼容性** — 最低支持 Android 5.0，推荐 Android 11+
- 🔑 **签名** — 当前使用自签 debug.keystore，正式发布请更换

---

## 📄 开源协议

MIT License — 自由使用、修改和分发。

---

<p align="center">
  <a href="https://whxwhxwhx114514.github.io/Xuan/">🌐 在线介绍页面（PPT 风格）</a>
  &nbsp;|&nbsp;
  <a href="https://github.com/whxwhxwhx114514/Xuan/releases">⬇️ 下载 APK</a>
</p>

<p align="center">Built with ❤️ | 让应用管理更简单</p>
