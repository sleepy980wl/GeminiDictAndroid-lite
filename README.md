# Gemini词典 (Android 原生离线词典 + 离线神经语音 + FTS4 毫秒模糊检索 + Gemini AI + 有道单词本)

基于 **Kotlin + Jetpack Compose (Material 3) + Room (FTS4) + Sherpa-ONNX (Piper VITS) + OkHttp + Android Keystore** 构建的离线优先双语词典与背单词应用。

## 核心功能

1. **17 套公开离线词典一键下载 + 本地自有词典导入**
   - 全部支持 HTTP `Range` 断点续传、完整性校验与 Room 原子事务替换：
     - **英英公版权威**：[韦氏英英大词典 (Webster's Unabridged 公版)](https://github.com/matthewreagan/WebstersEnglishDictionary)（~28 MB，约 10.2 万词条）
     - **英汉大词库（含柯林斯星级 / 牛津3000 标注）**：[ECDICT 标准版](https://github.com/skywind3000/ECDICT)（77 万词，~66 MB）、[ECDICT 增强终极版](https://github.com/skywind3000/ECDICT-ultimate/releases)（340 万词，~94 MB）
     - **9 本含真实中英双语例句词库**（[kajweb/dict](https://github.com/kajweb/dict)：专八、TOEFL、GRE、考研、SAT、四级、六级、雅思、商务英语 BEC，自动与 ECDICT 合并补全例句）
     - **汉英词典**：[CC-CEDICT 汉英大词典](https://www.mdbg.net/chinese/dictionary?page=cedict)（12.5 万词，~4 MB）
     - **中文词典**：[中华新华字典](https://github.com/pwxcoo/chinese-xinhua)（汉字、成语、词语、歇后语 4 套独立库）
   - **本地自有词典导入**：受版权限制，现代商业版权词典（如牛津高阶、柯林斯 COBUILD、现代韦氏、新汉英大词典等）不内置下载链接；如你本地已有合法获取的 CSV/TSV/JSON/GZ/ZIP 格式词典，可在「词库」页点击「导入本地自有词典」直接导入并建立 FTS 索引。
2. **FTS4 毫秒级查词 + 英语拼写纠错模糊匹配**
   - Room v3 接入 `@Fts4` 全文倒排索引（附显式 `MIGRATION_1_2` 与 `MIGRATION_2_3`），彻底消除 77 万/340 万词库的全表扫描。
   - 查词采用 4 阶段加速：B-Tree 精确/前缀索引 → 英语词形还原（如 `studies → study`）→ FTS4 倒排全文匹配 → 索引化 **Damerau-Levenshtein 拼写纠错**（自动纠正漏字、多字、换位如 `recieve → receive`、`accomodate → accommodate`）。
3. **纯离线真人感神经语音朗读（Sherpa-ONNX + Piper VITS）**
   - 集成 [Sherpa-ONNX v1.12.31](https://github.com/k2-fsa/sherpa-onnx/releases)（保留内置 `espeak-ng`），支持断点下载 5 款 [Piper 英文离线神经语音模型](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models)（英音 Cori/Alba/Alan、美音 Lessac 各约 63 MB，美音 Amy 约 67 MB），并自动回退系统离线 TTS。
4. **Gemini API 深度翻译（可选）+ 有道单词本导入导出 + SM-2 遗忘曲线复习**
   - 不填 Gemini Key 也可完整离线使用；支持无 Root ADB 导入有道单词本（`tools/adb_import_youdao.py`）与防丢卡的本轮重学队列。

## 编译与构建
- **Android Studio**：解压后打开 `GeminiDictAndroid`，构建时自动拉取 `sherpa-onnx-1.12.31.aar`。
- **GitHub Actions**：推送至 GitHub 即可自动拉取 AAR、运行全部 JUnit 单元测试并生成 `GeminiDict.apk`。
