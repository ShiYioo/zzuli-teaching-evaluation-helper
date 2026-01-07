# ZZULI 教学评价助手

> 郑州轻工业大学教学评价自动化工具 | zzuli-teaching-evaluation-helper

⭐ 一键满分评价 | 📱 扫码登录 | 🎨 iOS 设计风格

## 🚀 快速开始

### 浏览器插件（推荐）

1. 安装 [Tampermonkey](https://www.tampermonkey.net/)
2. 安装脚本：[Greasyfork 链接](https://greasyfork.org/zh-CN/scripts/561669)
3. 打开评价页面，点击 **⭐ 一键满分**

### 命令行工具

**方式一：下载 JAR 包（推荐）**

1. 前往 [Releases](https://github.com/你的用户名/jwgl-zzuli/releases) 下载最新版本的 `zzuli-evaluation.jar`
2. 运行：
```bash
java -jar zzuli-evaluation.jar
```

**方式二：从源码构建**

```bash
cd kotlin
./gradlew jar
java -jar build/libs/zzuli-evaluation.jar
```

**登录方式**：支持 📱 扫码登录 和 🔑 账号密码登录

## 📝 日志模式

**默认（INFO）**：简洁输出
```bash
java -jar zzuli-evaluation.jar
```

**调试（DEBUG）**：详细日志
```bash
java -Dlogback.configurationFile=logback-debug.xml -jar zzuli-evaluation.jar
```

## 📋 要求

- Java 21+

## ⚠️ 免责声明

仅供学习交流使用。使用者应遵守学校规定，认真对待教学评价。

---

**Author**: ShiYi | **License**: MIT
