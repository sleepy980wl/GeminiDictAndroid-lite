package com.geminidict.app.data.dict

enum class DictFormat {
    ECDICT_CSV,
    CC_CEDICT_GZ,
    KAJWEB_JSONL_ZIP,
    XINHUA_WORD_JSON,
    XINHUA_IDIOM_JSON,
    XINHUA_CI_JSON,
    XINHUA_XIEHOUYU_JSON
}

data class DictionaryCatalogItem(
    val id: String,
    val name: String,
    val categoryLabel: String,
    val description: String,
    val approxSize: String,
    val approxSizeMb: Int,
    val approxEntries: String,
    val license: String,
    val downloadUrl: String,
    val isGithubRaw: Boolean,
    val format: DictFormat
)

/**
 * Verified publicly downloadable open-source offline dictionaries.
 * Includes kajweb/dict JSONL ZIP books that contain authentic bilingual example sentences.
 */
object DictionaryCatalog {
    val items: List<DictionaryCatalogItem> = listOf(
        DictionaryCatalogItem(
            id = "kajweb_level8",
            name = "专八核心词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "含美/英音标、中英释义与真实中英双语例句，支持离线朗读例句，安装后可与 ECDICT 自动合并补全例句。",
            approxSize = "~5.1 MB",
            approxSizeMb = 5,
            approxEntries = "12,197 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1521164650006_Level8luan_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP
        ),
        DictionaryCatalogItem(
            id = "kajweb_toefl",
            name = "TOEFL 核心词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "托福高频词库，每词附带美/英音标、中文释义及中英双语例句，下载快。",
            approxSize = "~4.1 MB",
            approxSizeMb = 4,
            approxEntries = "9,213 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1521164640451_TOEFL_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP
        ),
        DictionaryCatalogItem(
            id = "kajweb_kaoyan",
            name = "考研英语词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "考研核心词库，含音标、多词性释义及中英双语例句。",
            approxSize = "~2.3 MB",
            approxSizeMb = 2,
            approxEntries = "4,533 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1521164654696_KaoYan_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP
        ),
        DictionaryCatalogItem(
            id = "kajweb_cet4",
            name = "四级英语词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "大学英语四级核心词库，含音标、释义与中英双语例句。",
            approxSize = "~2.6 MB",
            approxSizeMb = 3,
            approxEntries = "3,739 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1524052539052_CET4luan_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP
        ),
        DictionaryCatalogItem(
            id = "kajweb_ielts",
            name = "雅思核心词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "IELTS 高频词库，含音标、释义与中英双语例句。",
            approxSize = "~1.5 MB",
            approxSizeMb = 2,
            approxEntries = "3,427 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1521164624473_IELTSluan_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP
        ),
        DictionaryCatalogItem(
            id = "ecdict_full",
            name = "ECDICT 完整英汉词典",
            categoryLabel = "英汉",
            description = "77 万+ 完整开源英汉词库，含音标、中英释义、柯林斯星级、牛津3000与词频（支持断点续传；本身不含例句，可搭配上方例句词库或 Gemini 使用）。",
            approxSize = "~66 MB",
            approxSizeMb = 66,
            approxEntries = "约 77 万词条",
            license = "MIT (skywind3000/ECDICT)",
            downloadUrl = "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv",
            isGithubRaw = true,
            format = DictFormat.ECDICT_CSV
        ),
        DictionaryCatalogItem(
            id = "cc_cedict",
            name = "CC-CEDICT 汉英词典",
            categoryLabel = "汉英",
            description = "持续维护的开源汉英词典，支持简体/繁体中文查英文释义及拼音标注。",
            approxSize = "~4 MB",
            approxSizeMb = 4,
            approxEntries = "约 12.5 万词条",
            license = "CC BY-SA 4.0 (MDBG)",
            downloadUrl = "https://www.mdbg.net/chinese/export/cedict/cedict_1_0_ts_utf-8_mdbg.txt.gz",
            isGithubRaw = false,
            format = DictFormat.CC_CEDICT_GZ
        ),
        DictionaryCatalogItem(
            id = "xinhua_word",
            name = "中华新华字典 · 汉字库",
            categoryLabel = "新华字典",
            description = "收录 1.6 万余汉字，含拼音、部首、笔画数、基本释义及古汉语详解。",
            approxSize = "~18 MB",
            approxSizeMb = 18,
            approxEntries = "16,142 汉字",
            license = "MIT (pwxcoo/chinese-xinhua)",
            downloadUrl = "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/word.json",
            isGithubRaw = true,
            format = DictFormat.XINHUA_WORD_JSON
        ),
        DictionaryCatalogItem(
            id = "xinhua_idiom",
            name = "中华新华字典 · 成语词典",
            categoryLabel = "新华字典",
            description = "收录 3.1 万余条汉语成语，包含拼音、释义、典故出处与例句。",
            approxSize = "~13 MB",
            approxSizeMb = 13,
            approxEntries = "31,648 成语",
            license = "MIT (pwxcoo/chinese-xinhua)",
            downloadUrl = "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/idiom.json",
            isGithubRaw = true,
            format = DictFormat.XINHUA_IDIOM_JSON
        ),
        DictionaryCatalogItem(
            id = "xinhua_ci",
            name = "中华新华字典 · 汉语词语库",
            categoryLabel = "新华字典",
            description = "收录 26 万余条汉语词汇与释义，适合中文词义离线检索。",
            approxSize = "~33 MB",
            approxSizeMb = 33,
            approxEntries = "264,434 词语",
            license = "MIT (pwxcoo/chinese-xinhua)",
            downloadUrl = "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/ci.json",
            isGithubRaw = true,
            format = DictFormat.XINHUA_CI_JSON
        ),
        DictionaryCatalogItem(
            id = "xinhua_xiehouyu",
            name = "中华新华字典 · 歇后语库",
            categoryLabel = "新华字典",
            description = "收录 1.4 万余条传统歇后语（谜面与谜底）。",
            approxSize = "~1.5 MB",
            approxSizeMb = 2,
            approxEntries = "14,032 歇后语",
            license = "MIT (pwxcoo/chinese-xinhua)",
            downloadUrl = "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/xiehouyu.json",
            isGithubRaw = true,
            format = DictFormat.XINHUA_XIEHOUYU_JSON
        )
    )
}
