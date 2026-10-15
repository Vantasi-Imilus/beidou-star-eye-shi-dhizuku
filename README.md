<div align="center">

# 北斗 · 群星之眼

**把七个开源 Android 神器的工作流，装进一个星空液态玻璃风格的应用里**

![License](https://img.shields.io/badge/license-GPL--3.0-blue)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)

</div>

---

## 这是什么

「北斗·群星之眼」是一个 Android 应用：把七个知名开源工具的核心能力集成进**单一 App**，
并以**北斗七星**为每个能力命名，内部 UI 统一重绘为**星空液态玻璃**风格。

> ⚠️ 这是一个**个人学习 / 自用项目**，以各上游项目的「能力面」为蓝本**自研实现**。
> 除 Shizuku（Apache-2.0，源码内嵌）外，**未拷贝任何上游代码**（上游多为 GPL-3.0，详见「致谢与许可证」）。

## 七大中枢

| 星位 | 中枢 | 能力 | 上游项目 |
|:---:|---|---|---|
| **天枢** | 授权中枢 | Shizuku 服务端全量嵌入，ADB / Root / **应用内一键无线激活** 三通道 | [Shizuku](https://github.com/RikkaApps/Shizuku) · Apache-2.0（源码内嵌）|
| **天璇** | 应用管理中枢 | 应用列表/详情、冻结解冻、强停、清数据、卸载、导出 APK（含 split 全量）、**组件管理**、**残留扫描**、**签名信息**、**APK 文件级编辑 + v1/v2/v3 重签名**、操作记录 | [App Manager](https://github.com/MuntashirAkon/AppManager) · GPL-3.0（零拷贝自研）|
| **天玑** | 安装器中枢 | APK/APKS/XAPK 解析、三授权器静默安装、分包选择、签名校验、**默认安装器/卸载器一键注册**、**批量安装队列**、**SAF 目录导入**、**安装后自动解冻被冻结应用** | [InstallerX-Revived](https://github.com/wxxsfxyzm/InstallerX-Revived) · GPL-3.0（零拷贝自研）|
| **天权** | 终端中枢 | 自研终端模拟器（**宽度重排 reflow**、多国字符集、扩展鼠标协议）+ **原生 pty** 会话、多会话、选择复制、局域网 HTTP/FTP 文件服务、**系统级浮动终端窗**、对外 action 与快捷方式 | [Termux-X](https://github.com/hanxinhao000/ZeroTermux)（ZeroTermux 系）· GPL-3.0（零拷贝自研）|
| **玉衡** | 设备管控中枢 | Device Owner 深度管控：系统策略 / 应用 / 用户 / **69 项用户限制** / 密码与锁屏 / 网络 + 24 个对外 API | [OwnDroid](https://github.com/BinTianqi/OwnDroid) · GPL-3.0（零拷贝自研）|
| **开阳** | Device Owner 中枢 | Device Owner 激活与管理 + **转接层**（第三方 Dhizuku 客户端的请求路由到本应用）| [Dhizuku](https://github.com/iamr0s/Dhizuku) · GPL-3.0（链接其 MIT 库）|
| **瑶光** | 冻结中枢 | **四语义冻结**（挂起/隐藏/禁用/强停）× 三通道、我的清单/标签/白名单、自动冻结（**开机自启 + 延迟兜底闹钟**）、**延时冻结（跨进程持久化）**、磁贴与快捷方式、对外 API | [Hail](https://github.com/aistra0528/Hail) · GPL-3.0（零拷贝自研）|

## 特性

- 🎨 **星空液态玻璃 UI** —— 每个中枢的内部界面均按统一视觉语言重绘，不搬运原 UI
- 🔗 **跨中枢状态一致** —— 统一冻结状态判定源（`core:freeze`）+ 变更事件总线：
  任一中枢冻结 / 解冻后，其余中枢界面自动同步刷新，不会出现"同一个应用两处显示矛盾"
- 🧩 **可拓展架构** —— 每个中枢是独立 Gradle 模块（`feature:*`），共享 `core:model` / `core:ui` /
  `core:shell` / `core:freeze` 四层，新增中枢只需实现 `Feature` 并注册路由
- ⚙️ **多特权通道** —— 开阳（Device Owner）/ 天枢（Shizuku）/ Root 自动择优，
  「授权器」的激活与授权逻辑保持上游原样，仅以**转接层**对接

## 构建

**环境要求**：JDK 21 · Android SDK（compileSdk 37）· CMake 4.1.2 · NDK

```bash
# 一键构建（产出 debug + release APK，以及源码归档）
./build.sh          # Git Bash / Linux / macOS
build.bat           # Windows 双击
```

或手动：

```bash
export JAVA_HOME=<JDK 21 路径>
./gradlew :app:assembleDebug     # 调试版
./gradlew :app:assembleRelease   # 发行版（复用 debug 签名，可覆盖安装）
```

产物输出到 `dist/`（已整体 gitignore）。**APK 经 GitHub Releases 发布**，debug 与 release 同签名，可直接覆盖安装。

## 技术栈

`AGP 9.3.2`（内置 Kotlin 2.4）· `Gradle 9.7.1` · `Jetpack Compose`（BOM 2026.08）·
`compileSdk 37` / `minSdk 26`（Android 8.0+）

## 项目结构

```
BeidouStarEye/
├── app/               宿主：星空壳 + 七中枢导航 + 原生 starter（CMake）
├── core/
│   ├── model/         Feature 抽象 + 北斗七星枚举
│   ├── ui/            共享 UI 原语（星空主题 / 液态玻璃 / 星空背景）
│   ├── shell/         共享 Shizuku shell 执行原语
│   └── freeze/        跨中枢冻结状态统一判定 + 变更事件
├── feature/           七大中枢（tianshu / tianxuan / tianji / tianquan / yuheng / kaiyang / yaoguang）
├── shizuku/           Shizuku 原版源码模块（客户端 + 服务端 + starter）
└── build.sh / build.bat / package-source.ps1   一键构建与打包
```

## 致谢与许可证

本项目以 **GPL-3.0** 发布，见 [LICENSE](LICENSE)。Copyright (C) 2026 Vantasi-Imilus

**特别感谢**以下开源项目 —— 它们定义了「Android 上能做什么」：

| 项目 | 许可 | 在本项目中的角色 |
|---|---|---|
| [Shizuku](https://github.com/RikkaApps/Shizuku) / [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) | Apache-2.0 | **源码内嵌**（`shizuku/` 模块）|
| [App Manager](https://github.com/MuntashirAkon/AppManager) | GPL-3.0 | 天璇能力面参考（零拷贝）|
| [InstallerX-Revived](https://github.com/wxxsfxyzm/InstallerX-Revived) | GPL-3.0 | 天玑能力面参考（零拷贝）|
| [Termux-X](https://github.com/hanxinhao000/ZeroTermux) / ZeroTermux | GPL-3.0 | 天权能力面参考（零拷贝）|
| [OwnDroid](https://github.com/BinTianqi/OwnDroid) | GPL-3.0 | 玉衡能力面参考（零拷贝）|
| [Dhizuku](https://github.com/iamr0s/Dhizuku) | GPL-3.0 / MIT（API 库）| 开阳转接层（链接 MIT 库）|
| [Hail](https://github.com/aistra0528/Hail) | GPL-3.0 | 瑶光能力面参考（零拷贝）|

另链接若干宽松许可库：Dhizuku-API（MIT）、tinypinyin（Apache-2.0）、BouncyCastle、
Gson、AndroidX 等。

## 联系与维护

- 📮 **邮箱**：yi___fan@outlook.com
- 📌 **维护响应须知**：

> 本项目由个人业余维护，不做响应承诺：本人是个高中生，没时间去管这些，看到了随缘解决，（甚至不解决）诶嘿~

