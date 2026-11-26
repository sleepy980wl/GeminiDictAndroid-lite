# Gemini词典 (Android 原生离线词典 + Gemini AI + 有道单词本 + 遗忘曲线)

基于 **Kotlin + Jetpack Compose (Material 3) + Room + OkHttp + Android Keystore** 构建的离线优先中英双语词典与背单词应用。

## 核心功能

1. **默认离线中英互译、双语例句词库 & 中华新华字典**
   - 内置 59 个示例高频词供首次启动验证，可在「词库」页一键下载完整公开离线词典。
   - **离线词典一键下载中心**（支持 HTTP `Range` 断点续传、完整性校验、Room 原子事务替换、大文件确认与取消下载）：
     - **带真实双语例句的开源英汉词库**（来自 [kajweb/dict](https://github.com/kajweb/dict)，与 ECDICT 共存时自动合并补全双语例句）：
       - 专八核心词汇（12,197 词，~5.1 MB）
       - TOEFL 核心词汇（9,213 词，~4.1 MB）
       - 考研英语词汇（4,533 词，~2.3 MB）
       - 四级英语词汇（3,739 词，~2.6 MB）
       - 雅思核心词汇（3,427 词，~1.5 MB）
     - [ECDICT 完整英汉词典](https://github.com/skywind3000/ECDICT) (`ecdict.csv`, ~66 MB, 约 77 万词条)
     - [CC-CEDICT 汉英词典](https://www.mdbg.net/chinese/dictionary?page=cedict) (`cedict_1_0_ts_utf-8_mdbg.txt.gz`, ~4 MB, 约 12.5 万词条)
     - [中华新华字典](https://github.com/pwxcoo/chinese-xinhua)：汉字库（16,142 字）、成语词典（31,648 条）、汉语词语库（264,434 条）、歇后语库（14,032 条）
   - 设置页支持填写可选的 GitHub Raw 镜像加速前缀，方便国内网络下载。
2. **Gemini API 深度翻译（可选，不填 Key 也可完整离线使用）**
   - 调用 [Gemini Developer API](https://aistudio.google.com/api-reference) (`generateContent`) 提供精准语境翻译、词性、音标、用法解析与双语例句，支持 HTTP 429/503 自动退避重试与设置页「测试连接」。
   - API Key 通过 Android Keystore (`EncryptedSharedPreferences`) 加密存储，支持 Keystore 失效自动恢复/降级兜底，并已排除云备份与换机迁移。
   - 收藏 Gemini 释义到单词本时自动与已有词条合并，完整保留原有 SM-2 复习进度。
3. **英文离线发音与例句朗读**
   - 基于 Android 原生 TextToSpeech (TTS) 懒加载初始化，优先匹配本机已安装的离线英文语音包，支持美音 (`en-US`) / 英音 (`en-GB`) 切换、语速调节与单词/例句完全离线朗读。
4. **有道「我的单词本」导入与完整单词本功能**
   - 支持收藏、分组筛选、关键词搜索、4 种排序、编辑释义/例句/笔记、删除二次确认、熟练度进度条以及双向导出为有道标准 XML 格式。
   - 支持批量事务导入有道 `.xml` / `.txt` / `.csv` / `.db` 文件，导入时自动去重、按原有 `progress` 错峰安排复习日期，并利用本地离线词典批量补全音标、释义与双语例句。
   - **无 Root 手机 ADB 一键导入工具**（`tools/adb_import_youdao.py`）：
     ```bash
     python3 tools/adb_import_youdao.py --file /path/to/youdao_wordbook.xml
     python3 tools/adb_import_youdao.py --scan-phone
     ```
5. **遗忘曲线智能学习与复习 (SM-2)**
   - 基于 SM-2 / 艾宾浩斯遗忘曲线调度每日待复习队列；点选「忘记」的词（含最后一张卡）会进入本轮重学池，即使点击刷新也不会丢失，直到回忆通过。
   - 支持**词卡翻转模式**与**拼写听写模式**，实时显示剩余待复习数与「今日已记牢」计数（不含「忘记」）。

## 编译与构建

### 方式一：Android Studio 本地编译（推荐）
1. 解压源码包，使用 Android Studio (Ladybug / 2024.2+) 打开 `GeminiDictAndroid` 目录。
2. 连接手机或模拟器点击 **Run** 安装，或点击 **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** 生成 APK。

### 方式二：命令行本地编译（需本机已安装 Gradle 8.9+ 与 Android SDK 35）
```bash
gradle testDebugUnitTest
gradle assembleDebug
```

### 方式三：GitHub Actions 自动构建 APK
仓库已内置 `.github/workflows/android-build.yml`（显式配置 JDK 17、Android SDK 35、Gradle 8.9）。推送至 GitHub 后在 **Actions** 页面即可下载自动构建的 `GeminiDict-debug-apk`。
